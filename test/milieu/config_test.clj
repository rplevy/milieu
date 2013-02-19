(ns milieu.config-test
  (:use midje.sweet)
  (require [milieu.config :as config]))

(facts
 "make sure we can load and parse a config file"

 (do
   (swap! @#'config/configuration (constantly {}))
   @@#'config/configuration)
 => {}

 (config/load-config "non-existent-file.yml") => (throws Exception))

(facts
 "all of the example config files are identical, but in different formats.
  all of the config loading methods should result in same data structures."

 ;; and this also sets up state for the following tests

 (let [from-yml      (do (reset! @#'config/configuration {})
                         (config/load-config       "configure.example.yml")
                         @@#'config/configuration)
       from-yml-alt  (do (reset! @#'config/configuration {})
                      (config/load-config {:src "configure.example.yml"
                                           :as :yml})
                      @@#'config/configuration)
       from-json     (do (reset! @#'config/configuration {})
                         (config/load-config       "configure.example.json")
                         @@#'config/configuration)
       from-json-alt (do (reset! @#'config/configuration {})
                      (config/load-config {:src "configure.example.json"
                                           :as :json})
                      @@#'config/configuration)
       from-edn      (do (reset! @#'config/configuration {})
                         (config/load-config       "configure.example.edn")
                         @@#'config/configuration)
       from-edn-alt  (do (reset! @#'config/configuration {})
                         (config/load-config {:src "configure.example.edn"
                                              :as :edn})
                         @@#'config/configuration)]
   from-json     => from-json-alt
   from-json-alt => from-yml
   from-yml      => from-yml-alt
   from-yml-alt  => from-edn
   from-edn      => from-edn-alt
   from-edn-alt      => from-json))

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
 "cli as source"
 (#'config/cli ["--fou.barre" "1", "--skidoo" "1 2 3"])
 => [[:fou :barre] 1, [:skidoo] "1 2 3"]
 (#'config/cli ["--fou" "1", "--skidoo" "(1 2 3)"])
 => [[:fou] 1, [:skidoo] "(1 2 3)"])

(facts
 "overriding values"
 (config/with-env :dev
   :overrides {:src ["--fou.barre" "1", "--skidoo" "(1 2 3)"] :as :cli}
   config/*overrides*)
 => {:skidoo "(1 2 3)", :fou {:barre 1}}

 (config/with-env :dev
   :overrides {:src ["--fou" "1", "--skidoo" "(1 2 3)"] :as :cli}
   config/*overrides*)
 => {:skidoo "(1 2 3)", :fou 1}

 (config/with-env :dev
   :overrides {:src ["--fou" "1", "--skidoo" "1 2 3"] :as :cli}
   config/*overrides*)
 => {:skidoo "1 2 3", :fou 1}

 (config/with-env :dev
   :overrides {:src ["-fou" "1", "-skidoo" "(1 2 3)"] :as :cli}
   config/*overrides*)
 => {:skidoo "(1 2 3)", :fou 1}

 (config/with-env :dev
   :overrides {:src [[:fou] 1, [:skidoo] "(1 2 3)"] :as :assoc-in}
   config/*overrides*)
 => {:skidoo "(1 2 3)", :fou 1}

 (config/with-env :dev
   :overrides {:src {:fou 1, :skidoo "(1 2 3)"} :as :data}
   config/*overrides*)
 => {:skidoo "(1 2 3)", :fou 1}

 (config/with-env :dev
   :overrides {:src ["fou" "1", "skidoo" "(1 2 3)"] :as :cli}
   config/*overrides*)
 => {:skidoo "(1 2 3)", :fou 1}

 (config/with-env :dev
   :overrides {:src ["--fou.barre" "skidoo"] :as :cli}
   config/*overrides*)
 => {:fou {:barre "skidoo"}}

 (config/with-env :dev
   :overrides {:src ["--fou.barre" "1"] :as :cli}
   (config/value :fou :barre))
 => 1

 (config/with-env :dev
   :overrides {:src ["--smiles.0.fred" ":{)"] :as :cli}
   (config/value :smiles 0 :fred))
 => ":{)"

 (config/with-env :dev
   :overrides {:src [[:smiles 0 :fred] ":{)"] :as :assoc-in}
   (config/value :smiles 0 :fred))
 => ":{)"

 (config/with-env :dev
   :overrides {:src {:smiles [{:fred ":{)"}]} :as :data}
   (config/value :smiles 0 :fred))
 => ":{)"

 ;; override means it should take precedence over the active environment
 (let [_ (swap! @#'config/configuration
                #(assoc-in % [:prod :fou :my-barre] "127.0.0.1"))
       barre (config/with-env :prod (config/value :fou :my-barre))
       [hello barre'] (config/with-env :prod
                        :overrides {:src [[:hello]         2
                                          [:fou :my-barre] "1.2.3.4"]
                                    :as :assoc-in}
                        [(config/value :hello)
                         (config/value :fou :my-barre)])
       barre'' (config/with-env :prod
                 :overrides {:src [[:fou :my-barre] false]
                             :as :assoc-in}
                 (config/value :fou :my-barre))]
   [barre hello barre' barre''])
 => ["127.0.0.1" 2 "1.2.3.4" false])

(facts
 "about checking environment as valid/existing"
 (config/environments) => #{:dev :test :prod}
 (config/env? :dev) => truthy
 (config/env? :prod) => truthy
 (config/env? :foo) => falsey)
