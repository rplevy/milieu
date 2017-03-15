(defproject rplevy/milieu "0.9.2"
  :description "The environmentally friendly configuration tool."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [clj-yaml "0.3.1"]
                 [swiss-arrows "1.0.0"]]
  :profiles {:dev {:dependencies [[midje "1.8.3" :exclusions [org.clojure/clojure]]]}}
  :plugins [[lein-midje "3.2.1" :exclusions [org.clojure/clojure]]]
  :repositories [["clojars"
                  {:url "http://clojars.org/content/repositories/releases"
                   :snapshots false
                   :sign-releases false
                   :checksum :fail
                   :update :always
                   :releases {:checksum :fail :update :always}}]])
