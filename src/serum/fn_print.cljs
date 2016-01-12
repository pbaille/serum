(ns serum.fn-print
  (:require [clojure.string :as str]))

;fn printing -----------------------------------------------------------------

(defn unmunge [s]
  (-> s
      (str/replace #"_DOT_" ".")
      (str/replace #"_" "-")))

(defn get-func-signature [func]
  (let [name (.-name func)]
    (when (seq name)
      (let [[_ ns fn-name line :as x] (re-find #"fn_(.+)_SLASH_(.+)_(\d+)" name)]
        (if x
          (str (unmunge ns) "/" (unmunge fn-name) " line:" line)
          name)))))

#_(defn fn-pp []
  (do
    (extend-type js/Function
      IPrintWithWriter
      (-pr-writer [this writer opts]
        (if-let [sig (get-func-signature this)]
          (-write writer (str "#<function " sig ">"))
          (-write writer (str "#<" this ">")))))

    (extend-type cljs.core.MetaFn
      IPrintWithWriter
      (-pr-writer [this writer opts]
        (if-let [t (t this)]
          (-write writer (str "#<fn:" (name t) ">"))
          (-write writer (str "#<" this ">")))))))

(defn all-keys [obj]
  (sort (js* "eval('var results = []; for(var i in ~{}){ results.push(i) }; results')"
             obj)))
