(defproject mmoney-converter "0.1.0"
  :description "Convert or export mMoney XML export file"
  :url "https://github.com/malesch/mmoney-converter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.csv "0.1.4"]
                 [dk.ative/docjure "1.12.0"]]
  :min-lein-version "2.0.0"
  :source-paths ["src"]
  :resource-paths ["resources"]
  :profiles {:dev {:source-paths   ["dev/src"]
                   :resource-paths ["dev/resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}
             :uberjar {:uberjar-name "mmoney-converter.jar"
                       ; :omit-source  true
                       :aot [mmoney-converter.core]
                       :main mmoney-converter.core}})
