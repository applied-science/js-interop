(ns applied-science.shadow-rename-test
  (:require [applied-science.js-interop-test :refer [advanced?]]
            [clojure.test :refer [deftest is]]
            [goog.object :as gobj]))

(deftest should-rename-type-properties

  (deftype SomeType []
    Object
    (someFunction [x] x))

  (let [some-inst (new SomeType)]

    (is ((if advanced? nil? some?)
          (gobj/get some-inst "someFunction"))
        "Under advanced compilation, `someFunction` should be renamed,
         so we should get `nil` when reading the key as a string.")))

