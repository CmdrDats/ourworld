(ns ourworld.bonedrops
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
   [cljminecraft.files :as f]))

(defn entity-death [ev]
  (if (and (< (rand) 0.33)
           ((set (map ent/entitytypes [:cow :sheep :pig :horse])) (.getEntityType ev)))
    (.add (.getDrops ev) (i/item-stack :bone (inc (Math/round (rand 1)))))))

(defn events []
  [(ev/event "entity.entity-death" #'entity-death)])

(defn start [plugin]
  (ev/register-eventlist plugin (events)))

(defn stop [plugin])


