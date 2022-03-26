(ns repl-sessions.yaml
  (:require [clj-yaml.core :as yaml]
            [cognitect.transit :as transit]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn slurp-transit [f]
  (with-open [is (io/input-stream f)]
    (transit/read (transit/reader is :json))))

(def faker-data (atom {}))

(defn deep-merge [a b]
  (merge-with #(if (map? %1)
                 (deep-merge %1 %2)
                 %2)
              a b))

(defn ingest-transit [f]
  (swap! faker-data deep-merge (slurp-transit f)))

(defn transit-path [sections]
  (str
   "lambdaisland/faker/locales/"
   (str/join "/" (map name sections))
   ".transit"))

(defn find-transit [sections]
  (when (seq sections)
    (or (io/resource (transit-path sections))
        (find-transit (butlast sections)))))

(defn lookup [sections]
  (if-let [f (get-in @faker-data sections)]
    f
    (let [f (find-transit sections)
          data (ingest-transit f)]
      (get-in data sections))))

(lookup [:en :creature :bird])

(lookup [:en :creature :bird :emotional-adjectives])

(lookup [:en :culture-series :culture-ships])
