(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc! unchecked-get unchecked-set apply])
  (:require [clojure.string :as str]
            [clojure.core :as core]))

(def reflect-property 'js/goog.reflect.objectProperty)

(def lookup-sentinel 'applied-science.js-interop/lookup-sentinel)

(def gobj-get 'goog.object/get)
(def gobj-set 'goog.object/set)
(def gobj-contains? 'goog.object/containsKey)

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
   (if (dot-sym? k)
     (let [o (gensym "obj")]
       `(let [~o ~obj]
          (if (~gobj-contains? ~o ~(wrap-key k o))
            (~(dot-get k) ~o)
            ~not-found)))
     `(~gobj-get ~obj ~(wrap-key k) ~not-found))))

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

(defn contains? [obj k]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       (~gobj-contains? ~o ~(wrap-key k o)))))

(defmacro select-keys [obj ks]
  (if (vector? ks)
    (let [o (gensym "obj")
          out (gensym "out")]
      `(let [~o ~obj
             ~out (~'cljs.core/js-obj)]
         ~@(for [k ks]
             `(when (~gobj-contains? ~o ~(wrap-key k o))
                (unchecked-set ~out ~k
                               (unchecked-get ~o ~k))))
         ~out))
    `(~'applied-science.js-interop/select-keys* ~obj ~(wrap-keys ks))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defmacro assoc! [obj & pairs]
  `(-> (or ~obj (~'cljs.core/js-obj))
       ~@(for [[k v] (partition 2 pairs)]
           `(unchecked-set ~k ~v))))

(defmacro update! [obj k f & args]
  (let [o (gensym "obj")]
    `(let [~o (or ~obj (~'cljs.core/js-obj))]
       (unchecked-set ~o ~k
                      (~f (unchecked-get ~o ~k) ~@args)))))

(defmacro assoc-in! [obj ks v]
  (if (vector? ks)
    (let [[k & ks] ks]
      (if ks
        (let [o (gensym "obj")]
          `(let [~o ~obj]
             (assoc! ~o ~k (assoc-in! (get ~o ~k) ~(vec ks) ~v))))
        `(assoc! ~obj ~k ~v)))
    `(~'applied-science.js-interop/assoc-in* ~obj ~(wrap-keys ks) ~v)))

(defmacro update-in! [obj ks f & args]
  (if (vector? ks)
    (let [o (gensym "obj")]
      `(let [~o (or ~obj (~'cljs.core/js-obj))]
         (assoc-in! ~o ~ks (~f (get-in ~o ~ks) ~@args))))
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
  `(-> (~'cljs.core/js-obj)
       ~@(for [[k v] (partition 2 keyvals)]
           `(assoc! ~k ~v))))
