# milieu

The environmentally friendly configuration tool.

## Features:

* Set up environment-specific configuration for your Clojure application, using
the popular YAML file format.

* Optionally auto-load config file.
  * using the default filename of "configure.yml" enables autoload
  * any other config file name can be specified by calling load-config

* Bind the environment within a calling context using with-env.

* Specify the default environment using the MILIEU_ENV system variable.

* Override environment-specific settings using arguments to your command-line
application.

Helpful info / warnings that can be turned off with MILIEU_QUIET system variable:

* WARNING: system variable MILIEU_ENV was not set. Default value will be "dev"

* WARNING: requested config setting [:fou :barre] not found!

* INFO: to enable auto-load, name your config-file configure.yml.

## Use

### Installation

http://clojars.org/milieu

### Getting Started

```
(ns example.core
  (:require [milieu.config :as config]))
```

### Example

```
(defn -main [env & args]
  (config/commandline-overrides! args)
  (config/with-env env
    (when (config/value :some :setting) ,,,
```

### Command-line Override

```
$ myprogram prod --fou.barre Fred --some.setting 127.0.0.1
```

## License

Contributors: Robert Levy / @rplevy-draker, Alan Peabody / @alanpeabody

Acknowledgments: Stephen Compall (initial code review and comments)

Copyright © 2012 Draker Labs

Distributed under the Eclipse Public License, the same as Clojure.
