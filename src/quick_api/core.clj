(ns quick-api.core
  (:require
   [ring.middleware.cors :refer [wrap-cors]]
   [ring.middleware.ssl :refer [wrap-forwarded-scheme]]
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
   [quick-api.cache :as cache]
   [card-game.deck.cards :refer [rule-card]]
   [card-game.fluxx :as fluxx])
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

;; (defn format-date [format-string]
;;   (let [date-form (if-not format-string "MM/dd/yyyy" format-string)]
;;     (.format (java.text.SimpleDateFormat. date-form) (new java.util.Date))))

(defn create-user
  [{{:keys [email name]} :body-params}]
  (let [id (str (java.util.UUID/randomUUID))
        _ (swap! state update-in [:users] conj {:name name :email email :id id})]
    {:status 200 :body {:user-id id}}))

(defn get-users [_] {:status 200 :body (@state :users)})

(defn get-user-by-id [{{:keys [id]} :path-params}]
  {:status 200 :body (apply merge (map (fn [u] (when (= id (u :id)) u)) (@state :users)))})

(defn create-page [{{:keys [page-data]} :body-params}]
  (if page-data (let [id (str (java.util.UUID/randomUUID))]
                  (swap! state update-in [:pages] conj {:id id :text page-data :ts (timestamp)})
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
        ok (= id ((old-page :body) :id))
        ts (timestamp)]
    (when ok
      (swap! state update-in [:pages] conj {:id id :text text :ts ts})
      (update-cache))
    (if ok
      {:status 200 :body {:id id :text text :ts ts}}
      {:status 404 :body {:id id :text "##error: page not found" :ts ts}})))

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

(defn get-json-game-state
  "return game-state in JSON"
  [id]
  (let [game-id (str id)
        result (cache/get-json (str "fluxx-" game-id))]
    result))

;; API handlers
(defn draw-cards [{{:keys [id number-of-cards]} :path-params}]
  (let [_ (fluxx/load-game id)
        body (fluxx/draw (Integer/parseInt number-of-cards))
        _ (fluxx/save-game id)] {:status 200 :body body}))

(defn shuffle-game [{{:keys [id]} :path-params}]
  (let [_ (fluxx/load-game id)
        _ (fluxx/shuffle-cards)
        _ (fluxx/save-game id)
        body (get-json-game-state id)]
    {:status 200 :body body}))

(defn start-new-game [{{:keys [id]} :path-params}]
  (let [_ (fluxx/load-game "RESET")
        _ (fluxx/shuffle-cards)
        _ (fluxx/save-game id)
        body (get-json-game-state id)]
    {:status 200 :body body}))

(defn player-list [{{:keys [id]} :path-params}]
  (let [_ (fluxx/load-game id)
        body (fluxx/player-list)]
    {:status 200 :body body}))

(defn add-player [{{:keys [name]} :body-params {:keys [id]} :path-params}]
  (let [_ (fluxx/load-game id)
        body (fluxx/join-player name)
        _ (fluxx/save-game id)]
    {:status 200 :body body}))

(defn remove-player [{{:keys [name]} :body-params {:keys [id]} :path-params}]
  (let [_ (fluxx/load-game id)
        _ (fluxx/remove-player name)
        body (fluxx/player-list)
        _ (fluxx/save-game id)]
    {:status 200 :body body}))

(defn play-card [{{:keys [card-name]} :body-params {:keys [id pid]} :path-params}]
  (let [_ (fluxx/load-game id)
        player-id (Integer/parseInt pid)
        player (last (filter #(= player-id (% :id)) (fluxx/player-list)))
        card (first (fluxx/cards-named card-name))
        card-type (card :type)
        _ (case card-type
            "keeper" (fluxx/add-keeper player card)
            "rule" (fluxx/new-rule card)
            "action" (fluxx/discard card)
            "goal" (fluxx/update-goal card))
        _ (fluxx/save-game id)]
    {:status 200 :body {:card card :player player}}))

(defn edit-game [{{:keys [id]} :path-params}]
  (let [_ (fluxx/load-game id)
        immutable_card (rule-card "basic rules" {:draw 1 :play 1})
        mutable_cards (@fluxx/game :deck)]
    {:status 200 :body {:total (count (@fluxx/game :deck))
                        :immutable immutable_card
                        :mutable mutable_cards}}))

(defn edit-card [{{:keys [type name detail]} :body-params {:keys [id card-id]} :path-params}]
  (let [_ (fluxx/load-game id)
        old_card (fluxx/card-with-id card-id)
        new_card {:id card-id :type type :name name :detail detail}
        _ (fluxx/replace-card card-id new_card)
        _ (fluxx/save-game id)]
    {:status 200 :body {:old old_card :new new_card}}))

(def api
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
     ["/game/:id/edit" {:get {:summary "list the cards"
                              :parameters {:path {:id string?}}
                              :handler edit-game}}]
     ["/game/:id/edit/:card-id" {:put {:summary "change the content of this card"
                                       :parameters {:path {:id string? :card-id string?} :body {:type string? :name string? :detail string?}}
                                       :handler edit-card}}]
     ["/game/:id/new-game" {:get {:summary "start a new game"
                                  :parameters {:path {:id string?}}
                                  :handler start-new-game}}]
     ["/game/:id/draw/:number-of-cards" {:get {:summary "take cards from the draw pile"
                                               :parameters {:path {:id string? :number-of-cards number?}}
                                               :handler draw-cards}}]
     ["/game/:id/shuffle" {:get {:summary "shuffle or reset the draw-pile"
                                 :parameters {:path {:id string?}}
                                 :handler shuffle-game}}]
     ["/game/:id/players" {:get {:summary "returns a list of players of game with id"
                                 :parameters {:path {:id string?}}
                                 :handler player-list}}]
     ["/game/:id/join" {:post {:summary "join a game"
                               :parameters {:path {:id string?} :body {:name string?}}
                               :handler add-player}}]
     ["/game/:id/leave" {:post {:summary "leave a game"
                                :parameters {:path {:id string?} :body {:name string?}}
                                :handler remove-player}}]
     ["/game/:id/player/:pid/play" {:post {:summary "play a card"
                                           :parameters {:path {:id string? :pid string?} :body {:card-name string?}}
                                           :handler play-card}}]
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
            :middleware [#(wrap-forwarded-scheme %)
                         #(wrap-cors %
                                     :access-control-allow-origin [#".*"]
                                     :access-control-allow-methods [:get :post :put :delete])
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

(defn configure-service
  "[name? email? signature?]"
  [& args]
  (let [r (zipmap [:name :email :signature] args)
        sig (if-not (nil? (r :signature)) (r :signature) (if-not (nil? (@state :signature)) (@state :signature) "generic-key"))
        _ (identify sig)
        _ (fluxx/save-game "RESET")
        email (if (not-empty (r :email)) (r :email) "admin@localhost")
        name (if  (empty? (r :name)) "admin" (r :name))]
    (println "starting API...")
    ;; create an account
    (create-user {:body-params {:name name
                                :email email}})
    (update-cache)))

(defn init []
  (configure-service))

(defn destroy []
  (println "stopping API..."))
