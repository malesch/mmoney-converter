(ns mmoney-converter.excel
  (:require [taoensso.timbre :as timbre]
            [dk.ative.docjure.spreadsheet :as ss]
            [mmoney-converter.mmoney :as mm]
            [mmoney-converter.account :as account]))

(defn build-context [data {:keys [account-mapping] :as config}]
  {:config          config
   :category-lookup (mm/category-lookup (:category data))
   :account-mapping (account/read-mappings account-mapping)})

(defn credit-account?
  "Return true if the given account numer is a configured credit account"
  [{:keys [credit-accounts]} account-number]
  (contains? credit-accounts account-number))

(defn select-first-sheet [workbook]
  (-> workbook (ss/sheet-seq) first))

(defn add-export-column-headers! [sheet column-definitions]
  (let [headers (map :label column-definitions)]
    (ss/add-row! sheet headers)))

(defmulti format-op-value (fn [_ col-def _] (:column col-def)))

(defmethod format-op-value :amount [value _ _]
  (.setScale (bigdec value) 2 java.math.RoundingMode/HALF_UP))

(defmethod format-op-value :debit-account [value _ {:keys [config category-lookup account-mapping]}]
  (let [category-path (some->> value (get category-lookup) (mm/category-path category-lookup))
        leaf-account-name (first category-path)
        account-number (account/resolve-account-number account-mapping leaf-account-name)]
    (when-not (credit-account? config account-number)
      (or account-number
          (do
            (timbre/warnf "Missing account mapping for `%s`" leaf-account-name)
            "-")))))

(defmethod format-op-value :credit-account [value _ {:keys [config category-lookup account-mapping]}]
  (let [category-path (some->> value (get category-lookup) (mm/category-path category-lookup))
        leaf-account-name (first category-path)
        account-number (account/resolve-account-number account-mapping leaf-account-name)]
    (when (credit-account? config account-number)
      (or account-number
          (do
            (timbre/warnf "Missing account mapping for `%s`" leaf-account-name)
            "-")))))

(defmethod format-op-value :account-name [value _ {:keys [category-lookup]}]
  (let [category-path (some->> value (get category-lookup) (mm/category-path category-lookup))]
    (first category-path)))

(defmethod format-op-value :default [value _ _]
  value)


(defn select-operation-values [op ctx]
  (let [columns (get-in ctx [:config :columns])]
    (map (fn [cdef]
           (-> op
               (get (:source-key cdef))
               (format-op-value cdef ctx)))
         columns)))

(defn add-export-data! [sheet data ctx]
  (doseq [op (:operation data)]
    (as-> op $
          (select-operation-values $ ctx)
          (ss/add-row! sheet $))))

(defn write-export-sheet [sheet data ctx]
  (let [columns (get-in ctx [:config :columns])]
    (doto sheet
      (add-export-column-headers! columns)
      (add-export-data! data ctx))))

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

(defn style-export-sheet [sheet ctx]
  (let [columns (get-in ctx [:config :columns])]
    (doto sheet
      (set-column-widths! columns)
      (style-column-header!)
      (style-data-rows! columns))))

(defn export [data config fout]
  (let [workbook (ss/create-workbook "mMoney Export" nil)
        ctx (build-context data config)]
    (-> workbook
        (select-first-sheet)
        (write-export-sheet data ctx)
        (style-export-sheet ctx))
    (ss/save-workbook! fout workbook)
    :OK))