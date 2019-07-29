(ns plus-minus.routes.multiplayer.game
  (:require [plus-minus.routes.multiplayer.topics :as topics]
            [plus-minus.multiplayer.contract :as contract
             :refer [->Reply ->Message]]
            [plus-minus.routes.multiplayer.room :as room]
            [plus-minus.routes.multiplayer.matcher :as matcher]
            [beicon.core :as rx]
            [clojure.spec.alpha :as spec]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.logging :as log])
  (:import io.reactivex.internal.observers.LambdaObserver
           [io.reactivex.disposables CompositeDisposable Disposable]))

;; communication is set up with multiplayer/topics.clj

(def ^:private id->msgs (ref {}))
(def ^:private timers   (ref {})) ;; TODO impl timers

(defn- game->reply-obs! [{id1 :player1, id2 :player2 :as new-game}]
  (let [msg-subj  (-> (rx/subject) rx/to-serialized)
        reply-obs (->> msg-subj
                       (rx/observe-on :io)
                       (room/reply new-game)
                       rx/share)
        _         (log/info "about to add msg-subj to ids")
        _         (dosync (alter id->msgs assoc id1 msg-subj, id2 msg-subj))
        game-played  (->> reply-obs
                          (rx/filter #(= (:reply-type %) :end))
                          (rx/buffer 2)
                          (rx/flat-map (fn [_]
                                         (dosync (alter id->msgs dissoc id1 id2))
                                         (rx/end! msg-subj)
                                         (rx/empty))))]
    (rx/merge reply-obs game-played)
    #_(rx/create
     (fn [sink]
       (let [push-replies (rx/subscribe reply-obs
                                        #(do #_(log/info "reply" %)
                                             (sink %)))
             clear        (fn []
                            (dosync (alter id->msgs dissoc id1 id2))
                            (sink rx/end)
                            (rx/end! msg-subj))
             game-played  (->> reply-obs
                               (rx/filter #(= (:reply-type %) :end))
                               (rx/buffer 2))
             await-finish (rx/subscribe game-played (fn [_] (clear)))]
         (log/info "push-replies and await-finished subscribed")
         (fn [] ;; unsubscribe all
           (log/info "about to unsubscribe push-replies")
           (clear)
           (rx/cancel! push-replies)
           (rx/cancel! await-finish)))))))

(defn subscribe-message-processing []
  (let [new-games        (->> (topics/consume :msg)
                              (rx/filter #(= (:msg-type %) :new))
                              matcher/generate-games
                              (rx/flat-map game->reply-obs!))
        new-game-disp    (rx/subscribe new-games
                                       (fn [reply]
                                         (log/info "about to publish reply" reply)
                                         (topics/publish :reply reply))
                                       (fn [e] (log/error "new-game-disp" e)))

        game-message     (->> (topics/consume :msg)
                              (rx/filter #(not= (:msg-type %) :new)))
        message-disp     (rx/subscribe game-message
                                       (fn [{:keys [id] :as msg}]
                                         #_(log/info "about to push" msg)
                                         (let [subj (get @id->msgs id)]
                                           (rx/push! subj msg)
                                           (log/info "pushed!")))
                                       (fn [e] (log/error "message-disp" e)))]
    (doto (CompositeDisposable.) (.add new-game-disp) (.add message-disp))))





;; (def stream
;;   (->> (rx/create (fn [sink]
;;                 (sink 1)          ;; next with `1` as value
;;                 (sink rx/end)     ;;end the stream
;;                 (fn []
;;                   (println "unsubscribed with " 1))))
;;       (rx/publish)))
;;
;; (def disp (rx/subscribe stream
;;                           #(prn "next" %)
;;                           #(prn "error" %)))
;;
;; (rx/connect! stream)
