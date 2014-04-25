(ns ourworld.core
  (:require
   [cljminecraft.bukkit :as bk]
   [cljminecraft.blocks :as blocks]
   [cljminecraft.events :as ev]
   [cljminecraft.entity :as ent]
   [cljminecraft.player :as plr]
   [cljminecraft.util :as util]
   [cljminecraft.logging :as log]
   [cljminecraft.config :as cfg]
   [cljminecraft.commands :as cmd]
   [cljminecraft.recipes :as r]
   [cljminecraft.items :as i]
   [cljminecraft.files :as f]

   [ourworld.memorystone :as memstone]
   [ourworld.recipes :as recipes]
   [ourworld.bonedrops :as bonedrops]
   [ourworld.logger :as logger]))

;;TODO: Lockable doors
;;TODO: Graves
;;TODO: Limited creatures (no zombies)
;;DONE: ourworld.logger - Chop tree down, break the whole tree (and leaves, if possible)
;;DONE: Fix startup loading problem
;;DONE: ourworld.bonedrops - Bone drops from cows and pigs.
;;DONE: ourworld.recipes - Wool -> String recipe
;;DONE: ourworld.memorystone - Portals with redstone cost.

(defonce plugin (atom nil))

(defn start
  [plugin-instance]
  (log/info "%s" "in start ourworld")
  (reset! plugin plugin-instance)
  (memstone/start plugin-instance)
  (recipes/start plugin-instance)
  (bonedrops/start plugin-instance)
  (logger/start plugin-instance))

(defn stop
  [plugin]
  (log/info "%s" "in stop ourworld")
  (memstone/stop plugin)
  (recipes/stop plugin)
  (bonedrops/stop plugin)
  (logger/stop plugin))

