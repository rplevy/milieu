(ns milieu.config-test
  (:use midje.sweet)
  (require [milieu.config :as config]))

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

(facts
 "with-env observes specified restrictions"
 (let [some-fun (fn [env]
                  (config/with-env [env :only [:dev :test]]
                    (config/value :fou :barre)))]
   (some-fun :dev) => "127.0.0.1"
   (some-fun :test) => "9.9.9.9"
   (some-fun :prod) => (throws Exception)))

(facts
 "only-env observes specified restrictions"
 (let [some-fun (fn []
                  (config/only-env
                   [:dev :test]
                   (config/value :fou :barre)))]
   (some-fun) => "127.0.0.1" ; milieu default is :dev
   (config/with-env :test (some-fun)) => "9.9.9.9"
   (config/with-env :prod (some-fun)) => (throws Exception)))

(facts
 "you can use vectors"
 (config/value :smiles 0 :mary) => "8-)"
 (config/value :smiles 1 :fred) => "-__-"
 (config/value| :smiles 1 :mary) => "*_*"
 (config/value| [:smiles 1 :mary] "ಠ_ಠ") => "*_*")

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

(against-background
 [(around :facts (config/with-env :prod ?form))]
 (facts
  (config/value| :fou)        => map?
  (config/value| :fou :barre) => "7.7.7.7"
  (config/value| [:fou :barre] "") => "7.7.7.7"

  (fact
   "alternate value should be used when item is not defined"
   (config/value| [:fou :berry] "9.9.9.9") => "9.9.9.9")

  (fact
   "flow of control should shortcut and not evaluate the alternative"
   (let [a (atom 0)]
     (config/value| [:fou :barre] (do (reset! a 1))) => "7.7.7.7"
     @a => 0))))

(facts
 "environment conditionals"
 (config/with-env :test (config/if-env :dev 1 2)) => 2
 (config/with-env :test (config/if-env :test 1 2)) => 1
 (config/with-env :dev (config/when-env :dev 1 2 3 4 5)) => 5
 (config/with-env :dev (config/when-env :test 1 2 3 4 5)) => nil)

(fact
 "warning when not found and not in quiet mode"
 (against-background (#'config/getenv @#'config/quiet-sysvar-name) => nil)
 (config/value :non-existent) => "performed warning"
 (provided (#'config/warn* irrelevant irrelevant) => "performed warning"))

(fact
 "no warning when not found but in quiet mode"
 (against-background (#'config/getenv @#'config/quiet-sysvar-name) => true)
 (config/value :non-existent) => anything
 (provided (#'config/warn* irrelevant irrelevant) => true :times 0))

(fact
 "no warning for optional regardless of not being in quiet mode"
 (against-background (#'config/getenv @#'config/quiet-sysvar-name) => nil)
 (config/value| :non-existent) => anything
 (provided (#'config/warn* irrelevant irrelevant) => true :times 0))

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

 (#'config/commandline-overrides* ["--smiles.1.mary" "\":D\""])
 => #(= ":D" (str (get-in % [:cmdargs :smiles 1 :mary])))

 (do (config/commandline-overrides! ["--fou.barre" "1"])
     (config/with-env :cmdargs (config/value :fou :barre)))
 => 1

 (do (config/commandline-overrides! ["--smiles.0.fred" "\":{)\""])
     (config/with-env :cmdargs (config/value :smiles 0 :fred)))
 => ":{)"

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
