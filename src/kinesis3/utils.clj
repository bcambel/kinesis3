(ns kinesis3.utils
  (:require [clojure.string :as s]
            [cheshire.core :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :refer [from-long]]
            [taoensso.timbre :as log]
            [clojure.core.memoize :refer [memo]])
   (:import  [java.net URLDecoder URLEncoder]
              ; [eu.bitwalker.useragentutils UserAgent DeviceType Browser OperatingSystem]
              ))

(def custom-formatter (f/formatter "yyyy-MM-dd"))
(def date-format (f/formatter "yyyy-MM-dd"))
(def datetime-formatter (f/formatter "yyyy-MM-dd'T'HH:mm:ss"))

(defn separate-number-data
  [line]
  [(subs line 0 56) (subs line 57)])

(defn epoch->date
  [epoch]
  (try
    (-> (Long/parseLong epoch)
        (from-long))
    (catch Throwable t
      (do
        (log/error "[" epoch "] failed to parse")
        (t/now)
      ))))

(defn get-day
  [epoch]
  (->> (epoch->date epoch)
       (f/unparse custom-formatter)))

(defn epoch->datetime
  [epoch]
  (->> (epoch->date epoch)
       (f/unparse datetime-formatter)))

(defn date->str
  [date]
  (f/unparse date-format date))

(defn str->date
  [date]
  (f/parse date-format date))


(defn get-hour
  "Expects a timestamp in milliseconds
  Returns the starting second(!) of the hour.
  Ex: Passing 1437609280911 (Wed, 22 Jul 2015 23:54:40 GMT)
  1437606000 (GMT: Wed, 22 Jul 2015 23:00:00 GMT)
  "
  [epoch]
  (let [epoch (Long/parseLong epoch)
        date (from-long epoch)]
    ; remove extra minute and seconds
  (- (int (Math/floor (/ epoch 1000)))
    (+ (* (.getMinuteOfHour date) 60)
       (.getSecondOfMinute date)))))

(defn extract-json
  [data]
  (try (or (parse-string data true) {})
    (catch Throwable t
      (do
        (log/error t)
        ))))

(defn as-vector [x]
  (cond
    (vector? x) x
    (sequential? x) (vec x)
    :else (vector x)))

(defn >urlsafe-str
  [s]
  (URLEncoder/encode s "UTF-8"))

(defn <urlsafe-str
  [s]
  (URLDecoder/decode s "UTF-8"))

(defn proc-id
  "Returns Process id"
  []
  (-> (java.lang.management.ManagementFactory/getRuntimeMXBean)
   (.getName)
   (s/split #"@")
   (first)))

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) seq))


(defn select-values
  "clojure.core contains select-keys
  but not select-values."
  [m ks]
  (reduce
    #(if-let [v (m %2)]
        (conj %1 v) %1)
    [] ks))
