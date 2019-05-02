# js-interop ![latest release](https://img.shields.io/github/tag/appliedsciencestudio/js-interop.svg?color=%23309631&label=release) [![tests passing?](https://circleci.com/gh/appliedsciencestudio/js-interop.svg?style=svg)](https://circleci.com/gh/appliedsciencestudio/js-interop)

A JavaScript-interop library for ClojureScript.

## Features

1. Operations that mirror behaviour of core Clojure functions like `get`, `get-in`, `assoc!`, etc.
2. Keys are parsed at compile-time, and support both static keys (via keywords) and compiler-renamable forms (via dot-properties, eg `.-someAttribute`)
    
## Quick Example

```clj
(ns my.app
  (:require [applied-science.js-interop :as j]))

(def o #js{ …some javascript object… })

;; Read
(j/get o :x)
(j/get o .-x "fallback-value")
(j/get-in o [:x :y])
(j/select-keys o [:a :b :c])

(let [{:keys [x]} (j/lookup o)]
  ...)

;; Write
(j/assoc! o :a 1)
(j/assoc-in! o [:x :y] 100)
(j/assoc-in! o [.-x .-y] 100)

(j/update! o :a inc)
(j/update-in! o [:x :y] + 10)

;; Call functions
(j/call o :someFn 42)
(j/apply o :someFn #js[42])

(j/call-in o [:x :someFn] 42)
(j/apply-in o [:x :someFn] #js[42])

;; Create
(j/obj :a 1 .-b 2)
(j/lit {:a 1 .-b [2 3 4]})
```

## Installation

This library is **alpha** and is published to Clojars. It has no external dependencies.

```clj
;; lein or boot
[appliedscience/js-interop "..."]
```
```clj
;; deps.edn
appliedscience/js-interop {:mvn/version "..."}
```


## Motivation

ClojureScript does not have built-in functions for cleanly working with JavaScript
objects without running into complexities related to the Closure Compiler.
The built-in host interop syntax (eg. `(.-theField obj)`) leaves keys subject to renaming,
which is a common case of breakage when working with external libraries. The [externs](https://clojurescript.org/guides/externs)
handling of ClojureScript itself is constantly improving, as is externs handling of the
build tool [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html#infer-externs), but this is still
a source of bugs and does not cover all cases.

The recommended approach for JS interop when static keys are desired is to use functions in the `goog.object` namespace such
as `goog.object/get`, `goog.object/getValueByKeys`, and `goog.object/set`. These functions are
performant and useful but they do not offer a Clojure-centric api. Keys need to be passed in as strings,
and return values from mutations are not amenable to threading. The `goog.object` namespace has published breaking changes as recently as [2017](https://github.com/google/closure-library/releases/tag/v20170910).

One third-party library commonly recommended for JavaScript interop is [cljs-oops](https://github.com/binaryage/cljs-oops). This solves the renaming problem and is highly performant, but the string-oriented api diverges far from Clojure norms.

Neither library lets you choose to allow a given key to be renamed. For that, you must fall back to host-interop (dot) syntax, which has a different API, so the structure of your code may need to change based on unrelated compiler issues.

The functions in this library work just like their Clojure equivalents, but adapted to a JavaScript context. Static keys are expressed as keywords, renamable keys are expressed via host-interop syntax (eg. `.-someKey`), nested paths are expressed as vectors of keys. Mutation functions are nil-friendly and return the original object, suitable for threading. Usage should be familiar to anyone with Clojure experience.

### Reading

Reading functions include `get`, `get-in`, `select-keys` and follow Clojure lookup syntax (fallback to default values only when keys are not present)

```clj
(j/get obj :x)

(j/get obj :x default-value) ;; `default-value` is returned if key `:x` is not present

(j/get-in obj [:x :y])

(j/get-in obj [:x :y] default-value)

(j/select-keys obj [:x :z])
```

The `lookup` function wraps an object with an `ILookup` implementation, suitable for destructuring:

```clj
(let [{:keys [x]} (j/lookup obj)] ;; `x` will be looked up as (j/get obj :x)
  ...)
```

### Mutation

Mutation functions include `assoc!`, `assoc-in!`, `update!`, and `update-in!`. These functions
**mutate the provided object** at the given key/path, and then return it.

```clj
(j/assoc! obj :x 10) ;; mutates obj["x"], returns obj

(j/assoc-in! obj [:x :y] 10) ;; intermediate objects are created when not present

(j/update! obj :x inc)

(j/update-in! obj [:x :y] + 10)
```

### Host-interop (renamable) keys

Keys of the form `.-someName` may be renamed by the Closure compiler just like other dot-based host interop forms.

```clj
(j/get obj .-x) ;; like (.-x obj)

(j/get obj .-x default) ;; like (.-x obj), but `default` is returned when `x` is not present

(j/get-in obj [.-x .-y])

(j/assoc! obj .-a 1) ;; like (set! (.-a obj) 1), but returns `obj`

(j/assoc-in! obj [.-x .-y] 10)

(j/update! obj .-a inc)
```

### Wrappers

These utilities provide more convenient access to built-in JavaScript operations.

#### Array operations

Wrapped versions of `push!` and `unshift!` operate on arrays, and return the mutated array.

```clj
(j/push! a 10)

(j/unshift! a 10)
```

#### Function operations

`j/call` and `j/apply` look up a function on an object, and invoke it with `this` bound to the object. These types of calls are particularly hard to get right when externs aren't available because there are no `goog.object/*` utils for this.


```clj
;; before
(.someFunction o 10)

;; after
(j/call o :someFunction 10)
(j/call o .-someFunction 10)

;; before
(let [f (.-someFunction o)]
  (.apply f o #js[1 2 3]))

;; after
(j/apply o :someFunction #js[1 2 3])
(j/apply o .-someFunction #js[1 2 3])
```

`j/call-in` and `j/apply-in` evaluate nested functions, with `this` bound to the function's parent object.

```clj
(j/call-in o [:x :someFunction] 42)
(j/call-in o [.-x .-someFunction] 1 2 3)

(j/apply-in o [:x :someFunction] #js[42])
(j/apply-in o [.-x .-someFunction] #js[1 2 3])
```

### Object/array creation 

`j/obj` returns a literal js object for provided keys/values, `j/lit` returns literal js objects/arrays for an arbitrarily nested structure of maps/vectors.

```clj
(j/obj :a 1 .-b 2) ;; can use renamable keys
(j/lit {:a 1 .-b [2 3]})

```

### Threading

Because all of these functions return their primary argument (unlike the functions in `goog.object`),
they are suitable for threading.

```clj
(-> #js {}
    (j/assoc-in! [:x :y] 9)
    (j/update-in! [:x :y] inc)
    (j/get-in [:x :y]))

#=> 10
```
 
## Table of core operations

|  | _arguments_| _examples_ |
|-------------------|-------------------------------------------|----------------------------------------------------------------|
| **j/get**         | [obj key]<br/>[obj key not-found]         | `(j/get o :x)`<br/>`(j/get o :x :default-value)`<br/>`(j/get o .-x)`|
| **j/get-in**      | [obj path]<br/>[obj path not-found]       | `(j/get o [:x :y])`<br/>`(j/get o [:x :y] :default-value)`         |
| **j/select-keys** | [obj keys]                                | `(j/select-keys o [:a :b :c])`                                   |
| **j/assoc!**      | [obj key value]<br/>[obj key value & kvs] | `(j/assoc! o :a 1)`<br/>`(j/assoc! o :a 1 :b 2)`                   |
| **j/assoc-in!**   | [obj path value]                          | `(j/assoc-in! o [:x :y] 100)`                                    |
| **j/update!**     | [obj key f & args]                        | `(j/update! o :a inc)`<br/>`(j/update! o :a + 10)`                 |
| **j/update-in!**  | [obj path f & args]                       | `(j/update-in! o [:x :y] inc)`<br/>`(j/update-in! o [:x :y] + 10)` |


## Tests

To run the tests:

```clj
yarn test;
```
