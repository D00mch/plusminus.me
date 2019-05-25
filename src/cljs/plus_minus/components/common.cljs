(ns plus-minus.components.common
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plus-minus.app-db :as app-db]))

(defn modal [& {:keys [header body footer style]}]
  [:div.modal.is-active
   [:div.modal-background
    {:on-click #(app-db/remove! :modal)}]
   [:div.modal-card
    style
    [:header.modal-card-head header]
    [:section.modal-card-body body]
    [:footer.modal-card-foot footer]]])

(defn input [& {:keys [type hint on-save on-stop id fields styles]
                :or {type "text"}}]
  (let [stop #(when on-stop (on-stop))
        save #(-> (get @fields id) str str/trim on-save)]
    (fn []
      (prn "styles changed to " @styles)
      [:div.field
       [:div.control
        {:class (get-in @styles [id :class])}
        [:input.input.is-primary
         {:type        type
          :placeholder hint
          :value       (id @fields)
          :auto-focus  true
          :on-blur     save
          :on-change   #(swap! fields assoc id (-> % .-target .-value))
          :on-key-down #(case (.-which %)
                          13 (save)
                          27 (stop)
                          nil)}]
        [:p.is-medium {:style {:color "red"}}
         (get-in @styles [:errors id])]]])))
