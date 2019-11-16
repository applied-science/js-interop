(ns applied-science.js-interop-usage
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]))

;; sample operations, used to inspect generated code.
;;
;; to compile, run:
;; clj -A:usage -m cljs.main --optimizations advanced -co '{:pseudo-names true}' -c applied-science.js-interop-usage
;;
;; compiled js will be in:
;;
;; out/applied_science/js_interop_usage   (uncompressed)
;; out/main.js                            (advanced-compiled with pseudo-names)

(def o #js{})

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

(js/console.log "___checked-contains"
                (def x (rand-nth [#js{}]))
                "can infer:"
                (j/get #js{} :a)
                "cannot infer:"
                (j/get x :a))