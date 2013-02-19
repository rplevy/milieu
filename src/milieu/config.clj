(ns milieu.config
  (:require [clojure.java.io :as io]
            [clojure.tools
             [logging :as log]
             [cli :as cli]]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [clojure.string :as str]
            [swiss-arrows.core :refer [-<>]]))

(defonce ^:private configuration (atom {}))

(def ^{:private false :dynamic true} *overrides* {})

(def ^:private env-sysvar-name "MILIEU_ENV")

(def ^:private quiet-sysvar-name "MILIEU_QUIET")

(def ^:private default-config-name "configure.yml")

(defn ^:private getenv [sysvar]
  (System/getenv sysvar))

(defn ^:private warn* [message format-args]
  (do (log/warn (apply format message format-args))
      nil))

(defn ^:private warn [message & format-args]
  (when-not (getenv quiet-sysvar-name) (warn* message format-args)))

;; Determining the environment: if *env* is bound (as when using with-env),
;; use that.  If not, use the default, set at compile time.  If the env var
;; MILIEU_ENV exists, use that, otherwise default to dev.

(def ^:dynamic *env*
  (or (keyword (getenv env-sysvar-name))
      :dev))

(defn ^:private cli [cli-options args]
  (let [read-string' (fn [s]
                       #_"read-string could be used as it is, but it is tiresome
                          to enter strings like '\"....\"' in command-line args.
                          To address this, accept tokens verbatim and string-ify
                          non-keyword, non-number tokens. Tokens that don't play
                          well with the reader are also interpreted as strings.

                          As an alternative to this 'type guessing' :cli-options
                          can be specified, applying clojure.tools.cli."
                       (if (re-find #"\s" s)
                         s
                         (let [x (binding [*read-eval* false]
                                   (try (read-string s) (catch Exception e s)))]
                           (if (or (keyword? x)
                                   (= (class x) java.lang.Boolean)
                                   (isa? (class x) java.lang.Number))
                             x
                             (str s)))))
        generate-options (fn [args] (reduce
                                     (fn [r k]
                                       (conj r [k :parse-fn read-string']))
                                     []
                                     (filter #(= (first %) \-) args)))
        cmdarg->cfgkey (fn [s] (-<> s
                                    (str/replace <> #"^-+" "")
                                    (str/split <> #"\.")
                                    (map #(if (re-matches #"\d+" %)
                                            (Integer/parseInt %)
                                            (keyword %))
                                         <>)
                                    vec))
        [cmdargs _ banner] (cli/cli args (or cli-options
                                             (generate-options args)))
    (reduce-kv (fn [r cmdarg cmdval]
                 (conj r (cmdarg->cfgkey cmdarg) cmdval))
               []
               (apply hash-map args))))

(defn ^:private resolve-format
  "if :as format is not specified, determine the format based on source"
  [src-spec]
  (when src-spec
    (or (:as src-spec)
        (let [resolve-format*
              (fn [src-spec]
                (condp re-find src-spec
                  #"\.ya?ml$" :yml
                  #"\.json$" :json
                  #"\.edn$" :edn
                  (throw (Exception. "Unable to resolve config format."))))]
          (if (string? src-spec)
            (resolve-format* src-spec)
            (resolve-format* (:src src-spec)))))))

(defn ^:private file-src [src-spec process-config]
  (let [config-file (io/resource (if (string? src-spec)
                                   src-spec
                                   (:src src-spec)))]
    (if-not config-file
      (throw (Exception. "config file not found."))
      (process-config config-file))))

(defmulti src
  ":tgt (not yet implemented)
     specify a target config environment for these values, for cases where it
     is not already specified in the source data itself.

   :scope (not yet implemented)
     specify a limited scope, such that provided values only apply when the
     given scope is indicated at the time of accesing the value.

   :as specifies source type.
     :yml|:json|:edn
       for file-based configuration
       :src \"filenamestring\"
     :cli
       command-line interface
       :src [\"--option\" \"value\" ...]
       :cli-options (see clojure.tools.cli documentation)
         If not specified, then the values are automatically converted in a way
         that maintains parity with the conversions applied parsing file formats
       :cli-help-function [:help-key (fn [usage-doc] ... )]
         optional hook for printing help/usage using tool.cli-generated usage
         string.
     :data
       directly specify values as data.
       :src {:dev {:foo 1}}
     :assoc-in
       directly specify values in the style used by assoc-in.
       :src [[:key1 :key1] value, ...]
     :environ (system environment vars and lein config)
       (not yet implemented - will integrate weavejester/environ functionality)"
  resolve-format)

(defmethod src nil [_] {})

(defmethod src :yml [src-spec]
  (file-src src-spec #(yaml/parse-string (slurp %))))

(defmethod src :json [src-spec]
  (file-src src-spec #(json/parse-string (slurp %) true)))

(defmethod src :edn [src-spec]
  (file-src src-spec #(binding [*read-eval* false] (read-string (slurp %)))))

(defmethod src :assoc-in [src-spec]
  (reduce-kv assoc-in {} (apply hash-map (:src src-spec))))

(defmethod src :cli [src-spec]
  (src (assoc (update-in src-spec [:src] (partial cli (:cli-options src-spec)))
         :as :assoc-in)))

(defmethod src :data [src-spec]
  (:src src-spec))

(defmacro with-env
  "bind the environment to a value for the calling context.

   Env can optionally be a vector containing the env and options. Presently
   the option :only is supported, which stops execution if the provided
   env is not in the limited set.

   The option :overrides provides the ability to override values for the
   specified environment

   Usage:

    (with-env <ENV> ...)
    (with-env [<ENV> :only [<ENV1>, <ENV2>, ... ]]
    (with-env <ENV> :overrides {:src <DATA> :as <FORMAT>} ...)"
  [env & body]
  (let [[env {:keys [only] :as options}] (if (vector? env)
                                           [(first env)
                                            (apply hash-map (rest env))]
                                           [env])
        [src-spec body]                  (if (= (first body) :overrides)
                                           [(second body) (next (next body))]
                                           [nil body])]
    `(if (and ~only (not ((set ~only) (keyword ~env))))
       (throw (Exception. "Access to this environment is prohibited."))
       (binding [*env* (or (keyword ~env) *env*)
                 *overrides* (src ~src-spec)]
         ~@body))))

(defmacro only-env
  "similar to :only option in with-env but allows for assertion of environment
   restricted code more generally, including when the environement is set by
   the system variable and not by with-env"
  [env-vector & body]
  `(if (not ((set ~env-vector) *env*))
     (throw (Exception. "Access to this environment is prohibited."))
     (do ~@body)))

(defmacro if-env [env if-form else-form]
  `(if (= *env* ~env) ~if-form ~else-form))

(defmacro when-env [env & body]
  `(when (= *env* ~env) ~@body))

(defn environments "list all available environments" []
  (set (keys @configuration)))

(defn env? "check if the env keyword refers to an existing environment" [env]
  ((environments) env))

(defn value*
  [[k & ks] & optional?]
  (let [env-value      (get-in @configuration (concat [*env* k] ks))
        override-value (get-in *overrides*    (cons          k  ks))]
    (cond (or override-value (false? override-value)) override-value
          (or env-value (false? env-value))           env-value
          :none-provided (when-not optional?
                           (warn "requested config setting %s not found!"
                                 (cons k ks))))))

(defn value
  "Access a config value.
   Example: (config/value :cassandra :ip)

   If commandline-overrides are in place (including false-valued ones),
   these take precedent over the present environment's fields."
  [k & ks]
  (value* (cons k ks)))

(defmacro value|
  "Same as value function, except:
     * treats the value as optional
     * accepts an alternate syntax for default/failover
       e.g. (config/value| [:j :k] \"alternative\"
     * is defined as a macro to support control flow shortcutting"
  [ks & [alt & more]]
  (if (vector? ks)
    `(or (value* ~ks :optional)
         ~alt)
    (let [ks (keep identity (flatten [ks alt more]))]
      `(value* [~@ks] :optional))))

(defn load-config
  "load configuration source"
  [src-spec]
  ;; TODO: recursive merge with preexisting config
  (swap! configuration #(merge % (src src-spec))))

(when (io/resource default-config-name) ; auto-load if file name convention
  (load-config default-config-name))    ; for auto-load is followed.
