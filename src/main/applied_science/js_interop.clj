(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in assoc! contains? set!])
  (:require [clojure.core :as core]))

(defn wrap-key
  "Convert key to string at compile time when possible."
  [k]
  (cond
    (string? k) k
    (keyword? k) (name k)
    (symbol? k) (if (= (:tag (meta k)) "String")
                  k
                  `(wrap-key ~k))
    :else `(wrap-key ~k)))

(defn wrap-path
  "Convert keys of path to strings at compile time where possible."
  [path]
  (if (vector? path)
    (mapv wrap-key path)
    `(mapv wrap-key ~path)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defmacro get
  ([o k]
   `(get ~o ~k nil))
  ([o k not-found]
   `(~'goog.object/get ~o ~(wrap-key k) ~not-found)))

(defmacro get-in
  ([obj path]
   `(get-in ~obj ~path nil))
  ([obj ks not-found]
   `(let [obj# ~obj
          ks# ~(wrap-path ks)
          last-obj# (when obj#
                      (~'.apply ~'goog.object/getValueByKeys nil
                       (doto (to-array (butlast ks#))
                         (.unshift obj#))))]
      (~'goog.object/get last-obj# (last ks#) ~not-found))))

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

;; TODO
;; - assoc-in!
;; - update-in!

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