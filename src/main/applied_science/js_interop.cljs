;; Some docstrings copied and/or adapted from ClojureScript, which is copyright (c) Rich Hickey.
;;   See https://github.com/clojure/clojurescript/blob/master/src/main/cljs/cljs/core.cljs

(ns applied-science.js-interop
  "A JavaScript-interop library for ClojureScript."
  (:refer-clojure :exclude [get get-in assoc! assoc-in! update! update-in! select-keys contains? unchecked-get unchecked-set apply])
  (:require [goog.reflect :as reflect]
            [cljs.core :as core]
            [applied-science.js-interop.impl :as impl])
  (:require-macros [applied-science.js-interop :as j]))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defn unchecked-set [obj & keyvals]
  (loop [[k v & keyvals] keyvals]
    (core/unchecked-set obj (impl/wrap-key k) v)
    (when keyvals
      (recur keyvals)))
  obj)

(defn unchecked-get [obj k]
  (core/unchecked-get obj (impl/wrap-key k)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn get
  "Returns the value mapped to key, not-found or nil if key not present.

  ```
  (j/get o :k)
  (j/get o .-k)
  ```"
  ([obj k]
   (j/get obj k))
  ([obj k not-found]
   (j/get obj k not-found)))

(defn get-in
  "Returns the value in a nested object structure, where ks is
   a sequence of keys. Returns nil if the key is not present,
   or the not-found value if supplied.

   ```
   (j/get-in o [:x :y] :fallback-value)
   (j/get-in o [.-x .-y] :fallback-value)
   ```"
  ([obj ks]
   (impl/get-in* obj (mapv impl/wrap-key ks)))
  ([obj ks not-found]
   (impl/get-in* obj (mapv impl/wrap-key ks) not-found)))

(defn ^boolean contains?
  "Returns true if `obj` contains `k`.

  ```
  (j/contains? o :k)
  (j/contains? o .-k)
  ```"
  [obj k]
  (impl/contains?* obj (impl/wrap-key k)))

(defn select-keys
  "Returns an object containing only those entries in `o` whose key is in `ks`.

  ```
  (j/select-keys o [:a :b :c])
  (j/select-keys o [.-a .-b .-c])
  ```"
  [obj ks]
  (impl/select-keys* obj (mapv impl/wrap-key ks)))

(deftype ^:no-doc JSLookup [obj]
  ILookup
  (-lookup [_ k]
    (j/get obj k))
  (-lookup [_ k not-found]
    (j/get obj k not-found))
  IDeref
  (-deref [o] obj))

(defn lookup
  "Wraps `obj` with an ILookup implementation, to support reading/destructuring. Does not support renamable keys.

  ```
  (let [{:keys [a b c]} (j/lookup o)]
   ...)
  ```"
  [obj]
  (when obj
    (JSLookup. obj)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defn assoc!
  "Sets key-value pairs on `obj`, returns `obj`.

  ```
  (j/assoc! o :x 10)
  (j/assoc! o .-x 10)
  ```"
  [obj & keyvals]
  (let [obj (if (some? obj) obj #js{})]
    (loop [[k v & kvs] keyvals]
      (unchecked-set obj k v)
      (if kvs
        (recur kvs)
        obj))))

(defn assoc-in!
  "Mutates the value in a nested object structure, where ks is a
  sequence of keys and v is the new value. If any levels do not
  exist, objects will be created.

  ```
  (j/assoc-in! o [:x :y] 10)
  (j/assoc-in! o [.-x .-y] 10)
  ```"
  [obj ks v]
  (impl/assoc-in* obj (mapv impl/wrap-key ks) v))

(defn update!
  "'Updates' a value in a JavaScript object, where k is a key and
  f is a function that will take the old value and any supplied
  args and return the new value, which replaces the old value.
  If the key does not exist, nil is passed as the old value.

  ```
  (j/update! o :a + 10)
  (j/update! o .-a + 10)
  ```"
  [obj k f & args]
  (let [obj (if (some? obj) obj #js{})
        k* (impl/wrap-key k)
        v (core/apply f (core/unchecked-get obj k*) args)]
    (core/unchecked-set obj k* v)
    obj))

(defn update-in!
  "'Updates' a value in a nested object structure, where ks is a
  sequence of keys and f is a function that will take the old value
  and any supplied args and return the new value, mutating the
  nested structure.  If any levels do not exist, objects will be
  created.

  ```
  (j/update-in! o [:x :y] + 10)
  (j/update-in! o [.-x .-y] + 10)
  ```"
  [obj ks f & args]
  (impl/update-in* obj (mapv impl/wrap-key ks) f args))

(defn extend!
  "Extends `obj` with the properties of one or more objects, overwriting
   existing properties, moving left to right. Returns `obj`.
   An empty starting object is provided if `obj` is nil.
  ```
  (j/extend o other)
  (j/extend o other #js{:x 1})
  ```
  Not IE6-friendly"
  ([obj] obj)
  ([obj x]
   (let [obj (j/some-or obj #js{})]
     (when (some? x)
       (doseq [k (js-keys x)]
         (unchecked-set obj k (unchecked-get x k))))
     obj))
  ([obj x & more]
   (reduce extend! (extend! obj x) more)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defn push!
  "Appends `v` to `array` and returns the mutated array.

  ```
  (j/push! arr 10)
  ```"
  [^js array x]
  (doto array
    (.push x)))

(defn unshift!
  "Prepends `v` to `a` and returns the mutated array.

  ```
  (j/unshift! arr 10)
  ```"
  [^js array x]
  (doto array
    (.unshift x)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defn call
  "Call function `k` of `obj`, binding `this` to `obj`.

  ```
  (j/call o :someFunction arg1 arg2)
  (j/call o .-someFunction arg1 arg2)
  ```"
  [obj k & args]
  (.apply (j/get obj k) obj (to-array args)))

(defn apply
  "Apply function `k` of `obj`, binding `this` to `obj`.

  ```
  (j/apply o :someFunction #js [arg1 arg2])
  (j/apply o .-someFunction #js [arg1 arg2])
  ```"
  [obj k arg-array]
  (.apply (j/get obj k) obj arg-array))

(defn call-in
  "Call function nested at `path` with `args`, binding `this` to its parent object.

  ```
  (j/call-in o [:x :someFunction] arg1 arg2)
  ```"
  [obj ks & args]
  (impl/apply-in* obj (mapv impl/wrap-key ks) (to-array args)))

(defn apply-in
  "Apply function nested at `path` with `arg-array`, binding `this` to its parent object.

  ```
  (j/apply-in o [:x :someFunction] arg1 arg2)
  ```"
  [obj ks arg-array]
  (impl/apply-in* obj (mapv impl/wrap-key ks) arg-array))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Object creation

(defn obj
  "Create JavaScript object from an even number arguments representing
   interleaved keys and values.

   ```
   (obj :a 1 :b 2 .-c 3 .-d 4)
   ```"
  [& keyvals]
  (let [obj (js-obj)]
    (doseq [[k v] (partition 2 keyvals)]
      (j/assoc! obj k v))
    obj))
