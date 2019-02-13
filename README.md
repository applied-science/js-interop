# applied-science/js-interop

A JavaScript interop library for ClojureScript.

## Features

1. Operations that mirror behaviour of core Clojure functions
2. Wrapped versions of JavaScript functions that are suitable for threading
3. Keys are static and not subject to Closure Compiler renaming

## Installation

This library is **alpha** and is currently only available as a git dep in `deps.edn`:

```clj
:deps
{applied-science/js-interop {:git/url "https://github.com/appliedsciencestudio/js-interop"
                             :sha "5747ebd4f13ae1dec2b9e658477d797652f690ba"}}
```

## Motivation

ClojureScript does not have built-in functions for cleanly working with JavaScript
objects without running into complexities related to Closure Compiler variable renaming.
The built-in host interop syntax (eg. `(.-theField obj)`) leaves keys subject to renaming,
which is a common case of breakage when working with external libraries. The [externs](https://clojurescript.org/guides/externs)
handling of ClojureScript itself is constantly improving, as is externs handling of the
build tool [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html#infer-externs), but this is still
a source of bugs and does not cover all cases.

The officially recommended approach is to use functions in the `goog.object` namespace such
as `goog.object/get`, `goog.object/getValueByKeys`, and `goog.object/set`. These functions are
performant and useful but they do not offer a Clojure-centric api. Keys need to be passed in as strings,
and return values are not what we expect in Clojure.

One library commonly recommended for JavaScript interop is [cljs-oops](https://github.com/binaryage/cljs-oops). This solves
the renaming problem but the api is centered around strings, different from Clojure norms.

The functions in this library use `goog.object` under the hood, but usage should be familiar to
anyone with Clojure experience. They are designed to work just like their Clojure equivalents,
but adapted to a JavaScript context. Keywords are converted to strings at compile-time when
possible, paths are expressed as vectors.

## Usage

The following examples assume the library is aliased as `j`:

```clj
(ns my.app
  (:require [applied-science.js-interop :as j]))
```

### Reading

Reading functions include `get`, `get-in`, `select-keys`.

```clj
(j/get obj :x)

(j/get obj :x default) ;; `default` is returned if key `:x` is not present

(j/get-in obj [:x :y])

(j/get-in obj [:x :y] default)

(j/select-keys obj [:x :z])
```

The `lookup` function wraps an object with an `ILookup` implementation, suitable for destructuring:

```clj
(let [{:keys [x]} (j/lookup obj)] ;; `x` will be looked up as (j/get obj :x)
  ...)
```

### Mutation

Mutation functions include `assoc!`, `assoc-in!`, `update!`, and `update-in!`. These functions
**mutate the provided object** but provide the return value you would expect.

```clj
(j/assoc! obj :x 10) ;; mutates obj["x"], returns obj

(j/assoc-in! obj [:x :y] 10) ;; intermediate objects are created when not present

(j/update! obj :x inc)

(j/update-in! obj [:x :y] + 10)
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
    (j/assoc-in [:x :y] 9)
    (j/update-in [:x :y) inc)
    (j/get-in [:x :y]))

#=> 10
```

## Tests

To run the tests:

```clj
yarn install;
shadow-cljs compile test;
```