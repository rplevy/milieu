(defproject milieu "0.0.1"
  :description "The environmentally friendly configuration tool."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-yaml "0.3.1"]
                 [swiss-arrows "0.0.4"]]
  :profiles {:dev {:dependencies [[midje "1.3.1"]]}}
  :plugins [[lein-midje "2.0.0-SNAPSHOT"]])
