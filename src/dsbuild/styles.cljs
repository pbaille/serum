(ns dsbuild.styles)

(def entities-index-item
  {:display :block
   :text-decoration  :none
   :color            :white
   :border-radius    :5px
   :font-size        :22px
   :background-color :#19B5FE
   :padding          :5px
   :margin           :10px
   :cursor           :pointer})

(def entity-index-item
  {:border-top "2px solid grey"
   :box-sizing :border-box
   :display    :inline-block})

(defn entity-index-button [bg]
  {:display          :inline-block
   :text-decoration  :none
   :color            :white
   :background-color bg
   :padding          :5px
   :margin           "0 0 10px 10px"
   :border-radius    :3px
   :cursor           :pointer})
