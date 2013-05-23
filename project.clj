(defproject clj-finagle "0.1.0-SNAPSHOT"
  :description "Clojure wrappers around Twitter's Finagle"
  ;;:java-source-path "src/java"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["twitter" "http://maven.twttr.com/"]]
  :dev-dependencies [[swank-clojure "1.4.2"]]
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.twitter/finagle-core "6.3.0"]
                 [com.twitter/finagle-thrift "6.3.0"]
                 ;; [com.twitter/util-core "6.3.3"]
                 [com.twitter/scrooge-runtime "3.1.1"]
                 [org.apache.thrift/libthrift "0.8.0"]])
