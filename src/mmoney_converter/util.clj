(ns mmoney-converter.util
  (:require [clojure.java.io :as io])
  (:import (java.util Date)
           (java.time LocalDate ZoneOffset)
           (java.time.format DateTimeFormatter)))

(defn safe-parse-integer [^String s]
  (try
    (Long/parseLong s)
    (catch Exception _
      nil)))

(defn safe-parse-float [^String s]
  (try
    (Float/parseFloat s)
    (catch Exception _
      nil)))

(defn safe-parse-boolean [^String s]
  (try
    (case s
      "1" true
      "0" false
      (Boolean/parseBoolean s))
    (catch Exception _
      nil)))

(defn safe-parse-date
  ([^String s] (safe-parse-date s ZoneOffset/UTC))
  ([^String s ^ZoneOffset zone-offset]
   (try
     (-> s
         (LocalDate/parse DateTimeFormatter/ISO_LOCAL_DATE)
         (.atStartOfDay)
         (.toInstant zone-offset)
         (Date/from))
     (catch Exception _
       nil))))

(defn safe-parse-epoch [^String s]
  (try
    (when-let [epoch (safe-parse-integer s)]
      (Date. epoch))
    (catch Exception _
      nil)))

(defn resource-reader
  ([res] (resource-reader res nil))
  ([res encoding]
   (let [r (or (io/resource res)
               (let [res-file (io/file res)]
                 (when (.exists res-file)
                   res-file)))]
     (when r
       (if encoding
         (io/reader r :encoding encoding)
         (io/reader r))))))