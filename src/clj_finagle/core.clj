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

(defn lookup-rpc [service-id]
  (get @rpc-registry service-id))

(defn rpc-server [service-id]
  (get-in @rpc-registry [service-id :server]))

(defn register-rpc [the-name config]
  (swap! rpc-registry
         update-in [the-name]
         merge config))

(defn rpc-service-impl [service-id]
  (when-not (get-in @rpc-registry [service-id :service-impl])
    (register-rpc
     service-id
     {:service-impl (clojure.lang.Reflector/invokeConstructor
                     (resolve (symbol (str (get-in @rpc-registry [service-id :service]) "$FinagledService")))
                     (to-array [(processor (get-in @rpc-registry [service-id :processor])) (TBinaryProtocol$Factory.)]))}))
  (get-in @rpc-registry [service-id :service-impl]))

(defn rpc-server-builder [service-id]
  (when-not (get-in @rpc-registry [service-id :server-builder])
    (register-rpc
     service-id
     {:server-builder (.. (ServerBuilder/get)
                          (name   (get-in @rpc-registry [service-id :name]))
                          (codec  (ThriftServerFramedCodec/get))
                          (bindTo (InetSocketAddress. (get-in @rpc-registry [service-id :port]))))}))
  (get-in @rpc-registry [service-id :server-builder]))

(defn rpc-client [service-id]
  (when-not (get-in @rpc-registry [service-id :client])
    (register-rpc
     service-id
     {:client (clojure.lang.Reflector/invokeConstructor
               (resolve (symbol (str (get-in @rpc-registry [service-id :service]) "$FinagledClient")))
               (to-array [(ClientBuilder/safeBuild
                           (.. (ClientBuilder/get)
                               (hosts (InetSocketAddress. (get-in @rpc-registry [service-id :port])))
                               (codec (ThriftClientFramedCodec/get))
                               (hostConnectionLimit (or (get-in @rpc-registry [service-id :host-connection-limit]) 1))))
                          (TBinaryProtocol$Factory.)
                          (get-in @rpc-registry [service-id :name])
                          (make-stats-receiver (get-in @rpc-registry [service-id :stats-receiver]))]))}))
  (get-in @rpc-registry [service-id :client]))


(defn start-server [service-id]
  (register-rpc
   service-id
   {:server (ServerBuilder/safeBuild
             (rpc-service-impl service-id)
             (rpc-server-builder service-id))}))

(defn shutdown-server [service-id]
  (-> (rpc-server service-id)
      (.close)))

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

(defn def-server [service-id config]
  (register-rpc
   service-id
   {:server-builder (.. (ServerBuilder/get)
                        (name   (:name config))
                        (codec  (ThriftServerFramedCodec/get))
                        (bindTo (InetSocketAddress. (:port config))))
    :service-impl   (clojure.lang.Reflector/invokeConstructor
                     (resolve (symbol (str (:service config) "$FinagledService")))
                     (to-array [(processor (:processor config)) (TBinaryProtocol$Factory.)]))})
  (when (:autostart-server config)
    (start-server service-id)))

(defn def-client [service-id config]
  (register-rpc
   service-id
   {:client
    (clojure.lang.Reflector/invokeConstructor
     (resolve (symbol (str (:service config) "$FinagledClient")))
     (to-array [(ClientBuilder/safeBuild
                 (.. (ClientBuilder/get)
                     (hosts (InetSocketAddress. (:port config)))
                     (codec (ThriftClientFramedCodec/get))
                     (hostConnectionLimit (or (:host-connection-limit config) 1))))
                (TBinaryProtocol$Factory.)
                (:name config)
                (make-stats-receiver (:stats-receiver config))]))}))


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

       (register-rpc ~service-id {:server-builder server-builder#
                                  :service-impl   service-impl#
                                  :client client#})

       (when ~(:autostart-server cfg-map)
         (start-server ~service-id)))))


(def ^:dynamic current-service-id)

(defn with-service* [service-id body-fn]
  (binding [current-service-id service-id]
    (body-fn)))

(defmacro with-service [service-id & body]
  `(with-service* ~service-id (fn [] ~@body)))

(defmacro call [fn-call success-cb failure-cb]
  `(do
     (.. (rpc-client current-service-id)
         ~fn-call
         (addEventListener
          (proxy [FutureEventListener] []
            ~success-cb
            ~failure-cb)))
     :request-sent-to-server))

(defmacro call-service [service-id fn-call success-cb failure-cb]
  `(with-service ~service-id
     (call
      ~fn-call
      ~success-cb
      ~failure-cb)))