(ns mmoney-converter.account
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.data.csv :as csv]
            [mmoney-converter.util :as util]))

(defn- resource-ext [res]
  (when res
    (let [dot (.lastIndexOf res ".")]
      (when (pos? dot)
        (let [ext-str (subs res (inc dot))]
          (when-not (string/blank? ext-str)
            (some-> ext-str string/lower-case keyword)))))))


; CSV is the default format.
; EDN is available only for debugging purposes
(defmulti read-mappings (fn [res] (resource-ext res)))

(defmethod read-mappings :edn [mapping-file]
  (if-let [reader (util/resource-reader mapping-file)]
    (reduce (fn [acc {:keys [account label currency]}]
              (assoc acc label {:account account :currency (name currency)}))
            {}
            (-> reader (slurp) (edn/read-string)))
    (throw (ex-info (format "Account mapping EDN file not found: %s" mapping-file) {:resource mapping-file}))))

(defmethod read-mappings :default [mapping-file]
  (if-let [reader (util/resource-reader mapping-file)]
    (reduce (fn [acc [account currency label]]
              (if (or (string/blank? account) (string/blank? label))
                acc
                (assoc acc label {:account (Integer/parseInt account) :currency currency})))
            {}
            (csv/read-csv reader :encoding :windows-1252))
    (throw (ex-info (format "Account mapping CSV file not found: %s" mapping-file) {:resource mapping-file}))))


(defn resolve-account-number [mappings account-name]
  (:account (get mappings account-name)))