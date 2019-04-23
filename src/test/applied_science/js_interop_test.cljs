(ns applied-science.js-interop-test
  (:require [applied-science.js-interop :as j]
            [clojure.core :as core]
            [cljs.test :as test :refer [is
                                        are
                                        testing
                                        deftest]]
            [clojure.pprint :refer [pprint]]
            [goog.object :as gobj]
            [goog.reflect :as reflect]))

(goog-define advanced? false)

(def advanced-= (if advanced? = not=))
(def advanced-not= (if advanced? not= =))

(defn clj= [& args]
  (->> args
       (mapv #(js->clj % :keywordize-keys true))
       (apply =)))

(deftest js-interop

  (are [macro-expr fn-expr val]
    (clj= macro-expr fn-expr val)


    ;; get with nil
    (j/get nil :x)
    (apply j/get [nil :x])
    nil

    ;;^same, but undefined
    (j/get js/undefined :x)
    (apply j/get [js/undefined :x])
    nil

    ;; get with default
    (j/get nil :x 10)
    (apply j/get [nil :x 10])
    10

    ;; ^same but undefined
    (j/get js/undefined :x 10)
    (apply j/get [js/undefined :x 10])
    10

    ;; lookup semantics for default with nil-present
    (j/get #js{:x nil} :x 10)
    (apply j/get [#js{:x nil} :x 10])
    nil

    ;; get-in, nil root
    (j/get-in nil [:x])
    (apply j/get-in [nil [:x]])
    nil

    ;; ^same but undefined
    (j/get-in js/undefined [:x])
    (apply j/get-in [js/undefined [:x]])
    nil

    ;; get-in, nil nested
    (j/get-in #js {:x nil} [:x :y])
    (apply j/get-in [#js {:x nil} [:x :y]])
    nil

    ;; ^same but undefined
    (j/get-in #js {:x js/undefined} [:x :y])
    (apply j/get-in [#js {:x js/undefined} [:x :y]])
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

    (j/contains? (j/obj :aaaaa 10) :aaaaa)
    (apply j/contains? (j/obj :aaaaa 10) [:aaaaa])
    true

    (j/contains? (j/obj .-bbbbb 20) .-bbbbb)
    (j/contains? (j/obj .-bbbbb 20) .-bbbbb)
    true

    (j/assoc! #js{} :x 10)
    (apply j/assoc! #js{} [:x 10])
    {:x 10}

    (j/assoc! nil :x 10 :y 20)
    (apply j/assoc! nil [:x 10 :y 20])
    {:x 10
     :y 20}

    (j/unchecked-set #js{} :x 10 :y 20)
    (apply j/unchecked-set #js{} [:x 10 :y 20])
    {:x 10
     :y 20}

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
    (j/assoc-in! #js {:x nil} [:x :y] 10)
    (apply j/assoc-in! [#js {:x nil} [:x :y] 10])
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

    ;; update-in with args
    (j/update-in! #js {:x nil} [:x :y] (fnil + 0) 10)
    (apply j/update-in! [#js {:x nil} [:x :y] (fnil + 0) 10])
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
    0

    (j/call #js [10] .-indexOf 10)
    (j/call #js [10] .-indexOf 10)
    0

    (j/apply #js[10] :indexOf #js[10])
    (apply j/apply [#js [10] :indexOf #js[10]])
    0

    (j/apply #js[10] .-indexOf #js[10])
    (j/apply #js[10] .-indexOf #js[10])
    0)


  (is (-> (j/assoc-in! #js {} [] 10)
          (j/get :null)
          (= 10))
      "Same behaviour as Clojure for assoc-in with empty path.
       JavaScript coerces `nil` to the string 'null'.")

  (let [obj (j/assoc-in! nil [:x :y]
              (fn [x]
                (this-as this
                  [x                                        ;; variables are passed in
                   (fn? (j/get this :y))])))]               ;; `this` is set to parent


    (is (= (j/call-in obj [:x :y] 10)
           (apply j/call-in [obj [:x :y] 10])
           [10 true])
        "call-in")

    (is (= (j/apply-in obj [:x :y] #js[10])
           (apply j/apply-in [obj [:x :y] #js[10]])
           [10 true])
        "apply-in"))

  (testing "Host interop keys"

    (testing "get"
      (let [obj #js{}]
        (set! (.-hostProperty obj) "x")

        (is (= (.-hostProperty obj) "x"))
        (is ((if advanced? not= =)
             (j/get obj :hostProperty) "x")
            "Unhinted object property is renamed under :advanced optimizations")
        (is (= (j/get obj .-hostProperty)
               "x"))))

    (testing "select-keys"
      (let [obj (-> #js{}
                    (j/assoc! .-aaaaa 1 .-bbbbb 2 .-ccccc 3 :ddddd 4)
                    (j/select-keys [.-aaaaa .-bbbbb :ddddd]))]

        (is (= 1 (j/get obj .-aaaaa)))
        (is (= 2 (j/get obj .-bbbbb)))
        (is (nil? (j/get obj .-ccccc)))
        (is (= 4 (j/get obj :ddddd)))

        (is (advanced-not= 1 (j/get obj :aaaaa)))
        (is (advanced-not= 2 (j/get obj :bbbbb)))))

    (testing "^js type hinting"
      (when advanced?
        (let [^js obj #js{}]
          (set! (.-hostProperty2 obj) "x")
          (is (= (j/get obj :hostProperty2) "x")
              "^js hint prevents renaming"))))

    (testing "paths"
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
        (is (= (j/get-in obj [.-ddddd .-eeeee]) "ff"))
        (is (advanced-not= (j/get-in obj [:ddddd :eeeee]) "ff"))))

    (testing "multiple types with same property name"
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
            "host-interop keys work across different types using the same keys")))

    (testing "function operations with deftype"

      (deftype F [someArg]
        Object
        (someFunction [this s] [someArg s]))

      (let [F-instance (-> (new F "x")
                           (j/assoc! :staticFunction identity))]

        (is (= (.someFunction F-instance "y")
               (j/call F-instance .-someFunction "y")
               (j/apply F-instance .-someFunction #js["y"])
               ["x" "y"])
            "host interop, j/call, and j/apply equivalence")

        (is (= (j/call F-instance :staticFunction "y")
               (apply j/call F-instance [:staticFunction "y"])
               (j/apply F-instance :staticFunction #js["y"])
               (apply j/apply F-instance [:staticFunction #js["y"]])
               "y"))

        (when advanced?

          (is (nil? (j/get F-instance :someFunction))
              "Property is renamed on `deftype`")

          (is (thrown? js/Error
                       (= ["x" "y"]
                          (j/call F-instance :someFunction "y")))
              "advanced: j/call with keyword throws on renamable key")
          (is (thrown? js/Error
                       (= ["x" "y"]
                          (j/apply F-instance :someFunction #js["y"])))
              "advanced: j/apply with keyword throws on renamable key"))

        (testing "nested function operations"

          (deftype G [someArg]
            Object
            (someFunction [this s]
              [someArg s]))

          (let [obj #js{:xxxxx #js{:yyyyy (-> (new G "x")
                                              (j/assoc! :staticFunction identity
                                                        .-dynamicKey 999))}}]
            (testing "static function"
              (is (-> obj
                      (j/get-in [:xxxxx :yyyyy])
                      (j/call :staticFunction 10)
                      (= 10)))
              (is (-> obj
                      (j/get-in [:xxxxx :yyyyy])
                      (j/apply :staticFunction #js[10])
                      (= 10))))

            (testing "renamable method"
              (is (-> obj
                      (j/get-in [:xxxxx :yyyyy])
                      (j/call .someFunction 10)
                      (= ["x" 10]))
                  "j/call, nested application")
              (is (-> obj
                      (j/get-in [:xxxxx :yyyyy])
                      (j/apply .someFunction #js[10])
                      (= ["x" 10]))
                  "j/apply, nested application"))

            (testing "renamable property"
              (is (-> obj
                      (j/get-in [:xxxxx :yyyyy .-dynamicKey])
                      (= 999)))

              (is (-> obj
                      (j/get-in [:xxxxx :yyyyy :dynamicKey])
                      (advanced-not= 999)))))))))

  (testing "function operations with deftype"

    (deftype H [someArg]
      Object
      (some_fn_H [this s] [someArg s])
      (some_fn_HH [this s] [someArg s]))

    (let [h-inst (new H "x")]

      (is (= (.some_fn_H h-inst "y")
             ["x" "y"]))

      (is (= (j/call h-inst .-some_fn_H 10)
             ["x" 10])
          "some_fn_H is not inlined by GCC")

      ;; this test represents _weird behaviour_,
      ;; GCC has inlined `some_fn_H`
      (let [property-name (reflect/objectProperty "some_fn_HH" h-inst)
            some_fn2 (gobj/get h-inst property-name)]

        (is (= (.some_fn_HH h-inst "y")
               ["x" "y"]))


        (is (= (.call some_fn2 h-inst 10)
               (if advanced?
                 ["x" "y"]
                 ["x" 10]))
            "some_fn_H is inlined by GCC")

        ;; either of the following two expressions can prevent this inlining.
        ;; sinkValue has the further effect of preventing DCE.
        #_(.-some_fn_HH h-inst)
        #_(reflect/sinkValue
           (.-some_fn_HH h-inst))
        )))

  (is (clj= (j/select-keys #js{:x 1 :y 2} (do [:x]))
            {:x 1})
      "fallback to runtime parsing")

  ;; the following test fails before the code can even load
  #_(is (thrown? js/Error
                 (j/select-keys nil (do [.-x]))))

  (testing "unchecked operations"

    (is (thrown? js/Error
                 (j/unchecked-get nil :k)))

    (let [o #js{}]
      (j/unchecked-set o :x 1 .-yyyyy 2 .-zzzzz 3)

      (is (= (j/unchecked-get o :x)
             (j/get o :x)
             1))
      (is (= (j/unchecked-get o .-yyyyy) 2))
      (is (= (j/get o .-zzzzz) 3)))

    (is (clj= (j/unchecked-set #js{} :x 10 :y 20)
              {:x 10
               :y 20}))

    (is (clj= (j/unchecked-set #js{} .-aaaaaa 10 .-bbbbbb 20)
              (j/obj .-aaaaaa 10 .-bbbbbb 20)))

    (testing "unchecked-get compiles directly to expected syntax"
      (is (= (macroexpand-1 '(applied-science.js-interop/unchecked-get o .-y))
             '(.-y o)))

      (is (-> '(applied-science.js-interop/unchecked-set o .-y :value)
              (macroexpand-1)
              (flatten)
              (set)
              (contains? '.-y))
          "unchecked-set uses host-interop syntax directly (GCC friendly)"))

    )

  (testing "object creation"

    (let [o (j/obj :aaaaa 1
                   :bbbbb 2
                   .-ccccc 3
                   .-ddddd 4)]
      (is (= [(j/get o :aaaaa)
              (j/get o :bbbbb)
              (j/get o .-ccccc)
              (j/get o .-ddddd)]
             [1 2 3 4])))

    (let [o2 (apply j/obj [:aaaaa 1
                           :bbbbb 2])]

      (is (= [(j/get o2 :aaaaa)
              (j/get o2 :bbbbb)]
             [1 2])))

    (testing "js-literal behaviour"
      (let [o #js {:yyyyyy  10
                   "zzzzzz" 20}]
        (is (= (j/get o .-yyyyyy) (if advanced? nil 10)))
        (is (= (j/get o :yyyyyy) 10))
        (is (= (j/get o .-zzzzzz) (if advanced? nil 20)))
        (is (= (j/get o :zzzzzz) 20)))))

  (testing "argument evaluation"
    (let [counter (atom 0)]
      (j/get (do (swap! counter inc)
                 #js{:x 10})
             :x)
      (j/get (do (swap! counter inc)
                 #js{:x 10})
             :x :not-found)
      (j/get-in (do (swap! counter inc)
                    #js{:x 10})
                [:x])
      (j/assoc! (do (swap! counter inc)
                    #js{:x 10}) :x 20)
      (j/assoc-in! (do (swap! counter inc)
                       #js{:a #js{:b 0}}) [:a :b] 20)
      (j/update! (do (swap! counter inc)
                     #js{:x 10}) :x inc)
      (j/update-in! (do (swap! counter inc)
                        #js{:a #js{:b 0}}) [:a :b] inc)
      (j/select-keys (do (swap! counter inc)
                         #js{:x 10})
                     [:x])
      (j/call (do (swap! counter inc)
                  #js{:x 10}) :hasOwnProperty "x")
      (j/apply (do (swap! counter inc)
                   #js{:x 10}) :hasOwnProperty #js["x"])

      (is (= @counter 10)
          "macros do not evaluate their obj argument more than once")))

  (testing "extend!"

    ;; extend

    (is (clj= (j/extend! nil #js{:x 2})
              {:x 2})
        "extend `nil`")

    (is (clj= (j/extend! #js{:x 1} #js{:x 2})
              {:x 2})
        "extend two objects")

    (is (clj= (j/extend! #js{:x 1} #js{:y 2 :z 3} #js{:w 0})
              {:w 0 :x 1 :y 2 :z 3})
        "extend three objects")

    (is (clj= (j/extend! #js{:w 0} nil)
              {:w 0})
        "extend with nil object")))