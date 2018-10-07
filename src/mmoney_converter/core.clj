(ns mmoney-converter.core
  (:require [clojure.string :as string]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [taoensso.timbre :as timbre]
            [mmoney-converter.mmoney :as mm]
            [mmoney-converter.util :as u]
            [mmoney-converter.excel :as xls])
  (:gen-class))

(def cli-options
  [["-c" "--config FILE" "Configuration file"
    :default "config.edn"]
   ["-i" "--input FILE" "mMoney export file"
    :parse-fn #(io/file %)
    :missing "Input file required (-i, --input)"
    :validate [#(.exists %) "mMoney export file does not exist"
               #(.isFile %) "Input is not a file"]
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

(defn read-configuration [^String res]
  (if-let [rdr (u/resource-reader res)]
    (edn/read-string (slurp rdr))
    (throw (ex-info "Configuration file not accessible" {:file res}))))

(defn configure-cli-logging!
  ([] (configure-cli-logging! :info))
  ([level]
   (timbre/merge-config! {:level     level
                          :output-fn (fn [{:keys [?err msg_]}]
                                       (->> [(force msg_) (when ?err (.getMessage ?err))]
                                            (filter (complement string/blank?))
                                            (interpose ": ")
                                            (apply str)))})))

(defn configure-collecting-logging!
  "Configure a collecting logger and return the atom which records all messages."
  ([] (configure-collecting-logging! :info))
  ([level]
   (let [collector (atom [])]
     (timbre/merge-config! {:level     level
                            :output-fn (fn [{:keys [?err level msg_]}]
                                         (swap! collector conj {:level level :message (force msg_) :err ?err}))})
     collector)))

(defn -main [& args]
  (configure-cli-logging!)
  (try
    (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
      (cond
        (:help options) (exit 0 (usage summary))
        errors (exit 1 (usage summary errors)))

      (let [cfg-file (:config options)
            in-file (:input options)
            out-file (:output options)
            config (read-configuration cfg-file)]
        (println "Convert mMoney XML export to Excel")
        (println "  Configuration:    " cfg-file)
        (println "  Input:            " in-file)
        (println "  Output:           " out-file)
        (println)
        (-> in-file
            (mm/parse-file)
            (xls/export config out-file))
        (println)
        (println "Done")))
    (catch Exception ex
      (timbre/error ex "Application error"))))