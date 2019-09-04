(ns plus-minus.components.theme
  (:require [plus-minus.cookies :as cookies]))

(defn dark-options [brightness contrast sepia grayscale]
  (str "{brightness:" brightness ",constrast:" contrast
       ",sepia:" sepia ",grayscale" grayscale "}"))

(defn dark-reader! [enable? & [options]]
  (cookies/set! :theme-dark enable?)
  (if enable?
    (.enable js/DarkReader (or options (dark-options 100 100 10 40)))
    (.disable js/DarkReader)))

(defn set-up []
  (let [dark? (cookies/get :theme-dark true)]
    (dark-reader! dark?)))
