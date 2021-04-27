(ns quick-api.core
  (:require
   [ring.adapter.jetty :as ring-jetty]
   [ring.middleware.cors :refer [wrap-cors]]
   [reitit.ring :as ring]
   [reitit.coercion.spec :as spec]
   [reitit.dev.pretty :as pretty]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [muuntaja.core :as m]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [quick-api.atom :refer [state]]
   [quick-api.cache :as cache])
  (:gen-class))

(defn update-cache []
  (cache/set-object "app-state" @state))

(defn identify
  "store memory under signature"
  [signature]
  (when-not (@state :signature)
    (let [s (if (string? signature) signature "generic-key")]
     ; create a signature for this app
      (swap! state assoc :signature s)))
  ; return the app signature
  (@state :signature))

;; ---- utils ----
(defn timestamp []
  (.getTime (java.util.Date.)))

(defn format-date [format-string]
  (let [date-form (if-not format-string "MM/dd/yyyy" format-string)]
    (.format (java.text.SimpleDateFormat. date-form) (new java.util.Date))))

(defn create-user
  [{{:keys [email name]} :body-params}]
  (let [id (str (java.util.UUID/randomUUID))
        _ (swap! state update-in [:users] conj {:name name :email email :id id})]
    {:status 200 :body {:user-id id}}))

(defn get-users [_] {:status 200 :body (@state :users)})

(defn get-user-by-id [{{:keys [id]} :path-params}]
  {:status 200 :body (apply merge (map (fn [u] (when (= id (u :id)) u)) (@state :users)))})

;; (defn get-user-by-email [{{:keys [email]} :path-params}]
;;   {:status 200 :body (apply merge (map (fn [u] (when (= email (u :email)) u)) (@state :users)))})

(defn create-page [{{:keys [page-data]} :body-params}]
  (if page-data (let [id (str (java.util.UUID/randomUUID))]
                  (swap! state update-in [:pages] conj {:id id :text page-data :ts (timestamp) })
                  (update-cache)
                  {:status 201 :body {:id id :text page-data}})
      {:status 400 :body {:error "missing :page-data"}}))

(defn get-page-by-id [{{:keys [id]} :path-params}]
  {:status 200 :body (apply merge (map (fn [u] (when (= id (u :id)) u)) (@state :pages)))})

(defn list-pages [_]
  (let [sorted-pages (sort-by :ts  #(> %1 %2) ((cache/get-edn "app-state") :pages))]
    {:status 200 :body (or sorted-pages ["##error loading"])}))

(defn update-page [{{:keys [text]} :body-params {:keys [id]} :path-params}]
  (let [params {:path-params {:id id}}
        old-page (get-page-by-id params)
        ok (= id ((old-page :body) :id))]
    (when ok
      (swap! state update-in [:pages] conj {:id id :text text})
      (update-cache))
    (if ok
      {:status 200 :body {:id id :text text}}
      {:status 404 :body {:id id :text text :error "page not found"}})))

(defn delete-page-by-id [{{:keys [id]} :path-params}]
  (let [sig (@state :signature)
        old-page ((get-page-by-id {:path-params {:id id}}) :body)
        old-id (old-page :id)
        ok (= id old-id)
        newstate {:users (@state :users) :pages (vec (remove #(= (% :id) id) (@state :pages)))}]
    (when ok
      (reset-vals! state newstate)
      (identify sig)
      (update-cache))
    (list-pages true)))

(def app
  (ring/ring-handler
   (ring/router
    [["/swagger.json"
      {:get {:no-doc true
             :swagger {:info {:title "api"
                              :description "with reitit-ring"}}
             :handler (swagger/create-swagger-handler)}}]
     ["/users/:id" {:get {:summary "request a user by id"
                          :parameters {:path {:id string?}}
                          :handler get-user-by-id}}]
     ["/users"
      {:get {:summary "request the list of users"
             :handler get-users}
       :post {:summary "create a new user"
              :parameters {:body {:name string? :email string?}}
              :handler create-user}}]
     ["/pages/:id" {:get {:summary "request a page"
                          :parameters {:path {:id string?}}
                          :handler get-page-by-id}
                    :put {:summary "update a page"
                          :parameters {:path {:id string?} :body {:text string?}}
                          :handler update-page}
                    :delete {:summary "delete a page"
                             :parameters {:path {:id string?}}
                             :handler delete-page-by-id}}]
     ["/pages" {:post {:summary "create a new page"
                       :parameters {:body {:page-data string?}}
                       :handler create-page}
                :get {:summary "request a list of pages"
                      :handler list-pages}}]]
    {:exception pretty/exception
     :data {:coercion spec/coercion
            :muuntaja m/instance
            :middleware [#(wrap-cors %
                                     :access-control-allow-origin [#".*"]
                                     :access-control-allow-methods [:get :post :delete])
                         muuntaja/format-middleware
                         ;; swagger feature
                         swagger/swagger-feature
                           ;; query-params & form-params
                         parameters/parameters-middleware
                           ;; content-negotiation
                         muuntaja/format-negotiate-middleware
                           ;; encoding response body
                         muuntaja/format-response-middleware
                           ;; exception handling
                         exception/exception-middleware
                           ;; decoding request body
                         muuntaja/format-request-middleware
                           ;; coercing response bodys
                         coercion/coerce-response-middleware
                           ;; coercing request parameters
                         coercion/coerce-request-middleware
                           ;; multipart
                         multipart/multipart-middleware]}})
   (ring/routes
    (swagger-ui/create-swagger-ui-handler
     {:path "/"
      :config {:validatorUrl nil
               :operationsSorter "alpha"}})
    (ring/create-default-handler))))

(defn start []
  (ring-jetty/run-jetty #'app {:port 8000
                               :join? false}))

(defn -main
  "[name? email? signature?]"
  [& args]
  (let [r (zipmap [:name :email :signature] args)
        sig (if-not (nil? (r :signature)) (r :signature) (if-not (nil? (@state :signature)) (@state :signature) "generic-key"))
        _ (identify sig)
        email (if (not-empty (r :email)) (r :email) "admin@localhost")
        name (r :name)]
    ;; when we have a name, set local and cloud state accordingly
    (when-not (nil? name)
      (println "NAME " name)
      (create-user {:body-params {:name name
                                  :email email}})
      (update-cache))
    ;; otherwise, load state from cloud
    (when (nil? name)
      (println "SIGNATURE: " (@state :signature))
      (let [app-state (cache/get-edn "app-state")]
        (println "APP_STATE" app-state)
        (when app-state (reset! state app-state))))
    (start)))
