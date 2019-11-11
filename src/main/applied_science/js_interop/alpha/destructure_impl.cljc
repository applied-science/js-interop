(ns applied-science.js-interop.alpha.destructure-impl
  (:refer-clojure :exclude [destructure])
  (:require [clojure.string :as str]
            [clojure.core :as core]
            [clojure.spec.alpha :as s]
            #?@(:clj [[net.cgrand.macrovich :as macros]
                      [applied-science.js-interop.alpha.inference :as inf]]))
  #?(:cljs (:require-macros [net.cgrand.macrovich :as macros])))

(macros/deftime

  (def ^:dynamic *env* nil)

  (defn- dequote [x]
    (if (and (list? x) (= 'quote (first x)))
      (second x)
      x))

  (defn- dot-sym? [x]
    (and (symbol? x) (str/starts-with? (name x) ".-")))

  (defn- dot-sym [s]
    (symbol (str/replace-first (name s) #"^(?:\.\-)?" ".-")))

  (defn- get-meta [x k]
    (when #?(:cljs (satisfies? IMeta x) :clj true)
      (get (meta x) k)))

  (declare process-binding)

  (defn- process-vec [out bind-as value js?]
    (let [gvec (gensym "vec__")
          gseq (gensym "seq__")
          gfirst (gensym "first__")
          has-rest (some #{'&} bind-as)]
      (loop [ret (let [ret (conj out gvec value)]
                   (if has-rest
                     (conj ret gseq (list `seq gvec))
                     ret))
             n 0
             bs bind-as
             seen-rest? false]
        (if (seq bs)
          (let [firstb (first bs)]
            (cond
              (= firstb '&) (recur (process-binding ret (second bs) gseq)
                                   n
                                   (nnext bs)
                                   true)
              (= firstb :as) (process-binding ret (second bs) gvec)
              :else (if seen-rest?
                      (throw #?(:clj  (new Exception "Unsupported binding form, only :as can follow & parameter")
                                :cljs (new js/Error "Unsupported binding form, only :as can follow & parameter")))
                      (recur (process-binding (if has-rest
                                                (conj ret
                                                      gfirst `(first ~gseq)
                                                      gseq `(next ~gseq))
                                                ret)
                                              firstb
                                              (if has-rest
                                                gfirst
                                                ;; swap in array access
                                                (if js?
                                                  (list 'cljs.core/aget value n) ; TODO - checked-aget
                                                  (list `nth value n nil))))
                             (inc n)
                             (next bs)
                             seen-rest?))))
          ret))))

  (defn- process-map [out {:as bind-as
                           defaults :or
                           alias :as} value js?]
    (let [record-fields (some-> (inf/infer-type *env* value)
                                (inf/record-fields))
          gmap (gensym "map__")]
      (loop [ret (-> out
                     (conj gmap value)
                     (cond-> (not js?)
                             (conj gmap `(if ~(macros/case :clj `(seq? ~gmap)
                                                           :cljs `(implements? ISeq ~gmap))
                                           (apply hash-map ~gmap) ~gmap)))
                     (cond-> alias (conj alias gmap)))
             bes (let [transforms
                       (reduce
                         (fn [transforms mk]
                           (if (keyword? mk)
                             (let [mkns (namespace mk)
                                   mkn (name mk)]
                               (if-some [transform (case mkn
                                                     "keys" #(keyword (or mkns (namespace %)) (name %))
                                                     "syms" #(list `quote (symbol (or mkns (namespace %)) (name %)))
                                                     "strs" str
                                                     nil)]
                                 (assoc transforms mk transform)
                                 transforms))
                             transforms))
                         {}
                         (keys bind-as))]
                   (reduce
                     (fn [bes entry]
                       (reduce #(assoc %1 %2 ((val entry) %2))
                               (dissoc bes (key entry))
                               ((key entry) bes)))
                     (-> bind-as (dissoc :as :or))
                     transforms))]
        (if (seq bes)
          (let [bb (key (first bes))
                bk (val (first bes))
                local (if #?(:clj  (core/instance? clojure.lang.Named bb)
                             :cljs (cljs.core/implements? INamed bb))
                        (with-meta (symbol nil (name bb)) (meta bb))
                        bb)

                ;; identify renamable keys and xform
                bk (let [renamable-k? (let [k (dequote bk)]
                                        (or (and js? (symbol? k))
                                            ;; renamable record
                                            (contains? record-fields (symbol (name k)))))]
                     (cond-> bk renamable-k? (dot-sym)))
                ;; swap in js-interop/get
                getf (if (or js? (dot-sym? bk))
                       'applied-science.js-interop/get
                       `get)

                bv (if (contains? defaults local)
                     (list getf gmap bk (defaults local))
                     (list getf gmap bk))]
            (recur (if (ident? bb)
                     (-> ret (conj local bv))
                     (process-binding ret bb bv))
                   (next bes)))
          ret))))

  (defn- process-binding
    ([out pair]
     (process-binding out (first pair) (second pair)))
    ([out binding-form value]
     (cond
       (symbol? binding-form) (conj out binding-form value)
       (vector? binding-form) (process-vec out binding-form value (= 'js (get-meta value :tag)))
       (map? binding-form) (process-map out binding-form value (= 'js (get-meta value :tag)))
       :else (throw #?(:clj  (new Exception (str "Unsupported binding form: " binding-form))
                       :cljs (new js/Error (str "Unsupported binding form: " binding-form)))))))

  (defn destructure
    "Destructure with direct array and object access on records, types, and ^js hinted values.

    Hints may be placed on the binding or value:
    (let [^js {:keys [a]} obj] ...)
          ^
    (let [{:keys [a]} ^js obj] ...)
                      ^

    Keywords compile to static keys, symbols to renamable keys,
    and array access to `aget`."
    [env bindings]
    (if (:ns env)                                           ;; cljs target
      (binding [*env* env]
        (reduce process-binding [] (partition 2 bindings)))
      (core/destructure bindings)))

  ;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
  ;; Function argument parsing

  (s/def ::argv+body
    (s/cat :params (s/and
                     vector?
                     (s/conformer identity vec)
                     (s/cat :params (s/* any?)))
           :body (s/alt :prepost+body (s/cat :prepost map?
                                             :body (s/+ any?))
                        :body (s/* any?))))

  (s/def ::function-args
    (s/cat :fn-name (s/? simple-symbol?)
           :docstring (s/? string?)
           :meta (s/? map?)
           :fn-tail (s/alt :arity-1 ::argv+body
                           :arity-n (s/cat :bodies (s/+ (s/spec ::argv+body))
                                           :attr-map (s/? map?)))))

  (core/defn- spec-reform [spec args update-conf]
    (->> (s/conform spec args)
         (update-conf)
         (s/unform spec)))

  (core/defn- update-argv+body [update-fn {[arity] :fn-tail :as conf}]
    (let [update-pair
          (fn [conf]
            (let [body-path (cond-> [:body 1]
                                    (= :prepost+body (first (:body conf))) (conj :body))
                  [params body] (update-fn [(get-in conf [:params :params])
                                            (get-in conf body-path)])]
              (-> conf
                  (assoc-in [:params :params] params)
                  (assoc-in body-path body))))]
      (case arity
        :arity-1 (update-in conf [:fn-tail 1] update-pair)
        :arity-n (update-in conf [:fn-tail 1 :bodies] #(mapv update-pair %)))))

  (core/defn- maybe-destructured
    [[params body]]
    (if (every? symbol? params)
      [params body]
      (loop [params params
             new-params (with-meta [] (meta params))
             lets []]
        (if params
          (if (symbol? (first params))
            (recur (next params) (conj new-params (first params)) lets)
            (core/let [gparam (gensym "p__")]
              (recur (next params) (conj new-params gparam)
                     (conj lets (first params) gparam))))
          [new-params
           `[(let ~lets
               ~@body)]]))))

  (core/defn destructure-fn-args [args]
    (spec-reform ::function-args args #(update-argv+body maybe-destructured %))))
