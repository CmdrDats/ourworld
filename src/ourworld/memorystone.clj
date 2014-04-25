(ns ourworld.memorystone
  (:require [cljminecraft.events :as ev]
            [clojure.string :as str])
  (:require [cljminecraft.player :as pl])
  (:require [cljminecraft.bukkit :as bk])
  (:require [cljminecraft.logging :as log]
            [cljminecraft.commands :as cmd]
            [cljminecraft.world :as w]
            [cljminecraft.items :as i])
  (:require [cljminecraft.files :as files])
  (:require [clojure.string :as str])
  (:import [org.bukkit ChatColor]))


;;DONE: Teleport onto sign, instead of facing block
;;DONE: Face away from sign on teleport
;;TODO: Slower teleporting with smoke effects
;;TODO: On switching to compass, describe currently selected destination
;;TODO: Link to closest possible candidate
;;TODO: Blue [Portal] colour if in different world.
;;DONE: Remove memorizing stones
;;DONE: Linked memorystone system - First line name, second line linkname
;;DONE: Teleport only when pointing at another memory stone
;;DECL: Auto-memorize created memory stones
;;DECL: Auto-memorize on teleporting from a memorystone
;;DONE: Use compass for teleport
;;DONE: Consume redstone on teleporting - depends on distance
;;DONE: Left/Right click with compass in air back/forward selection
;;DONE: Fixed cost between worlds - 32
;;DONE: Initial setup cost for portal - paid on first jump
;;DONE: Right click compass on portal, set Compass Target
;;DONE: Delete one side of portal, should break link
;;DONE: Teleport sheep to compass target (redstone cost based on current distance + constant)

(defonce plugin (atom nil))
(defonce memorystones (atom {}))

;; ======================= Utility functions ========================
(defn sanitize-name [name]
  (.replaceAll name "[^A-Za-z0-9:_/ \\()@#$%&!^*-]" ""))

(defn uuid []
  (.toString (java.util.UUID/randomUUID)))

(defn sign-state [block]
  (.getState (.getBlock (org.bukkit.Location. (.getWorld block) (.getX block) (.getY block) (.getZ block)))))

(defn valid-link? [{:keys [block linkblock]}]
  (and (instance? org.bukkit.block.Sign (sign-state block))
       (or (nil? linkblock)
           (instance? org.bukkit.block.Sign (sign-state linkblock)))))

(defn update-lines [b [l1 l2 l3 l4 :as lines]]
  (bk/ui-sync
   @plugin
   (fn []
     (.load (.getChunk b))
     (let [sign (sign-state b)]
       (println "Updating: " (class sign) " - " lines)
       (when (instance? org.bukkit.block.Sign sign)
         (doto sign
           (.setLine 0 l1)
           (.setLine 1 l2)
           (.setLine 2 l3)
           (.setLine 3 l4)
           (.update true)))))))

(defn teleport [ent block]
  (.load (.getChunk block))
  (let [location (.clone (.getLocation block))
        facing (.clone (.getLocation (w/facing-block block)))
        direction (.toVector (.subtract (.clone facing) location))]
    (.setDirection location direction)
    (.add location 0.5 0 0.5)
    (.setVelocity ent (org.bukkit.util.Vector. 0 0 0))
    (.teleport ent location)
    {:msg (str "Teleported " (.getName ent) " - " (.toString location))}))

(defn portal-cost [block linkblock]
  (if (and block linkblock)
    (if (.equals (.getWorld block) (.getWorld linkblock))
      (inc (Math/round (/ (.distanceSquared (.getLocation block) (.getLocation linkblock)) 200000)))
      32)
    0))

(defn has-enough? [player material amount]
  (.contains (.getInventory (pl/get-player player)) (get i/materials material) amount))

(defn use-if-enough [player material amount]
  (let [inventory (.getInventory (pl/get-player player))]
    (if (has-enough? player material amount)
      (do
        (bk/ui-sync
         @plugin
         #(reduce
           (fn [q [k i]]
             (let [a (.getAmount i)]
               (cond
                (<= q 0) q
                (>= q a)
                (do
                  (.setItem inventory k nil) (- q a))
                :else
                (do
                  (.setAmount i (- a q))
                  (.setItem inventory k i)
                  0))))
           amount
           (.all inventory (material i/materials))))
        true)
      false)))

(defn update-sign [block name linkname cost type]
  (let [state (sign-state block)]
    (cond
     (not (= type :portal))
     (update-lines
      block
      [(str ChatColor/GREEN "[MemoryStone]") name "" ""])
     (str/blank? linkname)
     (update-lines
      block
      [(str ChatColor/DARK_RED "[Portal]") name "" ""])
     :else
     (update-lines
      block
      [(str ChatColor/GREEN "[Portal]") name (str ChatColor/DARK_RED "[" cost "]" " ->") linkname]))))

(defn update-link [{:keys [block linkblock name linkname cost type] :as m}]
  (update-sign block name linkname cost type)
  (when linkblock
    (update-sign linkblock linkname name cost type)))

(defn update-all-links []
  (doseq [l (vals @memorystones)]
    (update-link l)))

;; ================== Persistence handling functions =================
(defn memstone-to-file
  [[uuid {:keys [block linkblock name uuid linkname type cost] :as memstone}]]
  (let [{:keys [x y z] :as location} (bean (.getLocation block))
        {lx :x ly :y lz :z :as linklocation} (if linkblock (bean (.getLocation linkblock)))]
    {:world-uuid (str (.. block getWorld getUID))
     :location [x y z]
     :linkworld-uuid (if linkblock (str (.. linkblock getWorld getUID)))
     :linklocation [lx ly lz]
     :name name
     :uuid uuid
     :linkname linkname
     :cost cost
     :type type}))

(defn memstone-from-file
  [{:keys [world-uuid location linkworld-uuid linklocation name uuid linkname type cost] :as memstone}]
  (let [world (bk/world-by-uuid world-uuid)
        [x y z] location
        block (.getBlockAt world x y z)
        linkworld (if linkworld-uuid (bk/world-by-uuid linkworld-uuid))
        [lx ly lz] linklocation
        linkblock (if lx (.getBlockAt linkworld lx ly lz))]
    [uuid {:block block :linkblock linkblock :name name
           :linkname linkname :uuid uuid :cost cost :type (keyword type)}]))

(defn write-memstones []
  (files/write-json-file @plugin
   "stones.json"
   {:stones (map memstone-to-file @memorystones)}))

(defn read-memstones []
  (let [stones (into {} (map memstone-from-file (:stones (files/read-json-file @plugin "stones.json"))))]
    (reset! memorystones stones)))



;; ============= Event Handling ============================
(defn create-portal [type name link ev]
  (let [linked (first (filter #(and (= (:type %) :portal)
                                    (not (:linkname %))
                                    (= (:name %) link)) (vals @memorystones)))
        linkblock (.getBlock ev)
        newlink (assoc linked :linkblock linkblock :linkname name
                       :cost (* 3 (portal-cost (:block linked) linkblock)))]
    (cond
     linked
     (do
       (swap! memorystones assoc (:uuid linked) newlink)
       (update-sign (:block newlink) (:name newlink) (:linkname newlink) (:cost newlink) (:type newlink))
       (update-sign linkblock name (:name newlink) (:cost newlink) (:type newlink))
       {:msg "Portal linked and ready."})
     (not (str/blank? link))
     (do
       (.setCancelled ev true)
       {:msg "Target destination not found."})
     :else
     (let [id (uuid)]
       (swap! memorystones assoc id {:block (.getBlock ev) :name name :uuid id :type :portal})
       (update-sign (.getBlock ev) name "" 0 :portal)
       (.setLine ev 0 (str ChatColor/RED "[Portal]"))
       (write-memstones)
       {:msg (str "You placed a Portal stub " ChatColor/GREEN name ChatColor/BLACK ", well done.")}))))

(defn sign-change [ev]
  (let [[type name link] (seq (.getLines ev))
        [name link] (map sanitize-name [name link])
        id (uuid)]
    (cond
     (= "[Portal]" type)
     (create-portal type name link ev)
     (not= "[MemoryStone]" type) nil
     (empty? name)
     {:msg "Please enter a name on the second line"}
     :else
     (do
       (swap! memorystones assoc id {:block (.getBlock ev) :name name :uuid id})
       (write-memstones)
       {:msg "You placed a Memory Stone. Well done."}))))

(defn memstone-for-block [block]
  (if block
    (->> @memorystones
         vals
         (filter #(or (and (:block %) (.equals block (:block %)))
                      (and (:linkblock %) (.equals block (:linkblock %)))))
         first)))

(defn sign-break [ev]
  (when-let [memstone (memstone-for-block (.getBlock ev))]
    (if (:linkblock memstone)
      (do
        (if (= (.getBlock ev) (:block memstone))
          (swap! memorystones assoc (:uuid memstone)
                 (-> memstone
                     (dissoc :linkblock :linkname :cost)
                     (assoc :block (:linkblock memstone) :name (:linkname memstone))))
          (swap! memorystones assoc (:uuid memstone)
                 (dissoc memstone :linkblock :linkname :cost)))
        (update-link (get @memorystones (:uuid memstone))))
      
      (swap! memorystones dissoc (:uuid memstone)))
    (write-memstones)
    {:msg "You broke a Portal! oh dear."}))

(def fired-interact (atom {}))

(defn player-interact [ev]
  (let [pname (.getName (pl/get-player ev))
        interact (get @fired-interact pname)
        block (.getClickedBlock ev)
        memstone (if-not interact (memstone-for-block block))]
    (swap! fired-interact assoc pname false)
    (if (and block (not interact)
             (= (.getItemType (i/get-material :compass)) (.getType (.getItemInHand (.getPlayer ev)))))
      (if (i/is-block? block :wall_sign :sign_post)
        (cond
         (not memstone) nil
         (not= (:type memstone) :portal) nil
         (not (:linkblock memstone)) {:msg "Portal not yet linked!"}
         (= (.getAction ev) (:right_click_block ev/actions))
         (do
           (.setCompassTarget (pl/get-player ev) (.getLocation block))
           {:msg "Setting target portal"})
         (not= (.getAction ev) (:left_click_block ev/actions)) nil
         (not (has-enough? ev :redstone (:cost memstone)))
         {:msg (str "You don't have enough RedStone for this! Need " (:cost memstone))}
         :else
         (do
           (use-if-enough ev :redstone (:cost memstone))
           (let [newcost (portal-cost (:block memstone) (:linkblock memstone))]
             (when-not (= newcost (:cost memstone))
               (swap! memorystones assoc-in [(:uuid memstone) :cost] newcost)
               (update-link (assoc memstone :cost newcost))))
           (if (.equals block (:block memstone))
             (teleport (pl/get-player ev) (:linkblock memstone))
             (teleport (pl/get-player ev) (:block memstone)))
           {:msg "Activating portal"}))))))

(defn player-interact-entity [ev]
  (if (= (.getType (.getItemInHand (.getPlayer ev))) (.getItemType (i/get-material :compass)))
    (let [player (pl/get-player ev)
          pname (.getName player)
          target (memstone-for-block (.getCompassTarget player))
          ent (.getRightClicked ev)
          cost (inc (portal-cost (.getBlock (.getLocation ent)) (.getBlock (.getCompassTarget player))))]
      (.setCancelled ev true)
      (swap! fired-interact assoc pname true)
      (cond
       (not (has-enough? ev :redstone cost))
       {:msg (str "You don't have enough RedStone for this! Need " cost)}
       (and (instance? org.bukkit.entity.Player ent)
            (not (= (.getType (.getItemInHand ent)) (.getItemType (i/get-material :feather)))))
       (do
         (pl/send-msg ent (str pname " is trying to teleport you, but you need to be holding a feather."))
         {:msg (str "Player needs to consent to teleportation by holding a feather")})
       :else
       (do
         (use-if-enough ev :redstone cost)
         (teleport ent (.getBlock (.getCompassTarget player))))))))




;; Command Handling







(defn events
  []
  [(ev/event "player.player-interact" #'player-interact)
   (ev/event "player.player-interact-entity" #'player-interact-entity)
   (ev/event "block.sign-change" #'sign-change)
   (ev/event "block.block-break" #'sign-break)])


;; Plugin lifecycle
(defn start
  [plugin-instance]
  (log/info "%s" "in start memorystone")
  (reset! plugin plugin-instance)
  (ev/register-eventlist @plugin (events))
  (read-memstones))

(defn stop
  [plugin]
  (log/info "%s" "in stop memorystone")
  (write-memstones))
