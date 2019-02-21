(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc! unchecked-get unchecked-set apply])
  (:require [clojure.string :as str]))

(def reflect-property 'js/goog.reflect.objectProperty)
(def reflect-contains? 'js/goog.reflect.canAccessProperty)

(def gobj-get 'goog.object/get)
(def gobj-set 'goog.object/set)
(def gobj-contains? 'goog.object/containsKey)

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Host-key utils

(defn- dot-field? [k]
  (str/starts-with? (name k) ".-"))

(defn- dot-sym? [k]
  (str/starts-with? (name k) "."))

(defn- dot-name [sym]
  (str/replace (name sym) #"^\.\-?" ""))

(defn- dot-get [sym]
  (symbol (str ".-" (dot-name sym))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Key conversion

(defn wrap-key
  "Convert key to string at compile time when possible."
  ([k] (wrap-key k 'applied-science.js-interop/reflection-stub))
  ([k obj]
   (cond
     (string? k) k
     (keyword? k) (name k)
     (symbol? k) (cond (= (:tag (meta k)) "String") k
                       (dot-sym? k) `(~reflect-property ~(dot-name k) ~obj)
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

(defn- doto-pairs
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

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro unchecked-get [obj k]
  `(~'cljs.core/unchecked-get ~obj ~(wrap-key k)))

(defmacro unchecked-set [obj & pairs]
  (doto-pairs obj
              (fn [k v]
                `(~'cljs.core/unchecked-set ~k ~v)) pairs))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn- wrapped-get
  ([obj k]
   (wrapped-get obj k nil))
  ([obj k not-found]
   `(if (and (some? ~obj)
             (~'cljs.core/js-in ~(wrap-key k obj) ~obj))
      ~(if (dot-sym? k)
         `(~(dot-get k) ~obj)
         `(unchecked-get ~obj ~k))
      ~not-found)))

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

(defmacro assoc! [obj & pairs]
  (doto-pairs `(or ~obj (~'js-obj))
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
