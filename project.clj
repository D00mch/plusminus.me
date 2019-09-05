(defproject plus-minus "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[buddy "2.0.0"]
                 [ch.qos.logback/logback-classic "1.2.3"]
                 [cheshire "5.8.1"]
                 [stuarth/clj-oauth2 "0.3.2"]
                 [cljs-ajax "0.8.0"]
                 [clojure.java-time "0.3.2"]
                 [com.cognitect/transit-clj "0.8.313"]
                 [conman "0.8.3"]
                 [cprop "0.1.13"]
                 [luminus-immutant "0.2.5"]
                 [luminus-migrations "0.6.5"]
                 [luminus-transit "0.1.1"]
                 [markdown-clj "1.10.0"]
                 [metosin/muuntaja "0.6.4"]
                 [metosin/reitit "0.3.9"]
                 [metosin/ring-http-response "0.9.1"]
                 [mount "0.1.16"]
                 [nrepl "0.6.0"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]
                 [org.clojure/tools.cli "0.4.2"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.postgresql/postgresql "42.2.5"]
                 [org.webjars.npm/bulma "0.7.4"]
                 [org.webjars.npm/material-icons "0.3.0"]
                 [org.webjars/webjars-locator "0.36"]
                 [reagent "0.8.1"]
                 [reanimated "0.6.1"]
                 [org.clojure/test.check "0.9.0"] ;; for spec
                 [clojure-term-colors "0.1.0"] ;; to color game board in repl
                 [org.clojure/core.async "0.4.500"]
                 [net.cgrand/xforms "0.18.2"] ;; transducers extension
                 [com.walmartlabs/cond-let "1.0.0"]
                 [ring-webjars "0.2.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [selmer "1.12.12"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "src/cljs" "src/cljc"]
  :test-paths ["test/clj"]
  :monkeypatch-clojure-test false ;; https://github.com/technomancy/leiningen/issues/2524
  :resource-paths ["resources" "target/cljsbuild"]
  :target-path "target/%s/"
  :main ^:skip-aot plus-minus.core

  :plugins [[lein-cljsbuild "1.1.7"]]
  :clean-targets ^{:protect false}
  [:target-path [:cljsbuild :builds :app :compiler :output-dir] [:cljsbuild :builds :app :compiler :output-to]]
  :figwheel
  {:http-server-root "public"
   :server-logfile "log/figwheel-logfile.log"
   :nrepl-port 7002
   :css-dirs ["resources/public/css"]
   :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
  

  :profiles
  {:uberjar {:omit-source true
             :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
             :cljsbuild{:builds
              {:min
               {:source-paths ["src/cljc" "src/cljs" "env/prod/cljs"]
                :compiler
                {:output-dir "target/cljsbuild/public/js"
                 :output-to "target/cljsbuild/public/js/app.js"
                 :source-map "target/cljsbuild/public/js/app.js.map"
                 :optimizations :advanced
                 :pretty-print false
                 :infer-externs true
                 :closure-warnings
                 {:externs-validation :off :non-standard-jsdoc :off}
                 :externs ["react/externs/react.js"]}}}}
             
             :aot :all
             :uberjar-name "plus-minus.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   ;; java  -Dconf=dev-config.edn -cp target/uberjar/plus-minus.jar clojure.main -m plus-minus.core
   :project/dev  {:jvm-opts ["-Dconf=dev-config.edn"]
                  :dependencies [[binaryage/devtools "0.9.10"]
                                 [cider/piggieback "0.4.0"]
                                 [doo "0.1.11"]
                                 [expound "0.7.2"]
                                 [figwheel-sidecar "0.5.18"]
                                 [pjstadig/humane-test-output "0.9.0"]
                                 [prone "1.6.3"]
                                 [ring/ring-devel "1.7.1"]
                                 [ring/ring-mock "0.4.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.24.1"]
                                 [lein-doo "0.1.11"]
                                 [lein-figwheel "0.5.18"]]
                  :cljsbuild{:builds
                   {:app
                    {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
                     :figwheel {:on-jsload "plus-minus.core/mount-components"}
                     :compiler
                     {:main "plus-minus.app"
                      :asset-path "/js/out"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :source-map true
                      :optimizations :none
                      :pretty-print true}}}}
                  
                  
                  :doo {:build "test"}
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn"]
                  :resource-paths ["env/test/resources"]
                  :cljsbuild 
                  {:builds
                   {:test
                    {:source-paths ["src/cljc" "src/cljs" "test/cljs"]
                     :compiler
                     {:output-to "target/test.js"
                      :main "plus-minus.doo-runner"
                      :optimizations :whitespace
                      :pretty-print true}}}}
                  
                  }
   :profiles/dev {}
   :profiles/test {}})
