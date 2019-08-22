(ns plus-minus.game.statistics
  (:require [reagent.core :as r]
            [plus-minus.app-db :as db]
            [ajax.core :as ajax]
            [plus-minus.multiplayer.contract :as contract]))

;; online statistics
;; states:
;; (db/get-in [:online-stats :error])
;; (db/get-in [:online-stats :data])

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
        [:th [:abbr {:title "best players on the top"} "Name"]]
        [:th "Win"]
        [:th "Lose"]
        [:th "Draw"]]]
      [:tfoot
       [:tr
        [:th "name"]
        [:th "win"]
        [:th "lose"]
        [:th "draw"]]]
      [:tbody
       (for [{id :id {:keys [win lose draw]} :statistics} data]
         [:tr {:class (when (= id (db/get :identity)) "is-selected")}
          [:td id]
          [:td win]
          [:td lose]
          [:td draw]])]])])

(defn stats-component []
  ;;(load-stats! stats error)
  [:div
   [stats-table]

   ;; loading
   (when-not (db/get :online-stats)
     [:progress.progress.is-small.is-dark.board.top-mar.left-mar
      {:max 100}])

   ;; error
   (when-let [err (db/get-in [:online-stats :error])]
     [:div.notification.is-danger.board.left-mar
      [:button.delete {:on-click
                       (fn []
                         (db/remove! :online-stats)
                         (js/setTimeout #(init-stats!) 1000))}]
      (str "error occured while loading statistics: " err)])])



