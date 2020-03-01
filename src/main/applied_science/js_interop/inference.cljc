(ns applied-science.js-interop.inference
  (:require [cljs.analyzer :as ana]
            [clojure.set :as set]))

(defn normalize-tag [tag]
  (if (set? tag)
    (let [tags (into #{} (keep normalize-tag) tag)]
      (if (<= 1 (count tags)) (first tags) tags))
    (when tag
      ('{"Array"   array
         "String"  string
         "Keyword" keyword} (name tag) tag))))

(defn as-set [x] (if (set? x) x #{x}))

(defn within? [pred-tags tags]
  (set/superset? pred-tags (as-set tags)))

(defn infer-tags
  "Infers type of expr"
  [env expr]
  (->> (ana/analyze env expr)
       ana/no-warn
       (ana/infer-tag env)
       (normalize-tag)))

(defn tag-in? [env form tags]
  (when env
    (some->> (infer-tags env form)
             (within? tags))))