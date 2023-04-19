(ns applied-science.js-interop.alpha
  (:require [applied-science.js-interop :as j]
            [applied-science.js-interop.destructure :as d])
  #?(:cljs (:require-macros applied-science.js-interop.alpha)))

;; anything in this namespace is subject to change

(defmacro js [& forms]
  (binding [d/*js?* true]
    `(do ~@(map (partial j/lit* {:env &env :deep? true}) forms))))