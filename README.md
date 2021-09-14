# js-interop ![latest release](https://img.shields.io/clojars/v/applied-science/js-interop.svg?color=%23309631&label=release) [![tests passing?](https://circleci.com/gh/applied-science/js-interop.svg?style=svg)](https://circleci.com/gh/applied-science/js-interop)

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

(let [{:keys [x]} (j/lookup o)] ;; lookup wrapper
  ...)

;; Destructure
(j/let [^:js {:keys [a b c]} o] ...)
(j/fn [^:js [n1 n2]] ...)
(j/defn my-fn [^:js {:syms [a b c]}])

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

```clj
;; lein or boot
[applied-science/js-interop "..."]
```
```clj
;; deps.edn
applied-science/js-interop {:mvn/version "..."}
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

One third-party library commonly recommended for JavaScript interop is [cljs-oops](https://github.com/binaryage/cljs-oops). This solves the renaming problem and is highly performant, but the string-oriented api diverges from Clojure norms.

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

`get` and `get-in` return "getter" functions when called with one argument:

```clj
(j/get :x) ;; returns a function that will read key `x`
```

This can be useful for various kinds of functional composition (eg. `juxt`):

```clj

(map (j/get :x) some-seq) ;; returns item.x for each item

(map (juxt (j/get :x) (j/get :y)) some-seq) ;; returns [item.x, item.y] for each item

```

To cohere with Clojure semantics, `j/get` and `j/get-in` return `nil` if reading from a `nil` object instead of throwing an error. Unchecked variants (slightly faster) are provided as `j/!get` and `j/!get-in`. These will throw when attempting to read a key from an undefined/null object.

The `lookup` function wraps an object with an `ILookup` implementation, suitable for destructuring:

```clj
(let [{:keys [x]} (j/lookup obj)] ;; `x` will be looked up as (j/get obj :x)
  ...)
```

### Destructuring

With `j/let`, `j/defn` and `j/fn`, opt-in to js-interop lookups by adding `^js` in front of a
binding form:

```clj
(j/let [^js {:keys [x y z]} obj  ;; static keys using keywords
        ^js {:syms [x y z]} obj] ;; renamable keys using symbols
  ...)

(j/fn [^js [n1 n2 n3 & nrest]] ;; array access using aget, and .slice for &rest parameters
  ...)

(j/defn my-fn [^js {:keys [a b c]}]
  ...)

```

The opt-in `^js` syntax was selected so that bindings behave like regular Clojure
wherever `^js` is not explicitly invoked, and js-lookups are immediately recognizable
even in a long `let` binding. (Note: the keyword metadata `^:js` is also accepted.)

`^js` is recursive. At any depth, you may use `^clj` to opt-out.


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

`j/obj` returns a literal js object for provided keys/values:

```clj
(j/obj :a 1 .-b 2) ;; can use renamable keys
```

 `j/lit` returns literal js objects/arrays for an arbitrarily nested structure of maps/vectors:

```clj
(j/lit {:a 1 .-b [2 3]})
```

`j/lit` supports unquote-splicing (similar to es6 spread):

```clj
(j/lit [1 2 ~@some-sequential-value])
```

`~@` is compiled to a loop of `.push` invocations, using `.forEach` when we infer the value to be an array, otherwise `doseq`.

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
 
## Core operations

|  | _arguments_| _examples_ |
|-------------------|-------------------------------------------|----------------------------------------------------------------|
| **j/get**         | [key]<br/>[obj key]<br/>[obj key not-found]    | `(j/get :x)` ;; returns a getter function<br/>`(j/get o :x)`<br/>`(j/get o :x :default-value)`<br/>`(j/get o .-x)`|
| **j/get-in**      | [path]<br/>[obj path]<br/>[obj path not-found] | `(j/get-in [:x :y])` ;; returns a getter function <br/>`(j/get-in o [:x :y])`<br/>`(j/get-in o [:x :y] :default-value)`         |
| **j/select-keys** | [obj keys]                                     | `(j/select-keys o [:a :b :c])`                                   |
| **j/assoc!**      | [obj key value]<br/>[obj key value & kvs]      | `(j/assoc! o :a 1)`<br/>`(j/assoc! o :a 1 :b 2)`                   |
| **j/assoc-in!**   | [obj path value]                               | `(j/assoc-in! o [:x :y] 100)`                                    |
| **j/update!**     | [obj key f & args]                             | `(j/update! o :a inc)`<br/>`(j/update! o :a + 10)`                 |
| **j/update-in!**  | [obj path f & args]                            | `(j/update-in! o [:x :y] inc)`<br/>`(j/update-in! o [:x :y] + 10)` |

## Destructuring forms

|  | _example_ |
|-------------------|-------------------------------------------|
| **j/let**         | `(j/let [^:js {:keys [a]} obj] ...)`      |
| **j/fn**          | `(j/fn [^:js [a b c]] ...)`               |
| **j/defn**        | `(j/defn [^:js {:syms [a]}] ...)`         |

## Tests

To run the tests:

```clj
yarn test;
```

## Patronage

Special thanks to [NextJournal](https://nextjournal.com) for supporting the maintenance of this library.