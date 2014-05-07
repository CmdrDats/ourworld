(ns ourworld.recipes
  (:require [cljminecraft.recipes :as r]))

(def material-map
  {\W :wool \S :string \I :iron_ingot \N :netherrack \L :leather \D :dirt \s :seeds
   \C :cobblestone \b :snow_ball \w :water_bucket})

(defn register-recipes []
  (r/register-recipes
   (r/recipe material-map :string "W" 2)
   (r/recipe material-map :web ["SSS" "SSS" "SSS"] 1)
   (r/recipe material-map :chainmail_boots ["I I" "S S" "I I"] 1)
   (r/recipe material-map :chainmail_leggings ["ISI" "S S" "I I"] 1)
   (r/recipe material-map :chainmail_chestplate ["I I" "SIS" "ISI"] 1)
   (r/recipe material-map :chainmail_helmet ["SIS" "I I"])
   (r/recipe material-map :nether_brick ["NN " "NN "] 1)
   (r/recipe material-map :saddle [" L " "ILI" "SLS"])
   (r/recipe material-map :grass "sD" 1)
   (r/recipe material-map :mossy_cobblestone ["CsC" "sCs" "CsC"] 5)
   (r/recipe material-map :ice "bw" 3)
   (r/recipe {\B :brown_mushroom \R :red_mushroom \D :dirt} :mycel "BRDD" 2)
   (r/recipe {\S :sandstone} :sand "S" 4)
   (r/recipe {\C :clay} :clay_ball "C" 4)
   (r/recipe {\S :sand \s :string} :sponge ["sSs" "SsS" "sSs"] 1)
   ))

(defn start [plugin]
  (register-recipes))

(defn stop [plugin])
