(ns dsbuild.leaves
  (:require [dsbuild.core :as dab]
            [datascript.core :as d]
            [serum.core :refer [scomp] :refer-macros [sfn afn]]
            [dommy.core :refer-macros [sel1]]
            [schema.core :as s]
            [rum.core :as rum]))

;---------------------------------------------------------------------------------------------
; Leaves views
;---------------------------------------------------------------------------------------------

(do
  (defn get-kv [{{pid :id pkw :kw} :parent kw :kw}]
    (let [p (d/entity @dab/db pid)
          attr-key (keyword (name pkw) (name kw))]
      [attr-key (attr-key p)]))

  (def change-afn
    (afn
      {{pid :id} :parent :as s}
      {:on-change (fn [e] (d/transact! dab/db [[:db/add pid (first (get-kv s)) (.. e -target -value)]]))}))

  (def entity-schema
    {:kw s/Keyword :id s/Int})

  (def leaf-schema
    {:kw s/Keyword :parent entity-schema}))

(def name-view
  (scomp
    {:label  :name-view
     :body   (afn args [(second (get-kv args))])
     :style  {:padding    :10px
              :margin     :5px}
     :schema leaf-schema}))

;---------------------------------------------------------------------------------------------

(def name-edit
  (scomp
    {:label   :name-edit
     :wrapper :input
     :attrs   [change-afn (afn args {:value (second (get-kv args))})]
     :style   {:box-sizing       :border-box
               :border           :none
               :background-color :white
               :padding          :10px
               :color            :grey
               :outline          :none
               :font-size        :15px
               :width            :100%
               :height           :30px
               :margin           "10px 0"
               :focus            {:background-color :lightskyblue
                                  :color            :white}
               :hover            {:color :lightskyblue}}
     :schema  leaf-schema}))

;---------------------------------------------------------------------------------------------

(def text-view
  (scomp
    {:label  :text-view
     :body   (afn args [(second (get-kv args))])
     :style  {:background-color :#FAFAFA
              :padding          :10px
              :margin           :5px}
     :schema leaf-schema}))

;---------------------------------------------------------------------------------------------

(def text-overview
  (assoc text-view
    :body
    (afn args [(str (apply str (take 200 (second (get-kv args)))) "...")])))

;---------------------------------------------------------------------------------------------

(defn- auto-resize [e]
  (let [s (.-style e)]
    (aset s "height" "5px")
    (aset s "height" (str (aget e "scrollHeight") "px"))))

(def text-edit
  (scomp
    {:label   :text-edit
     :wrapper :textarea
     :attrs   [change-afn
               {:spell-check false
                :on-key-up   (fn [e] (auto-resize (.-target e)))}
               (afn args {:value (second (get-kv args))})]
     :style   {:box-sizing       :border-box
               :background-color :white
               :color            :grey
               :outline          :none
               :width            :100%
               :overflow         :none
               :font-size        :15px
               :padding          :20px
               :border           :none
               :resize           :none
               :focus            {:background-color :lightskyblue
                                  :color            :white}
               :hover            {:color :lightskyblue}}
     :mixins  [{:did-mount
               (fn [state]
                 (auto-resize (.getDOMNode (:rum/react-component state)))
                 state)}]
     :schema  leaf-schema}))

;---------------------------------------------------------------------------------------------

(do
  (defn file-reader [onload]
    (let [fr (js/FileReader.)]
      (aset fr "onload" onload)
      fr))

  (defn write-img-data [input pid c]
    (let [fs (.-files input)
          ff (when fs (first (array-seq fs)))]
      (if ff
        (let [r (file-reader
                  (fn [e]
                    (d/transact! dab/db [[:db/add pid :image/data (.. e -target -result)]])
                    (rum/request-render c)))]
          (.readAsDataURL r ff))))))

(def default-image
  "data:image/jpeg;base64,/9j/4QAYRXhpZgAASUkqAAgAAAAAAAAAAAAAAP/sABFEdWNreQABAAQAAAA8AAD/4QMpaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wLwA8P3hwYWNrZXQgYmVnaW49Iu+7vyIgaWQ9Ilc1TTBNcENlaGlIenJlU3pOVGN6a2M5ZCI/PiA8eDp4bXBtZXRhIHhtbG5zOng9ImFkb2JlOm5zOm1ldGEvIiB4OnhtcHRrPSJBZG9iZSBYTVAgQ29yZSA1LjAtYzA2MCA2MS4xMzQ3NzcsIDIwMTAvMDIvMTItMTc6MzI6MDAgICAgICAgICI+IDxyZGY6UkRGIHhtbG5zOnJkZj0iaHR0cDovL3d3dy53My5vcmcvMTk5OS8wMi8yMi1yZGYtc3ludGF4LW5zIyI+IDxyZGY6RGVzY3JpcHRpb24gcmRmOmFib3V0PSIiIHhtbG5zOnhtcD0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wLyIgeG1sbnM6eG1wTU09Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9tbS8iIHhtbG5zOnN0UmVmPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvc1R5cGUvUmVzb3VyY2VSZWYjIiB4bXA6Q3JlYXRvclRvb2w9IkFkb2JlIFBob3Rvc2hvcCBDUzUgV2luZG93cyIgeG1wTU06SW5zdGFuY2VJRD0ieG1wLmlpZDo2Q0FBMzAzNjkyQTcxMUUxQjBFRkVBMEI3QkI2MjVEOSIgeG1wTU06RG9jdW1lbnRJRD0ieG1wLmRpZDo2Q0FBMzAzNzkyQTcxMUUxQjBFRkVBMEI3QkI2MjVEOSI+IDx4bXBNTTpEZXJpdmVkRnJvbSBzdFJlZjppbnN0YW5jZUlEPSJ4bXAuaWlkOjZDQUEzMDM0OTJBNzExRTFCMEVGRUEwQjdCQjYyNUQ5IiBzdFJlZjpkb2N1bWVudElEPSJ4bXAuZGlkOjZDQUEzMDM1OTJBNzExRTFCMEVGRUEwQjdCQjYyNUQ5Ii8+IDwvcmRmOkRlc2NyaXB0aW9uPiA8L3JkZjpSREY+IDwveDp4bXBtZXRhPiA8P3hwYWNrZXQgZW5kPSJyIj8+/+4ADkFkb2JlAGTAAAAAAf/bAIQABgQEBAUEBgUFBgkGBQYJCwgGBggL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")

(def data-view
  (scomp
    {:label  :data-view
     :body   (afn args
                  [[:img {:style {:max-height :100%}
                          :src   (or (second (get-kv args)) default-image)}]])
     :style  {:height  :200px
              :display :inline-block}
     :schema leaf-schema}))

;---------------------------------------------------------------------------------------------

(def data-edit
  (scomp
    {:label  :data-edit
     :body   (sfn {c                            :rum/react-component
                   {{pid :id} :parent :as args} :args}
                  (let [click-link-class (str (gensym "file-input"))]
                    [[:img {:style    {:max-height :100%
                                       :margin     :auto}
                            :src      (or (second (get-kv args)) default-image)
                            :on-click (fn [_] (.click (sel1 (str "." click-link-class))))}]
                     [:input {:class     click-link-class
                              :style     {:display "none"}
                              :type      "file"
                              :on-change (fn [e] (write-img-data (.-target e) pid c))}]]))
     :style  {:background-color :#FAFAFA
              :display          :inline-block
              :height           :200px
              :padding          :10px
              :text-align       :middle
              :hover            {:background-color :lightskyblue}}
     :schema leaf-schema}))

;---------------------------------------------------------------------------------------------

(defn many-leaves-view [kw]
  (scomp
    {:label  (keyword (str "many-" (name kw) "-views"))
     :body   (afn args (let [cv (dab/get-comp :view kw)] (map cv (get-kv args))))
     :schema leaf-schema}))

(do
  (def tony-img-data "data:image/jpeg;base64,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")

  (defn lorem-gen [x]
    (let [lorem "At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat"]
      (apply str x " text " (interleave (map clojure.string/lower-case (shuffle (clojure.string/split lorem #"\s+")))
                                        (repeat " "))))))

;; -------------------------------------------------------------------------------------------

(dab/add-leaves!
  {:name  :name
   :comps {:view name-view :edit name-edit :many-view (many-leaves-view :name)}
   :gen   (fn [& [pref]] (str (gensym (or pref "name"))))}

  {:name  :text
   :comps {:view text-view :edit text-edit :many-view (many-leaves-view :text) :overview text-overview}
   :gen   lorem-gen}

  {:name  :data
   :comps {:view data-view :edit data-edit :many-view (many-leaves-view :data)}
   :gen   (constantly tony-img-data)})

