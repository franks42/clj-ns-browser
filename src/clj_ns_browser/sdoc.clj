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
  (:require [clj-ns-browser.seesaw :as ss]
            [clj-ns-browser.browser])
  (:use [clj-ns-browser.utils]
        [seesaw.core]
        [seesaw.border]
        [seesaw.mig]
        [seesaw.dev]
        [clj-info]
        [clj-info.doc2txt :only [doc2txt]]
        [clj-info.doc2html :only [doc2html]]))


(defn sdoc*
  "Brings up the clj-ns-browser documentation for a var, namespace,
  or special form given its name.
  (generates more info than clojure.core/doc)
  Name n is string or (quoted-)symbol."
  ([] (sdoc* (str *ns*) "sdoc"))
  ([a-name] (sdoc* (str *ns*) a-name))
  ([a-ns a-name]
  (binding [ss/*root-frm* (clj-ns-browser.browser/get-browser-root-frm)]
    (if-let [fqn (and a-name (or (string? a-name)(symbol? a-name)) (fqname a-name))]
      (let [sym1 (symbol fqn)
            name1 (name sym1)
            ns1 (try (namespace sym1)(catch Exception e))]
        (if ns1
          ;; we have a fq-var as a-ns/a-name
          (do
            (invoke-soon (ss/selection! :ns-lb ns1))
            (invoke-soon (ss/selection! :vars-lb name1)))
          (if (find-ns (symbol name1))
            ;; should be namespace
            (do
              (invoke-soon (ss/selection! :ns-lb name1))
              )
            ;; else must be class
            (do
              (invoke-soon (ss/selection! :ns-lb (str *ns*)))
              ;; find right entry in ns-maps for name1...
              ;;clj-ns-browser.core=> (some (fn [kv] (when (= (.getName (val kv)) "java.lang.Enum")(key kv)))(ns-imports *ns*))
              ;;Enum

              )))
        (invoke-later (show! (pack! ss/*root-frm*)))
        (println fqn))
      (println "Sorry, no info for give name: " a-name)))))


(defmacro sdoc
  "Brings up the clj-ns-browser documentation for a var, namespace,
  or special form given its name.
  (generates more info than clojure.core/doc)
  Name n is string, symbol, or quoted symbol."
  ([] (sdoc*))
  ([n]
  (cond (string? n) `(sdoc* ~n)
        (symbol? n) `(sdoc* ~(str n))
        (= (type n) clojure.lang.Cons) `(sdoc* ~(str (second n))))))

