(defproject linkoln "0.1.0-SNAPSHOT"
  :description "Git-based self-study system"
  :url "https://github.com/kawasima/linkoln"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.4"]
                 [ring "1.3.2"]
                 [ring/ring-defaults "0.1.5"]
                 [environ "1.0.0"]
                 [buddy "0.5.4"]
                 
                 [com.datomic/datomic-free "0.9.5173" :exclusions [org.slf4j/slf4j-api org.slf4j/slf4j-nop]]
                 [datomic-schema "1.3.0"]
                 [hiccup "1.0.5"]
                 [garden "1.2.5"]
                 [net.unit8/gring "0.2.0"]
                 [org.pegdown/pegdown "1.5.0"]]
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :plugins [[lein-ring "0.9.4"]
            [lein-figwheel "0.3.3"]]
  :ring {:handler linkoln.core/app
         :init    linkoln.core/init}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]]}})

