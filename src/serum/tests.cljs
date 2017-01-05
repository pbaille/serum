(ns serum.tests
  (:require [serum.core2 :refer [div swrap mount scomp rerender ref] :refer-macros [afn sfn]]
            [schema.core :as s]))

(defn arrow [dir]
  (let [dirkw (condp = (name dir) "left" "right" "right" "left")
        border-dir (keyword (str "border-" dirkw))
        b20 #(str "20px solid " (name %))]
    (scomp
      {:style
       {:width         0
        :height        0
        :margin        :20px
        :border-top    (b20 :transparent)
        :border-bottom (b20 :transparent)
        border-dir     (b20 :#E0E0E0)
        :hover         {border-dir (b20 :#A0A0A0)}}})))

(def frame-style
  {:display         :flex
   :flex-flow       "row wrap"
   :justify-content :center
   :align-items     :center
   :margin          :auto
   :height          :400px
   :width           :700px})

(def slideshow
  (scomp
    {:schema
     {:sel (ref s/Int) :urls [s/Str]}
     :attrs
     {:on-key-up (fn [e] (println e))}
     :body
     (afn {sel :sel urls :urls}
          [[:div {:style frame-style}
            [(arrow :left) {:on-click (fn [_] (reset! sel (mod (dec @sel) (count urls))))}]
            [(swrap :img)
             {:src   (get urls @sel)
              :style {:display :inline-block
                      :max-height  :500px}}]
            [(arrow :right) {:on-click (fn [_] (reset! sel (mod (inc @sel) (count urls))))}]]
           [:div {:style (merge frame-style {:height :70px :width :70%})}
            (for [[i u] (map-indexed vector urls)]
              [:img
               {:src
                u
                :on-click
                (fn [_] (reset! sel i))
                :style
                {:max-height :100%
                 :border     (when (= i @sel) "3px solid tomato")
                 :hover      {:border "3px solid lightblue"}}}])]])}))

(mount [slideshow
        {:args
         {:sel  (atom 0)
          :urls ["http://portra.wpshower.com/wp-content/uploads/2014/03/000112.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/936full-angelina-jolie.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/936full-christian-bale.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/Brad-Pitt-Martin-Schoeller-photoshoot-01.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/celeb-photos-19.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/martin-schoeller-emma-watson-portrait-up-close-and-personal-1126x1280.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/936full-zach-galifianakis.jpg"
                 "http://portra.wpshower.com/wp-content/uploads/2014/03/martin-schoeller-barack-obama-portrait-up-close-and-personal.jpg"
                 "http://www.forum.ofac.ch/files/live/sites/forumofac/files/contributed/intervenants/femme.jpg"]}}])

