(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc! unchecked-get unchecked-set apply extend])
  (:require [clojure.core :as core]
            [cljs.compiler :as comp]
            [cljs.analyzer :as ana]
            [clojure.walk :as walk]
            [clojure.string :as str]
            [applied-science.js-interop.inference :as inf]))

(def ^:private reflect-property 'js/goog.reflect.objectProperty)
(def ^:private lookup-sentinel 'applied-science.js-interop.impl/lookup-sentinel)
(def ^:private contains?* 'applied-science.js-interop.impl/contains?*)
(def ^:private wrap-key* 'applied-science.js-interop.impl/wrap-key)
(def ^:private empty-obj '(cljs.core/js-obj))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Host-key utils

(defn- dot-sym? [k]
  (and (symbol? k)
       (str/starts-with? (name k) ".")))

(defn- dot-name [sym]
  (str/replace (name sym) #"^\.\-?" ""))

(defn- dot-get [sym]
  (symbol (str ".-" (dot-name sym))))

(defn- dot-call [sym]
  (symbol (str "." (dot-name sym))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Key conversion
;;
;; Throughout this namespace, k* and ks* refer to keys that have already been wrapped.


(defn- wrap-key
  "Convert key to string at compile time when possible."
  ([env k]
   (wrap-key env k nil))
  ([env k obj]
   (cond
     (or (string? k)
         (number? k)) k
     (keyword? k) (name k)
     (or (list? k)
         (symbol? k)) (let [tag (inf/infer-tag env k)]
                        (cond (#{'string 'String} tag) k
                              (= 'keyword tag) `(name ~k)
                              (dot-sym? k) `(~reflect-property ~(comp/munge (dot-name k)) ~obj)

                              :else `(~wrap-key* ~k)))
     :else `(~wrap-key* ~k))))

(comment
  (defmacro inferred-type [k]
    (str (inf/infer-tag &env k))))

(defn- wrap-keys
  "Fallback to wrapping keys at runtime"
  [ks]
  `(mapv ~wrap-key* ~ks))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro unchecked-get [obj k]
  (if (dot-sym? k)
    `(~(dot-get k) ~obj)
    `(~'cljs.core/unchecked-get ~obj ~(wrap-key &env k))))

(defmacro !get [obj k]
  `(applied-science.js-interop/unchecked-get ~obj ~k))

(defmacro unchecked-set [obj & keyvals]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       ~@(for [[k v] (partition 2 keyvals)]
           (if (dot-sym? k)
             `(set! (~(dot-get k) ~o) ~v)
             `(~'cljs.core/unchecked-set ~o ~(wrap-key &env k) ~v)))
       ~o)))

(defmacro !set [obj & keyvals]
  `(applied-science.js-interop/unchecked-set ~obj ~@keyvals))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn- get*
  ([env obj k]
   (get* env obj k 'js/undefined))
  ([env obj k not-found]
   (let [o (gensym "obj")
         k-sym (gensym "k")]
     `(let [~o ~obj
            ~k-sym ~(wrap-key env k o)]
        (if (~contains?* ~o ~k-sym)
          ~(if (dot-sym? k)
             `(~(dot-get k) ~o)
             `(~'cljs.core/unchecked-get ~o ~k-sym))
          ~not-found)))))

(defmacro get
  ([obj k]
   (get* &env obj k))
  ([obj k not-found]
   (get* &env obj k not-found)))

(defmacro get-in
  ([obj ks]
   (reduce (partial get* &env) obj ks))
  ([obj ks not-found]
   (if (vector? ks)
     `(let [out# ~(reduce
                    (fn [out k]
                      `(let [out# ~out]
                         (if (identical? out# ~lookup-sentinel)
                           ~lookup-sentinel
                           (get out# ~k ~lookup-sentinel)))) obj ks)]
        (if (= ~lookup-sentinel out#)
          ~not-found
          out#))
     `(~'applied-science.js-interop.impl/get-in* ~obj ~(wrap-keys ks) ~not-found))))

(defmacro !get-in
  [obj ks]
  (reduce (fn [out k] `(!get ~out ~k)) obj ks))

(defmacro contains?
  [obj k]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       (~contains?* ~o ~(wrap-key &env k o)))))

(defmacro select-keys [obj ks]
  (if (vector? ks)
    (let [o (gensym "obj")
          out (gensym "out")]
      `(let [~o ~obj
             ~out ~empty-obj]
         ~@(for [k ks]
             `(when (~contains?* ~o ~(wrap-key &env k o))
                (unchecked-set ~out ~k
                               (unchecked-get ~o ~k))))
         ~out))
    `(~'applied-science.js-interop.impl/select-keys* ~obj ~(wrap-keys ks))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

;; helpers

(defmacro some-or
  "Like `or` but switches on `some?` instead of truthiness."
  [x y]
  `(if (some? ~x) ~x ~y))

(defn- get+!
  "Returns `k` of `o`. If nil, sets and returns a new empty child object."
  [o k]
  (let [child (gensym "child")]
    `(let [~child (unchecked-get ~o ~k)]
       (some-or ~child
                (let [new-child# ~empty-obj]
                  (unchecked-set ~o ~k new-child#)
                  new-child#)))))

(defn- get-in+!
  [o ks]
  (reduce get+! o ks))

;; core operations

(defmacro assoc! [obj & keyvals]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       (-> (some-or ~o ~empty-obj)
           ~@(for [[k v] (partition 2 keyvals)]
               `(unchecked-set ~k ~v))))))

(defmacro assoc-in! [obj ks v]
  (if (vector? ks)
    (let [o (gensym "obj")]
      `(let [~o ~obj
             ~o (some-or ~o ~empty-obj)
             inner-obj# ~(get-in+! o (drop-last ks))]
         (unchecked-set inner-obj# ~(last ks) ~v)
         ~o))
    `(~'applied-science.js-interop.impl/assoc-in* ~obj ~(wrap-keys ks) ~v)))

(defmacro update! [obj k f & args]
  (let [o (gensym "obj")]
    `(let [~o ~obj
           ~o (some-or ~o ~empty-obj)]
       (unchecked-set ~o ~k
                      (~f (unchecked-get ~o ~k) ~@args)))))

(defmacro update-in! [obj ks f & args]
  (if (vector? ks)
    (let [o (gensym "obj")]
      `(let [~o ~obj
             ~o (some-or ~o ~empty-obj)
             inner-obj# ~(get-in+! o (drop-last ks))]
         (update! inner-obj# ~(last ks) ~f ~@args)
         ~o))
    `(~'applied-science.js-interop.impl/update-in* ~obj ~(wrap-keys ks) ~f ~(vec args))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defmacro push! [array v]
  (let [sym (with-meta (gensym "array") {:tag 'js/Array})]
    `(let [~sym ~array]
       (~'.push ~sym ~v)
       ~sym)))

(defmacro unshift! [array v]
  `(doto ~array
     (~'.unshift ~v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defmacro call [obj k & args]
  (if (dot-sym? k)
    `(~(dot-call k) ~obj ~@args)
    `(let [obj# ~obj
           ^function f# (!get obj# ~k)]
       (.call f# obj# ~@args))))

(defmacro call-in [obj ks & args]
  (if (vector? ks)
    `(let [parent# (!get-in ~obj ~(pop ks))
           ^function f# (!get parent# ~(peek ks))]
       (.call f# parent# ~@args))
    `(~'applied-science.js-interop.impl/apply-in* ~obj ~(wrap-keys ks) (cljs.core/array ~@args))))

(defmacro apply [obj k arg-array]
  `(let [obj# ~obj
         ^function f# (!get obj# ~k)]
     (.apply f# obj# ~arg-array)))

(defmacro apply-in [obj ks arg-array]
  (if (vector? ks)
    `(let [parent# (!get-in ~obj ~(pop ks))
           ^function f# (!get parent# ~(peek ks))]
       (.apply f# parent# ~arg-array))
    `(~'applied-science.js-interop.impl/apply-in* ~obj ~(wrap-keys ks) ~arg-array)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Object creation

(defn- literal-obj
  [keyvals]
  (let [keyvals-str (str "({" (->> (map (fn [[k _]]
                                          (str (if (dot-sym? k)
                                                 (comp/munge (dot-name k)) ;; without quotes, can be renamed by compiler
                                                 (str \" (name k) \"))
                                               ":~{}")) keyvals)
                                   (str/join ",")) "})")]
    (vary-meta (list* 'js* keyvals-str (map second keyvals))
               assoc :tag 'object)))

(defmacro obj
  [& keyvals]
  (let [kvs (partition 2 keyvals)]
    (if (every? #(or (keyword? %)
                     (string? %)
                     (dot-sym? %)) (map first kvs))
      (literal-obj kvs)
      `(-> ~empty-obj
           ~@(for [[k v] kvs]
               `(unchecked-set ~k ~v))))))

;; Nested literals (maps/vectors become objects/arrays)

(defmacro lit
  "Returns literal JS forms for Clojure maps (->objects) and vectors (->arrays)."
  [form]
  (walk/prewalk
    (fn [x]
      (cond (map? x)
            (list* 'applied-science.js-interop/obj
                   (core/apply concat x))
            (vector? x)
            (list* 'cljs.core/array x)
            :else x))
    form))
