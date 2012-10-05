;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns cljs-doc
  "Namespace dedicated to the generation of text-formatted documentation for
  vars, namespaces and types in ClojureScript."
  (:require [clj-info.doc2txt] ;;clj-info.doc2txt/doc2txt
            [clojure.set]
            [clojure.string]
            [cljs-ns]
            [cljs.analyzer]))

;;;;

(defn cljs-ns [] cljs.analyzer/*cljs-ns*)

(defn cljs-empty-env [] (cljs.analyzer/empty-env))

;;;;


(def cljs-special-forms-doc
  "Some cljs-specific forms are no real vars and have no online-docs.
   This map substitutes for those."
  {
  'load-namespace {:name 'load-namespace :arglists '(quote ([n])) :doc "Load a namespace and all of its dependencies into the evaluation environment.
  The environment is responsible for ensuring that each namespace is loaded once and
  only once." :special-form true}
  'load-file {:name 'load-namespace :arglists '(quote ([n])) :doc "load-file." :special-form true}
  'in-ns {:name 'load-namespace :arglists '(quote ([n])) :doc "Sets *cljs-ns* to the namespace named by the symbol n, creating it if needed." :special-form true}
  })
  
;; text-docs generation

(defn doc2txt
  "Generates and returns string with text-page for the docs-map info obtained for identifier-string w."
  ([w] (doc2txt (cljs-empty-env) w))
  ([env w]
    (let [s (symbol w)
          
          m (or (cljs-special-forms-doc s)
                (cljs.analyzer/resolve-existing-var env s))

          fqname (:name m)
          the-name (name fqname)
          the-ns (or (:ns m) (namespace fqname))
          private (= \- (first the-name))
          
          title (if m
                  (str  (:name m)
                        "   -   "
                        (when private "Private ")
                        (cond
                          (:fn-var m)          "Function"
                          (:special-form m)    "Special Form"
                          (:protocol-symbol m) "Protocol"
                          (:type m)            "Type"
                          :true "Var"))
                  (str "Sorry, no doc-info for \"" w "\""))
  
          message (if m
            (str
              
              (when (:protocol m)
                (str \newline "Protocol: " (:protocol m)))
              
              (when (:protocols m)
                (str \newline "Protocols: " (:protocols m)))
              
              (when (:arglists m)
                (str  \newline 
                  (if (string? (:arglists m)) 
                    (:arglists m) 
                    (eval (:arglists m)))))
  
              (when (:doc m)
                (str  ;\newline "Documentation:"
                      \newline "  " (:doc m)))
  
            ""))
            ]
      {:title title :message message})))



(defn cljs-doc*
  "Function prints documentation for a var, namespace, or special form.
  Name n is string or quoted symbol."
  ([] (cljs-doc* 'cljs-doc*))
  ([n]
    (let [env (cljs-empty-env)
          s (symbol (str (if (= (type n) clojure.lang.Cons) (second n) n)))
          n-maybe-cljs-core (if (namespace s) s (symbol (str "cljs.core/" s)))
          m (if (or (cljs-ns/cljs-ns-resolve (cljs-ns) s) 
                    (cljs-special-forms-doc s))
              (doc2txt env s)
              (if (ns-resolve *ns* s)
                (clj-info.doc2txt/doc2txt s)
                (if (ns-resolve *ns* n-maybe-cljs-core)
                  (clj-info.doc2txt/doc2txt n-maybe-cljs-core)
                  {:title (str "Sorry, no doc-info for \"" s "\"")})))]
      (println "----------------------------------------------------------------------")
      (println (:title m) (:message m)))
    (symbol "")) ;; suppresses the eval-print of nil - purely esthetics
  ([n & m-n]
    ;; allows for (apply cljs-doc* (apropos "replace"))
    (doall (map cljs-doc* (cons n m-n))) (symbol "")))


(defmacro cljs-doc
  "Macro prints documentation for a var, namespace, or special form.
  Name n is string, symbol, or quoted symbol."
  ([] `(cljs-doc* "cljs-doc"))
;;   ([] `(~cljs-doc* "cljs-doc"))
  ([n] 
    (cond (string? n) `(cljs-doc* ~n)
      (symbol? n) `(cljs-doc* ~(str n))
      (= (type n) clojure.lang.Cons) `(cljs-doc* ~(str (second n))))))
        

;;;;
(def cljs-doc-special-fns
  "Function mapping table for use with run-repl-listen."
  {
    ;;'doc (fn [& p] (print (apply cljs-doc/cljs-doc* (cljs-ns/symbify p))))
    'cljs-doc (fn [& p] (print (apply cljs-doc/cljs-doc* (cljs-ns/symbify p))))
  })

;;;;

