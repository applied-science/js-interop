;; Some docstrings copied and/or adapted from ClojureScript, which is copyright (c) Rich Hickey.
;;   See https://github.com/clojure/clojurescript/blob/master/src/main/cljs/cljs/core.cljs

(ns applied-science.js-interop
  "Functions for working with JavaScript that mirror Clojure behaviour."
  (:refer-clojure :exclude [get get-in assoc! assoc-in! update! update-in! select-keys contains? unchecked-get unchecked-set])
  (:require [goog.object :as gobj]
            [cljs.core :as core])
  (:require-macros [applied-science.js-interop :as j]))

(defn wrap-key
  "Returns `k` or, if it is a keyword, its name."
  [k]
  (cond-> k
          (keyword? k) (name)))

(defn wrap-keys-js
  [ks]
  (reduce (fn [^js out k]
            (doto out
              (.push (wrap-key k)))) #js [] ks))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn get
  "Returns the value mapped to key, not-found or nil if key not present."
  ([o k]
   (j/get o k))
  ([o k not-found]
   (j/get o k not-found)))

(defn get-in*
  "Internal library use only. Mutates `key-arr`."
  ([obj ^js/Array key-arr]
   (when obj
     (gobj/getValueByKeys obj key-arr)))
  ([obj ^js/Array key-arr not-found]
   (let [last-k (.pop key-arr)]
     (if-some [last-obj (when obj
                          (gobj/getValueByKeys obj key-arr))]
       (gobj/get last-obj last-k not-found)
       not-found))))

(defn get-in
  "Returns the value in a nested object structure,
  where ks is a sequence of keys. Returns nil if the key is not present,
  or the not-found value if supplied."
  ([obj ks]
   (get-in* obj (wrap-keys-js ks)))
  ([obj ks not-found]
   (get-in* obj (wrap-keys-js ks) not-found)))

(deftype JSLookup [obj]
  ILookup
  (-lookup [_ k]
    (gobj/get obj (wrap-key k)))
  (-lookup [_ k not-found]
    (gobj/get obj (wrap-key k) not-found))
  IDeref
  (-deref [o] obj))

(defn lookup
  "Returns object which implements ILookup and reads keys from `obj`."
  [obj]
  (JSLookup. obj))

(defn contains? [o k]
  (gobj/containsKey o (wrap-key k)))

(defn select-keys
  "Returns an object containing only those entries in `o` whose key is in `ks`"
  [o ks]
  (reduce (fn [m k]
            (let [k (wrap-key k)]
              (cond-> m
                      (gobj/containsKey o k)
                      (doto
                        (core/unchecked-set k
                                            (gobj/get o k nil)))))) #js {} ks))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defn assoc!
  "Sets key-value pairs on `obj`, returns `obj`."
  [obj & pairs]
  (let [obj (or obj #js {})]
    (loop [[k v & more] pairs]
      (gobj/set obj (wrap-key k) v)
      (if (seq more)
        (recur more)
        obj))))

(defn ^:private get-in+!
  "Like `get-in` but creates new objects for empty levels."
  [obj ks]
  (loop [ks ks
         obj obj]
    (if (nil? ks)
      obj
      (recur
       (next ks)
       (or (let [k (first ks)
                 inner-obj (get obj k)]
             (if (some? inner-obj)
               inner-obj
               (let [inner-obj #js {}]
                 (core/unchecked-set obj k inner-obj)
                 inner-obj))))))))

(defn assoc-in!
  "Mutates the value in a nested object structure, where ks is a
  sequence of keys and v is the new value. If any levels do not
  exist, objects will be created."
  [obj ks v]
  (assert (> (count ks) 0)
          "assoc-in cannot accept an empty path")
  (let [obj (or obj #js {})
        ks (mapv wrap-key ks)
        inner-obj (get-in+! obj (butlast ks))]
    (gobj/set inner-obj (peek ks) v)
    obj))

(defn update!
  "'Updates' a value in a JavaScript object, where k is a key and
  f is a function that will take the old value and any supplied
  args and return the new value, which replaces the old value.
  If the key does not exist, nil is passed as the old value."
  [obj k f & args]
  (let [obj (or obj #js{})
        k (wrap-key k)
        v (gobj/get obj k)]
    (doto obj
      (gobj/set k (apply f (cons v args))))))

(defn update-in!
  "'Updates' a value in a nested object structure, where ks is a
  sequence of keys and f is a function that will take the old value
  and any supplied args and return the new value, mutating the
  nested structure.  If any levels do not exist, objects will be
  created."
  [obj ks f & args]
  (assert (> (count ks) 0)
          "assoc-in cannot accept an empty path")
  (let [obj (or obj #js {})
        ks (mapv wrap-key ks)
        val-at-path (.apply gobj/getValueByKeys nil (to-array (cons obj ks)))]
    (assoc-in! obj ks (apply f (cons val-at-path args)))))


;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defn push! [^js a v]
  (doto a
    (.push v)))

(defn unshift! [^js a v]
  (doto a
    (.unshift v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defn call [^js o k & args]
  (.apply (j/get o k) o (to-array args)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defn unchecked-set [obj k val]
  (core/unchecked-set obj (wrap-key k) val)
  obj)

(defn unchecked-get [o k]
  (core/unchecked-get o (wrap-key k)))