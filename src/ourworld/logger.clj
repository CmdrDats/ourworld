(ns ourworld.logger
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

(defn vect [block]
  (if-not (nil? block)
    [(.getX block) (.getY block) (.getZ block)]))

(defn nrange [x]
  (range (dec x) (+ x 2)))

(defn neighbours [block]
  (for [x (nrange 0) y (nrange 0) z (nrange 0)
        :when (not= [x y z] [0 0 0])]
    (.getRelative block x y z)))

(defn grounding? [block]
  (not
   ((set (map i/materials [:air :log :log_2 :leaves :leaves_2 :vine :cocoa :grass :long_grass :double_plant])) (.getType block))))

(defn should-break? [checked startlog]
  (if (= (.getType startlog) (:log i/materials))
    (let [log (:log i/materials)
          initial (neighbours startlog)]
      (loop [current (first initial)
             left (rest initial)
             checked (conj checked (vect startlog))]
        (let [c (vect current)]
          (cond
           (nil? current) true
           (checked c) (recur (first left) (rest left) checked)
           (grounding? current) false
           (= (.getType current) log)
           (let [left (concat left (neighbours current))]
             (recur (first left) (rest left) (conj checked c)))
           :else
           (recur (first left) (rest left) (conj checked c))))))))

(defn find-log-neighbours [block]
  (filter #(= (.getType %) (:log i/materials)) (neighbours block)))

(defn damage-item [stack]
  (cond
   (nil? stack) nil
   (> (.getMaxDurability (.getType stack)) 0)
   (.setDurability stack (inc (.getDurability stack)))
   :else
   nil))

(defn fell [player checked block]
  (let [log (:log i/materials)
        initial (concat [block] (neighbours block))]
    (loop [current (first initial)
           left (rest initial)
           checked checked]
      (let [c (vect current)]
        (cond
         (nil? current) true
         (checked c) (recur (first left) (rest left) checked)
         ((set (map i/materials [:leaves :leaves_2])) (.getType current))
         (do
           (.breakNaturally current (.getItemInHand player))
           (damage-item (.getItemInHand player))
           (recur (first left) (rest left) (conj checked c)))
         (not= (.getType current) log) (recur (first left) (rest left) (conj checked c))
         :else
         (let [left (concat left (neighbours current))]
           (.breakNaturally current (.getItemInHand player))
           (damage-item (.getItemInHand player))
           (recur (first left) (rest left) (conj checked c)))
         )))))

(defn block-break [ev]
  #_(if (= (.. ev getBlock getType) (:log i/materials))
    (let [checked #{(vect (.getBlock ev))}
          candidates (find-log-neighbours (.getBlock ev))
          breakable (filter (partial should-break? checked) candidates)]
      (doseq [log breakable]
        (fell (.getPlayer ev) checked log)))))

(defn start [plugin]
  (ev/register-eventlist
   plugin
   [(ev/event "block.block-break" #'block-break)]))

(defn stop [plugin]
  )
