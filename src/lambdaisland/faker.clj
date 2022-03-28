(ns lambdaisland.faker
  "Create fake values that look \"nice\", i.e. real-world-ish, so it's quick and
  easy to populate UIs to get a feel for how they behave.

  The single [[fake]] function is the main interface, it can do a bunch of
  things. You can pass it a \"path\" of something it knows about,

  - (fake [:address :city])
  - (fake [:vehicle :vin])

  A partial path will return a map of all nested fakers,

  - (fake [:address]) ;;=> {:country \"..\", :street_address \"...\", ....}

  A regex will generate values that match the regex

  - (fake #\"[A-Z0-9]{3}\")

  A string is treated as a pattern, use #{} to nest faker paths, using dotted
  notation, or \"#\" to put a digit.

  - (fake \"#{company.name} GmbH\")
  - (fake \"##-####-##\")

  Use a set to pick one of multiple choices

  - (fake #{[:name :first-name] [:name :last-name]})

  A map will generate values for each map value. Fakers that are invoked
  multiple times are memoized. Since a lot of fakers are based on other fakers,
  this allows you to generate related values.

  - (fake {:name [:name :name] :email [:internet :email]})
    ;; => {:name \"Freiherrin Marlena Daimer\", :email \"marlenadaimer@daimerohg.com\"}

  See the comment at the bottom of the namespace for examples.
  "
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [cognitect.transit :as transit]
            [lambdaisland.regal :as regal]
            [lambdaisland.regal.generator :as regal-gen]
            [lambdaisland.regal.parse :as regal-parse]))

(defn random-spliterator
  ([]
   (random-spliterator (System/nanoTime)))
  ([seed]
   ;; Tried using spliterator in an attempt to have relatively "stable"
   ;; randomness: when generating two random nested datastructure that only
   ;; differ slightly in structure, using the same seed, the common parts should
   ;; largely be identical. However it seems these spliterators "run out", which
   ;; is rather annoying, so falling back to plain RNGs.
   #_
   (.spliterator (.doubles (java.util.Random. seed)))
   (java.util.Random. seed)))

(defprotocol IRandom
  (rand-next [this])
  (rand-split [this]))

(extend-protocol IRandom
  java.util.Spliterator
  (rand-next [this]
    (let [p (promise)]
      (.tryAdvance this
                   (reify
                     java.util.function.Consumer
                     (accept [_ val] (deliver p val))))
      @p))
  (rand-split [this]
    ;; At a certain point trySplit starts returning `nil`, just continue with
    ;; the spliterator we have.
    (or (.trySplit this)
        this))

  java.util.Random
  (rand-next [this]
    (.nextDouble this))
  (rand-split [this]
    ;; "Branch" the stream of random values, returning a predictable but
    ;; separate stream, while the main stream continues. This consumes a single
    ;; random number.
    (java.util.Random. (.nextLong this))))

(defn random-int [rng max]
  (long (Math/floor (* (rand-next rng) max))))

(defn random-nth [rng coll]
  (nth coll (random-int rng (count coll))))

(def ^:dynamic *locale* :en)
(def ^:dynamic *context* nil)

(defmulti -fake (fn [rng v] v))

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

(defn lookup* [sections]
  (if-let [f (get-in @faker-data sections)]
    f
    (let [f (find-transit sections)
          data (ingest-transit f)]
      (get-in data sections))))

(defn lookup [path]
  (or (some (fn [[k]]
              (when (and (vector? k) (= path (into [:en] k)))
                ::method))
            (.getMethodTable -fake))
      (lookup* (into [*locale*] path))
      (when (not= :en *locale*)
        (binding [*locale* :en]
          (lookup path)))))

(defn clamp-str [s len]
  (if (< len (count s))
    (subs s 0 len)
    s))

(declare handle-result)

(def parse-pattern
  (memoize
   (fn [p]
     (regal-parse/parse-pattern p))))

(defn fake
  "Main faker interface, takes a path, regex, string pattern, or set."
  ([value]
   (fake (random-spliterator) value))
  ([rng value]
   (let [rng (if (number? rng) (random-spliterator rng) rng)
         rng (rand-split rng)
         res (if-let [res (and *context* (get @*context* value))]
               res
               (binding [*context* (or *context* (atom {}))]
                 (cond
                   (sequential? value) (-fake rng value)

                   (instance? java.util.regex.Pattern value)
                   (gen/generate (regal-gen/gen (parse-pattern (regal/regex-pattern value)))
                                 30
                                 (long (* 10e6 (rand-next rng))))

                   (set? value)
                   (fake rng (random-nth rng (seq value)))

                   (map? value)
                   (into (sorted-map)
                         (map (juxt key (comp (fn [v] (fake rng v))
                                              val)))
                         (sort-by key value))

                   (fn? value)
                   (value)

                   :else
                   (handle-result rng [] value))))]
     (when *context*
       (swap! *context* assoc value res))
     res)))

;; By default we base ourselves on the EDN/YAML data, but specific paths can be
;; implemented with custom logic
(defmethod -fake :default [rng path]
  (handle-result rng path (lookup path)))

(defmethod -fake [:address :zip-code] [rng _]
  ;; The Ruby gem has some extra logic here, but this should be fine and
  ;; fixes [:address :full-address]
  (-fake rng [:address :postcode]))

(defn alias-faker [to from]
  (defmethod -fake to [rng _] (-fake rng from)))


;; More Ruby compat to support specific lookup strings in the original YAMLs
(alias-faker [:compass :half-wind-abbreviation] [:compass :half-wind :abbreviation])
(alias-faker [:compass :quarter-wind-abbreviation] [:compass :quarter-wind :abbreviation])
(alias-faker [:compass :ordinal-abbreviation] [:compass :ordinal :abbreviation])
(alias-faker [:compass :cardinal-abbreviation] [:compass :cardinal :abbreviation])
(alias-faker [:compass :quarter-wind-azimuth] [:compass :quarter-wind :azimuth])
(alias-faker [:compass :cardinal-azimuth] [:compass :cardinal :azimuth])
(alias-faker [:compass :ordinal-azimuth] [:compass :ordinal :azimuth])
(alias-faker [:compass :half-wind-azimuth] [:compass :half-wind :azimuth])
(alias-faker [:compass :cardinal] [:compass :cardinal :word])
(alias-faker [:compass :ordinal] [:compass :ordinal :word])
(alias-faker [:compass :half-wind] [:compass :half-wind :word])
(alias-faker [:compass :quarter-wind] [:compass :quarter-wind :word])
(alias-faker [:creature :bird :adjective] [:creature :bird :adjectives])
(alias-faker [:creature :bird :emotional-adjective] [:creature :bird :emotional-adjectives])
(alias-faker [:creature :bird :silly-adjective] [:creature :bird :silly-adjectives])
(alias-faker [:creature :bird :plausible-common-name] [:creature :bird :plausible-common-names])
(alias-faker [:creature :bird :implausible-common-name] [:creature :bird :implausible-common-names])
(alias-faker [:creature :bird :color] [:creature :bird :colors])

(defmethod -fake [:creature :bird :common-name] [rng path]
  (handle-result rng path (apply concat (vals (lookup [:creature :bird :order-common-map])))))

(defmethod -fake [:internet :email] [rng path]
  (handle-result rng path "#{username}@#{domain-name}"))

(defmethod -fake [:internet :domain-name] [rng _]
  (str (clamp-str
        (str/replace (str/lower-case (fake [:company :name])) #"[^a-z-]" "")
        15)
       "."
       (fake rng [:internet :domain-suffix])))

(defmethod -fake [:internet :username] [rng _]
  (str/replace
   (str/lower-case
    (case (rand-int 3)
      0 (fake rng [:name :first-name])
      1 (str (fake rng [:name :first-name])
             (random-nth rng ["." "_"])
             (fake rng [:name :last-name]))
      2 (str (fake rng [:name :first-name]) (random-int rng 99))))
   #"[^a-z0-9\.-]" ""))

(defmethod -fake [:lorem :sentence] [rng _]
  (str (->> (repeatedly #(-fake rng [:lorem :words]))
            (take (+ 4 (random-int rng 12)))
            (str/join " ")
            str/capitalize)
       "."))

(defmethod -fake [:lorem :paragraph] [rng _]
  (->> (repeatedly #(-fake rng [:lorem :sentence]))
       (take (+ 2 (random-int rng 5)))
       (str/join " ")))

(defmethod -fake [:time :date] [rng _]
  ;; 1997-05-19 -> 2024-10-04
  (java.time.LocalDate/ofEpochDay (+ 10000
                                     (random-int rng 10000))))

(defmethod -fake [:time :year] [rng _]
  (+ 1970 (random-int rng 40)))

(defmethod -fake [:time :time] [rng _]
  (java.time.LocalTime/ofNanoOfDay (random-int rng 86400000000000)))

(defmethod -fake [:time :date-time] [rng _]
  (java.time.LocalDateTime/of (fake rng [:time :date])
                              (fake rng [:time :time])))

(defmethod -fake [:number :small-integer] [rng _]
  (random-int rng 1000))

(defmethod -fake [:number :big-integer] [rng _]
  (random-int rng 1e15))

(defmethod -fake [:number :decimal] [rng _]
  (bigdec (/ (random-int rng 100000) 100)))

(defmethod -fake [:number :percentage] [rng _]
  (bigdec (inc (random-int rng 99))))

(defmethod -fake [:number :float] [rng _]
  (* (rand-next rng) (random-int rng 100)))

#_(fake [:number])
;; => {:big-integer 436587027687068,
;;     :decimal 691.76M,
;;     :float 16.918592534103272,
;;     :percentage 0.59M,
;;     :small-integer 792}

(defn handle-result [rng path result]
  (let [rng (rand-split rng)]
    (cond
      (sequential? result)
      (handle-result rng path (random-nth rng result))

      (string? result)
      (-> result
          (str/replace #"#\{(.*?)\}"
                       (fn [[_ pat]]
                         (let [pat (str/replace pat #"_" "-")]
                           (cond
                             (re-matches #"\d+" pat)
                             (format (str "%0" pat "d") (random-int rng (Math/pow 10 (Integer/parseInt pat))))
                             (some #{\.} pat)
                             (fake rng (map keyword (str/split (str/lower-case pat) #"\.")))
                             :else
                             (fake rng (concat (butlast path) [(keyword pat)]))))))
          (str/replace #"#" (fn [_] (str (random-int rng 10)))))

      (map? result)
      (into (sorted-map)
            (map (juxt key #(handle-result rng (concat path [(key %)]) (val %))))
            (sort-by key result))

      (= ::method result)
      (fake rng path)

      :else
      result)))


(comment
  (fake [:name :first-name]) ;; => "Charlott"
  (fake [:name :last-name]) ;; => "Langworth"
  (fake [:name :name]) ;; => "Zoe Block"
  (fake [:internet :domain-suffix]) ;; => "io"
  (fake [:company :name]) ;; => "Tremblay-Will"
  (fake [:internet :domain-name]) ;; => "auer-hermiston.co"
  (fake [:internet :email]) ;; => "dylan.carter@hartmann-baumbach.org"
  (fake [:address :city])
  (fake [:address :postcode])
  (fake [:address :street-address])
  (fake [:address :default-country])
  (fake [:color :name]) ;; => "bordeaux"
  (fake "###-####-###") ;; => "283-7393-806"

  (fake [:vehicle :car-types])

  (fake #"[A-Z0-9]{17}") ;; => "1OG0DDZV473P5RH47"

  (fake #{#"0[0-9]{2}" #"[A-Z]{3}"})

  (fake {:name [:name :name]
         :email [:internet :email]})
  ;; => {:name "Freiherrin Marlena Daimer", :email "marlenadaimer@daimerohg.com"}

  (fake [:name :name])

  ;; See all fakers in the data
  (for [[k v] (:faker (:de faker-data))
        kk (if (map? v)
             (keys v)
             [:_])]
    [k kk (try
            (binding [*locale* :de]
              (fake [k kk]))
            (catch Exception e
              :ERR))])

  (fake 0 {:a [:address :city]
           :b [:address :state]
           :c [:address :street-address]})

  (lookup [:address :street-address])
  )
