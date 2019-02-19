(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in assoc! contains? unchecked-get unchecked-set])
  (:require [clojure.string :as str]))

(def reflect-property 'js/goog.reflect.objectProperty)
(def reflect-contains? 'js/goog.reflect.canAccessProperty)

(defn dot-property-name [x]
  (str/replace (name x) #"^\.-" ""))

(defn wrap-key
  "Convert key to string at compile time when possible."
  [k]
  (cond
    (string? k) k
    (keyword? k) (name k)
    (symbol? k) (cond (= (:tag (meta k)) "String") k
                      (str/starts-with? (name k) ".-") `(~reflect-property ~(dot-property-name k) ~'applied-science.js-interop/_obj)
                      :else `(wrap-key ~k))
    :else `(wrap-key ~k)))

(defn wrap-keys-macro
  "Convert keys of path to strings at compile time where possible."
  [ks]
  (if (vector? ks)
    (mapv wrap-key ks)
    `(mapv wrap-key ~ks)))

(defn wrap-keys-js [ks]
  (if (vector? ks)
    `(~'cljs.core/array ~@(mapv wrap-key ks))
    `(~'applied-science.js-interop/wrap-keys-js ~ks)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defmacro get
  ([o k]
   `(~'goog.object/get ~o ~(wrap-key k)))
  ([o k not-found]
   `(~'goog.object/get ~o ~(wrap-key k) ~not-found)))

(defmacro get-in
  ([obj ks]
   `(get-in ~obj ~ks nil))
  ([obj ks not-found]
   `(~'applied-science.js-interop/get-in* ~obj ~(wrap-keys-js ks) ~not-found)))

(defn contains? [o k]
  `(~'goog.object/containsKey o ~(wrap-key k)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defn ^:private doto-pairs
  "Expands to an expression which calls `f` on `o` with
   successive pairs of arguments, returning `o`."
  [o f pairs]
  `(doto ~o
     ~@(loop [pairs (partition 2 pairs)
              out []]
         (if (empty? pairs)
           out
           (let [[k v] (first pairs)]
             (recur (rest pairs)
                    (conj out (f (wrap-key k) v))))))))

(defmacro assoc! [o & pairs]
  (doto-pairs `(or ~o (~'js-obj))
              (fn [k v]
                `(~'goog.object/set ~k ~v)) pairs))

(defmacro update! [obj k f & args]
  `(let [obj# (or ~obj (~'cljs.core/js-obj))
         k# ~(wrap-key k)
         v# (~'goog.object/get obj# k#)]
     (doto obj#
       (~'goog.object/set k# (~f v# ~@args)))))

(defmacro assoc-in! [obj ks v]
  `(~'applied-science.js-interop/assoc-in* ~obj ~(wrap-keys-macro ks) ~v))

(defmacro update-in! [obj ks f & args]
  `(~'applied-science.js-interop/update-in* ~obj ~(wrap-keys-macro ks) ~f ~@args))

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

(defmacro call [o k & args]
  `(let [^js f# (get ~o ~k)]
     (~'.call f# ~o ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro unchecked-get [o k]
  `(~'cljs.core/unchecked-get ~o ~(wrap-key k)))

(defmacro unchecked-set [o & pairs]
  (doto-pairs o
              (fn [k v]
                `(~'cljs.core/unchecked-set ~k ~v)) pairs))