(ns plus-minus.game.statistics
  (:require [reagent.core :as r]
            [plus-minus.app-db :as db]
            [ajax.core :as ajax]
            [plus-minus.multiplayer.contract :as contract]))

;; online statistics
;; states:
;; (db/get-in [:online-stats :error])
;; (db/get-in [:online-stats :data])

(def ^{:private true, :const true} tname "Name")
(def ^{:private true, :const true} win "Win")
(def ^{:private true, :const true} lose "Lose")
(def ^{:private true, :const true} draw "Draw")
(def ^{:private true, :const true} infl "$")

(defn- sum-stats [stats]
  (map #(update % :statistics contract/stats-summed) stats))

(defn- load-stats! []
  (ajax/GET "api/online/statistics"
            {:handler (fn [r]
                        (db/assoc-in! [:online-stats :error] nil)
                        (db/assoc-in! [:online-stats :data] (-> r :data sum-stats)))
             :error-handler (fn [r]
                              (db/assoc-in! [:online-stats :data] nil)
                              (db/assoc-in! [:online-stats :error] (:status-text r)))}))

(defn init-stats! []
  (load-stats!))

(defn- stats-table []
  [:div.container.table-container
   (when-let [data (db/get-in [:online-stats :data])]
     [:table.table.is-striped.is-narrow
      [:thead
       [:tr
        [:th tname]
        [:th win ]
        [:th lose]
        [:th draw]
        [:th infl]]]
      [:tfoot
       [:tr
        [:th tname]
        [:th win ]
        [:th lose]
        [:th draw]
        [:th infl]]]
      [:tbody
       (for [{id :id {:keys [win lose draw influence]} :statistics} data]
         [:tr {:class (when (= id (db/get :identity)) "is-selected")
               :key id}
          [:td id]
          [:td win]
          [:td lose]
          [:td draw]
          [:td influence]])]])])

(defn stats-component []
  ;;(load-stats! stats error)
  [:div.container>div.column
   [stats-table]

   ;; loading
   (when-not (db/get :online-stats)
     [:progress.progress.is-small.is-dark.board.top-mar
      {:max 100}])

   ;; error
   (when-let [err (db/get-in [:online-stats :error])]
     [:div.notification.is-danger.board
      [:button.delete {:on-click
                       (fn []
                         (db/remove! :online-stats)
                         (js/setTimeout #(init-stats!) 1000))}]
      (str "error occured while loading statistics: " err)])])
