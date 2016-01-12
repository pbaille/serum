(ns dsbuild.client2
  (:require [dsbuild.core :as d]
            [datascript.core :as ds]
            [datascript.transit :as dt]
            [dsbuild.test]
            [serum.core :refer [rerender << scomp scomp? mount div] :refer-macros [afn sfn]]))

;; styles ---------------------------------------------------------------------

(def c1 :dodgerblue)
(def c2 :white)
(def d1 :5px)
(def fsize :20px)

(def button
  {:display          :inline-block
   :text-decoration  :none
   :color            c2
   :font-size        fsize
   :background-color "rgba(255,255,255,.2)"
   :padding          d1
   :margin           d1
   :border-radius    d1
   :cursor           :pointer})

(def button-right
  (merge button {:float :right}))

(def button-left
  (merge button {:float :left}))

(def entities-index-item
  {:display          :block
   :text-decoration  :none
   :color            c2
   :border-radius    d1
   :font-size        fsize
   :background-color "rgba(255,255,255,.2)"
   :padding          d1
   :margin           "8px 5px"
   :cursor           :pointer
   :border           "4px solid rgba(255,255,255,.5)"})

(def entity-index-item
  {:border-top "2px solid grey"
   :box-sizing :border-box
   :display    :inline-block})


;; -----------------------------------------------------------------------------

(defn mount* [c]
  (mount c (.getElementById js/document "content")))


(defn entity-index [kw]
  (let [n (name kw)]
    (scomp
      {:body
       (sfn
         {c :rum/react-component}
         (cons
           [:div [:a {:on-click (fn [_] (mount* [(d/get-comp kw :edit) {:args {:kw kw :id (d/new-instance kw)}}]))
                      :style    button}
                  (str "new " n)]]
           (for [id (d/ids-for kw)]
             [(d/get-comp kw :ref-view)
              {:args
               {:kw kw :id id}
               :style
               (merge entity-index-item
                      {(d/$kw :name) {:font-size :20px}})
               :bpipe
               (fn [b]
                 (conj (vec b)
                       [:div
                        [:a {:on-click (fn [_] (mount* [(d/get-comp kw :view) {:args {:kw kw :id id}}]))
                             :style    button}
                         "view"]
                        [:a {:on-click (fn [_] (mount* [(d/get-comp kw :edit) {:args {:kw kw :id id}}]))
                             :style    button}
                         "edit"]
                        [:a {:style
                             button
                             :on-click
                             (fn [_]
                               (d/delete-instance (int id))
                               (rerender c))}
                         "delete"]]))}])))})))

(def entities-index
  (scomp
    {:body (for [kw (keys @d/entities)]
             [:div.entity {:style    entities-index-item
                           :on-click (fn [_] (mount* (entity-index kw)))}
              (str (name kw) "s")])}))

(def layout
  (scomp
    {:body
     [[div
       {:style {:padding          :10px
                :background-color c1}}
       [:.group
        [:.button {:style button-left :on-click (fn [_] (mount* entities-index))} "index"]
        [:.button {:style button-right :on-click (fn [_])} "save"]
        [:.button {:style button-right :on-click (fn [_])} "load"]]
       [:#content]]]}))

(mount layout)
(mount* entities-index)



