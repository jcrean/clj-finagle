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
      (fn [] (InMemoryStatsReceiver.))})

(defn make-stats-receiver [type]
  (if-let [make-fn (get stats-receiver-makers type)]
    (make-fn)
    (throw (RuntimeException. (format "dont know how to make that: %s" type)))))

(defmacro def-rpc [service-id & config]
  (let [cfg-map (apply hash-map config)]
    `(let [server#
           (ServerBuilder/safeBuild
            (~(symbol (str (:service cfg-map) "$FinagledService.")) (processor ~(:processor cfg-map)) (TBinaryProtocol$Factory.))
            (.. (ServerBuilder/get)
                (name   ~(:name cfg-map))
                (codec  (ThriftServerFramedCodec/get))
                (bindTo (InetSocketAddress. ~(:port cfg-map)))))
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
       (register ~service-id {:server server#
                              :client client#}))))


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