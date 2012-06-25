# milieu

The environmentally friendly configuration tool.

------

**Build status:** [![Build Status](https://secure.travis-ci.org/drakerlabs/milieu.png?branch=master)](http://travis-ci.org/drakerlabs/milieu)

## Features:

* Set up environment-specific configuration for your Clojure application, using
the popular YAML file format.

* Access config values:

  Specifying config values:
  ```clojure
  (config/value :my :config :value)
  ```
  This will access the value in
  ```yaml
    dev:
      my:
        config:
          value: 2

    ...

    test:
      my:
        config:
          value: 3
  ```

  Another example:
  ```yaml
    dev:
      smiles:
        - mary: "8-)"
          fred: ":-|"
        - mary: "*_*"
          fred: "-__-"
  ```
  Access using:
  ```clojure
    (config/value :smiles 0 :mary) => "8-)"
    (config/value :smiles 1 :fred) => "-__-"
  ```

  This will access the value in

  Specifying config values as optional:
  ```clojure
  (config/value| :my :config :value) ; same as config/value except doesn’t warn if it’s not found
  (config/value| [:my :config :value] "alternate value") ; provide alternate value
  ```

* Optionally auto-load config file.
  * using the default filename of "configure.yml" enables autoload
  * any other config file name can be specified by calling load-config

* Bind the environment within a calling context using with-env.
  ```clojure
    (config/with-env env
      (when (config/value :some :setting) ... ))
  ```

* Specify the default environment using the MILIEU_ENV system variable.

* Override environment-specific settings using arguments to your command-line
application.
  ```clojure
    (config/commandline-overrides! args)
    (config/with-env env
      ... )
  ```

* In cases where the environment can be variable, code evaluation can by
restricted in with-env or only-env, or more generally conditional using
if-env and when-env.

  ```clojure
    ;; If env is prod, the code in the body will not be exercised,
    ;; an exception is thrown instead:
    (defn -main [env & args]
      (config/with-env [env :only [:test :dev]] ,,,))
  ```

  Alternatively (for example if you aren't in the context of a with-env)...
  ```clojure
    ;; If env is prod, the code in the body will not be exercised,
    ;; an exception is thrown instead:
    (config/only-env [:test :dev] ,,,)
  ```

  The following forms are general purpose conditionals (not assertions of
  environment restrictions).
  ```clojure
    (config/if-env :test "hello" "goodbye")
  ```

  ```clojure
    (config/when-env :dev ,,,)
  ```

Helpful info / warnings that can be turned off with MILIEU_QUIET system variable:

* WARNING: system variable MILIEU_ENV was not set. Default value will be "dev"

* WARNING: requested config setting [:fou :barre] not found!

* INFO: to enable auto-load, name your config-file configure.yml.

## Use

### Installation

http://clojars.org/milieu

### Getting Started

```clojure
(ns example.core
  (:require [milieu.config :as config]))
```

### Example

```clojure
(defn -main [env & args]
  (config/commandline-overrides! args)
  (config/with-env env
    (when (config/value| :some :setting) ,,,
```

### Command-line Override

```shell
$ myprogram prod --fou.barre Fred --some.setting 127.0.0.1
```

## License

Author: Robert Levy / @rplevy-draker

Acknowledgments: @alanpeabody, @S11001001, @AlexBaranosky

Copyright © 2012 Draker Labs

Distributed under the Eclipse Public License, the same as Clojure.
