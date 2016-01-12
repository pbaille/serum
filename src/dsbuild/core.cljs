(ns ^:figwheel-always dsbuild.core
  (:require [datascript.core :as d]
            [dsbuild.utils :refer [t t?] :as u]
            [dsbuild.styles :as s]
            [serum.core :as serum :refer [scomp scomp? << rerender mount] :refer-macros [afn sfn]]))

(enable-console-print!)

;; state -----------------------------------------------------------------

(def leaves (atom {}))
(def entities (atom {}))
(def schema (atom {}))

(defn conn! []
  (def db (d/create-conn @schema)))

(defn leaf? [kw]
  ((set (keys @leaves)) kw))

(defn entity? [kw]
  ((set (keys @entities)) kw))

(defn get-members [kw]
  (get-in @entities [kw :members]))

(defn type-kw [t]
  (if (vector? t) (first t) t))

(defn get-type [ns kw]
  (type-kw (kw (get-members ns))))

(defn get-comp [kw ctype & [not-found]]
  (cond
    (leaf? kw) (get-in @leaves [kw :comps ctype] not-found)
    (entity? kw) (get-in @entities [kw :comps ctype] not-found)
    :else (u/throw-type-error)))

(defn meta-type [t]
  (let [m? (vector? t)
        [l? e?] ((juxt leaf? entity?) (type-kw t))]
    (cond
      (and l? m?) :many-leaves
      (and e? m?) :many-refs
      l? :leaf
      e? :ref)))

;; queries -----------------------------------------------------------------

(defn id->e [id]
  (d/entity @db id))

(defn id->hm [id]
  (into {} (id->e id)))

(defn id->hm* [id]
  (into {} (map (fn [[k v]] [(keyword (name k)) v]) (id->hm id))))

(defn attr->ids [attr]
  (map first (d/q '[:find ?e :in $ ?a :where [?e ?a]] @db attr)))

(defn ids-for [ekw]
  (attr->ids (keyword (name ekw) (name (key (first (get-members ekw)))))))

(defn leaf-args->kv [{{pid :id pkw :kw} :parent kw :kw}]
  (let [p (d/entity @db pid)
        attr-key (keyword (name pkw) (name kw))]
    [attr-key (attr-key p)]))

;; updates -----------------------------------------------------------------

(defn switch-ref [pid pkw id kw]
  (let [attr (keyword (name pkw) (name kw))]
    (d/transact! db [[:db/add pid attr id]])))

(defn conj-ref [pid pkw id kw]
  (let [attr (keyword (name pkw) (name kw))]
    (d/transact! db [[:db/add pid attr id]])))

(defn remove-ref [pid pkw id kw]
  (let [attr (keyword (name pkw) (name kw))
        olds (map :db/id (attr (d/entity @db pid)))]
    (d/transact! db [[:db.fn/retractAttribute pid attr]
                     {:db/id pid attr (set (filter (partial not= id) olds))}])))

(defn delete-instance [id]
  (d/transact! db [[:db.fn/retractEntity id]]))

;; comps -------------------------------------------------------------------

(defn- args->parent
  "in case of leaf format args (refs) compute the new parent for sub components"
  [{{pid :id pkw :kw :as parent} :parent eid :id ekw :kw}]
  (if parent {:kw (get-type pkw ekw) :id (int (:db/id (ekw (id->hm* pid))))}
             {:kw ekw :id eid}))

(defn label [text]
  [:div {:style {:border-bottom "2px solid grey"
                 :padding       :10px}}
   text])

(defn labelize-members
  ([]
   {:bpipe (fn [xs] (interleave (map (fn [x] (label (name (get-in x [:args :kw])))) xs) xs))})
  ([x & xs]
   (let [labelized (set (cons x xs))]
     {:bpipe (fn [xs]
               (interleave
                 (map
                   (fn [x]
                     (let [kw (get-in x [:args :kw])]
                       (when (labelized kw)
                         (label (name kw))))) xs)
                 xs))})))

(defn $kw
  "a serum selector for grabing subcomponents by keyword"
  [kw]
  (serum/$p #(= kw (get-in % [:args :kw]))))

(defn customc [spec]
  (scomp
    {:label :customc
     :body  (afn args
                 (let [{pkw :kw :as parent} (args->parent args)]
                   (map
                     (fn [[kw v]]
                       [(get-comp (get-type pkw kw) v)
                        {:args {:kw kw :parent parent}}])
                     spec)))}))

(def many-refs-view
  (scomp
    {:label :many-refs-view
     :body  (afn {{pid :id pkw :kw :as parent} :parent kw :kw :as args}
                 (let [ids (set (map :db/id (second (leaf-args->kv args))))
                       t (get-type pkw kw)]
                   (map #(<< (get-comp t :ref-view)
                             {:args  {:id % :kw t}
                              :style {:display :inline-block}})
                        ids)))}))

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

(def ref-edit
  (scomp
    {:label :ref-edit
     :body  (sfn {{{pid :id pkw :kw} :parent kw :kw :as args} :args
                  o                                           :open
                  c                                           :rum/react-component}
                 (let [t (get-type pkw kw)
                       cv (get-comp t :ref-view)
                       xs (ids-for t)
                       sel (:db/id (second (leaf-args->kv args)))
                       body (map #(let [sel? (= sel %)]
                                   [cv
                                    {:args  {:kw t :id %}
                                     :style {:margin  :3px
                                             :border  (str "5px solid " (if (and sel? @o) "lightblue" "white"))
                                             :display (if (or @o sel?) :inline-block :none)}
                                     :attrs {:on-click
                                             (fn [_]
                                               (if sel?
                                                 (swap! o not)
                                                 (switch-ref pid pkw % kw))
                                               (rerender c))}}])
                                 xs)]
                   (if (or sel @o)
                     body
                     (cons [:.open
                            {:on-click (fn [_] (swap! o not)(rerender c))
                             :style button}
                            (str "select " (name kw))]
                           body))))
     :open  (atom false)}))

(def many-refs-edit
  (scomp
    {:label :many-refs-edit
     :attrs (sfn {c :rum/react-component o :open}
                 {(if @o :on-mouse-leave :on-click)
                  (fn [_] (swap! o not) (rerender c))})
     :body  (sfn {{{pid :id pkw :kw} :parent kw :kw :as args} :args
                  o                                           :open
                  c                                           :rum/react-component}
                 (let [t (get-type pkw kw)
                       cv (get-comp t :ref-view)
                       xs (ids-for t)
                       sels (set (map :db/id (second (leaf-args->kv args))))
                       body (map #(let [sel? (sels %)]
                                   [cv
                                    {:args  {:kw t :id %}
                                     :style {:margin  :3px
                                             :border  (str "5px solid " (if (and sel? @o) "lightblue" "white"))
                                             :display (if (or @o sel?) :inline-block :none)}
                                     :attrs {:on-click
                                             (fn [_]
                                               (if (and @o sel?)
                                                 (remove-ref pid pkw % kw)
                                                 (conj-ref pid pkw % kw))
                                               (rerender c))}}])
                                 xs)]
                   (if (or (seq sels) @o)
                     body
                     (cons [:.open
                            {:on-click (fn [e] (swap! o not)(.stopPropagation e)(rerender c))
                             :style button}
                            (str "select " (name kw))]
                           body))))

     :open  (atom false)}))

(declare new-instance)

(defn entity-index [kw]
  (scomp
    {:body
     (fn [_]
       (for [id (ids-for kw)]
         [(get-comp kw :ref-view)
          {:args {:kw kw :id id}}]))}))

;; local helpers -----------------------------------------------------------

(defn get-default-view [t]
  (condp = (meta-type t)
    :many-leaves :many-view
    :many-refs :many-refs-view
    :leaf :view
    :ref :ref-view))

(defn get-default-edit [t]
  (condp = (meta-type t)
    :many-leaves :many-edit
    :many-refs :many-refs-edit
    :leaf :edit
    :ref :ref-edit))

(defn mview
  ([kw] #(mview kw %))
  ([kw members] [kw (get-default-view (kw members))]))

(defn medit
  ([kw] #(medit kw %))
  ([kw members] [kw (get-default-edit (kw members))]))

(defn mviews [& xs]
  (t :spec-fn
     (fn [members]
       (map #(cond
              (vector? %) %
              (fn? %) (% members)
              :else (mview % members))
            xs))))

(defn medits [& xs]
  (t :spec-fn
     (fn [members]
       (map #(cond
              (vector? %) %
              (fn? %) (% members)
              :else (medit % members))
            xs))))

(defn format-comps [members comps]
  (u/map-vals (fn [v]
                (cond
                  (scomp? v) v
                  (t? :spec-fn v) (customc (v members))
                  (sequential? v) (customc (map #(% members) v))))
              comps))

(defn- default-comps [name members]
  (let [mks (keys members)
        editc (customc ((apply medits mks) members))
        viewc (customc ((apply mviews mks) members))]
    {:index          (entity-index name)
     :edit           editc
     :view           viewc
     :ref-view       viewc
     :ref-edit       ref-edit
     :many-refs-view many-refs-view
     :many-refs-edit many-refs-edit}))

(defn- apply-cmods [cmods comps]
  (u/map-hm
    (fn [[k v]]
      [k (if-let [mod (k cmods)]
           (apply << v (u/ensure-vec mod))
           v)])
    comps))

(defn- build-comps [name members comps cmods]
  (apply-cmods
    cmods
    (merge
      (default-comps name members)
      (format-comps members comps))))

(defn- add-schema! [n members-map]
  (swap! schema
         merge
         (u/map-hm
           (fn [[kw t]]
             [(keyword (name n) (name kw))
              (let [[l? e?] ((juxt leaf? entity?) t)]
                (condp = (meta-type t)
                  :many-refs {:db/cardinality :db.cardinality/many
                              :db/valueType   :db.type/ref}
                  :many-leaves (merge {:db/cardinality :db.cardinality/many}
                                      (get-in @leaves [t :schema] {}))
                  :ref {:db/valueType :db.type/ref}
                  :leaf (get-in @leaves [t :schema] {})
                  :else (u/throw-type-error)))])
           members-map)))

;------------------------------------------------------------------------------

(defn add-entity! [{:keys [name members comps cmods]
                    :or   {comps {} cmods {}}}]
  (swap! entities assoc name
         {:members members
          :comps   (build-comps name members comps cmods)})
  (add-schema! name members))

(defn add-leaf! [{:keys [name ds-attrs comps gen]}]
  (swap! leaves assoc name {:schema (or ds-attrs {}) :comps comps :gen gen}))

(defn add-leaves! [& xs]
  (doseq [x xs] (add-leaf! x)))

(defn add-entities! [& xs]
  (doseq [x xs] (add-entity! x)))

(defn add-comps! [ekw comps & xs]
  (swap! entities update-in [ekw :comps] merge comps)
  (when (seq xs) (apply add-comps! xs)))

;; gen ---------------------------------------------------------------

(defn gen1 [ename]
  (u/map-hm
    (fn [[kw t]]
      (let [tkw (type-kw t)]
        [(keyword (name ename) (name kw))
         (condp = (meta-type t)
           :many-leaves (map (fn [_] ((:gen (tkw @leaves)) (name ename))) (range 3))
           :many-refs (set (take 3 (shuffle (ids-for tkw))))
           :leaf ((:gen (tkw @leaves)) (name ename))
           :ref (first (shuffle (ids-for tkw))))]))
    (get-members ename)))

(defn gen [n ename]
  (map (fn [_] (gen1 ename)) (range n)))

(defn gen! [n ename]
  (d/transact! db (gen n ename)))

(defn gen-all! [n]
  (doseq [kw (keys @entities)] (gen! n kw)))

(defn new-instance
  "create a new entity instance of type kw and return the new id"
  [kw]
  (ffirst (:tx-data (gen! 1 kw))))

