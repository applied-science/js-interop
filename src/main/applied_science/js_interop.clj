(ns applied-science.js-interop
  (:refer-clojure :exclude [get get-in contains? select-keys assoc!
                            unchecked-get unchecked-set apply extend
                            let fn defn spread])
  (:require [clojure.core :as c]
            [cljs.compiler :as comp]
            [cljs.analyzer :as ana]
            [clojure.string :as str]
            [applied-science.js-interop.destructure :as d]
            [applied-science.js-interop.inference :as inf]))

(def ^:private reflect-property 'js/goog.reflect.objectProperty)
(def ^:private wrap-key* 'applied-science.js-interop.impl/wrap-key)
(def ^:private empty-obj '(cljs.core/js-obj))
(def ^:private *let 'clojure.core/let)

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

(defn- as-string [x] (with-meta x {:tag 'string}))

(defn- wrap-key
  "Convert key to string at compile time when possible."
  [env obj k]
  (cond
    (or (string? k)
        (number? k)) k
    (keyword? k) (name k)
    (or (symbol? k)
        (seq? k)) (if (dot-sym? k)
                    (as-string `(~reflect-property ~(comp/munge (dot-name k)) ~obj))
                    (c/let [tags (inf/infer-tags env k)]
                      (cond
                        (inf/within? '#{string number} tags) k
                        (inf/within? '#{keyword} tags) `(name ~k)
                        :else (as-string `(~wrap-key* ~k)))))
    :else (as-string `(~wrap-key* ~k))))

(defn- wrap-keys
  "Fallback to wrapping keys at runtime"
  [ks]
  `(mapv ~wrap-key* ~ks))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defmacro in? [k obj]
  `(~'applied-science.js-interop.impl/in?* ~k ~obj))

(defmacro unchecked-get
  ([obj k]
   (if (dot-sym? k)
     `(~(dot-get k) ~obj)
     `(~'cljs.core/unchecked-get ~obj ~(wrap-key &env nil k))))
  ([obj k not-found]
   (c/let [o (gensym "obj")
           k-sym (gensym "k")]
     `(~*let [~o ~obj
              ~k-sym ~(wrap-key &env o k)]
       (if (in? ~k-sym ~o)
         (unchecked-get ~o ~k-sym)
         ~not-found)))))

(defmacro !get [& args]
  `(unchecked-get ~@args))

(defmacro unchecked-set [obj & keyvals]
  (c/let [o (gensym "obj")]
    `(~*let [~o ~obj]
      ~@(for [[k v] (partition 2 keyvals)]
          (if (dot-sym? k)
            `(set! (~(dot-get k) ~o) ~v)
            `(~'cljs.core/unchecked-set ~o ~(wrap-key &env nil k) ~v)))
      ~o)))

(defmacro !set [obj & keyvals]
  `(applied-science.js-interop/unchecked-set ~obj ~@keyvals))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defmacro contains?
  [obj k]
  (if (:ns &env)
    (c/let [o (gensym "obj")]
      `(~*let [~o ~obj]
        (and (some? ~o)
             (in? ~(wrap-key &env o k) ~o))))
    `(~'clojure.core/contains? ~obj ~k)))

(defn- get*
  ([env obj k]
   (if (:ns env)
     (c/let [o (gensym "obj")]
       `(~*let [~o ~obj]
         (if (some? ~o)
           (cljs.core/unchecked-get ~o ~(wrap-key env o k))
           ~'js/undefined)))
     `(~'clojure.core/get ~obj ~k)))
  ([env obj k not-found]
   (if (:ns env)
     `(~*let [val# ~(get* env obj k)]
       (if (cljs.core/undefined? val#)
         ~not-found
         val#))
     `(~'clojure.core/get ~obj ~k ~not-found))))

(defmacro get
  ([k]
   `(c/fn [obj#] (get obj# ~k)))
  ([obj k]
   (get* &env obj k))
  ([obj k not-found]
   (get* &env obj k not-found)))

(defmacro get-in
  ([ks]
   (if (:ns &env)
     `(c/let [ks# ~(if (vector? ks)
                     (mapv #(wrap-key &env nil %) ks)
                     `(wrap-keys ks))]
        (c/fn [obj#]
          (~'applied-science.js-interop.impl/get-in* obj# ks#)))
     `(c/let [ks# ~ks]
        (c/fn
          ([m#] (c/get-in m# ks#))
          ([m# not-found#] (c/get-in m# not-found#))))))
  ([obj ks]
   (if (:ns &env)
     (reduce (partial get* &env) obj ks)
     `(c/get-in ~obj ~ks)))
  ([obj ks not-found]
   (if-not (:ns &env)
     `(c/get-in ~obj ~ks ~not-found)
     (if (vector? ks)
       `(~*let [out# ~(reduce
                       (c/fn [out k]
                         `(~*let [out# ~out]
                           (if (cljs.core/undefined? out#)
                             ~'js/undefined
                             (get out# ~k)))) obj ks)]
         (if (cljs.core/undefined? out#)
           ~not-found
           out#))
       `(~'applied-science.js-interop.impl/get-in* ~obj ~(wrap-keys ks) ~not-found)))))

(defmacro !get-in
  [obj ks]
  (if (:ns &env)
    (reduce (c/fn [out k] `(!get ~out ~k)) obj ks)
    `(c/get-in ~obj ~ks)))

(defmacro select-keys [obj ks]
  (if (:ns &env)
    (if (vector? ks)
      (c/let [o (gensym "obj")
              out (gensym "out")]
        `(~*let [~o ~obj]
          (if (some? ~o)
            (~*let [~out ~empty-obj]
             ~@(for [k ks]
                 `(~*let [k# ~(wrap-key &env o k)]
                   (when (in? k# ~o)
                     (!set ~out k# (!get ~o k#)))))
             ~out)
            ~empty-obj)))
      `(~'applied-science.js-interop.impl/select-keys* ~obj ~(wrap-keys ks)))
    `(c/select-keys ~obj ~ks)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

;; helpers

(defmacro some-or
  "Like `or` but switches on `some?` instead of truthiness."
  [x y]
  `(if (some? ~x) ~x ~y))

(defn- get+!
  ;; internal
  ;; Returns `k` of `o`. If nil, sets and returns a new empty child object.
  [o k]
  (c/let [child (gensym "child")]
    `(~*let [~child (!get ~o ~k)]
      (some-or ~child
               (~*let [new-child# ~empty-obj]
                (!set ~o ~k new-child#)
                new-child#)))))

(defn- get-in+!
  ;; internal
  [o ks]
  (reduce get+! o ks))

;; core operations

(defmacro assoc! [obj & keyvals]
  (if (:ns &env)
    (c/let [o (gensym "obj")]
      `(~*let [~o ~obj]
        (-> (some-or ~o ~empty-obj)
            ~@(for [[k v] (partition 2 keyvals)]
                `(!set ~k ~v)))))
    `(c/assoc ~obj ~@keyvals)))

(defmacro assoc-in! [obj ks v]
  (if (:ns &env)
    (if (vector? ks)
      (c/let [o (gensym "obj")]
        `(~*let [~o ~obj
                 ~o (some-or ~o ~empty-obj)]
          (!set ~(get-in+! o (drop-last ks)) ~(last ks) ~v)
          ~o))
      `(~'applied-science.js-interop.impl/assoc-in* ~obj ~(wrap-keys ks) ~v))
    `(c/assoc-in ~obj ~ks ~v)))

(defmacro !assoc-in! [obj ks v]
  (if (:ns &env)
    `(~*let [obj# ~obj]
      (-> (!get-in obj# ~(drop-last ks))
          (!set ~(last ks) ~v))
      obj#)
    `(c/assoc-in ~obj ~ks ~v)))

(defmacro !update [obj k f & args]
  (if (:ns &env)
    `(~*let [o# ~obj]
      (!set o# ~k (~f (!get o# ~k) ~@args)))
    `(c/update ~obj ~k ~f ~@args)))

(defmacro update! [obj k f & args]
  (if (:ns &env)
    `(~*let [o# ~obj]
      (!update (some-or o# ~empty-obj) ~k ~f ~@args))
    `(c/update ~obj ~k ~f ~@args)))

(defmacro update-in! [obj ks f & args]
  (if (:ns &env)
    (if (vector? ks)
      (c/let [o (gensym "obj")]
        `(~*let [~o ~obj
                 ~o (some-or ~o ~empty-obj)
                 inner-obj# ~(get-in+! o (drop-last ks))]
          (update! inner-obj# ~(last ks) ~f ~@args)
          ~o))
      `(~'applied-science.js-interop.impl/update-in* ~obj ~(wrap-keys ks) ~f ~(vec args)))
    `(c/update-in ~obj ~ks ~f ~@args)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defn- tagged-sym [tag] (with-meta (gensym (name tag)) {:tag tag}))

(defmacro push! [array v]
  (if (:ns &env)
    (c/let [sym (tagged-sym 'js/Array)]
      `(~*let [~sym ~array]
        (~'.push ~sym ~v)
        ~sym))
    `(c/conj ~array ~v)))

(defmacro unshift! [array v]
  `(doto ~array
     (~'.unshift ~v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defmacro call
  ([k]
   `(c/fn [o#] (~'applied-science.js-interop/call o# ~k)))
  ([obj k & args]
   (if (dot-sym? k)
     `(~(dot-call k) ~obj ~@args)
     `(~*let [obj# ~obj
              ^function f# (!get obj# ~k)]
       (.call f# obj# ~@args)))))

(defmacro call-in [obj ks & args]
  (if (vector? ks)
    `(~*let [parent# (!get-in ~obj ~(pop ks))
             ^function f# (!get parent# ~(peek ks))]
      (.call f# parent# ~@args))
    `(~'applied-science.js-interop.impl/apply-in* ~obj ~(wrap-keys ks) (cljs.core/array ~@args))))

(defmacro apply [obj k arg-array]
  `(~*let [obj# ~obj
           ^function f# (!get obj# ~k)]
    (.apply f# obj# ~arg-array)))

(defmacro apply-in [obj ks arg-array]
  (if (vector? ks)
    `(~*let [parent# (!get-in ~obj ~(pop ks))
             ^function f# (!get parent# ~(peek ks))]
      (.apply f# parent# ~arg-array))
    `(~'applied-science.js-interop.impl/apply-in* ~obj ~(wrap-keys ks) ~arg-array)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Object creation

(defn- literal-obj
  [keyvals]
  (c/let [;; support for multiple keys binding to the same value, eg {[:a :b] :C}
          ;; (js objects would normally 'stringify' the vector)
          [lets keyvals] (reduce (c/fn [[lets keyvals] [k v]]
                                   (if (vector? k)
                                     (c/let [sym (gensym)]
                                       [(conj lets sym v)
                                        (reduce (c/fn [keyvals k] (assoc keyvals k sym)) keyvals k)])
                                     [lets (assoc keyvals k v)]))
                                 [[] {}]
                                 keyvals)
          keyvals-str (str "({" (->> (map (c/fn [[k _]]
                                            (str (if (dot-sym? k)
                                                   ;; renamable key
                                                   (comp/munge (dot-name k))
                                                   ;; static key
                                                   (str \" (name k) \"))
                                                 ":~{}")) keyvals)
                                     (str/join ",")) "})")
          obj (vary-meta (list* 'js* keyvals-str (map second keyvals))
                         assoc :tag 'object)]
    (if (seq lets)
      `(c/let ~lets ~obj)
      obj)))

(defmacro obj
  [& keyvals]
  (c/let [kvs (partition 2 keyvals)]
    (if (every? #(or (keyword? %)
                     (char? %)
                     (string? %)
                     (dot-sym? %)
                     (vector? %)) (map first kvs))
      (literal-obj kvs)
      `(-> ~empty-obj
           ~@(for [[k v] kvs]
               `(!set ~k ~v))))))

;; Nested literals (maps/vectors become objects/arrays)

(c/defn litval* [v]
  (if (keyword? v)
    (cond->> (name v)
             (namespace v)
             (str (namespace v) "/"))
    v))

(declare lit*)

(defn- spread
  "For ~@spread values, returns the unwrapped value,
   otherwise returns nil."
  [x]
  (when (and (seq? x)
             (= 'clojure.core/unquote-splicing (first x)))
    (second x)))

(c/defn apply-to-bodies
  "For recursive lit*, ignores known clj syntax"
  [f expr]
  (c/let [op (first expr)
          ;; default to js let, fn, defn
          qop (cond-> op (symbol? op) ana/resolve-symbol)
          op ('{clojure.core/let applied-science.js-interop/js-let
                cljs.core/let applied-science.js-interop/js-let
                clojure.core/fn applied-science.js-interop/js-fn
                cljs.core/fn applied-science.js-interop/js-fn
                clojure.core/defn applied-science.js-interop/js-defn
                clojure.core/defn- applied-science.js-interop/js-defn
                cljs.core/defn applied-science.js-interop/js-defn
                cljs.core/defn- applied-science.js-interop/js-defn} qop op)
          op-name (if (symbol? op)
                    (name op)
                    "")]
    (cond (re-find #"\b(let|loop)$" op-name)
          (c/let [bindings (->> (second expr)
                                (partition 2)
                                (mapcat
                                 (c/fn [[k v]] [k (f v)]))
                                vec)]
            `(~op ~bindings ~@(map f (drop 2 expr))))

          (re-find #"\b(defn\-?|fn\*?)$" op-name)
          (c/let [expr (cons op (rest expr))
                  [pre post] (split-with #(or (symbol? %)
                                              (string? %)
                                              (map? %)) expr)
                  handle-fn-body #(cons (d/js-tag-all (first %)) (map f (rest %)))
                  post (if (vector? (first post))
                         (handle-fn-body post)
                         (map handle-fn-body post))]
            (concat pre post))

          (#{'applied-science.js-interop/log
             'applied-science.js-interop/logret} qop)
          `(~op ~@(map (c/fn [x] (cond-> x (not (keyword? x)) f)) (rest expr)))

          :else (map f expr))))

(c/defn lit*
  "Recursively converts literal Clojure maps/vectors into JavaScript object/array expressions

  Options map accepts a :keyfn for custom key conversions."
  ([x]
   (lit* nil x))
  ([{:as opts
     :keys [keyfn valfn env deep?]
     :or {keyfn identity
          valfn litval*}} x]
   (if (and (coll? x) (d/clj-tag? (meta x)))
     x
     (cond (and deep? (list? x)) (apply-to-bodies (partial lit* opts) x)
           (map? x)
           (list* 'applied-science.js-interop/obj
                  (reduce-kv #(conj %1 (keyfn %2) (lit* opts %3)) [] x))
           (vector? x)
           (if (some spread x)
             (c/let [sym (tagged-sym 'js/Array)]
               `(c/let [~sym (~'cljs.core/array)]
                  ;; handling the spread operator
                  ~@(for [x'
                          ;; chunk array members into spreads & non-spreads,
                          ;; so that sequential non-spreads can be lumped into
                          ;; a single .push
                          (->> (partition-by spread x)
                               (mapcat (clojure.core/fn [x]
                                         (if (spread (first x))
                                           x
                                           (list x)))))]
                      (if-let [x' (spread x')]
                        (if (and env (inf/tag-in? env '#{array} x'))
                          `(.forEach ~x' (c/fn [x#] (.push ~sym x#)))
                          `(doseq [x# ~(lit* x')] (.push ~sym x#)))
                        `(.push ~sym ~@(map (partial lit* opts) x'))))
                  ~sym))
             (list* 'cljs.core/array (mapv (partial lit* opts) x)))
           :else (valfn x)))))

(c/defn clj-lit
  "lit for Clojure target, only handles ~@unquote-splicing"
  [x]
  (cond (map? x)
        (reduce-kv (c/fn [m k v] (c/assoc m k (clj-lit v))) {} x)
        (vector? x)
        (if (some spread x)
          (c/let [sym (gensym)]
            `(c/let [~sym (volatile! [])]
               ;; handling the spread operator
               ~@(for [x' x
                       :let [many (spread x')]]
                   (if many
                     (if (vector? many)
                       `(vswap! ~sym conj ~@(map clj-lit many))
                       `(vswap! ~sym into ~many))
                     `(vswap! ~sym conj ~x')))
               @~sym))
          (mapv clj-lit x))
        :else x))

(defmacro lit
  "Recursively converts literal Clojure maps/vectors into JavaScript object/array expressions
   (using j/obj and cljs.core/array)"
  [form]
  (if (:ns &env)
    (lit* {:env &env} form)
    (clj-lit form)))

(defmacro js [& forms]
  (binding [d/*js?* true]
    (macroexpand
     (list* 'do (map (partial lit* {:env &env :deep? true}) forms)))))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Destructured forms

(defmacro let
  "`let` with destructuring that supports js property and array access.
   Use ^:js metadata on the binding form to invoke. Eg/

   (let [^:js {:keys [a]} obj] …)"
  [bindings & body]
  `(c/let ~(cond-> bindings (:ns &env) d/destructure) ~@body))

(defmacro fn
  "`fn` with argument destructuring that supports js property and array access.
   Use ^:js metadata on binding forms to invoke. Eg/

   (fn [^:js {:keys [a]}] …)"
  [& args]
  (if (:ns &env)
    (cons 'clojure.core/fn (d/destructure-fn-args args))
    `(c/fn ~@args)))

(defmacro defn
  "`defn` with argument destructuring that supports js property and array access.
   Use ^:js metadata on binding forms to invoke."
  [& args]
  (if (:ns &env)
    (cons 'clojure.core/defn (d/destructure-fn-args args))
    `(c/defn ~@args)))

;; variants of let, fn, defn which destructure as js by default
(defmacro js-let [bindings & body]
  `(~'applied-science.js-interop/let ~(vary-meta bindings assoc :tag 'js) ~@body))
(defmacro js-fn [& args]
  (binding [d/*js?* true]
    `(~'clojure.core/fn ~@(d/destructure-fn-args args))))
(defmacro js-defn [& args]
  (binding [d/*js?* true]
    `(~'clojure.core/defn ~@(d/destructure-fn-args args))))

(defn- str-kw [k] (cond-> k (keyword? k) str))

(defmacro log
  "js/console.log with nice keyword printing (shallow)"
  [& args]
  `(~'js/console.log ~@(map str-kw args)))

(defmacro logret
  "like j/log but, returns the last argument"
  [& args]
  (let [syms (take (dec (count args)) (repeatedly gensym))
        bindings (interleave syms (map str-kw (butlast args)))]
    `(let [~@bindings
           result# ~(last args)]
       (~'js/console.log ~@syms result#)
       result#)))


(defmacro expand [expr]
  `'~(macroexpand expr))

(comment
 ;; clj examples - default clojure behaviour
 (let [x [6 7]]
   (lit {:a [1 2 3 ~@[4 5] ~@x]}))
 (get {:x 1} :x)
 (get '{x 1} 'x nil)
 (get-in {:x {:y 1}} [:x :y])
 (let [^js {:keys [x]} {:x 1}] x)
 (contains? {:x 1} :x))