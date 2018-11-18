(ns user
  (:require [mmoney-converter.core :as core]
            [mmoney-converter.mmoney :as mm]
            [mmoney-converter.excel :as xls]))

; (def data (read-xml "example.xml"))
; (def data (read-xml "mmoney-export.xml"))
(defn read-xml [xml-file]
  (mm/parse-file xml-file))

; (export-data "config.edn" "example.xml" "export.xls")
(defn export-data [config xml-file out-file]
  (let [config (core/read-configuration config)
        data (read-xml xml-file)]
    (xls/export data config out-file)))
