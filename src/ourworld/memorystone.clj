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


;;TODO: Linked memorystone system - First line name, second line linkname
;;TODO: Teleport only when pointing at another memory stone
;;TODO: Auto-memorize created memory stones
;;TODO: Auto-memorize on teleporting from a memorystone
;;TODO: Use compass for teleport
;;TODO: Consume feathers on teleporting
;;TODO: On switching to compass, describe currently selected destination
;;DONE: Left/Right click with compass in air back/forward selection
;;TODO: Delete one side of portal, should break other side as well.



(defonce plugin (atom nil))
(defonce memorystones (atom {}))
(defonce membackup (atom #{}))
(defonce memorizedstones (atom {}))

(defn sanitize-name [name]
  (.replaceAll name "[^A-Za-z0-9:_/ \\()@#$%&!^*-]" ""))

(defn uuid []
  (.toString (java.util.UUID/randomUUID)))

;; Persistence handling
(defn memstone-to-file [[uuid {:keys [block linkblock name uuid link type] :as memstone}]]
  (let [{:keys [x y z] :as location} (bean (.getLocation block))
        {:keys [lx ly lz] :as linklocation} (if linkblock (bean (.getLocation linkblock)))]
    {:world-uuid (str (.. block getWorld getUID))
     :location [x y z]
     :linkworld-uuid (if linkblock (str (.. linkblock getWorld getUID)))
     :linklocation [lx ly lz]
     :name name
     :uuid uuid
     :link link
     :type type}))

(defn memstone-from-file [{:keys [world-uuid location linkworld-uuid linklocation name uuid link type] :as memstone}]
  (let [world (bk/world-by-uuid world-uuid)
        [x y z] location
        block (.getBlockAt world x y z)
        linkworld (if linkworld-uuid (bk/world-by-uuid linkworld-uuid))
        [lx ly lz] linklocation
        linkblock (if linklocation (.getBlockAt linkworld lx ly lz))]
    [uuid {:block block :linkblock linkblock :name name :link link :uuid uuid :type type}]))

(defn write-memstones []
  (files/write-json-file @plugin
   "stones.json"
   {:stones (map memstone-to-file @memorystones)}))

(defn read-memstones []
  (let [{:keys [stones]} (files/read-json-file @plugin "stones.json")]
    (reset! memorystones (into {} (map memstone-from-file stones)))))

(defn write-memory [player-name]
  (let [memory (get @memorizedstones player-name)]
    (files/write-json-file
     @plugin
     (str "memory-" player-name ".json")
     {:memory memory})))

(defn read-memory [player-name]
  (let [{:keys [memory]} (files/read-json-file @plugin (str "memory-" player-name ".json"))]
    (swap! memorizedstones update-in [player-name] (comp set concat) memory)))

;; Event Handling
(defn memorize-stone! [player {:keys [name] :as memstone}]
  (swap! memorizedstones update-in [player] (comp set conj) name)
  (write-memory player))

(defn unmemorize-stone! [player {:keys [name] :as memstone}]
  (swap! memorizedstones update-in [player] (comp set disj) name)
  (write-memory player))

(defn update-sign [b line message]
  (doto (.getState (.getBlock (org.bukkit.Location. (.getWorld b) (.getX b) (.getY b) (.getZ b))))
    (.setLine line message)
    (.update true)))

(defn teleport [ent block]
  (do
    (.load (.getChunk block))
    (.setVelocity ent (org.bukkit.util.Vector. 0 0 0))
    (.teleport ent (.getLocation (w/facing-block block)))
    {:msg (str "Teleported " (.getName ent))}))

(defn create-portal [type name link ev]
  (let [linked (first (filter #(and (= (:type %) :portal)
                                    (not (:linkname %))
                                    (= (:name %) link)) (vals @memorystones)))]
    (cond
     linked
     (do
       (swap! memorystones update-in [(:uuid linked)] #(assoc % :linkblock (.getBlock ev) :linkname name))
       (update-sign (:block linked) 0 (str ChatColor/GREEN "[Portal]"))
       (update-sign (:block linked) 2 (str ChatColor/GREEN "->" ChatColor/BLACK name))
       (.setLine ev 0 (str ChatColor/GREEN "[Portal]"))
       (.setLine ev 2 (str ChatColor/GREEN "->" ChatColor/BLACK link))
       {:msg "Portal linked and ready."})
     (not (str/blank? link))
     (do
       (.setCancelled ev true)
       {:msg "Target destination not found."})
     :else
     (let [id (uuid)]
       (swap! memorystones assoc id {:block (.getBlock ev) :name name :uuid id :type :portal})
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
     (empty? name) (do (pl/send-msg ev "Please enter a name on the second line"))
     :else
     (do
       (swap! memorystones assoc id {:block (.getBlock ev) :name name :uuid id})
       (write-memstones)
       {:msg "You placed a Memory Stone. Well done."}))))

(defn memstone-for-block [block]
  (first (filter #(or (.equals block (:block %)) (.equals block (:linkblock %))) (vals @memorystones))))

(defn sign-break [ev]
  (when-let [memstone (memstone-for-block (.getBlock ev))]
    (swap! memorystones dissoc (:uuid memstone))
    (doseq [[player memorized] @memorizedstones]
      (if (contains? memorized (:name memstone))
        (unmemorize-stone! player memstone)))
    (write-memstones)
    {:msg "You broke a Memory Stone! oh dear."}))

(defn player-interact [ev]
  (if (and
       (= (.getAction ev) (get ev/actions :left_click_block))
       (i/is-block? (.getClickedBlock ev) :wall_sign :sign_post))
    (if-let [memstone (memstone-for-block (.getClickedBlock ev))]
      (cond
       (= (:type memstone) :portal)
       (do
         (cond
          (not= (.getItemType (i/get-material :compass)) (.getType (.getItemInHand (.getPlayer ev))))
          nil ;; Don't handle this unless we have a compass in hand
          (not (:linkblock memstone))
          {:msg "Portal not yet linked!"}
          :else
          (do
            (if (.equals (.getClickedBlock ev) (:block memstone))
              (teleport (pl/get-player ev) (:linkblock memstone))
              (teleport (pl/get-player ev) (:block memstone)))
            {:msg "Activating portal"})))
       :else
       (do
         (memorize-stone! (.getName (pl/get-player ev)) memstone)
         {:msg (format "Memorized %s" (:name memstone))})))))

(defn player-login [ev]
  (read-memory (.getName (pl/get-player ev))))



;; Command Handling
(defn get-memorized-stones [named]
  (let [stones (get @memorizedstones (.getName named) #{})]
    (filter #(contains? stones (:name %)) (vals @memorystones))))

(defmethod cmd/convert-type :memorystone [sender type arg]
  (log/info "Converting %s" (get @memorizedstones (.getName sender) #{}))
  (first (filter #(= (.toLowerCase (:name %)) (.toLowerCase arg)) (get-memorized-stones sender))))

(defmethod cmd/param-type-tabcomplete :memorystone [sender type arg]
  (let [lower (.toLowerCase arg)]
    (filter #(.startsWith % lower) (map #(.toLowerCase (:name %)) (get-memorized-stones sender)))))

(defn go-command [sender memorystone]
  (when memorystone
    (log/info "Teleporting %s to %s " sender memorystone)
    (.teleport sender (.getLocation (w/facing-block (:block memorystone))))
    {:msg "Teleported"}))




(def selected-stone (atom {}))

(defn teleport-selected [ev]
  (let [stone (get-in @selected-stone [(.getName (pl/get-player ev)) :selected])
        stone (first (filter #(= (:name %) stone) (vals @memorystones)))]
    (if stone
      (teleport (pl/get-player ev) (:block stone))
      {:msg "Please select a teleport location first"})))

(defn rotate-selection [ev]
  (let [pname (.getName (pl/get-player ev))
        idx (get-in @selected-stone [pname :index] 0)
        stones (get @memorizedstones pname {})
        stone (get (vec (sort stones)) (mod idx (count stones)))]
    (if (empty? stones)
      {:msg "You don't have any memory stones memorized!"}
      (do
        (swap! selected-stone assoc pname {:index (inc idx) :selected stone})
        {:msg (str "Rotating selection: " stone)}))))

(def fired-interact (atom {}))
(defn feather-event-handler [ev]
  (let [pname (.getName (pl/get-player ev))
        interact (get @fired-interact (.getName (pl/get-player ev)))]
    (swap! fired-interact assoc pname false)
    (if (and (= (.getMaterial ev) (.getItemType (i/get-material :feather)))
             (not interact))
      (if
          (or (= (.getAction ev) (org.bukkit.event.block.Action/LEFT_CLICK_AIR))
              (= (.getAction ev) (org.bukkit.event.block.Action/LEFT_CLICK_BLOCK)))
        (teleport-selected ev)
        (rotate-selection ev)))))

(defn feather-interact-handler [ev]
  (if (and (= (.getType (.getItemInHand (.getPlayer ev))) (.getItemType (i/get-material :feather))))
    (let [pname (.getName (pl/get-player ev))
          stone (get-in @selected-stone [pname :selected])
          stone (first (filter #(= (:name %) stone) (vals @memorystones)))]
      (.setCancelled ev true)
      (swap! fired-interact assoc pname true)
      (when stone
        (teleport (.getRightClicked ev) (:block stone))))))


(defn events
  []
  [(ev/event "player.player-interact" #'feather-event-handler)
   (ev/event "player.player-interact-entity" #'feather-interact-handler)
   (ev/event "player.player-interact" #'player-interact)
   (ev/event "block.sign-change" #'sign-change)
   (ev/event "block.block-break" #'sign-break)])

#_[

(ev/event "player.player-interact" #'player-interact)
(ev/event "player.player-login" #'player-login)]

;; Plugin lifecycle
(defn start
  [plugin-instance]
  (log/info "%s" "in start memorystone")
  (reset! plugin plugin-instance)
  (ev/register-eventlist @plugin (events))
  (cmd/register-command @plugin "go" #'go-command :memorystone)
  (read-memstones))

(defn stop
  [plugin]
  (log/info "%s" "in stop memorystone")
  (write-memstones))
