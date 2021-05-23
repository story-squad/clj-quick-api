<img src="https://upload.wikimedia.org/wikipedia/commons/5/5d/Clojure_logo.svg?download" height="120">

# Quick-API

template to bootstrap a Clojure API:

> from [Leiningen](https://leiningen.org/).

- [Ring](https://github.com/ring-clojure/ring) (a Clojure web applications library inspired by Python's WSGI and Ruby's Rack)

- [Lein Ring](https://github.com/weavejester/lein-ring) (a plugin that automates common Ring tasks)

- [Reitit](https://github.com/metosin/reitit) (A fast data-driven router for Clojure(Script))

- [carmine](https://github.com/ptaoussanis/carmine) (a pure-Clojure Redis client & message queue)

- [edn-json](https://cljdoc.org/d/edn-json/edn-json/1.1.0/api/com.wsscode.edn-json) (object notation translation library)

- [ring-cors](https://github.com/r0man/ring-cors) (Ring middleware for Cross-Origin Resource Sharing)

- [muuntaja](https://github.com/metosin/muuntaja) (Clojure library for fast http format negotiation, encoding and decoding.)

## Requires

[Clojure](https://clojure.org), [Leiningen](https://leiningen.org/#install), & [Java](https://adoptopenjdk.net/)

## Environment

- PORT
- SSLPORT

> *read documentation on [Lein Ring](https://github.com/weavejester/lein-ring#environment-variables).

Start a Ring server and open a browser.
```
lein ring server
```

Start a Ring server without opening a browser.
```
lein ring server-headless
```

## Compile

Create an executable $PROJECT-$VERSION.jar file with dependencies.
```
lein ring uberjar
```

Create an executable $PROJECT-$VERSION.jar file.
```
lein ring jar
```

## Run

> the compiled jar runs on java.
>
> example:
```
java -jar target/quick-api-0.1.0-standalone.jar
```

...

### Bugs

...

### Any Other Sections
### That You Think
### Might be Useful
