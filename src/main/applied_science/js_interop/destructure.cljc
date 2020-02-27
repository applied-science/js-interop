(ns applied-science.js-interop.destructure
  (:refer-clojure :exclude [destructure])
  (:require [clojure.string :as str]
            [clojure.core :as c]
            [clojure.spec.alpha :as s]))

(defn- dequote [x]
  (if (and (list? x) (= 'quote (first x)))
    (second x)
    x))

(defn- dot-access? [x]
  (and (symbol? x) (str/starts-with? (name x) ".-")))

(defn- dot-access [s]
  (symbol (str/replace-first (name s) #"^(?:\.\-)?" ".-")))

(def ^:dynamic *js?* false)

(c/defn destructure
  "Destructure with direct array and object access.

  Invoked via ^:js metadata on binding form:

  (let [^:js {:keys [a]} obj] ...)

  Keywords compile to static keys, symbols to renamable keys,
  and array access to `aget`."
  [bindings]
  ;; modified from cljs.core/destructure
  (c/let [bents (partition 2 bindings)
          pb (c/fn pb [bvec b v]
               (binding [*js?* (cond (:clj (meta b)) false
                                     *js?* true
                                     :else (true? (:js (meta b))))]
                 (c/let [pvec
                         (c/fn [bvec b v]
                           (c/let [gvec (gensym "vec__")
                                   gvec? (gensym "some_vec__")
                                   gseq (gensym "seq__")
                                   gfirst (gensym "first__")
                                   has-rest (some #{'&} b)
                                   clj-rest? (and has-rest (not *js?*))
                                   get-nth (fn [n]
                                             (if *js?*
                                               `(when ~gvec? (aget ~gvec ~n))
                                               `(nth ~gvec ~n nil)))
                                   get-rest (fn [n]
                                              (if *js?*
                                                `(some->
                                                   ~(with-meta gvec {:tag 'array})
                                                   (.slice ~n))
                                                gseq))]
                             (c/loop [ret (c/let [ret (cond-> (conj bvec gvec v)
                                                              *js?* (conj gvec? `(some? ~gvec)))]
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
                                     (= firstb :as) (pb ret (second bs) gvec)
                                     :else (if seen-rest?
                                             (throw #?(:clj  (new Exception "Unsupported binding form, only :as can follow & parameter")
                                                       :cljs (new js/Error "Unsupported binding form, only :as can follow & parameter")))
                                             (recur (pb (if clj-rest?
                                                          (conj ret
                                                                gfirst `(first ~gseq)
                                                                gseq `(next ~gseq))
                                                          ret)
                                                        firstb
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
                                                (conj gmap) (conj `(if (~'cljs.core/implements? c/ISeq ~gmap) (apply cljs.core/hash-map ~gmap) ~gmap))
                                                ((c/fn [ret]
                                                   (if (:as b)
                                                     (conj ret (:as b) gmap)
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
                                              (if (and *js?* (symbol? k))
                                                (dot-access k)
                                                bk))
                                         ;; use js-interop for ^js-tagged bindings & other renamable keys
                                         getf (if *js?*
                                                'applied-science.js-interop/get
                                                'cljs.core/get)

                                         local (if #?(:clj  (c/instance? clojure.lang.Named bb)
                                                      :cljs (cljs.core/implements? INamed bb))
                                                 (with-meta (symbol nil (name bb)) (meta bb))
                                                 bb)
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
                                :cljs (new js/Error (c/str "Unsupported binding form: " b))))))))
          process-entry (c/fn [bvec b] (pb bvec (first b) (second b)))]
    (if (every? c/symbol? (map first bents))
      bindings
      (c/if-let [kwbs (seq (filter #(c/keyword? (first %)) bents))]
        (throw
          #?(:clj  (new Exception (c/str "Unsupported binding key: " (ffirst kwbs)))
             :cljs (new js/Error (c/str "Unsupported binding key: " (ffirst kwbs)))))
        (reduce process-entry [] bents)))))

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
  (if (every? symbol? params)
    [params body]
    (loop [params params
           new-params (with-meta [] (meta params))
           lets []]
      (if params
        (if (symbol? (first params))
          (recur (next params) (conj new-params (first params)) lets)
          (c/let [gparam (gensym "p__")]
            (recur (next params) (conj new-params gparam)
                   (conj lets (first params) gparam))))
        [new-params
         `[(~'applied-science.js-interop/let ~lets
             ~@body)]]))))

(c/defn destructure-fn-args [args]
  (spec-reform ::function-args args #(update-argv+body maybe-destructured %)))
