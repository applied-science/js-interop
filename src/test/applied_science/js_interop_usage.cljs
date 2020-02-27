(ns applied-science.js-interop-usage
  (:require [applied-science.js-interop :as j]))

(set! *warn-on-infer* true)

;; sample operations, used to inspect generated code.
;;
;; to compile, run:
;; clj -A:usage -m cljs.main -o out/USAGE.js -co '{:pseudo-names true :optimizations :advanced :infer-externs true}' -c applied-science.js-interop-usage
;;
;; compiled js will be in:
;;
;; out/applied_science/js_interop_usage   (uncompressed)
;; out/USAGE.js                           (advanced-compiled with pseudo-names)

(def ^:export o #js{})
(def ^:export out #js{})
(def ^:export o2 (j/obj .-world 10))

(j/let [a #js[1 2 3 4 5]
        ^:js [a b c & xs] a]
  (js/console.log "Array destructuring" #js[b xs]))

((fn [o2]
   (unchecked-set o "__!get"
                  #js [(j/!get o2 .-world)
                       (.-world o2)]))
 o2)

(unchecked-set o "___get-1"
               (j/get o :something))

(unchecked-set o "___get-2"
               (j/get o .-something))

(unchecked-set o "___get-3"
               (j/get o :something "default"))

(unchecked-set o "___get-4"
               (j/get o .-something "default"))


(unchecked-set o "___get-in-1"
               (j/get-in o [:something :more]))

(unchecked-set o "___get-in-2"
               (j/get-in o [.-something .-more]))

(unchecked-set o "___get-in-3"
               (j/get-in o [:something :more] "default"))

(unchecked-set o "___get-in-4"
               (j/get-in o [.-something .-more] "default"))

(js/console.log "___assoc-1")
(j/assoc! o :something "value")

(js/console.log "___assoc-2")
(j/assoc! o .-something "value")

(js/console.log "___assoc-in-1")
(j/assoc-in! o [:something :more] "value")

(js/console.log "___assoc-in-2")
(j/assoc-in! o [.-something .-more] "value")

(js/console.log "___update-1")
(j/update! o :something str "--suffix")

(js/console.log "___update-2")
(j/update! o .-something str "--suffix")

(js/console.log "___update-in-1")
(j/update-in! o [:something :more] str "--suffix")

(js/console.log "___update-in-2")
(j/update-in! o [.-something .-more] str "value")

(js/console.log "___select-keys-1"
                (j/select-keys o [:something]))

(js/console.log "___select-keys-2"
                (j/select-keys o [.-something]))
