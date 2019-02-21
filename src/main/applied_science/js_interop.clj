(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc! unchecked-get unchecked-set])
  (:require [clojure.string :as str]))

(def reflect-property 'js/goog.reflect.objectProperty)
(def reflect-contains? 'js/goog.reflect.canAccessProperty)

(def gobj-get 'goog.object/get)
(def gobj-set 'goog.object/set)
(def gobj-contains? 'goog.object/containsKey)

(defn dot-property-name [x]
  (str/replace (name x) #"^\.-" ""))

(defn wrap-key
  "Convert key to string at compile time when possible."
  ([k] (wrap-key k 'applied-science.js-interop/_obj))
  ([k obj]
   (cond
     (string? k) k
     (keyword? k) (name k)
     (symbol? k) (cond (= (:tag (meta k)) "String") k
                       (str/starts-with? (name k) ".-") `(~reflect-property ~(dot-property-name k) ~obj)
                       :else `(wrap-key ~k))
     :else `(wrap-key ~k))))

(defn wrap-keys->vec
  "Convert keys of path to strings at compile time where possible."
  [ks]
  (if (vector? ks)
    (mapv wrap-key ks)
    `(mapv wrap-key ~ks)))

(defn wrap-keys->array [ks]
  (if (vector? ks)
    `(~'cljs.core/array ~@(mapv wrap-key ks))
    `(~'applied-science.js-interop/wrap-keys->array ~ks)))

(defn wrapped-get
  ([obj k]
   (wrapped-get obj k nil))
  ([obj k not-found]
   `(~gobj-get ~obj ~(wrap-key k obj) ~not-found)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defmacro get
  ([obj k]
   (let [o (gensym "obj")]
     `(let [~o ~obj]
        ~(wrapped-get o k))))
  ([obj k not-found]
   (let [o (gensym "obj")]
     `(let [~o ~obj]
        ~(wrapped-get o k not-found)))))

(defmacro get-in
  ([obj ks]
   `(get-in ~obj ~ks nil))
  ([obj ks not-found]
   `(~'applied-science.js-interop/get-in* ~obj ~(wrap-keys->array ks) ~not-found)))

(defn contains? [obj k]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       (~gobj-contains? ~o ~(wrap-key k o)))))

(defmacro select-keys [obj ks]
  (if (vector? ks)
    (let [o (gensym "obj")]
      `(let [~o ~obj]
         (~'applied-science.js-interop/select-keys* ~o ~(mapv #(wrap-key % o) ks))))
    `(~'applied-science.js-interop/select-keys* ~obj ~(wrap-keys->vec ks))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defn ^:private doto-pairs
  "Expands to an expression which calls `f` on `o` with
   successive pairs of arguments, returning `o`."
  [obj f pairs]
  (let [o (gensym "obj")]
    `(let [~o ~obj]
       (doto ~o
         ~@(loop [pairs (partition 2 pairs)
                  out []]
             (if (empty? pairs)
               out
               (let [[k v] (first pairs)]
                 (recur (rest pairs)
                        (conj out (f (wrap-key k o) v))))))))))

(defmacro assoc! [o & pairs]
  (doto-pairs `(or ~o (~'js-obj))
              (fn [k v]
                `(~gobj-set ~k ~v)) pairs))

(defmacro update! [obj k f & args]
  (let [o (gensym "obj")]
    `(let [~o (or ~obj (~'cljs.core/js-obj))
           k# ~(wrap-key k o)
           v# (~gobj-get ~o k#)]
       (doto ~o
         (~gobj-set k# (~f v# ~@args))))))

(defmacro assoc-in! [obj ks v]
  `(~'applied-science.js-interop/assoc-in* ~obj ~(wrap-keys->vec ks) ~v))

(defmacro update-in! [obj ks f & args]
  `(~'applied-science.js-interop/update-in* ~obj ~(wrap-keys->vec ks) ~f ~@args))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defmacro push! [a v]
  `(doto ~a
     (~'.push ~v)))

(defmacro unshift! [arr v]
  `(doto ~arr
     (~'.unshift ~v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defmacro call [obj k & args]
  `(let [obj# ~obj
         ^js f# (get obj# ~k)]
     (~'.call f# obj# ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro unchecked-get [o k]
  `(~'cljs.core/unchecked-get ~o ~(wrap-key k)))

(defmacro unchecked-set [o & pairs]
  (doto-pairs o
              (fn [k v]
                `(~'cljs.core/unchecked-set ~k ~v)) pairs))