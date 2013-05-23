(ns clj-finagle.core
  (:import
   [java.net InetSocketAddress]
   [org.apache.thrift.protocol TBinaryProtocol$Factory]
   [com.twitter.finagle.builder ServerBuilder ClientBuilder]
   [com.twitter.finagle.thrift ThriftServerFramedCodec ThriftClientFramedCodec]
   [com.twitter.finagle.stats InMemoryStatsReceiver]
   [com.twitter.util Future FutureEventListener]))

(defmacro def-processor [name service-type & fn-sigs]
  `(def ~name
        (proxy [~(symbol (str service-type "$FutureIface"))] []
          ~@fn-sigs)))

(defmacro def-server [name & config]
  (let [cfg-map (apply hash-map config)]
    `(def ~name
          (ServerBuilder/safeBuild
           (~(symbol (str (:service cfg-map) "$FinagledService.")) ~(:processor cfg-map) (TBinaryProtocol$Factory.))
           (.. (ServerBuilder/get)
               (name   ~(:name cfg-map))
               (codec  (ThriftServerFramedCodec/get))
               (bindTo (InetSocketAddress. ~(:port cfg-map))))))))

(def stats-receiver-makers
     {:in-memory
      (fn [] (InMemoryStatsReceiver.))})

(defn- make-stats-receiver [type]
  (if-let [make-fn (get stats-receiver-makers type)]
    (make-fn)
    (throw (RuntimeException. (format "dont know how to make that: %s" type)))))

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


(defmacro def-api-for [the-client fn-name arglist client-call success-handler failure-handler]
  `(defn ~fn-name ~arglist
     (.. ~the-client
         ~client-call
         (addEventListener
          (proxy [FutureEventListener] []
            ~success-handler
            ~failure-handler)))))