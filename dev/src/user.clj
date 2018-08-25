(ns user
  (:require [mmoney-converter.mmoney :as mm]
            [mmoney-converter.excel :as xls]))

; (def data (read-example))
(defn read-example []
  (mm/parse-file "example.xml"))

; (export-data data)
(defn export-data [data]
  (xls/export "export.xls" data xls/column-definitions))