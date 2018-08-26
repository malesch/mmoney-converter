(ns mmoney-converter.core
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [mmoney-converter.mmoney :as mm]
            [mmoney-converter.excel :as xls])
  (:gen-class))

(def cli-options
  [["-i" "--input FILE" "mMoney export file"
    :parse-fn #(io/file %)
    :missing "Input file required (-i, --input)"
    :validate [#(.exists %) "mMoney export file does not exist"
               #(.isFile %) "Input is not a file"]
    :assoc-fn (fn [m k v] (assoc m k (.getAbsolutePath v)))]
   ["-a" "--account-mapping FILE" "Account mapping files"
    :parse-fn #(io/file %)
    :missing "Account mapping file required (-a, --account-mapping)"
    :validate [#(.exists %) "Account mapping file does not exist"
               #(.isFile %) "Account mapping file is not a file"]
    :assoc-fn (fn [m k v] (assoc m k (.getAbsolutePath v)))]
   ["-o" "--output FILE" "Output Excel file"
    :parse-fn #(io/file %)
    :missing "Output file not specified (-o, --output)"
    :validate [#(not (.exists %)) "Output file already exists"]
    :assoc-fn (fn [m k v] (assoc m k (.getAbsolutePath v)))]
   ["-h" "--help" "Print help message"]])

(defn usage
  ([summary] (usage summary nil))
  ([summary errors]
   (->> (concat
          (when errors
            (if (> (count errors) 1)
              (concat ["" "!!! Errors !!!" ""]
                      (map #(str "  - " %) errors)
                      [""])
              ["" (str  "!!! Error: " "" (first errors) " !!!") ""]))
          ["Usage:"
           ""
           "  java -jar mmoney-converter.jar [options]"
           ""
           "Available options:"
           ""
           summary
           ""
           ""])
        (string/join \newline))))

(defn exit [status msg]
  (println msg)
  (System/exit status))


(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (usage summary errors)))

    (let [in-file (:input options)
          mapping-file (:account-mapping options)
          out-file (:output options)]
      (println "Convert mMoney XML export to Excel")
      (println "  Account mapping:  " mapping-file)
      (println "  Input:            " in-file)
      (println "  Output:           " out-file)
      (println)
      (-> in-file
          (mm/parse-file)
          (xls/export xls/columns mapping-file out-file))
      (println "Done"))))