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

(defonce moon-world (atom nil))

(defonce gens (atom nil))

(defn noisegen [world]
  (->
   (swap! gens
          (fn [a] (update-in a [(.getUID world)] #(if % % (org.bukkit.util.noise.SimplexNoiseGenerator. world)))))
   (get (.getUID world))))

(defn calc-height [world x y variance]
  (let [gen (noisegen world)]
    (org.bukkit.util.noise.NoiseGenerator/floor (* variance (.noise gen x y)))))

(defn construct-generator []
  (proxy [org.bukkit.generator.ChunkGenerator] []
    (generate [world random cx cz]
      (let [material (.getId (:sponge i/materials))
            result (byte-array 32768)]
        (doseq [x (range 16) z (range 16)]
          (let [height (+ 60 (calc-height world (+ cx (* x 0.0625)) (+ cz (* z 0.0625)) 2))]
            (doseq [y (range height)]
              (aset-byte result (+ y (* 128 (+ (* x 16) z)))  material))))
        result))
    (getDefaultPopulators [world]
      '())
    (getFixedSpawnLocation [world random]
      (log/info "FixedSpawn")
      (let [x (- (.nextInt random 200) 100)
            z (- (.nextInt random 200) 100)
            y (.getHighestBlockYAt world x z)]
        (org.bukkit.Location. world x y z)))))

(defn generator [plugin world id]
  (construct-generator))

(defn check-moon [m]
  (if m m
      (.createWorld (bk/server) "BukkitMoon" org.bukkit.World$Environment/NORMAL (construct-generator))))

(defn moon []
  (swap! moon-world check-moon))

(defn send-to-moon
  [sender]
  (.teleport sender (.getSpawnLocation (moon))))

(defn start
  [plugin-instance]
  (log/info "%s" "in start ourworld")
  (reset! plugin plugin-instance)
  (memstone/start plugin-instance)
  (recipes/start plugin-instance)
  (bonedrops/start plugin-instance)
  (logger/start plugin-instance)
  (cmd/register-command plugin-instance "moon" #'send-to-moon))

(defn stop
  [plugin]
  (log/info "%s" "in stop ourworld")
  (memstone/stop plugin)
  (recipes/stop plugin)
  (bonedrops/stop plugin)
  (logger/stop plugin))

