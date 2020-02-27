(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc!
                            unchecked-get unchecked-set apply extend
                            let fn defn])
  (:require [clojure.core :as core]
            [cljs.compiler :as comp]
            [clojure.string :as str]
            [applied-science.js-interop.destructure :as d]))

(def ^:private reflect-property 'js/goog.reflect.objectProperty)
(def ^:private lookup-sentinel 'applied-science.js-interop.impl/lookup-sentinel)
(def ^:private contains?* 'applied-science.js-interop.impl/contains?*)
(def ^:private in?* 'applied-science.js-interop.impl/in?*)
(def ^:private wrap-key* 'applied-science.js-interop.impl/wrap-key)
(def ^:private empty-obj '(cljs.core/js-obj))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Host-key utils

(defn- dot-sym? [k]
  (and (symbol? k)
       (str/starts-with? (name k) ".")))

(defn- dot-name [sym]
  (str/replace (name sym) #"^\.\-?" ""))

(defn- dot-get [sym]
  (symbol (str ".-" (dot-name sym))))

(defn- dot-call [sym]
  (symbol (str "." (dot-name sym))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Key conversion
;;
;; Throughout this namespace, k* and ks* refer to keys that have already been wrapped.


(defn- wrap-key
  "Convert key to string at compile time when possible."
  ([k]
   (wrap-key k nil))
  ([k obj]
   (cond
     (or (string? k)
         (number? k)) k
     (keyword? k) (name k)
     (symbol? k) (cond (= (:tag (meta k)) "String") k
                       (dot-sym? k) ^::wrapped-key `(~reflect-property ~(comp/munge (dot-name k)) ~obj)
                       :else `(~wrap-key* ~k))
     (and (seq? k)
          (::wrapped-key (meta k))) k
     :else `(~wrap-key* ~k))))

(defn- wrap-keys
  "Fallback to wrapping keys at runtime"
  [ks]
  `(mapv ~wrap-key* ~ks))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro unchecked-get
  ([obj k]
   (if (dot-sym? k)
     `(~(dot-get k) ~obj)
     `(~'cljs.core/unchecked-get ~obj ~(wrap-key k))))
  ([obj k not-found]
   (core/let [o (gensym "obj")
              k-sym (gensym "k")]
     `(core/let [~o ~obj
                 ~k-sym ~(wrap-key k o)]
        (if (~in?* ~k-sym ~o)
          (unchecked-get ~o ~k-sym)
          ~not-found)))))

(defmacro !get [& args]
  `(unchecked-get ~@args))

(defmacro unchecked-set [obj & keyvals]
  (core/let [o (gensym "obj")]
    `(core/let [~o ~obj]
       ~@(for [[k v] (partition 2 keyvals)]
           (if (dot-sym? k)
             `(set! (~(dot-get k) ~o) ~v)
             `(~'cljs.core/unchecked-set ~o ~(wrap-key k) ~v)))
       ~o)))

(defmacro !set [obj & keyvals]
  `(applied-science.js-interop/unchecked-set ~obj ~@keyvals))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn- get*
  ([obj k]
   (get* obj k 'js/undefined))
  ([obj k not-found]
   (core/let [o (gensym "obj")
              k-sym (gensym "k")]
     `(core/let [~o ~obj
                 ~k-sym ~(wrap-key k o)]
        (if (some->> ~o (~in?* ~k-sym))
          (!get ~o ~k-sym)
          ~not-found)))))

(defmacro get
  ([obj k]
   (get* obj k))
  ([obj k not-found]
   (get* obj k not-found)))

(defmacro get-in
  ([obj ks]
   (reduce get* obj ks))
  ([obj ks not-found]
   (if (vector? ks)
     `(core/let [out# ~(reduce
                         (core/fn [out k]
                           `(core/let [out# ~out]
                              (if (identical? out# ~lookup-sentinel)
                                ~lookup-sentinel
                                (get out# ~k ~lookup-sentinel)))) obj ks)]
        (if (= ~lookup-sentinel out#)
          ~not-found
          out#))
     `(~'applied-science.js-interop.impl/get-in* ~obj ~(wrap-keys ks) ~not-found))))

(defmacro !get-in
  [obj ks]
  (reduce (core/fn [out k] `(!get ~out ~k)) obj ks))

(defmacro contains?
  [obj k]
  (core/let [o (gensym "obj")]
    `(core/let [~o ~obj]
       (some->> ~o (~in?* ~(wrap-key k o))))))

(defmacro select-keys [obj ks]
  (if (vector? ks)
    (core/let [o (gensym "obj")
               out (gensym "out")]
      `(core/let [~o ~obj
                  ~out ~empty-obj]
         ~@(for [k ks]
             `(when (some->> ~o (~in?* ~(wrap-key k o)))
                (!set ~out ~k (!get ~o ~k))))
         ~out))
    `(~'applied-science.js-interop.impl/select-keys* ~obj ~(wrap-keys ks))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

;; helpers

(defmacro some-or
  "Like `or` but switches on `some?` instead of truthiness."
  [x y]
  `(if (some? ~x) ~x ~y))

(defn- get+!
  "Returns `k` of `o`. If nil, sets and returns a new empty child object."
  [o k]
  (core/let [child (gensym "child")]
    `(core/let [~child (!get ~o ~k)]
       (some-or ~child
                (core/let [new-child# ~empty-obj]
                  (!set ~o ~k new-child#)
                  new-child#)))))

(defn- get-in+!
  [o ks]
  (reduce get+! o ks))

;; core operations

(defmacro assoc! [obj & keyvals]
  (core/let [o (gensym "obj")]
    `(core/let [~o ~obj]
       (-> (some-or ~o ~empty-obj)
           ~@(for [[k v] (partition 2 keyvals)]
               `(!set ~k ~v))))))

(defmacro assoc-in! [obj ks v]
  (if (vector? ks)
    (core/let [o (gensym "obj")]
      `(core/let [~o ~obj
                  ~o (some-or ~o ~empty-obj)]
         (!set ~(get-in+! o (drop-last ks)) ~(last ks) ~v)
         ~o))
    `(~'applied-science.js-interop.impl/assoc-in* ~obj ~(wrap-keys ks) ~v)))

(defmacro !assoc-in! [obj ks v]
  `(core/let [obj# ~obj]
     (-> (!get-in obj# ~(drop-last ks))
         (!set ~(last ks) ~v))
     obj#))

(defmacro update! [obj k f & args]
  (core/let [o (gensym "obj")]
    `(core/let [~o ~obj
                ~o (some-or ~o ~empty-obj)]
       (!set ~o ~k (~f (!get ~o ~k) ~@args)))))

(defmacro update-in! [obj ks f & args]
  (if (vector? ks)
    (core/let [o (gensym "obj")]
      `(core/let [~o ~obj
                  ~o (some-or ~o ~empty-obj)
                  inner-obj# ~(get-in+! o (drop-last ks))]
         (update! inner-obj# ~(last ks) ~f ~@args)
         ~o))
    `(~'applied-science.js-interop.impl/update-in* ~obj ~(wrap-keys ks) ~f ~(vec args))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defmacro push! [array v]
  (core/let [sym (with-meta (gensym "array") {:tag 'js/Array})]
    `(core/let [~sym ~array]
       (~'.push ~sym ~v)
       ~sym)))

(defmacro unshift! [array v]
  `(doto ~array
     (~'.unshift ~v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defmacro call [obj k & args]
  (if (dot-sym? k)
    `(~(dot-call k) ~obj ~@args)
    `(core/let [obj# ~obj
                ^function f# (!get obj# ~k)]
       (.call f# obj# ~@args))))

(defmacro call-in [obj ks & args]
  (if (vector? ks)
    `(core/let [parent# (!get-in ~obj ~(pop ks))
                ^function f# (!get parent# ~(peek ks))]
       (.call f# parent# ~@args))
    `(~'applied-science.js-interop.impl/apply-in* ~obj ~(wrap-keys ks) (cljs.core/array ~@args))))

(defmacro apply [obj k arg-array]
  `(core/let [obj# ~obj
              ^function f# (!get obj# ~k)]
     (.apply f# obj# ~arg-array)))

(defmacro apply-in [obj ks arg-array]
  (if (vector? ks)
    `(core/let [parent# (!get-in ~obj ~(pop ks))
                ^function f# (!get parent# ~(peek ks))]
       (.apply f# parent# ~arg-array))
    `(~'applied-science.js-interop.impl/apply-in* ~obj ~(wrap-keys ks) ~arg-array)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Object creation

(defn- literal-obj
  [keyvals]
  (core/let [keyvals-str (str "({" (->> (map (core/fn [[k _]]
                                               (str (if (dot-sym? k)
                                                      (comp/munge (dot-name k)) ;; without quotes, can be renamed by compiler
                                                      (str \" (name k) \"))
                                                    ":~{}")) keyvals)
                                        (str/join ",")) "})")]
    (vary-meta (list* 'js* keyvals-str (map second keyvals))
               assoc :tag 'object)))

(defmacro obj
  [& keyvals]
  (core/let [kvs (partition 2 keyvals)]
    (if (every? #(or (keyword? %)
                     (string? %)
                     (dot-sym? %)) (map first kvs))
      (literal-obj kvs)
      `(-> ~empty-obj
           ~@(for [[k v] kvs]
               `(!set ~k ~v))))))

;; Nested literals (maps/vectors become objects/arrays)

(core/defn lit*
  "Recursively converts literal Clojure maps/vectors into JavaScript object/array expressions

  Options map accepts a :keyfn for custom key conversions."
  ([x]
   (lit* nil x))
  ([{:as   opts
     :keys [keyfn]
     :or   {keyfn identity}} x]
   (cond (map? x)
         (list* 'applied-science.js-interop/obj
                (reduce-kv #(conj %1 (keyfn %2) (lit* opts %3)) [] x))
         (vector? x)
         (list* 'cljs.core/array (mapv lit* x))
         :else x)))

(defmacro lit
  "Recursively converts literal Clojure maps/vectors into JavaScript object/array expressions
   (using j/obj and cljs.core/array)"
  [form]
  (lit* form))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Destructured forms

(defmacro let
  "`let` with destructuring that supports js property and array access.
   Use ^:js metadata on the binding form to invoke. Eg/

   (let [^:js {:keys [a]} obj] …)"
  [bindings & body]
  (if (empty? bindings)
    `(do ~@body)
    `(~'clojure.core/let ~(vec (d/destructure &env (take 2 bindings)))
       (~'applied-science.js-interop/let
         ~(vec (drop 2 bindings))
         ~@body))))

(defmacro fn
  "`fn` with argument destructuring that supports js property and array access.
   Use ^:js metadata on binding forms to invoke. Eg/

   (fn [^:js {:keys [a]}] …)"
  [& args]
  (cons 'clojure.core/fn (d/destructure-fn-args args)))

(defmacro defn
  "`defn` with argument destructuring that supports js property and array access.
   Use ^:js metadata on binding forms to invoke."
  [& args]
  (cons 'clojure.core/defn (d/destructure-fn-args args)))