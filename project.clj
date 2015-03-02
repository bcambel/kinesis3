(defproject kinesis3 "0.1.0-SNAPSHOT"
  :description "Save Amazon Kinesis streams to S3"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
          [org.clojure/clojure "1.6.0"]
          [org.clojure/core.memoize "0.5.6"]
          [org.clojure/core.async "0.1.346.0-17112a-alpha"]
          [org.clojure/tools.cli "0.3.1"]
          [com.stuartsierra/component "0.2.2"]
          [compojure "1.3.1"]
          [ring "1.3.2"]
          [com.taoensso/timbre "3.3.1-1cd4b70" :exclusions [org.clojure/tools.reader]]
          [cheshire "5.4.0"]
          [amazonica "0.3.18" :exclusions [com.taoensso/encore org.clojure/tools.reader]]
          [metrics-clojure "2.4.0"]
          [byte-streams "0.2.0-alpha8"]

  ]
  :main ^:skip-aot kinesis3.core
  :target-path "target/%s"
  :uberjar-name "kinesis3.jar"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-midje "3.1.3"]]}
  })
