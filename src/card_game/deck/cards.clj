(ns card-game.deck.cards)

(defn _basic-rules [] {:draw 1 :play 1 :limits {}})

;; define a card base

(defn _card
  ([]
   (_card nil))
  ([type]
   (_card type nil))
  ([type name]
   (_card type name nil))
  ([type name detail]
   {:id (str (java.util.UUID/randomUUID))
    :type type
    :name name
    :detail detail}))

;; ---- Cards ----


;; ---- types ----
(defn rule-card
  ([] (_basic-rules))
  ([name]
   (rule-card name []))
  ([name desc]
   (_card "rule" name desc)))

;; (defn rule-card? [card]
;;   (= (card :type) "rule"))

(defn goal-card
  ([]
   (goal-card nil))
  ([name]
   (goal-card name [nil]))
  ([name conditions]
   (_card "goal" name conditions)))

;; (defn goal-card? [card]
;;   (= (card :type) "goal"))

(defn action-card
  ([] (action-card nil))
  ([name] (action-card name nil))
  ([name desc]
   (_card "action" name desc)))

;; (defn action-card? [card]
;;   (= (card :type) "action"))

(defn keeper-card
  ([] (keeper-card nil))
  ([name]
   (keeper-card name name))
  ([name desc]
   (_card "keeper" name desc)))

;; (defn keeper-card? [card]
;;   (= (card :type) "keeper"))
