(defproject kinesis3 "0.1.1"
  :description "Save Amazon Kinesis streams to S3"
  :url "http://github.com/bcambel/kinesis3"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [
          [org.clojure/clojure "1.8.0"]
          [org.clojure/core.async "0.2.374"]
          [org.clojure/tools.cli "0.3.1"]
          [com.stuartsierra/component "0.3.1"]
          [compojure "1.5.0"]
          [ring "1.4.0"]
          [com.taoensso/timbre "4.3.1"]
          [cheshire "5.6.0"]
          [amazonica "0.3.57"]
          [metrics-clojure "2.4.0"]
          [byte-streams "0.2.0"]
          ; [com.taoensso/nippy "2.7.0"]
          ]

  :main ^:skip-aot kinesis3.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:plugins [[lein-midje "3.1.3"]]
                   :dependencies [[midje "1.6.0" :exclusions [org.clojure/clojure]]]}
  })