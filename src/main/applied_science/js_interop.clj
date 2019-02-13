(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in assoc! contains? set!])
  (:require [clojure.core :as core]))

(defn wrap-key [k]
  (cond
    (string? k) k
    (keyword? k) (name k)
    (symbol? k) (if (= (:tag (meta k)) "String")
                  k
                  `(wrap-key ~k))
    :else `(wrap-key ~k)))

(defn wrap-path [p]
  (if (vector? p)
    (mapv wrap-key p)
    `(mapv wrap-key ~p)))

(defn- get*
  ([o k]
   (get* o k nil))
  ([o k not-found]
   `(~'goog.object/get ~o ~(wrap-key k) ~not-found)))

(defmacro get
  [& args]
  (apply get* args))

(defmacro !get [o k]
  `(core/unchecked-get ~o ~(wrap-key k)))

(comment

 ;; get-in needs to respect `not-found` behaviour of ILookup

 (defn- get-in*
   ([obj path]
    (get-in* obj path nil))
   ([obj path not-found]
    `(or ~(if (vector? path)
            `(~'goog.object/getValueByKeys (or ~obj (~'js-obj)) ~@(mapv wrap-key path))
            `(.apply ~'goog.object/getValueByKeys
                     nil
                     (to-array (cons (or ~obj (~'js-obj)) (map wrap-key ~path)))))
         ~not-found)))
 (defmacro get-in
   [& args]
   (apply get-in* args)))

(defn doto-pairs [o f pairs]
  `(doto (or ~o (~'js-obj))
     ~@(loop [pairs (partition 2 pairs)
              out []]
         (if (empty? pairs)
           out
           (let [[k v] (first pairs)]
             (recur (rest pairs)
                    (conj out (f (wrap-key k) v))))))))

(defmacro assoc! [o & pairs]
  (doto-pairs o (fn [k v]
                  `(~'goog.object/set ~k ~v)) pairs))

(defmacro unchecked-set [o & pairs]
  (doto-pairs o (fn [k v]
                  `(~'cljs.core/unchecked-set ~k ~v)) pairs))

(defn contains? [o k]
  `(~'goog.object/containsKey o ~(wrap-key k)))

(defmacro call [o k & args]
  `(let [^js f# (get ~o ~k)]
     (~'.call f# ~o ~@args)))

(defmacro push! [a v]
  `(doto ~a
     (~'.push ~v)))

(defmacro unshift! [arr v]
  `(doto ~arr
     (~'.unshift ~v)))

(defmacro then [promise arglist & body]
  `(~'.then ~'^js ~promise
    (fn ~arglist ~@body)))