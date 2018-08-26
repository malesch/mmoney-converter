(ns mmoney-converter.excel
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [dk.ative.docjure.spreadsheet :as ss]
            [mmoney-converter.mmoney :as mm]
            [mmoney-converter.util :as util]))

(def columns
  [{:column     :account-number
    :source-key :categoryId
    :label      "Konto"
    :styles     {:halign :center}}
   {:column     :amount
    :source-key :sum
    :label      "Betrag"
    :width      (* 10 256)
    :styles     {:halign      :center
                 :data-format "0.00"}}
   {:column     :date
    :source-key :created
    :label      "Datum"
    :width      (* 20 256)
    :styles     {:halign      :center
                 :data-format "dd.mm.yyyy"}}
   {:column     :currency
    :source-key :currencyId
    :label      "Währung"
    :width      (* 10 256)
    :styles     {:halign :center}}
   {:column     :account-name
    :source-key :categoryId
    :label      "Kategorie"
    :width      (* 40 256)
    :styles     {:halign :left
                 ; Bug? Halign is not applied.
                 ; Workaround: Update data-format (or set background color)
                 :data-format "General"}}
   {:column     :detail
    :source-key :note
    :label      "Beschreibung"
    :width      (* 80 256)
    :styles     {:halign :left
                 ; Bug? Halign is not applied.
                 ; Workaround: Update data-format (or set background color)
                 :data-format "General"}}])


(defn read-account-mappings [mapping-file]
  (if-let [reader (util/resource-reader mapping-file)]
    (edn/read-string (slurp reader))
    (throw (ex-info (format "Account mapping file not found: %s" mapping-file) {:resource mapping-file}))))

(defn build-context [data mapping-file]
  {:category-lookup (mm/category-lookup (:category data))
   :account-mapping (read-account-mappings mapping-file)})

(defn select-first-sheet [workbook]
  (-> workbook (ss/sheet-seq) first))

(defn add-export-column-headers! [sheet column-definitions]
  (let [headers (map :label column-definitions)]
    (ss/add-row! sheet headers)))

(defmulti format-op-value (fn [_ col-def _] (:column col-def)))

(defmethod format-op-value :account-number [value _ {:keys [category-lookup account-mapping]}]
  (let [category-path (some->> value (get category-lookup) (mm/category-path category-lookup))
        leaf-account-name (first category-path)]
    (or (get account-mapping leaf-account-name)
        (throw (ex-info (format "Missing account mapping for `%s`" leaf-account-name)
                        {:account-name leaf-account-name
                         :category-path category-path})))))

(defmethod format-op-value :account-name [value _ {:keys [category-lookup]}]
  (let [category-path (some->> value (get category-lookup) (mm/category-path category-lookup))]
    (first category-path)))

(defmethod format-op-value :default [value _ _]
  value)

(defn select-operation-values [op column-definitions ctx]
  (map (fn [cdef]
         (-> op
             (get (:source-key cdef))
             (format-op-value cdef ctx)))
       column-definitions))

(defn add-export-data! [sheet data column-definitions xfs]
  (doseq [op (:operation data)]
    (as-> op $
          (select-operation-values $ column-definitions xfs)
          (ss/add-row! sheet $))))

(defn write-export-sheet [sheet data column-definitions xfs]
  (doto sheet
    (add-export-column-headers! column-definitions)
    (add-export-data! data column-definitions xfs)))

(defn set-column-widths! [sheet column-definitions]
  (doseq [[idx {:keys [width]}] (map-indexed vector column-definitions)]
    (when width
      (.setColumnWidth sheet idx width))))

(defn style-column-header! [sheet]
  (let [workbook (.getWorkbook sheet)
        header (-> sheet (ss/row-seq) first)]
    (ss/set-row-style! header (ss/create-cell-style! workbook {:background :grey_25_percent, :font {:bold true}, :halign :center}))))

(defn style-data-rows!
  "Iterate over all data cells and set styles individually because of problems setting styles on columns
  (using SXSSFSheet#setDefaultColumnStyle) and to skip manipulating the header row."
  [sheet column-definitions]
  (let [workbook (.getWorkbook sheet)]
    (doseq [row (-> sheet (ss/row-seq) (rest))
            :let [row-cells (ss/cell-seq row)]]
      (doseq [[idx cdef] (map-indexed vector column-definitions)]
        (let [cell (nth row-cells idx)
              cell-style (:styles cdef)]
          (when-not (empty? cell-style)
            (ss/set-cell-style! cell (ss/create-cell-style! workbook cell-style))))))))

(defn style-export-sheet [sheet column-definitions]
  (doto sheet
    (set-column-widths! column-definitions)
    (style-column-header!)
    (style-data-rows! column-definitions)))

(defn export [data column-definitions account-mapping-file fout]
  (let [workbook (ss/create-workbook "mMoney Export" nil)
        ctx (build-context data account-mapping-file)]
    (-> workbook
        (select-first-sheet)
        (write-export-sheet data column-definitions ctx)
        (style-export-sheet column-definitions))
    (ss/save-workbook! fout workbook)
    :OK))