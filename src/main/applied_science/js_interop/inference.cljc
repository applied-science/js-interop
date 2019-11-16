(ns applied-science.js-interop.inference
  (:require [cljs.analyzer :as ana]
            [cljs.env :as env]
            [clojure.string :as str]))

(def ^:dynamic *&env*
  "Bind to &env value within a macro definition"
  nil)

(defn normalize-tag [tag]
  (if (set? tag)
    (let [tags (into #{} (keep normalize-tag) tag)]
      (if (<= 1 (count tags)) (first tags) tags))
    ({'Array 'array} tag tag)))

(defn maybe-nil? [tag]
  (cond (symbol? tag) (contains? #{'any 'clj-or-nil 'clj-nil 'js/undefined} tag)
        (set? tag) (or (empty? tag) (some maybe-nil? tag))
        :else true))

(def not-nil? (complement maybe-nil?))

(defn infer-tags
  "Infers type of expr"
  ([expr]
   (infer-tags *&env* expr))
  ([env expr]
   (->> (ana/analyze env expr)
        ana/no-warn
        (ana/infer-tag env)
        (normalize-tag))))

(defn record-fields
  "Returns record fields for given type tag"
  [tag]
  (when (qualified-symbol? tag)
    (let [positional-factory-name (symbol (str "->" (str/replace (name tag) #"^^" "")))]
      (some-> (get-in @env/*compiler* [:cljs.analyzer/namespaces
                                       (symbol (namespace tag))
                                       :defs
                                       positional-factory-name])
              :method-params
              first
              set))))