(ns mmoney-converter.mmoney
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as s]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> attr text]]
            [mmoney-converter.util :as u]))


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


(defn parse-category [loc]
  (let [{:keys [attrs]} (zip/node loc)]
    (when-not (s/valid? ::category attrs)
      (s/explain-data ::category attrs))
    (-> attrs
        (update-in [:type] u/safe-parse-integer)
        (update-in [:icon] u/safe-parse-integer)
        (update-in [:color] u/safe-parse-integer)
        (update-in [:tint] u/safe-parse-integer)
        (update-in [:active] u/safe-parse-boolean))))

(defn parse-operation [loc]
  (let [{:keys [attrs]} (zip/node loc)]
    (when-not (s/valid? ::operation attrs)
      (s/explain-data ::operation attrs))
    (-> attrs
        (update-in [:sum] u/safe-parse-float)
        (update-in [:date] u/safe-parse-date)
        (update-in [:created] u/safe-parse-epoch))))

(defn parse-xml [reader]
  (let [zxml (some-> reader xml/parse zip/xml-zip)]
    {:category  (mapv parse-category (xml-> zxml :db :category))
     :operation (mapv parse-operation (xml-> zxml :db :operation))}))

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

;(parse-file "example.xml")
(defn parse-file [^String resource-name]
  (with-open [rdr (u/resource-reader resource-name)]
    (parse-xml rdr)))

