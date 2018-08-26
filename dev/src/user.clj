(ns user
  (:require [mmoney-converter.mmoney :as mm]
            [mmoney-converter.excel :as xls]))

; (def data (read-example "example.xml"))
; (def data (read-example "mmoney-export.xml"))
(defn read-xml [xml-file]
  (mm/parse-file xml-file))

; (export-data "example.xml" "account-mapping.edn" "export.xls")
; (export-data "mmoney-export.xml" "accounts.edn" "export.xls")
(defn export-data [xml-file mapping-file out-file]
  (let [data (read-xml xml-file)]
    (xls/export out-file data xls/columns mapping-file)))