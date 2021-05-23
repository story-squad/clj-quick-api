(ns quick-api.cache
  (:require  [clojure.data.json :as json]
             [taoensso.carmine :as car]
             [com.wsscode.edn-json :as edn-json]
             [quick-api.atom :refer [state]]))
;; signature should be consistent across platforms.
;; a simple template like [app-name][layer][major version][keyname] 
;; should suffice and will allow cross-platform information lookups
(defonce app-signature "[QUICK-API][BACKEND][v1]") ; global Application signature

(defn signed-key
  "wrap name with signature"
  [name]
  (let [signature (@state :signature) ; instance signature
        key-sig (str app-signature (when signature (str "[" signature "]")))]
    (str key-sig "[" name "]")))

(def server-conn nil) ; defaults to localhost on 6379

; Clojure NOTE:
; when a function is quoted, we don't need to immediately import it's deps.
; (as long as we import them before invoking the quoted function)

; depends on [taoensso.carmine :as car]
(defmacro wcar*
  "queue redis commands on server-conn"
  [& body]
  `(car/wcar server-conn ~@body))

;; (defn test-handler
;;   "this handler might... update a list of connected websockets?"
;;   [msg current-state]
;;   (println (str "MSG " msg))
;;   (println (str "STATE " current-state)))

;; (defmacro w-listener
;;   [handler-fn init-state & body]
;;   `(car/with-new-listener server-conn ~handler-fn ~init-state ~@body))

;; (defn test-new-listener []
;;   (w-listener
;;    test-handler
;;    {:example "initial state"}
;;    (car/ping)
;;    (car/set "foo" "bar")
;;    (car/get "foo")))

;; Clojure NOTE: 
;; add to core.clj
;;
;; (ns app.core
;;   (:require
;;    [taoensso.carmine :as car]
;;    [quick-api.rdb :refer [wcar*]])
;;   (:gen-class))
;;

;; NOTE:
;; invoke
;; (wcar*
;;  (car/ping)
;;  (car/set "foo" "bar")
;;  (car/get "foo"))

;; encode String

(defn encode-str
  "string -> base64"
  [str-to-encode]
  (.encodeToString (java.util.Base64/getEncoder) (.getBytes str-to-encode)))

(defn decode-str
  "base64 -> string"
  [b64-str]
  (String. (.decode (java.util.Base64/getDecoder) b64-str)))

;; encode non-String

;; (defn decode-obj
;;   "base64 -> JSON string -> EDN"
;;   [b64-str]
;;   (edn-json/decode-value (decode-str b64-str)))

(defn encode-obj
  "object -> JSON -> base64"
  [obj-to-encode]
  (let [o (json/write-str obj-to-encode)]
    (encode-str o)))

;; Redis Clojure

(defn _redis-set
  [key value]
  (wcar*
   (car/set key value)
   (car/get key)))

(defn _redis-get
  [key]
  (wcar* (car/get key)))

(defn set-object
  "Redis-set"
  [key value]
  (_redis-set (signed-key key) (encode-obj value)))

;; (defn set-string [key value]
;;   (_redis-set (signed-key key) (encode-str value)))

(defn get-decoded-string [key]
  (decode-str (_redis-get (signed-key key))))

(defn get-json
  "Redis-get [key] -> decode [value] -> JSON"
  [key]
  (json/read-json (get-decoded-string key)))

(defn get-edn
  "Redis-get [key] -> decode [value] -> JSON -> EDN"
  [key]
  (let [encoded (or (_redis-get (signed-key key)) "")]
    (when (> (count encoded) 0)
      (edn-json/json-like->edn (json/read-json (decode-str encoded))))))
