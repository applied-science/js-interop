# applied-science/js-interop

A JavaScript-interop library for ClojureScript.

[![CircleCI](https://circleci.com/gh/appliedsciencestudio/js-interop.svg?style=svg)](https://circleci.com/gh/appliedsciencestudio/js-interop)

## Features

1. Operations that mirror behaviour of core Clojure functions like `get`, `get-in`, `assoc!`, etc.
2. Keys are parsed at compile-time, and support both static keys (via keywords) and compiler-renamable forms (via dot-properties, eg `.-someAttribute`)
    
## Quick Example

```clj
(ns my.app
  (:require [applied-science.js-interop :as j]))

(def o #js{ …some javascript object… })

(j/get o :x)
(j/get-in o [:x :y])
(j/select-keys o [:a :b :c])

(j/assoc! o :a 1)
(j/assoc-in! o [:x :y] 100)

(j/update! o :a inc)
(j/update-in! o [:x :y] + 10)
  
(j/get o .-x)
(j/assoc-in! o [.-x .-y] 100)
```    

## Installation

This library is **alpha** and is currently only available as a git dep in `deps.edn`:

```clj
:deps
{applied-science/js-interop {:git/url "https://github.com/appliedsciencestudio/js-interop"
                             :sha "e8666ad4680c9642e88ff23fc213b6e4e8bf5d6d"}}
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

Wrapped versions of `push!` and `unshift!` operate on arrays, and return the mutated array.

```clj
(j/push! a 10)

(j/unshift! a 10)
```

### Threading

Because all of these functions return their primary argument (unlike the functions in `goog.object`),
they are suitable for threading.

```clj
(-> #js {}
    (j/assoc-in! [:x :y] 9)
    (j/update-in! [:x :y) inc)
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
