(defproject quick-api "0.1.0-SNAPSHOT"
  :description "a ring"
  :url "https://github.com/story-squad/clj-quick-api"
  :license {:name "MIT"
            :url "https://raw.githubusercontent.com/story-squad/clj-quick-api/main/LICENSE"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring "1.9.2"]
                 [metosin/reitit "0.5.12"]
                 [metosin/muuntaja "0.6.8"]
                 [ring-cors "0.1.13"]
                 [ring/ring-ssl "0.3.0"]
                 [com.taoensso/carmine "2.6.2"]
                 [com.wsscode/edn-json "1.1.0"]
                 [ring-server "0.5.0"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler quick-api.core/api
         :init quick-api.core/init
         :destroy quick-api.core/destroy}
  :aot :all
  :jvm-opts ["-Dclojure.compiler.direct-linking=true"]
  :target-path "target/%s"
  :profiles {:production
             {:ring {:open-browser? false :stacktraces? false :auto-reload? false}}
             :dev
             {:dependencies [[ring-mock "0.1.5"] [ring/ring-devel "1.9.3"]]}})
