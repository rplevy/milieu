(ns milieu.config
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [swiss-arrows.core :refer [-<>]]))

(defonce ^:private configuration (atom {}))

(defonce ^:private overrides (atom {}))

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

(defn ^:private info [message & format-args]
  (when-not (getenv quiet-sysvar-name)
    (do (log/info (apply format message format-args))
        nil)))

;; Determining the environment: if *env* is bound (as when using with-env),
;; use that.  If not, use the default, set at compile time.  If the env var
;; MILIEU_ENV exists, use that, otherwise default to dev.

(def ^:dynamic *env*
  (or (keyword (getenv env-sysvar-name))
      (do (warn
           "system variable %s was not set. Default value will be \"dev\"."
           env-sysvar-name)
          :dev)))

(defmacro with-env
  "bind the environment to a value for the calling context.

   Env can optionally be a vector containing the env and options. Presently
   the option :only is supported, which stops execution if the provided
   env is not in the limited set."
  [env & body]
  (let [[env {:keys [only] :as options}]
        (if (vector? env)
          [(first env) (apply hash-map (rest env))]
          [env])]
    `(if (and ~only (not ((set ~only) (keyword ~env))))
       (throw (Exception. "Access to this environment is prohibited."))
       (binding [*env* (or (keyword ~env) *env*)]
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
  (let [env-value      (get-in @configuration (concat [*env*    k] ks))
        override-value (get-in @overrides     (concat [:cmdargs k] ks))]
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

(defn ^:private keywordize
  "helper function for load-config"
  [config-map]
  (walk/prewalk
   (fn [form]
     (cond (and (string? form) (= \: (first form)))
           (keyword (apply str (rest form))),
           (seq? form) (vec form),
           :else form))
   config-map))

(defn load-config
  "load the yaml config file"
  [config-name]
  (let [config-file (io/resource config-name)]
    (if-not config-file (throw (Exception. "config file not found.")))
    (swap! configuration
           #(merge %
                   (-> config-file
                       slurp
                       yaml/parse-string
                       keywordize)))))

(defn ^:private commandline-overrides* [args]
  (assert (even? (count args)))
  (let [index-or-key #(if (re-matches #"\d+" %)
                        (Integer/parseInt %) (keyword %))
        cmdarg->cfgkey
        (fn [s] (-<> s
                     (str/replace <> #"^-+" "")
                     (str/split <> #"\.")
                     (map index-or-key <>)
                     vec))
        read-string' (fn [s]
                       #_"read-string could be used as it is, but it is tiresome
                          to enter strings like '\"....\"' in command-line args.
                          To address this, accept tokens verbatim and string-ify
                          non-keyword, non-number tokens. Tokens that don't play
                          well with the reader are also interpreted as strings."
                       (let [x (binding [*read-eval* false]
                                 (try (read-string s) (catch Exception e s)))]
                         (if (or (keyword? x)
                                 (= (class x) java.lang.Boolean)
                                 (isa? (class x) java.lang.Number))
                           x
                           (str s))))]
    {:cmdargs
     (reduce-kv
      #(assoc-in %1 (cmdarg->cfgkey %2) (read-string' %3))
      {} (apply hash-map args))}))

(defn commandline-overrides!
  "override values, regardless of environment.
   $ myprogram prod --fou.barre Fred --db.host 127.0.0.1"
  [args]
  (swap! overrides (fn [m] (merge m (commandline-overrides* args)))))

(if-not (io/resource default-config-name)
  (info "to enable auto-load, name your config-file %s." default-config-name)
  (load-config default-config-name))
