(ns mmoney-converter.excel
  (:require [mmoney-converter.mmoney :as mm]
            [dk.ative.docjure.spreadsheet :as ss]))

(def column-definitions
  [{:key :created
    :label "Datum"
    :width (* 20 256)
    :align :left
    :date-format "DD.MM.YYYY HH:MM:SS"}
   {:key :sum
    :label "Währung"
    :width (* 10 256)
    :align :center}
   {:key :currencyId
    :label "Betrag"
    :width (* 10 256)
    :align :center}
   {:key :note
    :label "Beschreibung"
    :width (* 80 256)
    :align :left}
   {:key :categoryId
    :label "Kategorie"
    :width (* 40 256) }])

(defn write-header [workbook sheet column-names]
  (-> sheet
      (ss/add-row! column-names)
      (ss/set-row-style! (ss/create-cell-style! workbook {:background :grey_25_percent, :font {:bold true}, :halign :center}))))

(defn create-export-sheet! [workbook columns]
  (let [sheet (-> workbook (ss/sheet-seq) first)]
    (write-header workbook sheet columns)
    sheet))

(defn format-category [lookup c]
  (some->> c (get lookup) (mm/category-path lookup) (first)))


(defn format-operation-values [cat-lookup op column-defs]
  (mapv (fn [cdef]
         (let [v (get op (:key cdef))]
           (case (:key cdef)
             :currencyId (when v (name v))
             :categoryId (format-category cat-lookup v)
             v)))
        column-defs))

(defn set-data-column-styles! [sheet column-defs]
  (let [workbook (.getWorkbook sheet)]
    (doall (map-indexed
             (fn [idx {:keys [width align]}]
               (when align
                 (.setDefaultColumnStyle sheet idx (ss/create-cell-style! workbook {:halign align})))
               (when width
                 (.setColumnWidth sheet idx width)))
             column-defs)))
  sheet)

(defn add-data-rows [sheet {:keys [category operation]} column-defs]
  (let [category-lookup (mm/category-lookup category)]
    (doseq [op operation
            :let [values (format-operation-values category-lookup op column-defs)]]
      (-> sheet
          (ss/add-row! values)
          #_(set-data-cell-styles! column-defs)))))

(defn export [fout data column-defs]
  (let [wb (ss/create-workbook "MMoney Export" nil)
        sheet (-> (create-export-sheet! wb (map :label column-defs))
                  (set-data-column-styles! column-defs))]
    (add-data-rows sheet data column-defs)
    (ss/save-workbook! fout wb)
    :OK))