(defproject clj-finagle "0.1.1"
  :description "Clojure wrappers around Twitter's Finagle"
  :url "http://github.com/jcrean/clj-finagle"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["twitter" "http://maven.twttr.com/"]]
  :dev-dependencies [[swank-clojure "1.4.2"]]
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.twitter/finagle-core "6.3.0"]
                 [com.twitter/finagle-thrift "6.3.0"]
                 [com.twitter/scrooge-runtime "3.1.1"]
                 [org.apache.thrift/libthrift "0.8.0"]])
