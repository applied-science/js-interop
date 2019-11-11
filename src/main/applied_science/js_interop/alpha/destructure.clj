(ns applied-science.js-interop.alpha.destructure
  (:refer-clojure :exclude [let fn defn])
  (:require [clojure.core :as core]
            [applied-science.js-interop.alpha.destructure-impl :as d]))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Destructured forms

(defmacro let
  "`let` with destructuring that supports js property and array access.
   Use a ^js hint on the binding form to invoke. Eg:
   (let [^js {:keys [a]} obj] …)"
  [bindings & body]
  (if (empty? bindings)
    `(do ~@body)
    `(core/let ~(d/destructure &env (take 2 bindings))
               (~'applied-science.js-interop.alpha.destructure/let
                 ~(vec (drop 2 bindings))
                 ~@body))))

(defmacro fn
  "`fn` with argument destructuring that supports js property and array access.
   Use a ^js hint on binding forms to invoke."
  [& args]
  (cons `core/fn (d/destructure-fn-args args)))

(defmacro defn
  "`defn` with argument destructuring that supports js property and array access.
   Use a ^js hint on binding forms to invoke."
  [& args]
  (cons `core/defn (d/destructure-fn-args args)))