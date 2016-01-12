(ns dsbuild.client
  (:require [dsbuild.core :as d]
            [datascript.core :as ds]
            [datascript.transit :as dt]
            [dsbuild.test]
            [serum.core :refer [<< scomp scomp? mount rerender] :refer-macros [sfn]]
            [bidi.bidi :refer [match-route path-for]]
            [bidi.router :refer [start-router!]]
            [rum.core :as rum]
            [dsbuild.styles :as s]))

(enable-console-print!)

;; for db to be serialized ...
(extend-protocol IComparable
  PersistentHashSet
  (-compare [x y]
    (compare (vec (sort x)) (vec (sort y)))))

(do
  (defn file-reader [onload]
    (let [fr (js/FileReader.)]
      (aset fr "onload" onload)
      fr))

  (defn load-db [input]
    (let [fs (.-files input)
          ff (when fs (first (array-seq fs)))]
      (if ff
        (let [r (file-reader
                  (fn [e]
                    (reset! d/db (dt/read-transit-str (.. e -target -result)))))]
          (.readAsText r ff))))))

(def button
  {:display          :inline-block
   :text-decoration  :none
   :color            :white
   :font-size        :20px
   :background-color :dodgerblue
   :padding          :5px
   :margin-left      :10px
   :border-radius    :5px
   :cursor           :pointer})

(def button-right
  (merge button {:float :right}))

(def button-left
  (merge button {:float :left}))

(defn index [_]
  (scomp
    {:body
     [[:.group {:style {:padding "10px 10px 0 0"}}
       [:a {:style    button-right
            :href     (str "data:text/plain;charset=utf-8," (js/encodeURIComponent (dt/write-transit-str @d/db)))
            :download "clinique.transit"}
        "download"]
       [:a {:style    button-right
            :on-click (fn [_] (.click (.getElementById js/document "loaddb")))}
        "load"]
       [:input#loaddb
        {:style     {:display :none}
         :type      "file"
         :on-change (fn [e] (load-db (.-target e)))}]]
      (for [n (map name (keys @d/entities))]
        [:a.entity {:style s/entities-index-item
                    :href  (str "#/" n "/index")} (str n "s")])]}))

(defn entity-index [{n :ekw}]
  (let [kw (keyword n)]
    (scomp
      {:body
       (sfn
         {c :rum/react-component}
         (cons
           [:div [:a {:href  (str "#/" n "/new")
                      :style (s/entity-index-button :limegreen)}
                  (str "new " n)]]
           (for [id (d/ids-for kw)]
             [(d/get-comp kw :ref-view)
              {:args
               {:kw kw :id id}
               :style
               (merge s/entity-index-item
                      {(d/$kw :name) {:font-size :20px}})
               :bpipe
               (fn [b]
                 (conj (vec b)
                       [:div
                        [:a {:href (str "#/" n "/" id) :style (s/entity-index-button :lightskyblue)} "view"]
                        [:a {:href (str "#/" n "/" id "/edit") :style (s/entity-index-button :lightskyblue)} "edit"]
                        [:a {:style
                             (s/entity-index-button :lightcoral)
                             :on-click
                             (fn [_]
                               (d/delete-instance (int id))
                               (rerender c))}
                         "delete"]]))}])))})))


(defn- ec-helper [vtype {n :ekw id :id}]
  (let [kw (keyword n)]
    (<< (d/get-comp kw vtype) {:args {:kw kw :id (int id)}})))

(defn entity-view [x] (ec-helper :view x))
(defn entity-edit [x] (ec-helper :edit x))

(defn entity-new [{n :ekw}]
  (entity-edit {:ekw n :id (d/new-instance (keyword n))}))

(def routes ["/" {"index"                index
                  [:ekw "/index"]        entity-index
                  [:ekw "/new"]          entity-new
                  [:ekw "/" :id]         entity-view
                  [:ekw "/" :id "/edit"] entity-edit}])

(start-router! routes
               {:on-navigate
                (fn [{:keys [handler route-params]}]
                  (let [r (handler route-params)]
                    (if (scomp? r)
                      (mount r))))})

(comment
  (println (get-in @d/db [:schema :image/tags]))
  (:schema @d/db)
  (d/ids-for :tag)
  (ds/transact! d/db [[:db.fn/retractEntity 2]])
  (:image/tags (ds/entity @d/db (rand-nth (d/ids-for :image))))
  (ds/transact! d/db [{:db/id 1111 :image/name "yop" :image/tags #{2 3 4}}])
  (:image/tags (ds/entity @d/db 1111))
  (ds/q '[:find ?e ?t :where [?e :image/tags ?t] [(contains? ?t {:db/id 2})]] @d/db))


(comment
  (do
    (def schema
      {:refs {:db/cardinality :db.cardinality/many
              :db/valueType   :db.type/ref}})

    (def db (ds/create-conn {}))

    (ds/transact! db [[:db/add 2 :name "two"]
                      [:db/add 3 :name "three"]
                      {:refs #{2 3} :name "one"}])

    (swap! db assoc :schema schema)

    (defn rem-ref [db id attr rem]
      (ds/transact! db [[:db.fn/retractAttribute id attr]
                        {:db/id id attr (filter (partial not= rem) (map :db/id (attr (ds/entity @db id))))}]))

    (rem-ref db 1 :refs 3)

    (println (into {} (ds/entity @db 4)))
    ))