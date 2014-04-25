(ns ourworld.recipes
  (:require [cljminecraft.recipes :as r]))

(def material-map
  {\W :wool})

(defn register-recipes []
  (r/register-recipes
   (r/recipe material-map :string "W" 12)))

(defn start [plugin]
  (register-recipes))

(defn stop [plugin])
