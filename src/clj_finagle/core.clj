(ns clj-finagle.core
  (:import
   [java.net InetSocketAddress]
   [org.apache.thrift.protocol TBinaryProtocol$Factory]
   [com.twitter.finagle.builder ServerBuilder ClientBuilder]
   [com.twitter.finagle.thrift ThriftServerFramedCodec ThriftClientFramedCodec]
   [com.twitter.finagle.stats InMemoryStatsReceiver]
   [com.twitter.util Future FutureEventListener]))

(defonce processor-registry (atom {}))

(defonce rpc-registry (atom {}))

(defn rpc-server [service-id]
  (get-in @rpc-registry [service-id :server]))

(defn rpc-client [service-id]
  (get-in @rpc-registry [service-id :client]))

(defn start-server [service-id]
  (swap! rpc-registry
         assoc
         :service
         (ServerBuilder/safeBuild
          (get-in @rpc-registry [service-id :service-impl])
          (get-in @rpc-registry [service-id :server-builder]))))

(defn shutdown-server [service-id]
  (-> (rpc-server service-id)
      (.close)))

(defn register [the-name config]
  (swap! rpc-registry assoc the-name config))

(defn processor [processor-id]
  (get @processor-registry processor-id))

(defmacro def-processor [processor-id service-type & fn-sigs]
  `(swap! processor-registry assoc ~processor-id
          (proxy [~(symbol (str service-type "$FutureIface"))] []
            ~@fn-sigs)))

(def stats-receiver-makers
     {:in-memory
      (fn [] (InMemoryStatsReceiver.))
      :default
      (fn [] (InMemoryStatsReceiver.))})

(defn make-stats-receiver [type]
  (let [make-fn (or (get stats-receiver-makers type)
                    (:default stats-receiver-makers))]
    (make-fn)))

(defmacro def-rpc [service-id & config]
  (let [cfg-map (apply hash-map config)]
    `(let [service-impl#
           (~(symbol (str (:service cfg-map) "$FinagledService."))
            (processor ~(:processor cfg-map))
            (TBinaryProtocol$Factory.))

           server-builder#
           (.. (ServerBuilder/get)
               (name   ~(:name cfg-map))
               (codec  (ThriftServerFramedCodec/get))
               (bindTo (InetSocketAddress. ~(:port cfg-map))))

           client#
           (~(symbol (str (:service cfg-map) "$FinagledClient."))
            (ClientBuilder/safeBuild
             (.. (ClientBuilder/get)
                 (hosts (InetSocketAddress. ~(:port cfg-map)))
                 (codec (ThriftClientFramedCodec/get))
                 (hostConnectionLimit ~(or (:host-connection-limit cfg-map) 1))))
            (TBinaryProtocol$Factory.)
            ~(:name cfg-map)
            (make-stats-receiver (or ~(:stats-receiver cfg-map) :in-memory)))]

       (register ~service-id {:server-builder server-builder#
                              :service-impl   service-impl#
                              :client client#})

       (when ~(:autostart-server cfg-map)
         (start-server ~service-id)))))


(defmacro def-server [name & config]
  (let [cfg-map (apply hash-map config)]
    `(def ~name
          (ServerBuilder/safeBuild
           (~(symbol (str (:service cfg-map) "$FinagledService.")) ~(:processor cfg-map) (TBinaryProtocol$Factory.))
           (.. (ServerBuilder/get)
               (name   ~(:name cfg-map))
               (codec  (ThriftServerFramedCodec/get))
               (bindTo (InetSocketAddress. ~(:port cfg-map))))))))

(defmacro def-client [name & config]
  (let [cfg-map (apply hash-map config)]
    `(def ~name
          (~(symbol (str (:service cfg-map) "$FinagledClient."))
           (ClientBuilder/safeBuild
            (.. (ClientBuilder/get)
                (hosts (InetSocketAddress. ~(:port cfg-map)))
                (codec (ThriftClientFramedCodec/get))
                (hostConnectionLimit ~(:host-connection-limit cfg-map))))
           (TBinaryProtocol$Factory.)
           ~(:name cfg-map)
           ~(make-stats-receiver (:stats-receiver cfg-map))))))


(defmacro def-client-api [fn-name arglist cfg success-cb failure-cb]
  `(defn ~fn-name ~arglist
     (.. (rpc-client ~(:for-service cfg))
         (~fn-name ~@arglist)
         (addEventListener
          (proxy [FutureEventListener] []
            ~success-cb
            ~failure-cb)))))