(ns milieu.config-test
  (:use midje.sweet)
  (require [milieu.config :as config]))

(fact
 (config/with-env :test
   (do-something!)
   (do-another-thing!)) =expands-to=> (clojure.core/binding
                                       [milieu.config/*env*
                                        (clojure.core/or
                                         (clojure.core/keyword :test)
                                         milieu.config/*env*)]
                                       (do-something!)
                                       (do-another-thing!)))

(facts
 ;; make sure we can load and parse a config file

 (do
   (swap! @#'config/configuration (constantly {}))
   @@#'config/configuration)
 => {}

 (config/load-config "non-existent-file.yml") => (throws Exception)
 
 ;; this also sets up state for the following tests
 
 (do (config/load-config "configure.example.yml")
     (:dev @@#'config/configuration))
 => map?)


(against-background
 [(around :facts (config/with-env :prod ?form))]
 (facts
  (config/value :fou)        => map?
  (config/value :magic)      => true
  (config/value :fou :barre) => "7.7.7.7"
  (str (config/value :size)) => "large"
  (config/value :fou :car)   => 6))

(against-background
 [(around :facts (config/with-env :test ?form))]
 (facts
  (config/value :fou)        => map?
  (config/value :magic)      => false
  (config/value :fou :barre) => "9.9.9.9"
  (str (config/value :size)) => "medium"
  (config/value :fou :car)   => 9))

(fact
 "warning when not found and not in quiet mode"
 (against-background (config/getenv @#'config/quiet-sysvar-name) => nil)
 (config/value :non-existent) => "performed warning"
 (provided (config/warn* irrelevant irrelevant) => "performed warning"))

(fact
 "no warning when not found but in quiet mode"
 (against-background (config/getenv @#'config/quiet-sysvar-name) => true)
 (config/value :non-existent) => anything
 (provided (config/warn* irrelevant irrelevant) => true :times 0))

(facts
 "command-line overrides"
 (#'config/commandline-overrides* ["--fou.barre" "1" "--skidoo" "(1 2 3)"])
 => {:cmdargs {:skidoo '(1 2 3), :fou {:barre 1}}}
 
 (#'config/commandline-overrides* ["--fou" "1" "--skidoo" "(1 2 3)"])
 => {:cmdargs {:skidoo '(1 2 3), :fou 1}}

 (#'config/commandline-overrides* ["-fou" "1" "-skidoo" "(1 2 3)"])
 => {:cmdargs {:skidoo '(1 2 3), :fou 1}}

 (#'config/commandline-overrides* ["fou" "1" "skidoo" "(1 2 3)"])
 => {:cmdargs {:skidoo '(1 2 3), :fou 1}}

 (#'config/commandline-overrides* ["--fou.barre" "\"skidoo\""])
 => {:cmdargs {:fou {:barre "skidoo"}}}

 (#'config/commandline-overrides* ["--fou.barre" "skidoo"])
 => #(= "skidoo" (str (get-in % [:cmdargs :fou :barre])))

 (do (config/commandline-overrides! ["--fou.barre" "1"])
     (config/with-env :cmdargs (config/value :fou :barre)))
 => 1
 
 ;; override means it should take precedence over the active environment
 (let [_ (swap! @#'config/configuration
                #(update-in % [:prod :fou :my-barre] (constantly "127.0.0.1")))
       barre (config/with-env :prod (config/value :fou :my-barre))
       _ (config/commandline-overrides! ["--fou.my-barre" "\"1.2.3.4\""])
       changed-barre (config/with-env :prod (config/value :fou :my-barre))
       _ (config/commandline-overrides! ["--fou.my-barre" "false"])
       changed-to-false (config/with-env :prod (config/value :fou :my-barre))
       _ (config/commandline-overrides! ["--fou.my-barre" "nil"])
       changed-to-nil (config/with-env :prod (config/value :fou :my-barre))]
   [barre changed-barre changed-to-false changed-to-nil])
 => ["127.0.0.1" "1.2.3.4" false "127.0.0.1"])
