(ns mmoney-converter.mmoney
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.data.xml :as xml]
            [mmoney-converter.parse-utils :as pu]))


(defn non-blank-string? [x] (and (string? x) (not (string/blank? x))))
(defn item-identifier? [x] (and (string? x) (re-matches #"[-0-9a-fA-F]+" x)))
(defn date? [x] (and (string? x) (re-matches #"\d{4}-\d{2}-\d{2}" x)))

(s/def :category/id item-identifier?)
(s/def :category/type integer?)
(s/def :category/name non-blank-string?)
(s/def :category/icon integer?)
(s/def :category/color integer?)
(s/def :category/tint integer?)
(s/def :category/parentId item-identifier?)
(s/def :category/active integer?)

(s/def ::category (s/keys :req-un [:category/id :category/type :category/name :category/active]
                          :opt-un [:category/icon :category/color :category/tint :category/parentId]))

(s/def :operation/id item-identifier?)
(s/def :operation/note non-blank-string?)
(s/def :operation/sum float?)
(s/def :operation/currencyId non-blank-string?)
(s/def :operation/date date?)
(s/def :operation/categoryId item-identifier?)
(s/def :operation/created integer?)

(s/def ::operation (s/keys :req-un [:operation/id :operation/sum :operation/currencyId :operation/date :operation/categoryId :operation/created]
                           :opt-un [:operation/note]))

(defmulti parse-xml-line :tag)

(defmethod parse-xml-line :category [{:keys [attrs]}]
  (when-not (s/valid? ::category attrs)
    (s/explain-data ::category attrs))
  [:category (-> attrs
                 (update-in [:type] pu/safe-parse-integer)
                 (update-in [:icon] pu/safe-parse-integer)
                 (update-in [:color] pu/safe-parse-integer)
                 (update-in [:tint] pu/safe-parse-integer)
                 (update-in [:active] pu/safe-parse-boolean))])

(defmethod parse-xml-line :operation [{:keys [attrs]}]
  (when-not (s/valid? ::operation attrs)
    (s/explain-data ::operation attrs))
  [:operation (-> attrs
                  (update-in [:sum] pu/safe-parse-float)
                  (update-in [:date] pu/safe-parse-date)
                  (update-in [:created] pu/safe-parse-epoch))])

(defn parse-line [^String s]
  (-> s (xml/parse-str) (parse-xml-line)))

(defn parse [reader]
  (reduce
    (fn [acc line]
      (let [[typ data] (parse-line line)]
        (update acc typ conj data)))
    {:category  []
     :operation []}
    (rest (line-seq reader))))

(defn category-lookup
  "Return a map to lookup categories by their ID."
  [categories]
  (reduce (fn [acc cat]
            (assoc acc (:id cat) cat))
          {}
          categories))

(defn category-path
  "Return a vector with the hierarchical category names. First element is the
  name of the given node and following the name of the parent nodes."
  [lookup cat]
  (loop [c cat
         path []]
    (let [category-name (:name c)
          parent-id (:parentId c)]
      (if c
        (recur (get lookup parent-id) (conj path category-name))
        path))))

;(parse-file "example-export-file.xml")
(defn parse-file [^String resource-name]
  (with-open [rdr (io/reader (io/resource resource-name))]
    (parse rdr)))