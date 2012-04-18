;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.sdoc
  "Namespace for sdoc, which works like doc, but kicks-off the clj-ns-browser.
  Usage:
  (use 'clj-ns-browser.sdoc) in repl
  (ns... (:use [clj-ns-browser.sdoc])) in file."
  (:require [clj-ns-browser.browser]))


(defn sdoc*
  "Help function that brings up the clj-ns-browser documentation for a
  var, namespace, or special form given its name.
  (generates more info than clojure.core/doc)
  Input name a-name is a string or quoted-symbol.
  If a-name is not an fqn, then it is resolved within the a-ns namespace
  a-ns is symbol, string or namespace - defaults to *ns*.
  The following invocations at the repl yield the same result:
  (sdoc* 'clojure.core/map)
  (sdoc* 'clojure.core 'map)
  (sdoc* \"clojure.core\" 'map)
  (sdoc*  (find-ns 'clojure.core) 'map)
  (sdoc* \"clojure.core/map\")"
  ([] (sdoc* (str *ns*) "sdoc*"))
  ([a-name] (sdoc* (str *ns*) a-name))
  ([a-ns a-name] (sdoc* a-ns a-name (clj-ns-browser.browser/get-clj-ns-browser)))
  ([a-ns a-name browser-frame]
    (if-let [fqn (clj-ns-browser.browser/browser-with-fqn a-ns a-name browser-frame)]
      (println fqn)
      (println "Sorry, no info for given name: " a-name))))


(defmacro sdoc
  "Help macro that brings up the clj-ns-browser documentation for a
  var, namespace, or special form given its name \"a-name\".
  (generates more info than clojure.core/doc)
  Name a-name is string, symbol, or quoted symbol.
  If a-name is not an fqn, then it is resolved within *ns*.
  The following invocations at the repl yield the same result:
  (sdoc clojure.core/map)
  (sdoc 'clojure.core/map)
  (sdoc \"clojure.core/map\")
  "
  ([] (sdoc* "sdoc"))
  ([a-name]
  (cond (string? a-name) `(sdoc* ~a-name)
        (symbol? a-name) `(sdoc* ~(str a-name))
        (= (type a-name) clojure.lang.Cons) `(sdoc* ~(str (second a-name))))))

