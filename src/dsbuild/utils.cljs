(ns dsbuild.utils)

(defn t
  ([e] (:type (meta e)))
  ([sym e] (vary-meta e assoc :type sym)))

(defn t? [sym e] (= sym (t e)))

(defn map-vals [f m]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn map-hm [f m]
  (into {} (map f m)))

(defn- dissoc-in
  [m [k & ks]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(defn throw-type-error []
  (throw (js/Error. "not a known type")))

(defn ensure-vec [x]
  (cond (vector? x) x (sequential? x) (vec x) :else [x]))
