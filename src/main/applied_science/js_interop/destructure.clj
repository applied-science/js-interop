(ns applied-science.js-interop.destructure
  (:refer-clojure :exclude [let fn defn])
  (:require [applied-science.js-interop.destructure.impl :as d]))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Destructured forms

(defmacro let
  "`let` with destructuring that supports js property and array access.
   Use a ^js hint on the binding form to invoke. Eg/

   (let [^js {:keys [a]} obj] …)"
  [bindings & body]
  (if (empty? bindings)
    `(do ~@body)
    `(~'clojure.core/let ~(d/destructure &env (take 2 bindings))
       (~'applied-science.js-interop.destructure/let
         ~(vec (drop 2 bindings))
         ~@body))))

(defmacro fn
  "`fn` with argument destructuring that supports js property and array access.
   Use a ^js hint on binding forms to invoke. Eg/

   (fn [^js {:keys [a]}] …)"
  [& args]
  (cons 'clojure.core/fn (d/destructure-fn-args args)))

(defmacro defn
  "`defn` with argument destructuring that supports js property and array access.
   Use a ^js hint on binding forms to invoke."
  [& args]
  (cons 'clojure.core/defn (d/destructure-fn-args args)))