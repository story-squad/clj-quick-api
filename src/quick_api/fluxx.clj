(ns quick-api.fluxx
  (:require [quick-api.cache :as cache])
  (:require [clojure.string]))

;; define the basic rules of fluxx
(defn _basic-rules [] {:draw 1 :play 1 :discard {} :limits {}})

;; create a game state
(def game (atom {:rules [] :draw-pile [] :discard-pile [] :goal [] :players (sorted-map) :deck []}))

;; add the basic rules to the game state
(swap! game update-in [:rules] conj (_basic-rules))

;; define a card base
(defn _card
  ([]
   (_card nil))
  ([type]
   (_card type nil))
  ([type name]
   (_card type name nil))
  ([type name detail]
   {:type type
    :name name
    :detail detail}))

;; ---- Cards ----

(defn add-card
  "add a card to the deck"
  [any-card]
  (when any-card (swap! game update-in [:deck] conj any-card)))

;; ---- types ----
(defn rule-card
  ([] (_basic-rules))
  ([name]
   (rule-card name []))
  ([name desc]
   (_card "rule" name desc)))

(defn rule-card? [card]
  (= (card :type) "rule"))

(defn goal-card
  ([]
   (goal-card nil))
  ([name]
   (goal-card name [nil]))
  ([name conditions]
   (_card "goal" name conditions)))

(defn goal-card? [card]
  (= (card :type) "goal"))

(defn action-card
  ([] (action-card nil))
  ([name] (action-card name nil))
  ([name desc]
   (_card "action" name desc)))

(defn action-card? [card]
  (= (card :type) "action"))

(defn keeper-card
  ([] (keeper-card nil))
  ([name]
   (keeper-card name name))
  ([name desc]
   (_card "keeper" name desc)))

(defn keeper-card? [card]
  (= (card :type) "keeper"))

;; ---- search ----
(defn cards-of-type
  [card-type]
  (filter #(= (% :type) card-type) (@game :deck)))

(defn cards-named
  [card-name]
  (filter #(clojure.string/starts-with? (% :name) card-name) (@game :deck)))

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

;; ---- Keepers ----
((fn []
   (add-card (keeper-card "Peace" "☮"))
   (add-card (keeper-card "Television" "📺"))
   (add-card (keeper-card "The Sun" "🌞"))
   (add-card (keeper-card "Love" "💘"))
   (add-card (keeper-card "Sleep" "💤"))
   (add-card (keeper-card "The Toaster" "toaster"))
   (add-card (keeper-card "Bread" "🍞"))
   (add-card (keeper-card "Music" "🎶"))
   (add-card (keeper-card "Milk" "🥛"))
   (add-card (keeper-card "The Party" "🎉"))
   (add-card (keeper-card "The Brain" "🧠"))
   (add-card (keeper-card "The Eye" "👁"))
   (add-card (keeper-card "Money" "💰"))
   (add-card (keeper-card "Time" "🕟"))
   (add-card (keeper-card "Dreams" "😴"))
   (add-card (keeper-card "Chocolate" "🍫"))
   (add-card (keeper-card "The Moon" "🌝"))
   (add-card (keeper-card "Cookies" "🍪"))
   (add-card (keeper-card "The Rocket" "🚀"))))

;; ---- Goals ----
((fn []
   (add-card (goal-card "Hearts & Minds" ["Love" "The Brain"]))
   (add-card (goal-card "Dreamland" ["Sleep" "Dreams"]))
   (add-card (goal-card "Baked Goods" ["Bread" "Cookies"]))
   (add-card (goal-card "Rocket to the Moon" ["The Rocket" "The Moon"]))
   (add-card (goal-card "Turn it Up!" ["Music" "The Party"]))
   (add-card (goal-card "5 Keepers" ["Keepers" 5]))
   (add-card (goal-card "Day Dreams" ["The Sun" "Dreams"]))
   (add-card (goal-card "Rocket Science" ["The Rocket" "The Brain"]))
   (add-card (goal-card "Can't Buy Me Love" ["Money" "Love"]))
   (add-card (goal-card "Chocolate Cookies" ["Chocolate" "Cookies"]))
   (add-card (goal-card "Bread & Chocolate" ["Bread" "Chocolate"]))
   (add-card (goal-card "Chocolate Milk" ["Chocolate" "Milk"]))
   (add-card (goal-card "Time is Money" ["Time" "Money"]))
   (add-card (goal-card "Night & Day" ["The Sun" "The Moon"]))
   (add-card (goal-card "Winning the Lottery" ["Dreams" "Money"]))
   (add-card (goal-card "World Peace" ["Dreams" "Peace"]))
   (add-card (goal-card "Party Time" ["The Party" "Time"]))
   (add-card (goal-card "Milk & Cookies" ["Milk" "Cookies"]))
   (add-card (goal-card "Hippyism" ["Peace" "Love"]))
   (add-card (goal-card "Squishy Chocolate" ["Chocolate" "The Sun"]))
   (add-card (goal-card "The Minds Eye" ["The Brain" "The Eye"]))
   (add-card (goal-card "The Appliances" ["The Toaster" "Television"]))
   (add-card (goal-card "Lullaby" ["Sleep" "Music"]))
   (add-card (goal-card "Bed Time" ["Sleep" "Time"]))
   (add-card (goal-card "Great Theme Song" ["Music" "Television"]))
   (add-card (goal-card "The Brain (No TV)" ["The Brain" "NO Television"]))
   (add-card (goal-card "Toast" ["Bread" "The Toaster"]))
   (add-card (goal-card "The Eye of the Beholder" ["The Eye" "Love"]))
   (add-card (goal-card "10 Cards in hand" ["Cards" 10]))
   (add-card (goal-card "Party Snacks" ["The Party" "ANY food-keeper"]))))

;; ---- Actions ----

((fn []
   (add-card (action-card "Take Another Turn" ["PLAY AGAIN" "MAX 2"]))
   (add-card (action-card "Today's Special!" ["SET HAND ASIDE" "DRAW 3" "BIRTHDAY? PLAY 3" "HOLIDAY/ANNIVERSARY? PLAY 2" "OTHERWISE PLAY 1"]))
   (add-card (action-card "Share The Wealth" ["SHUFFLE AND SELF-FIRST DEAL" "IN-PLAY KEEPERS"]))
   (add-card (action-card "Empty the Trash" ["SHUFFLE THEN APPEND" "DISCARD-PILE TO DRAW-PILE"]))
   (add-card (action-card "Use What You Take" ["TAKE A CARD AT RANOM FROM" "ANOTHER PLAYERS HAND"]))
   (add-card (action-card "Steal a Keeper" ["CHOOSE 1 FROM" "IN-PLAY KEEPERS"]))
   (add-card (action-card "Discard & Draw" ["DISCARD HAND-COUNT" "DRAW HAND-COUNT-MANY CARDS"]))
   (add-card (action-card "Trade Hands" ["TRADE HANDS WITH" "ANOTHER PLAYER"]))
   (add-card (action-card "Trash a Keeper" ["DISCARD FROM" "ANOTHER PLAYERS IN-PLAY KEEPERS"]))
   (add-card (action-card "Draw 2, Play 2 of Them" ["SET HAND ASIDE" "DRAW 3" "PLAY 2"]))
   (add-card (action-card "Exchange Keepers" ["EXCHANGE KEEPERS WITH" "ANOTHER PLAYERS IN-PLAY KEEPERS"]))
   (add-card (action-card "Zap a Card" ["CHOOSE 1 FROM" "IN-PLAY CARDS"]))
   (add-card (action-card "Rotate Hands" ["ROTATE" "CLOCKWISE OR COUNTER-CLOCKWISE"]))
   (add-card (action-card "Rock-Paper-Scissors Showdown" ["ROCK-PAPER-SCISSORS ANOTHER PLAYER" "WINNER-TAKES-LOSER-HAND"]))
   (add-card (action-card "Jackpot!" ["DRAW 3 FROM" "DRAW-PILE"]))
   (add-card (action-card "Trash a New Rule" ["DISCARD FROM" "IN-PLAY RULE-CARDS"]))
   (add-card (action-card "Everybody Gets 1" ["SET HAND ASIDE" "DISTRIBUTE NUMBER-OF-PLAYERS CARDS" "FROM DRAW-PILE"]))
   (add-card (action-card "Let's Simplify" ["DISCARD <=50% FROM" "IN-PLAY RULE-CARDS"]))
   (add-card (action-card "Let's Do That Again" ["CHOOSE ANY ACTION OR RULE FROM" "DISCARD-PILE"]))
   (add-card (action-card "No Limits!" ["DISCARD" "IN-PLAY LIMITS"]))
   (add-card (action-card "Rules Reset" ["RESET RULES TO" "BASIC"]))
   (add-card (action-card "Random Tax" ["DRAW 1 FROM" "EVERY PLAYERS HAND"]))
   (add-card (action-card "Draw 2 and Use 'Em" ["DRAW AND PLAY 2 FROM" "DRAW-PILE"]))))

;; ---- Rules ----

((fn []
   (add-card (rule-card "Keeper Limit 2" {:limits {:keeper 2}}))
   (add-card (rule-card "Hand Limit 1" {:limits {:hand 1}}))
   (add-card (rule-card "Poor Bonus" {:fewest-keepers "DRAW +1"}))
   (add-card (rule-card "Play 4" {:play 4}))
   (add-card (rule-card "Play All" {:play "ALL"}))
   (add-card (rule-card "No-hand Bonus" {:empty-handed "DRAW 3, THEN PLAY"}))
   (add-card (rule-card "Draw 5" {:draw 5}))
   (add-card (rule-card "Get On With It!" {:get-on-with-it ["OPTIONAL" "BEFORE FINAL PLAY" "DISCARD HAND AND DRAW 3"]}))
   (add-card (rule-card "Draw 3" {:draw 2}))
   (add-card (rule-card "Party Bonus" {:when-party-in-play {:draw +1 :play +1}}))
   (add-card (rule-card "Inflation" {:numerical-plus-1 "+1 IN-PLAY NUMBERALS"}))
   (add-card (rule-card "Keeper Limit 3" {:limits {:keeper 3}}))
   (add-card (rule-card "Draw 3" {:draw 3}))
   (add-card (rule-card "Hand Limit 2" {:limits {:hand 2}}))
   (add-card (rule-card "Play 2" {:play 2}))
   (add-card (rule-card "Keeper Limit 4" {:limits {:keeper 4}}))
   (add-card (rule-card "First Play Random" {:randomize-first-play true}))
   (add-card (rule-card "Mystery Play (free action)" {:mystery-play ["OPTIONAL" "ONCE DURING TURN" "PLAY TOP CARD FROM DRAW PILE"]}))
   (add-card (rule-card "Hand Limit 0" {:limits {:hand 0}}))
   (add-card (rule-card "Recycling (free action)" {:recycling ["OPTIONAL" "ONCE DURING TURN" "DISCARD 1 OF YOUR KEEPERS" "DRAW 3"]}))
   (add-card (rule-card "Play all but 1" {:play "(- ALL 1)"}))
   (add-card (rule-card "Draw 4" {:draw 4}))
   (add-card (rule-card "Play 3" {:play 3}))
   (add-card (rule-card "Goal Mill (free action)" {:goal-mill ["OPTIONAL" "ONCE DURING TURN" "DISCARD ANY OF YOUR GOAL CARDS" "DRAW REPLACEMENTS"]}))
   (add-card (rule-card "Double Agenda" {:alternate-goal []}))
   (add-card (rule-card "Rich Bonus" {:rich-bonus ["OPTIONAL" "PLAYER WITH MOST KEEPERS MAY PLAY 1 EXTRA CARD"]}))
   (add-card (rule-card "Swap Plays for Draws" {:swap-plays ["OPTIONAL" "DRAW INSTEAD OF PLAY N-MANY CARDS"]}))))


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
   (let [cached-game (cache/get-edn (str "fluxx-" id))]
     (when cached-game
       (reset-vals! game cached-game)))))

(defn reset-game [] (load-game "RESET"))

;; ---- SAVE a restorable copy of the game state ----

(save-game "RESET")
