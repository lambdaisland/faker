(ns repl-sessions.import
  (:require [clj-yaml.core :as yaml]
            [cognitect.transit :as transit]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [camel-snake-kebab.core :as csk]
            [clojure.walk :as walk]))

(def faker-yml-path "/home/arne/github/fakers/faker-ruby__faker/lib/locales/")

(defn deep-merge [a b]
  (merge-with #(if (map? %1) (deep-merge %1 %2) %2)
              a b))

(doseq [[name maps]
        (group-by first
                  (for [url (next (file-seq (io/file faker-yml-path)))
                        :let [yml (try (with-open [rdr (io/reader url)]
                                         (yaml/parse-stream rdr))
                                       (catch Exception e
                                         ))]
                        :when (map? yml)]
                    (do
                      (when (not= 1 (count yml))
                        (println "Too many locales in " url))
                      (let [locale (key (first yml))
                            values (or (get-in yml [(keyword locale) :faker])
                                       (get yml [(keyword locale)]))
                            value  (first values)]
                        (if (= 1 (count values))
                          [(str (name locale) "/" (name (key value))) {locale values}]
                          [(name locale) {locale values}])))))
        :let [file (io/file (str "resources/lambdaisland/faker/locales/" (csk/->kebab-case-string name) ".transit"))
              _ (.mkdirs (io/file (.getParent file)))
              writer (transit/writer (io/output-stream file) :json)]]
  (transit/write writer (walk/postwalk
                         (fn [o]
                           (if (map? o)
                             (update-keys o #(when % (csk/->kebab-case-keyword %)))
                             o))
                         (reduce deep-merge (map second maps)))))
