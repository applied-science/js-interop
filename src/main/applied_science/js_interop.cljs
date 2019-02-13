(ns applied-science.js-interop
  (:refer-clojure :exclude [get unchecked-get unchecked-set get-in assoc! assoc-in! update! update-in! select-keys contains?])
  (:require [goog.object :as gobj]
            [cljs.core :as core])
  (:require-macros [applied-science.js-interop :as j]))

(defn wrap-key [k]
  (cond-> k
    (keyword? k) (name)))

(defn get
  ([o k]
   (j/get o k))
  ([o k not-found]
   (j/get o k not-found)))

(defn get-in
  ([obj ks]
   (get-in obj ks nil))
  ([obj ks not-found]
   (let [ks (mapv wrap-key ks)
         last-obj (when obj
                    (.apply gobj/getValueByKeys nil
                            (doto (to-array (butlast ks))
                              (.unshift obj))))]
     (gobj/get last-obj (last ks) not-found))))

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

(defn select-keys [o ks]
  (reduce (fn [m k]
            (let [k (wrap-key k)]
              (cond-> m
                      (gobj/containsKey o k)
                      (doto
                        (core/unchecked-set k
                                            (gobj/get o k nil)))))) #js {} ks))

(defn assoc!
  [obj & pairs]
  (let [obj (or obj #js {})]
    (loop [[k v & more] pairs]
      (gobj/set obj (wrap-key k) v)
      (if (seq more)
        (recur more)
        obj))))

(defn ^:private get-in+ [obj ks]
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
  (assert (> (count ks) 0))
  (let [obj (or obj #js {})
        ks (mapv wrap-key ks)
        inner-obj (get-in+ obj (butlast ks))]
    (gobj/set inner-obj (last ks) v)
    obj))

(defn update!
  "'Updates' a value in a JavaScript object, where k is a key and
  f is a function that will take the old value and any supplied
  args and return the new value, which replaces the old value.
  If the key does not exist, nil is passed as the old value."
  [obj k f & args]
  (gobj/set obj (wrap-key k)
            (apply f (cons (gobj/get obj (wrap-key k)) args)))
  obj)

(defn update-in!
  "'Updates' a value in a nested object structure, where ks is a
  sequence of keys and f is a function that will take the old value
  and any supplied args and return the new value, mutating the
  nested structure.  If any levels do not exist, objects will be
  created."
  [obj ks f & args]
  (let [obj (or obj #js {})
        ks (mapv wrap-key ks)
        val-at-path (.apply gobj/getValueByKeys nil (to-array (cons obj ks)))]
    (assoc-in! obj ks (apply f (cons val-at-path args)))))

(defn push! [^js a v]
  (doto a
    (.push v)))

(defn unshift! [^js a v]
  (doto a
    (.unshift v)))

(defn contains? [o k]
  (gobj/containsKey o (wrap-key k)))

(defn call [^js o k & args]
  (.apply (j/get o k) o (to-array args)))

(defn unchecked-set [obj k val]
  (core/unchecked-set obj (wrap-key k) val)
  obj)

(defn unchecked-get [o k]
  (core/unchecked-get o (wrap-key k)))