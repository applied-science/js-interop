(ns applied-science.js-interop.destructure
  (:refer-clojure :exclude [destructure])
  (:require [clojure.string :as str]
            [clojure.core :as core]
            [clojure.spec.alpha :as s]
            [applied-science.js-interop.inference :as inf]))

(defn- dequote [x]
  (if (and (list? x) (= 'quote (first x)))
    (second x)
    x))

(defn- dot-access? [x]
  (and (symbol? x) (str/starts-with? (name x) ".-")))

(defn- dot-access [s]
  (symbol (str/replace-first (name s) #"^(?:\.\-)?" ".-")))

(core/defn destructure* [bindings]
  ;; slightly modified from cljs.core/destructure
  (core/let [js-env? (:ns inf/*&env*)
             bents (partition 2 bindings)
             pb (core/fn pb [bvec b v]
                  (core/let [js? (and js-env? (or (= 'js (:tag (meta b)))
                                                  (= 'js (:tag (meta v)))
                                                  (inf/within? '#{js clj-nil js/undefined}
                                                               (inf/infer-tags v))))
                             pvec
                             (core/fn [bvec b val]
                               (core/let [gvec (gensym "vec__")
                                          gseq (gensym "seq__")
                                          gfirst (gensym "first__")
                                          has-rest (some #{'&} b)]
                                 (core/loop [ret (core/let [ret (conj bvec gvec val)]
                                                   (if has-rest
                                                     (conj ret gseq (core/list `seq gvec))
                                                     ret))
                                             n 0
                                             bs b
                                             seen-rest? false]
                                   (if (seq bs)
                                     (core/let [firstb (first bs)]
                                       (core/cond
                                         (= firstb '&) (recur (pb ret (second bs) gseq)
                                                              n
                                                              (nnext bs)
                                                              true)
                                         (= firstb :as) (pb ret (second bs) gvec)
                                         :else (if seen-rest?
                                                 (throw #?(:clj  (new Exception "Unsupported binding form, only :as can follow & parameter")
                                                           :cljs (new js/Error "Unsupported binding form, only :as can follow & parameter")))
                                                 (recur (pb (if has-rest
                                                              (conj ret
                                                                    gfirst `(first ~gseq)
                                                                    gseq `(next ~gseq))
                                                              ret)
                                                            firstb
                                                            (if has-rest
                                                              gfirst
                                                              (if js?
                                                                (list 'applied-science.js-interop/-checked-aget v n)
                                                                (list `nth v n nil))))
                                                        (core/inc n)
                                                        (next bs)
                                                        seen-rest?))))
                                     ret))))
                             pmap
                             (core/fn [bvec b v]
                               (core/let [record-fields (some-> (inf/infer-tags v)
                                                                (inf/record-fields))
                                          gmap (gensym "map__")
                                          defaults (:or b)]
                                 (core/loop [ret (core/-> bvec (conj gmap) (conj v)
                                                          (conj gmap) (conj `(if (~'cljs.core/implements? core/ISeq ~gmap) (apply cljs.core/hash-map ~gmap) ~gmap))
                                                          ((core/fn [ret]
                                                             (if (:as b)
                                                               (conj ret (:as b) gmap)
                                                               ret))))
                                             bes (core/let [transforms
                                                            (reduce
                                                              (core/fn [transforms mk]
                                                                (if (core/keyword? mk)
                                                                  (core/let [mkns (namespace mk)
                                                                             mkn (name mk)]
                                                                    (core/cond (= mkn "keys") (assoc transforms mk #(keyword (core/or mkns (namespace %)) (name %)))
                                                                               (= mkn "syms") (assoc transforms mk #(core/list `quote (symbol (core/or mkns (namespace %)) (name %))))
                                                                               (= mkn "strs") (assoc transforms mk core/str)
                                                                               :else transforms))
                                                                  transforms))
                                                              {}
                                                              (keys b))]
                                                   (reduce
                                                     (core/fn [bes entry]
                                                       (reduce #(assoc %1 %2 ((val entry) %2))
                                                               (dissoc bes (key entry))
                                                               ((key entry) bes)))
                                                     (dissoc b :as :or)
                                                     transforms))]
                                   (if (seq bes)
                                     (core/let [bb (key (first bes))
                                                bk (val (first bes))

                                                ;; convert renamable keys to .-dotFormat
                                                bk (let [k (dequote bk)]
                                                     (if (or (and js? (symbol? k))
                                                             ;; renamable record
                                                             (and js-env? (contains? record-fields (symbol (name k)))))
                                                       (dot-access k)
                                                       bk))
                                                ;; use js-interop for ^js-tagged bindings & other renamable keys
                                                getf (if (or js? (dot-access? bk))
                                                       'applied-science.js-interop/get
                                                       'cljs.core/get)

                                                local (if #?(:clj  (core/instance? clojure.lang.Named bb)
                                                             :cljs (cljs.core/implements? INamed bb))
                                                        (with-meta (symbol nil (name bb)) (meta bb))
                                                        bb)
                                                bv (if (contains? defaults local)
                                                     (core/list getf gmap bk (defaults local))
                                                     (core/list getf gmap bk))]
                                       (recur
                                         (if (core/or (core/keyword? bb) (core/symbol? bb)) ;(ident? bb)
                                           (core/-> ret (conj local bv))
                                           (pb ret bb bv))
                                         (next bes)))
                                     ret))))]
                    (core/cond
                      (core/symbol? b) (core/-> bvec (conj (if (namespace b) (symbol (name b)) b)) (conj v))
                      (core/keyword? b) (core/-> bvec (conj (symbol (name b))) (conj v))
                      (vector? b) (pvec bvec b v)
                      (map? b) (pmap bvec b v)
                      :else (throw
                              #?(:clj  (new Exception (core/str "Unsupported binding form: " b))
                                 :cljs (new js/Error (core/str "Unsupported binding form: " b)))))))
             process-entry (core/fn [bvec b] (pb bvec (first b) (second b)))]
    (if (every? core/symbol? (map first bents))
      bindings
      (core/if-let [kwbs (seq (filter #(core/keyword? (first %)) bents))]
        (throw
          #?(:clj  (new Exception (core/str "Unsupported binding key: " (ffirst kwbs)))
             :cljs (new js/Error (core/str "Unsupported binding key: " (ffirst kwbs)))))
        (reduce process-entry [] bents)))))

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
  (binding [inf/*&env* env]
    (destructure* bindings)))

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
         `[(~'applied-science.js-interop/let ~lets
             ~@body)]]))))

(core/defn destructure-fn-args [args]
  (spec-reform ::function-args args #(update-argv+body maybe-destructured %)))
