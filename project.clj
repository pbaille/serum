(defproject serum "0.1.1-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [org.clojure/core.async "0.2.395"]
                 [rum "0.10.8"]
                 [figwheel-sidecar "0.5.8"]
                 [prismatic/schema "1.1.3"]]

  :plugins [[lein-cljsbuild "1.1.5"]]
  :source-paths ["src" "script"]
  :cljsbuild {:builds [{:id           "min"
                        :source-paths ["src"]
                        :compiler     {:main          'serum.core
                                       :asset-path    "js/out"
                                       :optimizations :advanced
                                       :output-to     "resources/public/js/out/main.min.js"
                                       :output-dir    "resources/public/js/out"}}]})
