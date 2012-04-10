(ns milieu.config
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]))

(defonce ^:private configuration (atom {}))

(def env-sysvar-name "MILIEU_ENV")

(def quiet-sysvar-name "MILIEU_QUIET")

(def ^:dynamic *config-file-name* "configure.yml")

(defn ^:private warn [message & format-args]
  (when-not (System/getenv quiet-sysvar-name)
    (do (log/warn (apply format message format-args))
        nil)))

;; Determining the environment: if *env* is bound (as when using with-env),
;; use that.  If not, use the default, set at compile time.  If the env var
;; MILIEU_ENV exists, use that, otherwise default to the safest: dev.

(def ^:dynamic *env*
  (or (keyword (System/getenv env-sysvar-name))
      (do (warn "system variable %s was not set. Default value will be \"dev\"."
                env-sysvar-name)
          :dev)))

(defmacro with-env
  "bind the environment to a value for the calling context"
  [env & body]
  `(binding [*env* (or (keyword ~env) *env*)]
     ~@body))

(defn value
  "Access a config value.
   Example: (config/value :cassandra :ip)

   If commandline-overrides are in place (including false-valued ones),
   these take precedent over the present environment's fields."
  [k & ks]
  (let [value' (fn [e] (get-in @configuration (concat [e k] ks)))
        env-value (value' *env*)
        override (value' :cmdargs)]
    (cond (or override (false? override)) override
          (or env-value (false? env-value)) env-value
          :none-provided (warn "requested config setting %s not found!" ks))))

(defn ^:private keywordize
  "helper function for load-config"
  [config-map]
  (walk/prewalk
   (fn [form]
     (if (and (string? form)
              (= \: (first form)))
       (keyword (apply str (rest form))) form))
   config-map))

(defn load-config
  "load the yaml config file"
  []
  (when-let [config-file (io/resource *config-file-name*)]
    (swap! configuration
           #(merge % 
                   (-> config-file
                       slurp
                       yaml/parse-string
                       keywordize)))))

(defn ^:private commandline-overrides* [args]
  (assert (even? (count args)))
  (let [cmdarg->cfgkey
        (fn [s] (->>
                 (-> s (str/replace #"^-+" "") (str/split #"\."))
                 (map keyword)
                 vec))]
    {:cmdargs
     (reduce
      (fn [m [k v]]
        (update-in m (cmdarg->cfgkey k) (constantly (read-string v))))
      {} (partition 2 args))}))

(defn commandline-overrides!
  "destructively override values, regardless of environment.
   $ myprogram prod --fou.barre Fred --db.host 127.0.0.1"
  [args]
  (swap! configuration (fn [m] (merge m (commandline-overrides* args)))))

(load-config)
