(ns applied-science.js-interop-tests
  (:require [applied-science.js-interop :as j]
            [cljs.test :as test :refer [is
                                        are
                                        testing
                                        deftest]]
            [clojure.pprint :refer [pprint]]))

(defn clj= [x y]
  (= (js->clj x :keywordize-keys true)
     (js->clj y :keywordize-keys true)))

(deftest js-interop

  (testing "get-in"
    (are [expr val]
      (clj= expr val)


      ;; get

      (j/get nil :x)
      nil

      (j/get nil :x 10)
      10


      ;; get-in

      (j/get-in nil [:x])
      nil

      (j/get-in nil [:x] 10)
      10

      (j/get-in #js {:x 10} [:x])
      10

      (j/get-in #js {:x #js {:y 10}} [:x :y])
      10

      (j/get-in #js [#js {:x 10}] [0 :x])
      10


      ;; assoc-in

      (j/assoc-in! #js {} [:x :y] 10)
      {:x {:y 10}}

      (j/assoc-in! nil [:x :y] 10)
      {:x {:y 10}}


      ;; update(-in)

      (j/update! #js {:x 9} :x inc)
      {:x 10}

      (j/update! #js [10] 0 inc)
      [11]

      (j/update-in! nil [:x :y] (fnil inc 0))
      {:x {:y 1}}

      (j/update-in! #js {:x 0
                         :y 9} [:y] inc)
      {:x 0
       :y 10}


      ;; lookup

      (let [{:keys [x]} (j/lookup #js {:x 10})]
        x)
      10


      ;; select-keys

      (j/select-keys #js {:x 10} [:x :y])
      {:x 10}

      (j/select-keys nil [])
      {}


      ;; array ops

      (j/push! #js [0] 10)
      [0 10]

      (j/unshift! #js [0] 10)
      [10 0]

      (j/call #js [10] :indexOf 10)
      0)))

