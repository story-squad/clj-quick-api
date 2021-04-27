<img src="https://upload.wikimedia.org/wikipedia/commons/5/5d/Clojure_logo.svg?download" height="120">

# Quick-API

template to bootstrap a Clojure API:

> from [Leiningen](https://leiningen.org/).

- [Ring](https://github.com/ring-clojure/ring) (a Clojure web applications library inspired by Python's WSGI and Ruby's Rack)

- [Reitit](https://github.com/metosin/reitit) (A fast data-driven router for Clojure(Script))

- [carmine](https://github.com/ptaoussanis/carmine) (a pure-Clojure Redis client & message queue)

- [edn-json](https://cljdoc.org/d/edn-json/edn-json/1.1.0/api/com.wsscode.edn-json) (object notation translation library)

- [ring-cors](https://github.com/r0man/ring-cors) (Ring middleware for Cross-Origin Resource Sharing)

- [muuntaja](https://github.com/metosin/muuntaja) (Clojure library for fast http format negotiation, encoding and decoding.)

## Installation

Install [Clojure](https://clojure.org) & [Leiningen](https://leiningen.org/#install)

## Usage

to start an interactive REPL,
```
lein run repl
```
start the API
> note: (-main) is only invoked once, at startup.
- provide the first user name
```
(-main "Admin")
```

- you may also, in addition to name, provide an email...
```
(-main "Admin" "root@localhost")
```

- and a signature, should you choose to run several variations
```
(-main "Admin" "root@localhost" "unique-cloud-signature")
```

## Build

create a jar

```
lein uberjar
```

## Run
> note: run the compiled jar with those [args] you provided to (-main)
```
java -jar quick-api-0.1.0-standalone.jar [args]
```

## Example
```
java -jar quick-api-0.1.0-standalone.jar "Admin" "root@localhost" "unique-cloud-signature"
```

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful
