(ns plus-minus.common.json
  (:require [cognitect.transit :as t]
            [clojure.java.io :as io])
  (:import java.io.ByteArrayOutputStream
           [java.util.concurrent Executors Future TimeUnit]))

(defn- ->stream [input]
  (cond (string? input) (io/input-stream (.getBytes input))
        :else input))

(defn read-json [input]
  (with-open [ins (->stream input)]
    (-> ins (t/reader :json) t/read)))

(defn ->json [data]
  (let [out (ByteArrayOutputStream.)
        w   (t/writer out :json)
        _   (t/write w data)
        ret (.toString out)]
    (.reset out)
    ret))
