(ns applied-science.js-interop.destructure
  (:refer-clojure :exclude [destructure])
  (:require [clojure.string :as str]
            [clojure.core :as c]
            [clojure.spec.alpha :as s]
            [clojure.walk :as walk]))

(defn- dequote [x]
  (if (and (list? x) (= 'quote (first x)))
    (second x)
    x))

(defn- dot-access? [x]
  (and (symbol? x) (str/starts-with? (name x) ".-")))

(defn- dot-access [s]
  (symbol (str/replace-first (name s) #"^(?:\.\-)?" ".-")))

(def ^:dynamic *js?* false)

(defn tag-js [sym]
  (c/let [m (meta sym)]
    (cond-> sym
            (and (not (:clj m))
                 (not (:tag m)))
            (vary-meta assoc :tag 'js))))

(defn maybe-tag-js [x]
  (cond-> x *js?* tag-js))

(defn js-tag-all [expr]
  (walk/postwalk (c/fn [param]
                   (cond-> param (symbol? param) tag-js))
                 expr))

(defn js-tag? [m] (or (:js m) (= 'js (:tag m))))
(defn clj-tag? [m] (or (:clj m) (= 'clj (:tag m))))

(c/defn destructure
  "Destructure with direct array and object access.

  Invoked via ^:js metadata on binding form:

  (let [^:js {:keys [a]} obj] ...)

  Keywords compile to static keys, symbols to renamable keys,
  and array access to `aget`."
  [bindings]
  ;; modified from cljs.core/destructure
  (binding [*js?* (or *js?* (js-tag? (meta bindings)))]
    (c/let [bents (partition 2 bindings)
            pb (c/fn pb [bvec b v]
                 (let [b-meta (meta b)
                       _ (assert (not (:js/shallow b-meta)) "Deprecated :js/shallow meta, use ^clj instead")
                       js? (boolean (cond (clj-tag? b-meta) false
                                          (js-tag? b-meta) true
                                          *js?* true
                                          :else false))]
                   (binding [*js?* js?]
                     (c/let [pvec
                             (c/fn [bvec b v]
                               (c/let [gvec (gensym "vec__")
                                       gvec? (gensym "some_vec__")
                                       gseq (gensym "seq__")
                                       gfirst (gensym "first__")
                                       has-rest (some #{'&} b)
                                       clj-rest? (and has-rest (not js?))
                                       get-nth (fn [n]
                                                 (if js?
                                                   `(when ~gvec? (aget ~gvec ~n))
                                                   `(nth ~gvec ~n nil)))
                                       get-rest (fn [n]
                                                  (if js?
                                                    `(some->
                                                      ~(with-meta gvec {:tag 'array})
                                                      (.slice ~n))
                                                    gseq))]
                                 (c/loop [ret (c/let [ret (cond-> (conj bvec gvec v)
                                                                  js? (conj gvec? `(some? ~gvec)))]
                                                (if clj-rest?
                                                  (conj ret gseq (c/list `seq gvec))
                                                  ret))
                                          n 0
                                          bs b
                                          seen-rest? false]
                                   (if (seq bs)
                                     (c/let [firstb (first bs)]
                                       (c/cond
                                         (= firstb '&) (recur (pb ret (second bs) (get-rest n))
                                                              n
                                                              (nnext bs)
                                                              true)
                                         (= firstb :as) (pb ret (maybe-tag-js (second bs)) gvec)
                                         :else (if seen-rest?
                                                 (throw #?(:clj  (new Exception "Unsupported binding form, only :as can follow & parameter")
                                                           :cljs (new js/Error "Unsupported binding form, only :as can follow & parameter")))
                                                 (recur (pb (if clj-rest?
                                                              (conj ret
                                                                    gfirst `(first ~gseq)
                                                                    gseq `(next ~gseq))
                                                              ret)
                                                            (maybe-tag-js firstb)
                                                            (if clj-rest?
                                                              gfirst
                                                              (get-nth n)))
                                                        (c/inc n)
                                                        (next bs)
                                                        seen-rest?))))
                                     ret))))
                             pmap
                             (c/fn [bvec b v]
                               (c/let [gmap (gensym "map__")
                                       defaults (:or b)]
                                 (c/loop [ret (c/-> bvec (conj gmap) (conj v)
                                                    (conj gmap) (conj `(if (seq? ~gmap) (apply cljs.core/hash-map ~gmap) ~gmap))
                                                    ((c/fn [ret]
                                                       (if (:as b)
                                                         (conj ret (maybe-tag-js (:as b)) gmap)
                                                         ret))))
                                          bes (c/let [transforms
                                                      (reduce
                                                       (c/fn [transforms mk]
                                                         (if (c/keyword? mk)
                                                           (c/let [mkns (namespace mk)
                                                                   mkn (name mk)]
                                                             (c/cond (= mkn "keys") (assoc transforms mk #(keyword (c/or mkns (namespace %)) (name %)))
                                                                     (= mkn "syms") (assoc transforms mk #(c/list `quote (symbol (c/or mkns (namespace %)) (name %))))
                                                                     (= mkn "strs") (assoc transforms mk c/str)
                                                                     :else transforms))
                                                           transforms))
                                                       {}
                                                       (keys b))]
                                                (reduce
                                                 (c/fn [bes entry]
                                                   (reduce #(assoc %1 %2 ((val entry) %2))
                                                           (dissoc bes (key entry))
                                                           ((key entry) bes)))
                                                 (dissoc b :as :or)
                                                 transforms))]
                                   (if (seq bes)
                                     (c/let [bb (key (first bes))
                                             bk (val (first bes))

                                             ;; convert renamable keys to .-dotFormat
                                             bk (let [k (dequote bk)]
                                                  (if (and js? (symbol? k))
                                                    (dot-access k)
                                                    bk))
                                             ;; use js-interop for ^js-tagged bindings & other renamable keys
                                             getf (if js?
                                                    'applied-science.js-interop/get
                                                    'cljs.core/get)

                                             local (maybe-tag-js
                                                    (if #?(:clj  (c/instance? clojure.lang.Named bb)
                                                           :cljs (cljs.core/implements? INamed bb))
                                                      (with-meta (symbol nil (name bb)) (meta bb))
                                                      bb))
                                             bv (if (contains? defaults local)
                                                  (c/list getf gmap bk (defaults local))
                                                  (c/list getf gmap bk))]
                                       (recur
                                        (if (c/or (c/keyword? bb) (c/symbol? bb)) ;(ident? bb)
                                          (c/-> ret (conj local bv))
                                          (pb ret bb bv))
                                        (next bes)))
                                     ret))))]
                       (c/cond
                         (c/symbol? b) (c/-> bvec (conj (if (namespace b) (symbol (name b)) b)) (conj v))
                         (c/keyword? b) (c/-> bvec (conj (symbol (name b))) (conj v))
                         (vector? b) (pvec bvec b v)
                         (map? b) (pmap bvec b v)
                         :else (throw
                                #?(:clj  (new Exception (c/str "Unsupported binding form: " b))
                                   :cljs (new js/Error (c/str "Unsupported binding form: " b)))))))))
            process-entry (c/fn [bvec b] (pb bvec (first b) (second b)))]
      (->> (if (every? c/symbol? (map first bents))
             bindings
             (c/if-let [kwbs (seq (filter #(c/keyword? (first %)) bents))]
               (throw
                #?(:clj  (new Exception (c/str "Unsupported binding key: " (ffirst kwbs)))
                   :cljs (new js/Error (c/str "Unsupported binding key: " (ffirst kwbs)))))
               (reduce process-entry [] bents)))
           (partition 2)
           (mapcat (if *js?* #_true  ;; always tag these syms?
                     (fn [[k v]]
                       [(tag-js k) v])
                     identity))
           vec))))

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
  (s/cat :fn-prelude (s/* #(and (not (vector? %)) (not (list? %))))
         :fn-tail (s/alt :arity-1 ::argv+body
                         :arity-n (s/cat :bodies (s/+ (s/spec ::argv+body))
                                         :attr-map (s/? map?)))))

(c/defn- spec-reform [spec args update-conf]
  (->> (s/conform spec args)
       (update-conf)
       (s/unform spec)))

(c/defn- update-argv+body [update-fn {[arity] :fn-tail :as conf}]
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

(c/defn- maybe-destructured
  [[params body]]
  (let [syms (into []
                   (take (count params))
                   (repeatedly gensym))
        bindings (-> (interleave params syms)
                     vec
                     (with-meta (meta params))
                     destructure)]
    [syms
     `[(~'applied-science.js-interop/let ~bindings ~@body)]]))

(c/defn destructure-fn-args [args]
  (spec-reform ::function-args args #(update-argv+body maybe-destructured %)))
