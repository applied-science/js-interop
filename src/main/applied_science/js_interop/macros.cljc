(ns applied-science.js-interop.macros)

;; from macrovich/case https://github.com/cgrand/macrovich#usage
(defmacro target [& {:keys [cljs clj]}]
  (if (contains? &env '&env)
    `(if (:ns ~'&env) ~cljs ~clj)
    (if #?(:clj (:ns &env) :cljs true)
      cljs
      clj)))

(defmacro defmacro-cljs
  "defmacro with no-op in non-:cljs targets"
  [name & args]
  (let [[doc args] (if (string? (first args)) [(first args) (rest args)] [nil args])
        [opts args] (if (map? (first args)) [(first args) (rest args)] [nil args])
        kind (cond (list? (first args)) :multi-arity
                   (vector? (first args)) :single-arity
                   :else (throw (Exception. "Invalid defmacro form")))]
    `(defmacro ~name ~@(keep identity [doc opts])
       ~@(case kind
           :single-arity
           (let [[argv & body] args]
             [argv (target :cljs `(do ~@body))])
           :multi-arity
           (for [[argv & body] args]
             (list argv (target :cljs `(do ~@body))))))))
