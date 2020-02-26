(ns applied-science.js-interop.impl
  (:require-macros [applied-science.js-interop :as j]))

(def lookup-sentinel #js{})

(defn wrap-key
  "Returns `k` or, if it is a keyword, its name."
  [k]
  (cond-> k
          (keyword? k) (name)))

(defn ^boolean in?* [k* obj]
  (js-in k* obj))

(defn ^boolean contains?* [obj k*]
  (and (some? obj)
       (in?* k* obj)))

(defn- get+! [o k*]
  (if-some [child-obj (unchecked-get o k*)]
    child-obj
    (unchecked-set o k* #js{})))

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
     (let [k (peek ks*)]
       (if (js-in k last-obj)
         (j/unchecked-get last-obj k)
         not-found))
     not-found)))

(defn select-keys*
  "Returns an object containing only those entries in `o` whose key is in `ks`"
  [obj ks*]
  (->> ks*
       (reduce (fn [m k]
                 (cond-> m
                         ^boolean (contains?* obj k)
                         (doto
                           (unchecked-set k
                                          (unchecked-get obj k))))) #js {})))
(defn assoc-in*
  [obj ks* v]
  (let [obj (j/some-or obj #js{})
        inner-obj (reduce get+! obj (butlast ks*))]
    (unchecked-set inner-obj (peek ks*) v)
    obj))

(defn update-in*
  [obj ks* f args]
  (let [obj (j/some-or obj #js{})
        last-k* (peek ks*)
        inner-obj (reduce get+! obj (butlast ks*))
        old-val (unchecked-get inner-obj last-k*)]
    (unchecked-set inner-obj
                   last-k*
                   (apply f old-val args))
    obj))

(defn apply-in*
  [obj ks* arg-array]
  (let [parent (get-in* obj (pop ks*))
        f (unchecked-get parent (peek ks*))]
    (.apply f parent arg-array)))


