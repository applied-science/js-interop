(ns applied-science.js-interop.impl
  (:require [goog.object :as gobj]))

(def lookup-sentinel #js{})

(defn wrap-key
  "Returns `k` or, if it is a keyword, its name."
  [k]
  (cond-> k
          (keyword? k) (name)))

(defn ^boolean contains?* [obj k*]
  (gobj/containsKey obj k*))

(defn- get-value-by-keys
  "Look up `ks` in `obj`, stopping at any nil"
  [obj ks*]
  (when obj
    (let [end (count ks*)]
      (loop [i 0
             obj obj]
        (if (or (= i end)
                (nil? obj))
          obj
          (recur (inc i)
                 (unchecked-get obj (nth ks* i))))))))

(defn get-in*
  ([obj ks*]
   (get-value-by-keys obj ks*))
  ([obj ks* not-found]
   (if-some [last-obj (get-value-by-keys obj (butlast ks*))]
     (gobj/get last-obj (peek ks*) not-found)
     not-found)))

(defn select-keys*
  "Returns an object containing only those entries in `o` whose key is in `ks`"
  [obj ks*]
  (->> ks*
       (reduce (fn [m k]
                 (cond-> m
                         ^boolean (gobj/containsKey obj k)
                         (doto
                           (unchecked-set k
                                               (unchecked-get obj k))))) #js {})))
(defn assoc-in*
  [obj [k* & ks*] v]
  (let [obj (if (some? obj) obj #js{})]
    (unchecked-set obj k*
                        (if ks*
                          (assoc-in* (unchecked-get obj k*) ks* v)
                          v))
    obj))

(defn update-in*
  [obj ks* f args]
  (let [obj (if (some? obj) obj #js{})
        old-val (get-value-by-keys obj ks*)]
    (assoc-in* obj ks* (apply f old-val args))))