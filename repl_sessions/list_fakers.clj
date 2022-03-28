(ns repl-sessions.list-fakers
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [lambdaisland.faker :as faker]))

(def all
  (reduce
   faker/deep-merge
   (map faker/slurp-transit
        (filter #(str/ends-with? (str %) ".transit")
                (file-seq (io/file "resources/lambdaisland/faker/locales"))))))

(def l2
  (sort
   (distinct
    (for [tlk (into #{} (mapcat keys) [(get all :en)] #_(vals all))
          slk (mapcat keys (filter map? (map #(get % tlk) (vals all))))]
      [tlk slk]))))

(def l3
  (sort
   (distinct
    (for [tlk (into #{} (mapcat keys) [(get all :en)] #_(vals all))
          slk (mapcat keys (filter map? (map #(get % tlk) (vals all))))
          ilk (remove nil? (mapcat keys (filter map? (map #(get-in % [tlk slk]) (vals all)))))]
      [tlk slk ilk]))))

(spit "supported_fakers.clj"
      (with-out-str
        (doseq [f (sort-by str
                           (concat
                            (remove (set (map #(vec (take 2 %)) l3)) l2)
                            l3))]
          (let [res (try (faker/fake f) (catch Exception e))]
            (when res
              (println f)
              (print ";;=> ")
              (println res)
              (println))))))

(spit "unsupported_fakers.clj"
      (with-out-str
        (println ";; Fakers from the original Ruby gem that currently return `nil` or throw an exception.")
        (doseq [f (sort-by str
                           (concat
                            (remove (set (map #(vec (take 2 %)) l3)) l2)
                            l3))]
          (let [res (try (faker/fake f) (catch Exception e e))]
            (when (or (nil? res) (instance? Throwable res))
              (println f)
              (print ";;=> ")
              (println (when res (.getName (class res))))
              (println))))))

l3
l2

(binding [faker/*locale* :uk]
  (faker/lookup [:address :feminine-street-prefix]))


(faker/lookup [:address :full-address])
(faker/fake [:address :full-address])
(faker/lookup [:address :zip-code])
