(ns serum.core)

(defmacro selector [args & body]
  `(vary-meta (fn ~args ~@body) assoc :type :selector))

(defmacro afn [bindf & body]
  `(~'serum.core/t :sfn
     (fn [{a# :args}]
       (let [~bindf a#] ~@body))))

(defmacro sfn [bindf & body]
  `(~'serum.core/t :sfn
     (fn [s#]
       (let [~bindf s#] ~@body))))

