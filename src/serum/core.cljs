(ns serum.core
  (:require-macros [serum.core :refer [selector afn sfn]])
  (:require [rum.core :as rum]
            [sablono.core :refer-macros [html]]
            [schema.core :as s]))

(enable-console-print!)

; helpers -------------------------------------------------------------

(defn wrap-fn [x]
  (if (fn? x) x (constantly x)))

(defn ensure-vec [x]
  (cond (vector? x) x (sequential? x) (vec x) :else [x]))

(defn t
  ([e] (:type (meta e)))
  ([sym e] (vary-meta e assoc :type sym)))

(defn t? [sym e] (= sym (t e)))

(defn merge-mode [x]
  (if (satisfies? IMeta x)
    (or (:merge-mode (meta x)) :set)
    :set))

(defn ->merge-mode
  ([x] #(->merge-mode x %))
  ([x y]
   (if-let [mm (or (and (keyword? x) x) (merge-mode x))]
     (vary-meta y assoc :merge-mode mm)
     x)))

(defn inject-state [state xs]
  (map
    #(if (t? :sfn %)
       (->merge-mode % (% state))
       %)
    xs))

(defn assoc-kv [m [k v]]
  (assoc m k v))

(defn swap-if [m [k f]]
  (if-let [olv (k m)] (assoc m k (f olv)) m))

(defn join-kv [m [k v]]
  (let [oldv (k m)]
    (cond
      (not oldv) (assoc m k v)
      (fn? v) (update-in m [k] juxt v)
      (sequential? oldv) (update-in m [k] conj v)
      :else (assoc m k [oldv v]))))

(defn merger [xs]
  (loop [defaults {} attrs {} injections {} [x & nxt :as xs] xs]
    (cond
      (not (seq xs)) ;done case
      [(merge defaults attrs) injections]
      (t? :selector (first x)) ;injection
      (recur defaults attrs (conj injections x) nxt)
      :else
      (condp = (merge-mode x)
        :set (recur defaults (assoc-kv attrs x) injections nxt)
        :default (recur (assoc-kv defaults x) attrs injections nxt)
        :swap (recur defaults (swap-if attrs x) injections nxt)
        :join (recur defaults (join-kv attrs x) injections nxt)))))

(defn kv-seq [xs]
  (reduce
    (fn [acc el]
      (if (or (sequential? el) (map? el))
        (apply conj acc (map (partial ->merge-mode el) el))
        (conj acc el)))
    [] xs))

(defn build [x]
  (if-let [builder (:builder (meta x))]
    (builder x)
    x))

(defn build-body [body] (map build body))

(defn camel-case [k]
  (if k
    (let [[first-word & words] (clojure.string/split (name k) #"-")]
      (if (empty? words)
        (name k)
        (-> (map clojure.string/capitalize words)
            (conj first-word)
            clojure.string/join)))))

(defn format-style [m]
  (into {} (map (fn [[k v]] [(camel-case k) (when v (name v))]) m)))

(defn split-styles
  "split pseudo styles, styles and style injections"
  [kvs]
  (let [x (reduce
            (fn [acc [k v]]
              (if (#{:hover :active :focus} k)
                (update-in acc [:pseudo-styles k] conj v)
                (update-in acc [:styles] conj [k v])))
            {:styles [] :pseudo-styles {:hover [] :active [] :focus []}}
            kvs)
        [styles sis] (merger (:styles x))
        pss (into {} (map (fn [[k v]] [k (first (merger (kv-seq v)))]) (:pseudo-styles x)))
        ks (set (mapcat (fn [[k v]] (keys v)) pss))
        none-styles (merge (apply hash-map (interleave ks (repeat nil))) (select-keys styles ks))]
    {:style styles
     :sis sis
     :pseudo-styles (assoc pss :none none-styles)}))

; component extension -----------------------------------

(defn << [c x & xs]
  (let [cnxt (reduce
               (fn [c [k v]]
                 (cond
                   (= k :args) (update-in c [:args] merge v)
                   (#{:style :attrs :bpipe} k) (update-in c [k] #(vec (concat % (ensure-vec v))))
                   :else (assoc c k v)))
               c x)]
    (if-not (seq xs)
      cnxt
      (apply << cnxt xs))))

;merge utils ----------------------------------------------

(def m? (->merge-mode :default))
(def m! (->merge-mode :swap))
(def m> (->merge-mode :join))

;selectors ------------------------------------------------

(defn $parse [x]
  (let [s (name x)
        fs (first s)]
    {:id (second (re-find #"\#([a-zA-Z_0-9\-]+)" s))
     :classes (set (map second (re-seq #"\.([a-zA-Z_0-9\-]+)" s)))
     :el (when-not (or (= fs "$") (= fs ".") (= fs "#")) (re-find #"[a-zA-Z_0-9\-]+" s))
     :label (second (re-find #"\$([a-zA-Z_0-9\-]+)" s))}))

(defn c-matchers [c]
  (let [s (name (:wrapper c))
        div? (or (= (first s) ".") (= (first s) "#"))]
    {:id (second (re-find #"\#([a-zA-Z_0-9\-]+)" s))
     :classes (set (map second (re-seq #"\.([a-zA-Z_0-9\-]+)" s)))
     :el (if div? "div" (re-find #"[a-zA-Z_0-9\-]+" s))
     :label (when-let [l (:label c)] (name l))}))

(defn- map$ [m]
  (fn [body f]
    (second
      (reduce
        (fn [[m res] el]
          (if (t? :scomp el)
            (let [[el m] (m el m f)]
              [m (conj res el)])
            [m (conj res el)]))
        [m []]
        body))))

(defn inject [body injections]
  (if-let [[sel trans] (first injections)]
    (inject ((map$ sel) body trans)
            (next injections))
    body))

(defn <<$ [c i]
  (<< c {:bpipe #(inject % [i])}))

(defn $p [pred]
  (selector [c s f]
            (let [match? (pred c)]
              [(<<$ (if match? (f c) c) [s f]) s match?])))

(defn $e [x]
  (selector [c s f]
            (let [match? (= (:el (c-matchers c)) (name x))]
              [(<<$ (if match? (f c) c) [s f]) s match?])))

(defn $k [x]
  (selector [c s f]
            (let [match? ((:classes (c-matchers c)) (name x))]
              [(<<$ (if match? (f c) c) [s f]) s])))

(defn $id [x]
  (selector [c s f]
            (let [match? (= (:id (c-matchers c)) (name x))]
              [(<<$ (if match? (f c) c) [s f]) s match?])))

(defn $ [x]
  (selector [c s f]
            (let [{xcs :classes xid :id xel :el xlab :label} ($parse x)
                  {:keys [classes label id el]} (c-matchers c)
                  match? (and (clojure.set/subset? xcs classes)
                              (cond (and xid id) (= xid id) xid false :else true)
                              (cond (and xlab label) (= xlab label) xlab false :else true)
                              (if xel (= xel el) true))]
              [(<<$ (if match? (f c) c) [s f]) s match?])))

(def $childs
  (selector [c s f]
            [(f c) s true]))

(defn $and [& xs]
  (selector [c s f]
            (let [[match? sels]
                  (loop [ret true [x & nxt] xs sels []]
                    (if-not x [ret sels]
                              (let [[_ s match?] (x c s f)]
                                (recur (and match? ret) nxt (conj sels s)))))]
              [(<<$ (if match? (f c) c) [s f]) (apply $and sels) match?])))

(defn $or [& xs]
  (selector [c s f]
            (let [[match? sels]
                  (loop [ret false [x & nxt] xs sels []]
                    (if-not x [ret sels]
                              (let [[_ s match?] (x c s f)]
                                (recur (or match? ret) nxt (conj sels s)))))]
              [(<<$ (if match? (f c) c) [s f]) (apply $or sels) match?])))

(defn $not [c s _] [c s false])

(defn $nth [n x]
  (selector [c s f]
            (let [match? (last (x c s f))]
              (if match?
                (if (zero? n)
                  [(f c) $not true]
                  [c ($nth (dec n) x) false])
                [c s false]))))

;; rendering helpers -----------------------------------------------------

(defn style-injections [sis]
  (map (fn [[s t]]
         (fn [body]
           (inject body [[s #(<< % {:style t})]])))
       sis))

(defn attr-injections [ais]
  (map (fn [[s t]]
         (fn [body]
           (inject body [[s #(<< % {:attrs t})]])))
       ais))

(defn- deref-args [xs]
  ;; deref is not deep
  (mapv #(if (satisfies? IDeref %) @% %) xs))

; slighly modified version of (merge rum/cursored rum/cursored-watch)
(def watched
  {:transfer-state
   (fn [old new]
     (assoc new :om-args (:om-args old)))
   :should-update
   (fn [old-state new-state]
     (not= (:om-args old-state) (deref-args (:args new-state))))
   :wrap-render
   (fn [render-fn]
     (fn [state]
       (let [[dom next-state] (render-fn state)]
         [dom (assoc next-state :om-args (deref-args (:args state)))])))
   :did-mount
   (fn [state]
     (doseq [arg (vals (:args state))
             :when (satisfies? IWatchable arg)]
       (add-watch arg (str ":watched-" (:rum/id state))
                  (fn [_ _ _ _] (rum/request-render (:rum/react-component state)))))
     state)
   :will-unmount
   (fn [state]
     (doseq [arg (vals (:args state))
             :when (satisfies? IWatchable arg)]
       (remove-watch arg (rum/cursored-key state)))
     state)})

(defn compute-styles [pks pss]
  (let [pks @pks]
    (merge {}
           (if (pks :none) (:none pss))
           (if (pks :hover) (:hover pss))
           (if (pks :focus) (:focus pss))
           (if (pks :active) (:active pss)))))

(def css-handlers
  (m>
    (sfn {c :rum/react-component
          pks :pseudo-classes
          pss :pseudo-styles}
         (letfn [(do-styles [] (let [node (.getDOMNode c)]
                                 (doseq [[k v] (format-style (compute-styles pks pss))]
                                   (aset (.-style node) k v))))
                 (addpk [x] (fn [_] (swap! pks conj x) (do-styles)))
                 (rempk [x] (fn [_] (reset! pks (clojure.set/difference @pks #{x})) (do-styles)))]

           {:on-mouse-enter (addpk :hover)
            :on-mouse-leave (rempk :hover)
            :on-focus (addpk :focus)
            :on-blur (rempk :focus)
            :on-mouse-down (addpk :active)
            :on-mouse-up (rempk :active)}))))

(declare swrap)

(defn parse-litteral
  "turn [my-scomp spec-maps body-element...] into proper scomp"
  [x]
  (if (vector? x) ;is scomp vec litteral
    (let [[c & xs] x
          [spec body*]
          (if (map? (first xs))
            [(first xs) (rest xs)]
            [{} xs])
          body (reduce (fn [acc el] (if (seq? el) (vec (concat acc el)) (conj acc el))) [] body*)
          args (or (:args spec) (select-keys spec (keys (:schema c))))
          style (or (:style spec) {})
          bpipe (or (:bpipe spec) identity)
          attrs (or (:attrs spec) (apply dissoc spec :style :bpipe (keys (:schema c))))
          c (if (t? :scomp c) c (swrap c))]
      (if (seq body) ;litteral body
        (<< c {:style style :attrs attrs :args args :bpipe bpipe :body body})
        (<< c {:style style :attrs attrs :args args :bpipe bpipe})))
    x))

(defn- parse-litterals [body]
  (mapv parse-litteral body))

(def scomp-render
  {:render
   (fn [{:keys [wrapper attrs style bpipe body] :as state}]
     (let [{:keys [sis style pseudo-styles]} (->> style (inject-state state) kv-seq split-styles)
           state (assoc state :pseudo-styles pseudo-styles :pseudo-classes (atom #{:none}))
           [attrs ais] (->> (conj attrs css-handlers) (inject-state state) kv-seq merger) ;; css-handlers merge mode ????
           bpipe (concat (inject-state state bpipe) (style-injections sis) (attr-injections ais))
           btrans #(reduce (fn [b t] (t b)) % bpipe)]
       [(html (apply vector
                     wrapper
                     (assoc attrs :style style)
                     (-> ((wrap-fn (or body [])) state) parse-litterals btrans build-body)))
        state]))})

(defn scomp-class [mixins label]
  (rum/build-class (conj mixins scomp-render) label))


;;  -----------------------------------------------------------------------------

(defn ref
  "ref schema
   ex: (ref schema.core/Int)
   describe a ref that olds an Integer"
  [schema]
  (t :ref
     (s/conditional
       (constantly true) (s/pred #(satisfies? IDeref %))
       (constantly true) (s/pred #(s/validate schema @%)))))

(defn strict
  "make an args schema non extensible, no extra keys allowed"
  [schema]
  (vary-meta schema assoc {:strict true}))

(defn normalize-scomp
  [{:keys [mixins wrapper attrs style bpipe body schema label args] :as spec}]
  (assoc spec
    :mixins (or mixins [])
    :wrapper (or wrapper :div)
    :attrs (ensure-vec (or attrs []))
    :style (ensure-vec (or style []))
    :bpipe (ensure-vec (or bpipe []))
    :body (wrap-fn (or body (constantly [])))
    :args (or args {})
    :label (or (and label (name label)) (str (gensym "scomp")))
    :schema (let [sc (or schema {})]
              (if (:strict (meta schema)) sc (assoc sc s/Any s/Any)))))

;maybe turn this into a record
(defn scomp
  "serum component"
  [spec]
  (let [spec (normalize-scomp spec)
        has-refs? (some #(t? :ref %) (vals (:schema spec)))
        mixins (if has-refs? (conj (:mixins spec) watched) (:mixins spec))
        k (scomp-class mixins (:label spec))]
    (vary-meta
      spec
      assoc
      :type :scomp
      :builder
      (fn build [{s :schema args :args :as spec}]
        (s/validate s args)
        (rum/element k spec nil)))))

(def div (scomp {}))

(defn swrap [x]
  (scomp {:wrapper x}))

(defn scomp? [x] (t? :scomp x))

(defn wrap-render
  "little shortcut for rerender after an event-handler"
  [f]
  (t :sfn
     (fn [{c :rum/react-component}]
       (fn [e] (f e) (rum/request-render c)))))

(defn mount
  ([c] (mount c js/document.body))
  ([c target] (rum/mount (build (parse-litteral c)) target)))

(def cursor rum/cursor)

(def rerender rum/request-render)

(defn render [state]
  (rum/request-render (:rum/react-component state)))

;;----------------------------------------------------------------------------------------------------------------------
;; EXEMPLES
;; ---------------------------------------------------------------------------------------------------------------------

(comment
  "experimental, should use defrecord for scomp instead?"
  (extend-type cljs.core/PersistentHashMap
    IFn
    (-invoke ([x y] (if (t? :scomp x) (<< x y) (get x y)))
      ([x y & ys] (when (t? :scomp x) (apply << x y ys)))))

  (mount
    (<< (scomp {:body ["hi"]})
        {:args {:x 1}
         :style {:background-color :blue}
         :attrs (afn {x :x} {:on-click (fn [_] (println "yo" x))})})))

(comment

  (comment
    (mount [:div {:style {:background-color :limegreen
                          :hover {:background-color :lime}}
                  :on-click (fn [_] (println "hi!"))}
            "hello" "you"
            (map (fn [x] [:span x]) ["aze" "ert"])]))

  "pseudo classes test"
  (mount
    (scomp
      {:wrapper :input
       :attrs {:value "yop"
               :on-click (fn [_] (println "click"))
               :on-double-click (fn [_] (println "dblclick"))}
       :style {:hover {:background-color :green}
               :active {:background-color :pink}
               :focus {:background-color :purple}}}))

  "parse scomp vecs test"
  (let [c (scomp {:body ["hello"]})]
    (mount
      (scomp {:body [[c {:style {:background-color :blue}}]
                     "yop"]})))



  (mount
    [:div {:style {:background-color :limegreen}}
     [:div "yo"]
     [:div {:style {:background-color :green}} "bob" [:div "blop"]]])

  (mount
    [(swrap :div)
     {:style {:background-color :green}
      :attrs (afn args {:on-click (fn [_] (println args))})
      :args {:a 1 :b 2}}
     "yop"]))

;;TODO
;allows nested afns ???
;maybe have also merging variants like afn> afn? afn!  ???
;make the pseudo classes extensible , user able to define his own defined by on/off events
;it could be great to make scomp invokable for for being able to replace (<args c {}) by (c (args {})) or (c {args {}})

; tests ------------------------------------------------------------------------

(comment

  (mount (scomp {:body ["hello scomp!"]}))

  "the body key should contains a seq representing the body of the component"

  (mount (scomp {:body (fn [state] (println state) ["hello scomp!"])}))

  "it can also hold a function that given the component state return a seq representing the body"

  (mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
                 :args {:a "hello!"}}))

  "this makes more sense when we actually need the state to build the body!
  by the way we discover another option key named :args that simply hold arbitrary state that we need for our component"

  (mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
                 :schema {:a s/Str}
                 :args {:a "hello!"}}))

  "you can add a schema to check args"

  #_(mount (scomp {:body (fn [{{a :a} :args}] [[:div a]])
                   :schema {:a s/Str}
                   :args {:a 1}}))

  "should throw an exception"

  (def a0 (atom 0))

  (mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

  (swap! a0 inc)

  "if some args are refs that you want the component be reactive on you can tell it like this,
  the ref function is just a convenience that return a schema"

  (mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
                 :attrs (sfn {{a :a} :args} {:on-click (fn [_] (swap! a inc))})
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

  (mount (scomp {:body (fn [{{a :a} :args}] [[:div @a]])
                 :attrs (afn {a :a} {:on-click (fn [_] (swap! a inc))})
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

  (comment
    "those expressions are equivalent"
    (with-meta (fn [{{a :a} :args}] "body") {:type :sfn})
    (sfn {{a :a} :args} "body")
    (afn {a :a} "body"))

  "the attrs option is used to provide one or many attribute-constructor(s) or attribute-map(s),
  an attribute constructor is a fn that olds {:type :sfn} in metadata and return an attribute-map,
  sfn stands for 'state function' in other words a value that depends on the component state
  it can be built with sfn or afn macros (note that first argument is a binding form, for sfn it binds on full state and for afn on args)"

  (mount (scomp {:body (afn {a :a} [[:div @a]])
                 :attrs (afn {a :a} {:on-click (fn [_] (swap! a inc))})
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

  "the 'afn' macro provide a cleaner way to declare constructors that cares only about args"

  (mount (scomp {:body (afn {a :a} [[:div @a]])
                 :attrs [(afn {a :a} {:on-click (fn [_] (swap! a inc))})
                         {:on-mouse-over (fn [e] (println e))}]
                 :schema {:a (ref s/Int)}
                 :args {:a a0}}))

  "the attrs options can take several attributes-constructors or attributes-map at a time"

  (mount (scomp {:body (afn {t :text} [[:p t]])
                 :style (afn {c :color} {:background-color c})
                 :args {:color :lightskyblue :text "Hello!"}}))

  "you can specify styles in the same way than attrs"

  (def ss1 {:background-color :tomato
            :padding :5px
            :border-radius :5px
            :border "3px solid lightcyan"})

  (def c1
    (scomp {:body (afn {t :text} [[:p t]])
            :style [ss1 (afn {c :color} {:background-color c})]
            :args {:color :lightskyblue :text "Hello!"}}))

  (mount c1)

  "like attrs it can take several at a time"

  (mount [c1 {:args {:color :mediumaquamarine}}])

  "you can provide args styles attrs bpipes to your component with << function, by calling one of the builtin type wrapper (args style attrs or bpipe) on the second argument"

  (mount [c1 {:attrs {:on-click (fn [_] (println "yo"))}}])

  "this will add given constructor(s) or map(s) to your component"

  (mount (scomp {:body [c1 [c1 {:args {:text "goodbye!"}}]]
                 :bpipe (fn [b] (apply concat (repeat 3 b)))}))

  (mount [c1 {:bpipe (fn [b] (repeat 3 (first b)))}])

  "bpipe is used to transform the body of a component"

  (def polite-comp
    {:did-mount (fn [_] (println "Hello!"))})

  (mount (scomp {:mixins [polite-comp]
                 :body ["yop"]}))

  "you can provide mixins like in rum"

  (def c2 (scomp {:wrapper :.foo
                  :body (afn {c :content} c)}))

  (def c3 (scomp {:body [[c2 {:args {:content "foo"}}]
                         [c2 {:args {:content "bar"}}]]
                  :style {:background-color :purple
                          :padding :10px
                          ($ ".foo") {:background-color :lightcoral
                                      :font-size :25px
                                      :color :white
                                      :padding :10px}}}))

  (mount c3)

  "you can inject styles or attributes into sub components "

  (mount [c3 {:style {:border-radius :5px
                      :hover {:background-color :pink}}}])

  "you can specify :hover :active and :focus styles like this"

  (def c4 (scomp {:body ["click me and watch console"]
                  :attrs {:on-click (fn [_] (println "clicked"))}}))

  (mount [c4 {:attrs {:on-click (fn [_] (println "clicked overiden"))}}])

  "when doing this the old click event is overiden by the new"

  (mount [c4 {:attrs (m> {:on-click (fn [_] (println "clicked overiden"))})}])

  "with m> it is added"

  (def default-on-click (m? {:on-click (fn [_] (println "default click"))}))

  (def c5 (scomp {:body ["click me"]}))

  (mount [c5 {:attrs default-on-click}])

  "when wrap with m? an attribute or style is merged only if not present in the target component"

  (mount [c4 {:attrs default-on-click}])

  "should not change c4 click"

  (def wrap-click
    (m! {:on-click
         (fn [click-handler]
           (fn [_] (println "wrap") (click-handler) (println "wrap...")))}))

  (mount [c4 {:attrs wrap-click}])

  "with m! you can swap an attribute value"



  "usage test"
  (def atom1 (atom 1))
  (def atom2 (atom 10))
  (def atom3 (atom {:a 12 :b 13}))
  (swap! atom1 inc)
  (swap! atom3 update-in [:b] + 10)

  (def c1
    (scomp {:label :c1
            :wrapper :div#aze.ert
            :attrs [{:on-click (fn [_] (println "yop"))}
                    {:on-mouse-over (fn [_] (println "over"))}
                    (m> (afn {b :b} {:on-click (fn [_] (swap! b inc))}))]
            :style [{:background-color :mediumaquamarine
                     :padding (str "10px")
                     :border (str "10px solid grey")
                     :hover {:background-color :mediumslateblue}
                     :active {:background-color :pink}}
                    (afn {a :a b :b}
                         {:margin (str a "px")
                          :padding (str @b "px")})]
            :body (fn [_] ["Hello scomp!"])
            :schema {:a s/Int :b (ref s/Int)}
            :args {:a 50 :b (cursor atom3 [:b])}}))


  (mount [c1 {:style (afn {a :a} {:border (str (/ a 4) "px solid lightskyblue")})
              :attrs (m> {:on-click (fn [_] (println "yep"))})
              :args {:a 12}
              :bpipe (fn [b] (conj b [:div "one"]))}])

  (def c2
    (scomp {:wrapper :.qsd
            :label :c2
            :body [c1 c1]}))

  (def c3
    (scomp {:body [c2 c2]
            :style {($ :.qsd) (m? {:border "10px solid lightgrey"})
                    ($and ($ :.ert) ($ :$c1)) {:hover {:background-color :lightcoral}}
                    ($or ($ :.zup) ($ :$c1)) {:color :white}
                    ($p #(= :c1 (:label %))) {:font-size :30px}
                    ($and ($ :$c2) ($nth 1 ($ :$c2))) {:background-color :lightcyan}
                    ($or ($ :$c1) ($nth 1 ($ :$c2))) {:background-color :lightcyan}}}))

  (mount c3))

