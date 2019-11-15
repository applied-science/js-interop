(ns applied-science.js-interop.inference
  (:require [cljs.analyzer :as ana]
            [cljs.env :as env]
            [clojure.string :as str]))

(def ^:dynamic *&env*
  "Bind to &env value within a macro definition"
  nil)

(defn infer-tag
  "Infers type of expr"
  ([expr]
   (infer-tag *&env* expr))
  ([env expr]
   (->> (ana/analyze env expr)
        ana/no-warn
        (ana/infer-tag env))))

(defn- resolve-tag [tag]
  (or (#{'js} tag)
      (ana/resolve-symbol tag)))

(defn- get-def [sym]
  (get-in @env/*compiler* [:cljs.analyzer/namespaces
                           (symbol (namespace sym))
                           :defs
                           (symbol (name sym))]))

(defn record-fields
  "Returns record fields for given type tag"
  [tag]
  (let [tag (resolve-tag tag)
        positional-factory (symbol (namespace tag)
                                   (str "->" (str/replace (name tag) #"^^" "")))]
    (some-> (get-def positional-factory)
            :method-params
            first
            set)))