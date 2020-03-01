(ns applied-science.js-interop-usage
  (:require [applied-science.js-interop :as j])
  #?(:cljs (:require-macros [applied-science.js-interop-usage :refer [wrap]])))


;; sample operations, used to inspect generated code.
;;
;; to compile, run:
;; clj -A:usage -m cljs.main -o out/USAGE.js -co '{:pseudo-names true :optimizations :advanced :infer-externs true}' -c applied-science.js-interop-usage
;;
;; compiled js will be in:
;;
;; out/applied_science/js_interop_usage   (uncompressed)
;; out/USAGE.js                           (advanced-compiled with pseudo-names)

(defn log [x]
  #?(:cljs
     (js/console.log x)))

#?(:clj
   (defmacro wrap [label & body]
     `(do
        (log ~(str "------ " label " ------"))
        (log ~@body))))

#?(:cljs
   (do

     (set! *warn-on-infer* true)

     (def ^:export o #js{})
     (def ^:export out #js{})
     (def ^:export o2 (j/obj .-world 10))

     (let [k (str "a" :b)]
       (wrap "!get without hint - check inference"
             (j/!get o k)))

     (wrap "!get without hint - inline expr"
           (j/!get o (str "a" :b)))

     (wrap "!get with kw"
           (let [k :some-key]
             (j/!get o k)
             ))

     (wrap "Array destructuring"
           (j/let [a #js[1 2 3 4 5]
                   ^:js [a b c & xs] a]
             (js/console.log #js[b xs])))

     (wrap "!get"
           ((fn [o2]
              #js [(j/!get o2 .-world)
                   (.-world o2)])
            o2))

     (wrap "undefined"
           (let [o #js{}]
             (undefined? (j/!get o :whatever))))

     (wrap "get-1"
           (j/get o :something))

     (wrap "get-2"
           (j/get o .-something))

     (wrap "get-3"
           (j/get o :something "default"))

     (wrap "get-4"
           (j/get o .-something "default"))

     (wrap "get-in-1"
           (j/get-in o [:something :more]))

     (wrap "get-in-2"
           (j/get-in o [.-something .-more]))

     (wrap "get-in-3"
           (j/get-in o [:something :more] "default"))

     (wrap "get-in-4   3"
           (j/get-in o [.-something .-more] "default"))

     (wrap "assoc-1"
           (j/assoc! o :something "value"))

     (wrap "assoc-2"
           (j/assoc! o .-something "value"))

     (wrap "assoc-in-1"
           (j/assoc-in! o [:something :more] "value"))

     (wrap "assoc-in-2"
           (j/assoc-in! o [.-something .-more] "value"))

     (wrap "update-1"
           (j/update! o :something str "--suffix"))

     (wrap "update-2"
           (j/update! o .-something str "--suffix"))

     (wrap "update-in-1"
           (j/update-in! o [:something :more] str "--suffix"))

     (wrap "update-in-2"
           (j/update-in! o [.-something .-more] str "value"))

     (wrap "select-keys-0 "
           (j/select-keys o [:not-existing]))

     (wrap "select-keys-1"
           (j/select-keys o [:something :more]))

     (wrap "select-keys-2"
           (j/select-keys o [.-something]))


     (goog-define debug false)
     ;; sanity-check: both of the following are DCE'd, the ^boolean hint is unnecessary
     (when debug (js/console.log "CCC"))
     (when ^boolean debug (js/console.log "DDD"))

     ))
