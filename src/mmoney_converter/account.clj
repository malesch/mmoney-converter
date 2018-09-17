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
(defmulti read-mappings (fn [{:keys [file]}] (resource-ext file)))

(defmethod read-mappings :edn [{:keys [file encoding] :as account-mapping}]
  (if-let [reader (util/resource-reader file encoding)]
    (reduce (fn [acc {:keys [account label currency]}]
              (assoc acc label {:account account :currency (name currency)}))
            {}
            (-> reader slurp (edn/read-string)))
    (throw (ex-info (format "Account mapping EDN file not found: %s" file) account-mapping))))

(defmethod read-mappings :default [{:keys [file encoding] :as account-mapping}]
  (if-let [reader (util/resource-reader file encoding)]
    (reduce (fn [acc [account currency label]]
              (if (or (string/blank? account) (string/blank? label))
                acc
                (assoc acc label {:account (Integer/parseInt account) :currency currency})))
            {}
            (csv/read-csv reader))
    (throw (ex-info (format "Account mapping CSV file not found: %s" file) account-mapping))))


(defn resolve-account-number
  "Return the account number (int) for the given account name or nil,
  if no mapping is existing.
  Note: The mapped account number can either be a String or already an Integer value."
  [mappings account-name]
  (let [acc (:account (get mappings account-name))]
    (if (string? acc)
      (Integer/parseInt acc)
      acc)))