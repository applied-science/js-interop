(ns applied-science.js-interop-tests
  (:require [applied-science.js-interop :as j]
            [cljs.test :as test :refer [is
                                        are
                                        testing
                                        deftest]]
            [clojure.pprint :refer [pprint]]
            [goog.reflect :as reflect]))

(goog-define advanced? false)

(def advanced= (if advanced? = not=))

(defn clj= [& args]
  (->> args
       (mapv #(js->clj % :keywordize-keys true))
       (apply =)))

(deftest js-interop

  (testing "get-in"
    (are [macro-expr fn-expr val]
      (clj= macro-expr fn-expr val)


      ;; get with nil
      (j/get nil :x)
      (apply j/get [nil :x])
      nil

      ;; get with default
      (j/get nil :x 10)
      (apply j/get [nil :x 10])
      10

      ;; lookup semantics for default with nil-present
      (j/get #js{:x nil} :x 10)
      (apply j/get [#js{:x nil} :x 10])
      nil


      ;; get-in, nil root
      (j/get-in nil [:x])
      (apply j/get-in [nil [:x]])
      nil

      ;; get-in, nil nested
      (j/get-in #js {:x nil} [:x :y])
      (apply j/get-in [#js {:x nil} [:x :y]])
      nil

      ;; get-in with default
      (j/get-in nil [:x] 10)
      (apply j/get-in [nil [:x] 10])
      10

      ;; get-in lookup semantics with nil-present
      (j/get-in #js{:x nil} [:x] 10)
      (apply j/get-in [#js{:x nil} [:x] 10])
      nil

      (j/get-in #js {:x 10} [:x] 20)
      (apply j/get-in [#js {:x 10} [:x] 20])
      10

      ;; get-in multi-level
      (j/get-in #js {:x #js {:y 10}} [:x :y])
      (apply j/get-in [#js {:x #js {:y 10}} [:x :y]])
      10

      ;; get-in multi-level
      (j/get-in #js {} [:x :y])
      (apply j/get-in [#js {} [:x :y]])
      nil

      ;; get-in with nested not-present
      (j/get-in #js {:x #js {}} [:x :y])
      (apply j/get-in [#js {:x #js {}} [:x :y]])
      nil

      ;; get-in with nested nil-present
      (j/get-in #js {:x #js {:y nil}} [:x :y] 10)
      (apply j/get-in [#js {:x #js {:y nil}} [:x :y] 10])
      nil

      ;; get-in with array
      (j/get-in #js [#js {:x 10}] [0 :x])
      (apply j/get-in [#js [#js {:x 10}] [0 :x]])
      10


      ;; assoc-in
      (j/assoc-in! #js {} [:x :y] 10)
      (apply j/assoc-in! [#js {} [:x :y] 10])
      {:x {:y 10}}

      ;; assoc-in with nil
      (j/assoc-in! nil [:x :y] 10)
      (apply j/assoc-in! [nil [:x :y] 10])
      {:x {:y 10}}

      ;; assoc-in with nested not-present
      (j/assoc-in! #js {:x #js {}} [:x :y] 10)
      (apply j/assoc-in! [#js {:x #js {}} [:x :y] 10])
      {:x {:y 10}}

      ;; assoc-in with nested nil
      (j/assoc-in! #js {:x #js {:y nil}} [:x :y] 10)
      (apply j/assoc-in! [#js {:x #js {:y nil}} [:x :y] 10])
      {:x {:y 10}}

      ;; update with f
      (j/update! #js {:x 9} :x inc)
      (apply j/update! [#js {:x 9} :x inc])
      {:x 10}

      ;; update with f and args
      (j/update! #js {:x 0} :x + 1 9)
      (apply j/update! [#js {:x 0} :x + 1 9])
      {:x 10}

      ;; update an array
      (j/update! #js [10] 0 inc)
      (apply j/update! [#js [10] 0 inc])
      [11]

      ;; update nil
      (j/update! nil :x (fnil inc 9))
      (apply j/update! [nil :x (fnil inc 9)])
      {:x 10}

      ;; update-in nil
      (j/update-in! nil [:x :y] (fnil inc 0))
      (apply j/update-in! [nil [:x :y] (fnil inc 0)])
      {:x {:y 1}}

      ;; update-in with args
      (j/update-in! nil [:x :y] (fnil + 0) 10)
      (apply j/update-in! [nil [:x :y] (fnil + 0) 10])
      {:x {:y 10}}

      ;; update-in mutates provided object
      (j/update-in! #js {:x 0
                         :y 9} [:y] inc)
      (apply j/update-in! [#js {:x 0
                                :y 9} [:y] inc])
      {:x 0
       :y 10}


      ;; lookup
      (let [{:keys [a b c]} (j/lookup #js {:a 1
                                           :b 2
                                           :c 3})]
        [a b c])
      ((juxt :a :b :c) (apply j/lookup [#js {:a 1
                                             :b 2
                                             :c 3}]))
      [1 2 3]


      ;; select-keys
      (j/select-keys #js {:x 10} [:x :y])
      (apply j/select-keys [#js {:x 10} [:x :y]])
      {:x 10}

      ;; select-keys with nil
      (j/select-keys nil [:x])
      (apply j/select-keys [nil []])
      {}


      ;; array ops

      (j/push! #js [0] 10)
      (apply j/push! [#js [0] 10])
      [0 10]

      (j/unshift! #js [0] 10)
      (apply j/unshift! [#js [0] 10])
      [10 0]

      (j/call #js [10] :indexOf 10)
      (apply j/call [#js [10] :indexOf 10])
      0)

    (when-not advanced?
      (is (thrown? js/Error
                   (j/assoc-in! #js {} [] 10))
          "Empty paths for mutations are not accepted,
           JavaScript objects cannot have nil as a key"))

    (testing "Host interop keys"

      (let [obj #js{}]
        (set! (.-hostProperty obj) "x")

        (is (= (.-hostProperty obj) "x"))
        (is ((if advanced? not= =)
              (j/get obj :hostProperty) "x")
            "Unhinted object property is renamed under :advanced optimizations")
        (is (= (j/get obj .-hostProperty)
               "x")))

      (when advanced?
        (let [^js obj #js{}]
          (set! (.-hostProperty2 obj) "x")
          (is (= (j/get obj :hostProperty2) "x")
              "^js hint prevents renaming")))

      (let [obj #js{:x #js {:y "z"}}]

        (is (= (j/get-in obj [:x :y] "z")))
        (j/assoc-in! obj [:x :y] "zz")
        (is (= (j/get-in obj [:x :y] "zz")))

        (set! (.-aaaaa obj)
              (doto #js {}
                (-> .-bbbbb (set! "c"))))

        (is (= (j/get-in obj [.-aaaaa .-bbbbb] "c")))

        (j/assoc-in! obj [.-aaaaa .-bbbbb] "cc")
        (is (= (j/get-in obj [.-aaaaa .-bbbbb] "cc")))

        (j/assoc-in! obj [.-ddddd .-eeeee] "f")
        (is (= (j/get-in obj [.-ddddd .-eeeee]) "f"))

        (j/update-in! obj [.-ddddd .-eeeee] str "f")
        (is (= (j/get-in obj [.-ddddd .-eeeee]) "ff")))

      (deftype A [someProperty])
      (deftype B [someProperty])
      (deftype C [someProperty])

      (let [a (new A "x")
            b (new B "x")
            c (new C "x")
            d (doto #js{}
                (-> .-someProperty (set! "x")))]

        (is (= (reflect/objectProperty "someProperty" a)
               (reflect/objectProperty "someProperty" b)
               (reflect/objectProperty "someProperty" c)
               (reflect/objectProperty "someProperty" d))
            "goog.reflect returns the same property key for different types")

        (is (= (j/get a .-someProperty)
               (j/get b .-someProperty)
               (j/get c .-someProperty)
               (j/get d .-someProperty)
               "x")
            "host-interop keys work across different types using the same keys"))

      )))
