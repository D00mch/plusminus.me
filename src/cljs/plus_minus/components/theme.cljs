(ns plus-minus.components.theme
  (:require [plus-minus.cookies :as cookies]))

(defn dark-options [brightness contrast sepia grayscale]
  (str "{brightness:" brightness ",constrast:" contrast
       ",sepia:" sepia ",grayscale" grayscale "}"))

(defn is-light? [] (cookies/get :theme-light))

(defn dark-reader! [light? & [options]]
  (cookies/set! :theme-light light?)
  (if light?
    (.disable js/DarkReader)
    (.enable js/DarkReader (or options (dark-options 100 100 10 40)))))

(defn set-up []
  (dark-reader! (is-light?)))
