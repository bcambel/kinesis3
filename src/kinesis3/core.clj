(ns kinesis3.core
  (:require 
    [clojure.string                         :as s]
    [clojure.tools.cli                      :refer [parse-opts]]
    [ring.adapter.jetty                     :refer [run-jetty]]
    [compojure.route                        :as route]
    [compojure.core                         :refer [defroutes GET POST DELETE ANY HEAD context]]
    [compojure.handler                      :refer [site]]
    [cheshire.core                          :refer :all]
    [amazonica.core                         :refer [with-credential defcredential]]
    [amazonica.aws.kinesis                  :as kinesis]
    [amazonica.aws.s3                       :as s3]
    [clojure.core.async                     :as async :refer [alts! go chan >!]]
    [com.stuartsierra.component             :as component]
    [metrics.core                           :refer [new-registry]]
    [metrics.meters                         :refer (meter mark! defmeter rates)]
    [metrics.histograms                     :refer [defhistogram update! percentiles mean std-dev number-recorded]]
    [metrics.timers                         :refer [deftimer time!] :as timers]
    [metrics.reporters.console              :as console]
    [metrics.reporters.jmx                  :as jmx]
    [kinesis3.log                           :as log-base]
    [byte-streams                           :refer [convert print-bytes]]
    [taoensso.timbre                        :as timbre
         :refer (log  trace  debug  info  warn  error  fatal  report sometimes)])
  (:gen-class))

(def reg (new-registry))
(defmeter  message-ingested )
(defmeter  s3-uploads )
(defhistogram  queue-size)
(deftimer s3-upload-timing)

(def JR (jmx/reporter {}))
(def CR (console/reporter {}))

(defn new-q [] (java.util.concurrent.ConcurrentLinkedDeque.))
; (def q (dq/queues "/tmp" {}))

(defn upload-to-s3
  "Upload given file to the bucket.
  TODO: 
    - Retry logic
    - Auto gzipping"
  [f bucket k]
  (let [date-time (java.util.Date.)
        bucket-name (format "%s/kinesis3/%s" bucket (.format (java.text.SimpleDateFormat. "yyyy/MM/dd") date-time))
        key-name (format "%s.records" k) ]
    (time! s3-upload-timing
      (s3/put-object :bucket-name bucket-name
                     :key key-name
                     :file f))))

(defn write-to-disk
  [temp-queue threshold s3-bucket]
  (let [metadata (atom []) 
        out-file (java.io.File/createTempFile "records" ".log")]
    (with-open [wrt (clojure.java.io/writer out-file)]
      (loop [idx 0 
             first-seq nil 
             last-seq nil]
        (let [[sequence data] (.poll temp-queue)]
          (.write wrt (format "%s %s \n" sequence data))
          (reset! metadata [first-seq last-seq idx])
          (when (< idx threshold)
            (recur (inc idx) (if (= idx 0) sequence first-seq) sequence)))))
    (info "Completed " out-file)
    (upload-to-s3 (.getAbsolutePath out-file) s3-bucket (s/join "-" @metadata))
    (mark! s3-uploads)
    ))

(defrecord S3Sink [msg-chan threshold sleep-time s3-bucket aws-options]
  component/Lifecycle

  (start [this]
    (info "Starting S3Sink Component")
    (let [temp-queue (new-q)
          threshold (or threshold 100)
          sleep-time (or sleep-time 3000)]
      ; Queue management; check size, if > threshold, send to s3
      (go (while true
        (loop [cnt 0]
          (let [buffer-size (.size temp-queue)]
            (info (format "SIZE: %s ITER: %s " buffer-size cnt))
            (update! queue-size buffer-size)
            (when (> buffer-size threshold)
              (write-to-disk temp-queue threshold s3-bucket)))
          (Thread/sleep sleep-time)
          (rates message-ingested)
          (recur (inc cnt)))))
      ; listen channel, write to QUEUE
      (go (while true
        (let [[[topic {:keys [sequence-number data partition]}] ch] (alts! [msg-chan])]
                (.add temp-queue [sequence-number data]))))))
  (stop [this]

    ))
    
(defn s3sink
  [channel s3-bucket aws-options batch-size]
  (map->S3Sink {:msg-chan channel :s3-bucket s3-bucket :aws-options aws-options :threshold batch-size} ))

;A Basic HTTP Interface for status, and management(?)
(defrecord HTTP [port pipe listener conf server]
  component/Lifecycle

  (start [this]
    (info "Starting HTTP Component")
    (let []
      (try 
        (defroutes app-routes
          (HEAD "/" [] "")
          (GET "/"  request {:status  200 :body "ok" })
          (GET "/ping" request {:status  200 :body "pong" })
          (GET "/stats" request {:status 200 :headers {"Content-Type" "application/json"} 
                                             :body (generate-string { 
                                                      :events (rates message-ingested)
                                                      :s3-uploads (rates s3-uploads)
                                                      :s3-upload-timing {:percentile (timers/percentiles s3-upload-timing)
                                                                          :calls (timers/number-recorded s3-upload-timing)
                                                                          :min (/ (timers/smallest s3-upload-timing) 100000) ;ms
                                                                          :std-dev (/ (timers/std-dev s3-upload-timing) 100000) ;ms
                                                                          :mean (/ (timers/mean s3-upload-timing) 100000) ;ms
                                                                          }
                                                      :buffer {:percentiles (percentiles queue-size)
                                                                :mean (mean queue-size)
                                                                :std-dev (std-dev queue-size)
                                                                :records (number-recorded queue-size)
                                                                }
                                                      })})
          (route/not-found "<p>Page not found.</p>"))

        (let [server (run-jetty (site #'app-routes) {:port port :join? false})]
          (info "Listening events now...")
          (assoc this :server server))
      (catch Throwable t 
        (do 
          (error t))))))

  (stop [this]
    (.stop server)))

(defn http-server
  [port]
  (map->HTTP {:port port}))

(defn start-worker
  [msg-chan app-name aws-key aws-secret aws-endpoint aws-kinesis-stream]
  (kinesis/worker!  
    :app app-name
    :stream aws-kinesis-stream
    :checkpoint 10
    :credentials {:access-key aws-key :secret-key aws-secret :endpoint aws-endpoint }
    :endpoint (format "kinesis.%s.amazonaws.com" aws-endpoint)
    :processor (fn [records]
                  (doseq [row records]
                    (go (>! msg-chan ["master" row]))
                    (mark! message-ingested)))))

(defrecord KinesisConsumer [pipe app-name aws-key aws-secret aws-endpoint aws-kinesis-stream cons-chan channel]
  component/Lifecycle

  (start [component]
    (try
      (warn "Starting KINESIS CONSUMER Component " aws-kinesis-stream aws-key aws-endpoint)

      (start-worker pipe app-name aws-key aws-secret aws-endpoint aws-kinesis-stream)
      (assoc component :channel (chan))
      (catch Throwable t 
        (do
        (warn "[KINESIS-CONSUMER] FAILED")
        (error t)
        )))))

(defn kinesis-consumer
  "Returns a new kinesis producer with the given options"
  [options]
  (map->KinesisConsumer options))

(def cli-options 
  [["-p" "--port PORT" "Port number"
    :default 8888
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
    ["-a" "--app-name NAME" "Application name to use for Kinesis Stream" :default "kinesis-sample-consumer0"]
    ["-c" "--checkpoint CHECKPOINT" "Checkpoint where the application left" :validate [false "Checkpoint not implemented yet!"]] ; TODO
    [nil "--aws-key KEY" "AWS KEY" ]
    [nil "--aws-secret SECRET" "AWS SECRET" ]
    [nil "--aws-endpoint ENDPOINT" "AWS ENDPOINT to use" :default "eu-west-1"]
    [nil "--aws-kinesis-stream STREAM" "AWS Kinesis Stream name" ]
    ["-s" "--s3-bucket BUCKET" "S3 Bucket to output" :parse-fn str :validate [#(> (count %) 0) "S3 Bucket must be supplied"] ]
    ["-b"  "--batch-size SIZE" :default 100 :parse-fn #(Integer/parseInt %)]
    ["-h" "--help"]
    ])


(defn app-system 
  [options]
  (let [{:keys [port aws-key aws-secret aws-endpoint aws-kinesis-stream pipe s3-bucket app-name batch-size]} options
      msg-chan (chan 65535)
      aws-options (select-keys options [:aws-key :aws-secret :aws-endpoint :aws-kinesis-stream])]
    
    (defcredential aws-key aws-secret aws-endpoint)

    (-> (component/system-map 
          :sink (s3sink msg-chan s3-bucket aws-options batch-size)
          :pipe (kinesis-consumer (merge {:pipe msg-chan :app-name app-name  } aws-options))
          :app (component/using 
              (http-server port)
              [:pipe]
            )))))

(defn -main
  [& args]
  (reset! timbre/config log-base/log-config )
  (let [{:keys [options summary errors]} (parse-opts args cli-options)]
    (when (:help options)
      (println summary)
      (System/exit 0))

    (when errors
      (println errors)
      (System/exit 1))

    ;remove this when the tools.cli required option works!
    (when-not (contains? options :s3-bucket)
      (println "Missing S3 Bucket setting. Please supply `--s3-bucket` option")
      (System/exit 1))

    (info "Options-> " (select-keys options [:app-name :checkpoint :aws-kinesis-stream :s3-bucket :batch-size]))

    (jmx/start JR)
    ; report to console in every 100 seconds
    (console/start CR 100)
    (let [sys (component/start (app-system options))]
      (info "System started.."))))
