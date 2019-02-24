(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc! unchecked-get unchecked-set apply])
  (:require [clojure.string :as str]
            [clojure.core :as core]))

(def reflect-property 'js/goog.reflect.objectProperty)

(def lookup-sentinel 'applied-science.js-interop/lookup-sentinel)

(def js-obj 'cljs.core/js-obj)

(defn BOOL [form]
  (vary-meta form assoc :tag 'boolean))

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

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Key conversion

(defn wrap-key
  "Convert key to string at compile time when possible."
  ([k]
   (wrap-key k nil))
  ([k obj]
   (cond
     (or (string? k)
         (number? k)) k
     (keyword? k) (name k)
     (symbol? k) (cond (= (:tag (meta k)) "String") k
                       (dot-sym? k) `(~reflect-property ~(dot-name k) ~obj)
                       :else `(wrap-key ~k))
     :else `(wrap-key ~k))))

(defn wrap-keys
  "Fallback to wrapping keys at runtime"
  [ks]
  `(mapv wrap-key ~ks))

(defn- contains? [o wrapped-k]
  (assert (symbol? o))
  (BOOL `(~'goog.object/containsKey ~o ~wrapped-k)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro unchecked-get [obj k]
  (if (dot-sym? k)
    `(~(dot-get k) ~obj)
    `(~'cljs.core/unchecked-get ~obj ~(wrap-key k))))

(defmacro unchecked-set [obj & pairs]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       ~@(for [[k v] (partition 2 pairs)]
           (if (dot-sym? k)
             `(set! (~(dot-get k) ~o) ~v)
             `(~'cljs.core/unchecked-set ~o ~(wrap-key k) ~v)))
       ~o)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn- get*
  ([obj k]
   (get* obj k nil))
  ([obj k not-found]
   (let [o (gensym "obj")
         k-sym (gensym "k")]
     `(let [~o ~obj
            ~k-sym ~(wrap-key k o)]
        (if ~(contains? o k-sym)
          ~(if (dot-sym? k)
             `(~(dot-get k) ~obj)
             `(~'cljs.core/unchecked-get ~obj ~k-sym))
          ~not-found)))))

(defmacro get
  ([obj k]
   (get* obj k))
  ([obj k not-found]
   (get* obj k not-found)))

(defmacro get-in
  ([obj ks]
   (reduce get* obj ks))
  ([obj ks not-found]
   (if (vector? ks)
     (let [o (gensym "obj")
           sentinel (gensym "sent")]
       `(let [~sentinel ~lookup-sentinel
              out# ~(reduce
                      (fn [out k]
                        `(let [out# ~out]
                           (if (identical? out# ~sentinel)
                             ~sentinel
                             (get out# ~k ~sentinel)))) obj ks)]
          (if (= ~sentinel out#)
            ~not-found
            out#)))
     `(~'applied-science.js-interop/get-in* ~obj ~(wrap-keys ks) ~not-found))))

(defmacro select-keys [obj ks]
  (if (vector? ks)
    (let [o (gensym "obj")
          out (gensym "out")]
      `(let [~o ~obj
             ~out (~js-obj)]
         ~@(for [k ks]
             `(when ~(BOOL (contains? o (wrap-key k o)))
                (unchecked-set ~out ~k
                               (unchecked-get ~o ~k))))
         ~out))
    `(~'applied-science.js-interop/select-keys* ~obj ~(wrap-keys ks))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defmacro assoc! [obj & pairs]
  `(-> (or ~obj (~js-obj))
       ~@(for [[k v] (partition 2 pairs)]
           `(unchecked-set ~k ~v))))

(defn- get+! [o k]
  `(or (unchecked-get ~o ~k)
       (let [new-o# (~js-obj)]
         (unchecked-set ~o ~k new-o#)
         new-o#)))

(defmacro assoc-in! [obj ks v]
  (if (vector? ks)
    (let [[k & more-ks] ks]
      (if more-ks
        (let [o (gensym "obj")
              inner-obj (gensym "i-obj")]
          `(let [~o (or ~obj (~js-obj))
                 ~inner-obj ~(reduce get+! o (drop-last ks))]
             (unchecked-set ~inner-obj ~(last ks) ~v)
             ~o))
        `(assoc! ~obj ~k ~v)))
    `(~'applied-science.js-interop/assoc-in* ~obj ~(wrap-keys ks) ~v)))

(defmacro update! [obj k f & args]
  (let [o (gensym "obj")]
    `(let [~o (or ~obj (~js-obj))]
       (unchecked-set ~o ~k
                      (~f (unchecked-get ~o ~k) ~@args)))))

(defmacro update-in! [obj ks f & args]
  (if (vector? ks)
    (let [[k & more-ks] ks
          o (gensym "obj")
          inner-obj (gensym "i-obj")]
      (if more-ks
        `(let [~o (or ~obj (~js-obj))
               ~inner-obj ~(reduce get+! o (drop-last ks))
               v# (~f (get ~inner-obj ~(last ks)) ~@args)]
           (unchecked-set ~inner-obj ~(last ks) v#)
           ~o)
        `(update! ~obj ~k ~f ~@args)))
    `(~'applied-science.js-interop/update-in* ~obj ~(wrap-keys ks) ~f ~(vec args))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defmacro push! [a v]
  `(doto ~a
     (~'.push ~v)))

(defmacro unshift! [a v]
  `(doto ~a
     (~'.unshift ~v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defmacro call [obj k & args]
  `(let [obj# ~obj
         f# (get obj# ~k)]
     (.call f# obj# ~@args)))

(defmacro apply [obj k args]
  `(let [obj# ~obj
         f# (get obj# ~k)]
     (.apply f# obj# ~args)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Object creation

(defmacro obj
  [& keyvals]
  `(-> (~js-obj)
       ~@(for [[k v] (partition 2 keyvals)]
           `(assoc! ~k ~v))))
