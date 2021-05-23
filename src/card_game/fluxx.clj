(ns card-game.fluxx
  (:require [quick-api.cache :as cache]
            [card-game.deck.classic-fluxx :as classic-fluxx])
  (:require [clojure.string]))

;; define the basic rules of fluxx
(defn _basic-rules [] {:draw 1 :play 1 :limits {}})

;; create a game state
(def game (atom {:rules [] :draw-pile [] :discard-pile [] :goal [] :players (sorted-map) :deck []}))

(defn add-card
  "add a card to the deck"
  [any-card]
  (when any-card (swap! game update-in [:deck] conj any-card)))

;; add the basic rules to the game state
(swap! game update-in [:rules] conj (_basic-rules))

;; ---- search ----
;; (defn cards-of-type
;;   [card-type]
;;   (filter #(= (% :type) card-type) (@game :deck)))

(defn cards-named
  [card-name]
  (filter #(clojure.string/starts-with? (% :name) card-name) (@game :deck)))

(defn card-with-id [card-id]
  (last (filter #(= (% :id) card-id) (@game :deck))))

;; ---- edit ----

(defn replace-card [card-id replacement]
  (let [cards (vec (conj (remove #(= (% :id) card-id) (@game :deck)) replacement))
        _ (swap-vals! game #(assoc % :deck cards))
        _ (swap-vals! game #(assoc % :draw-pile (shuffle cards)))]
    (@game :deck)))

;; ---- shuffle ----
(defn shuffle-cards
  []
  (swap!
   game assoc :draw-pile
   (shuffle
    (or
     (when
      (> (count (@game :draw-pile)) 1)
       (@game :draw-pile))
     (when
      (> (count (@game :discard-pile)) 1)
       (@game :discard-pile))
     (@game :deck)))))

;; ---- In-Game ----

(defn draw
  "Draw n-many cards from the draw-pile"
  ([] (draw 1))
  ([n]
   (let [n-cards (take n (@game :draw-pile))
         _ (swap!
            game assoc :draw-pile
            (drop n (@game :draw-pile)))]
     n-cards)))

(defn discard
  "discard a card"
  [card]
  (let [_ (swap! game update-in [:discard-pile] conj card)]
    (count (@game :discard-pile))))

(defn update-goal 
  [card]
  (let [_ (swap! game update-in [:goal] conj card)]
    (last (@game :goal))))

(defn new-rule
  "play a rule-card; append card to :rules"
  ([]
   (println "requires rule-card"))
  ([rule]
   (let [_ (swap! game update-in [:rules] conj rule)]
     (@game :rules))))

(defn player-list
  "a (list) of {players}"
  []
  (map #((@game :players) %) (keys (@game :players))))

(defn find-player
  "find player by name"
  [name]
  (filter #(= (% :name) name) (player-list)))

(defn first-player? [] (= 0 (count (player-list))))

(defn next-available-player-id [] (+ 1 ((last (player-list)) :id)))

(defn join-player
  "join a player to the game"
  [name]
  (let [unique? (= 0 (count (find-player name)))
        id (if (first-player?) 1 (next-available-player-id))]
    (when unique?
      (swap! game update-in [:players] assoc id {:name name :keepers [] :id id}))))

(defn remove-player
  "remove player from the game"
  [name]
  (when (> (count (find-player name)) 0)
    (let [new-player-list (remove #(= (% :name) name) (player-list))
          player-ids (map #(% :id) new-player-list)
          new-player-map (zipmap player-ids new-player-list)]
      (swap-vals! game assoc :players new-player-map))))

(defn add-keeper
  "add cards to keeper pile"
  [player card]
  (let [name (player :name)
        old-list (remove #(= (% :name) name) (player-list))
        new-player (update player :keepers conj card)
        new-list (conj old-list new-player)
        player-ids (map #(% :id) new-list)
        new-player-map (zipmap player-ids new-list)]
    (swap-vals! game assoc :players new-player-map)))

;; ---- Cache ----
(defn save-game
  "save game state to cache"
  ([]
   (cache/set-object (str "fluxx") @game))
  ([id]
   (cache/set-object (str "fluxx-" id) @game)))

(defn load-game
  "load game from cache"
  ([id]
   (let [cached-game (cache/get-edn (str "fluxx-" id))
         body (when cached-game
                (reset-vals! game cached-game))]
     (or body []))))

(defn reset-game [] 
  (let [{:keys [players]} @game]
    (load-game "RESET")
    (swap-vals! game assoc :players players)))

;; LOAD the classic cards
(classic-fluxx/load-cards add-card)
;; ---- SAVE a restorable copy of the game state ----
(save-game "RESET")
;; ---- LOAD the restorable copy of game state ----
(reset-game)
