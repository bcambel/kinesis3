(ns kinesis3.db
  (:require [clojure.java.jdbc :as jdbc]
    [com.stuartsierra.component :as component]
    [honeysql.core :as sql]
    [honeysql.helpers :refer :all]
    [taoensso.timbre :as log]
    [cheshire.core :refer :all]
    [kinesis3.utils :refer [in? epoch->datetime <urlsafe-str]])
  (:import [org.postgresql.util PGobject]))

(def DEFAULT-DB-SPEC
{:classname   "org.postgresql.Driver"
 :subprotocol "postgresql"
 :subname "events"
 :user     "pgusr"
 :password "pgusr"
 :host "127.0.0.1"
 :db "events"})


(defrecord Database [host port connection]
;; Implement the Lifecycle protocol
component/Lifecycle

(start [component]
  (println ";; Starting database")

  (let [conn nil]

    (assoc component :connection conn)))

(stop [component]
  (println ";; Stopping database")
   (assoc component :connection nil)))

(defn new-database [host port]
(map->Database {:host host :port port}))

(def pg-db DEFAULT-DB-SPEC)

(def insert-queue (java.util.ArrayList. 1000))

(defn pg-dt [value]
(doto (PGobject.)
  (.setType "timestamp")
  (.setValue value)))

(defn pg-json [value]
  (doto (PGobject.)
    (.setType "json")
    (.setValue value)))


(defmacro with-safe
  [& body]
  `(try
    ~@body
    (catch org.postgresql.util.PSQLException psqlex#
      (log/warn (.getSQLState psqlex#) (.getMessage psqlex#))
    )
  (catch Throwable t#
    (do (log/warn (.getMessage t#))
      nil))))

(defn query!
[db q]
  (with-safe
    (jdbc/query db q))
)

(defn find-ids
[ids]
(map :id (query! pg-db
    (-> (select :id)
       (from :events)
       (where [:in :id ids])
       (limit 1e4)
       (sql/build)
       (sql/format :quoting :ansi)))))


(defmulti purify (fn [action dataset] action))

(defmethod purify :update
 [m dataset]
 (throw (java.lang.UnsupportedOperationException. "Purify UPDATE not Implemented")))

(defmethod purify :delete
 [m dataset]
 (throw (java.lang.UnsupportedOperationException. "Purify Delete not Implemented")))


(defmethod purify :diff
 [m dataset]
 (let [ids (set (map #(get % "id") dataset))
       existing-ids (set (or (find-ids ids) []))
       new-ids (clojure.set/difference ids existing-ids)]
     (log/warn "New IDS: " (count new-ids) (vec (take 3 new-ids)))
     (filter #(and (in? new-ids (get % "id"))
              true;  (= "pv" (get % "evt_name")
               )
        dataset)))



(defn flush-events!
  [dataset]
  (when-not (empty? dataset)
    (let [dataset* (purify :diff dataset)]
      (try
          (when-not (empty? dataset*)
            (apply (partial jdbc/insert! pg-db :events) dataset*))
        (catch org.postgresql.util.PSQLException psqlex
          (do
            (let [sqlState (.getSQLState psqlex)]
              (log/warn sqlState (.getMessage psqlex))
              (when (= sqlState "23505")
                (map (fn[x] (with-safe
                          (jdbc/insert! pg-db :events x)))
                  dataset*)))))
        (catch Throwable t
          (do
            (log/error t)
            (throw t)))))))

(defn- parse-cookies
  "Returns a map of cookies when given the Set-Cookie string sent
  by a server."
  [#^String cookie-string]
  (when cookie-string
    (into {}
      (for [#^String cookie (.split cookie-string ";")]
        (let [keyval (map (fn [#^String x] (.trim x)) (.split cookie "=" 2))]
          [(first keyval) (<urlsafe-str (second keyval))])))))

(defn insert-data
  [sid data]
  (let [data (parse-string data true)
        {:keys [m epoch ip time ua params headers host srv uri body refer]} data
        _headers headers
        request (parse-string body true)
        {:keys [args path method env headers referrer id url t form user]} request
        {:keys [X-Forward-For User-Agent Host Cookie]} headers
        cookie-data (parse-cookies Cookie)
        ]
        (println request)
  (flush-events! [{:id sid :received_at (pg-dt (epoch->datetime epoch))
                   :ts (pg-dt (epoch->datetime (str t))) :path path :url url
                   :user_data (pg-json (generate-string user))
                   :referrer referrer
                   :cookies (pg-json (generate-string cookie-data))
                   :ip X-Forward-For
                   :args (pg-json (generate-string args))
                   :form (pg-json (generate-string form))
                   :user_agent User-Agent
                   :orig_data (-> data generate-string pg-json)}])
  ))
