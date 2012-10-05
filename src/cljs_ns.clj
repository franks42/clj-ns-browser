;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns cljs-ns
  "Clojure-library for the ClojureScript resolution environment that gives 
  the equivalent functionality of some of the namespace specific resolution 
  and info functions, like all-ns, ns-map, ns-resolve, apropos, etc.
  The naming convention used is to prepend the clojure-equivalent function-names
  with \"cljs-\", such that ns-map becomes cljs-ns-map.
  Note that this is not a clojurescript-library, but its functions can be called 
  from the cljs-repl with some limitations."
  (:require [clojure.set]
            [cljs.analyzer :as ana])
  (:import (java.io LineNumberReader InputStreamReader PushbackReader)
           (clojure.lang RT Reflector)))



;;;;
;; keep cljs.analyzer dependencies separate
;; may make it easier to port to cljs in future

(defn ^:private namespaces [] @cljs.analyzer/namespaces)

(defn ^:private cljs-ns [] cljs.analyzer/*cljs-ns*)

(defn ^:private cljs-empty-env [] (cljs.analyzer/empty-env))

;;;;

(defn cljs-all-ns
  "Returns a sequence of all cljs-namespaces as symbols."
  [] (apply sorted-set (keys @cljs.analyzer/namespaces)))

(defn cljs-all-ns-str
  "Returns a sequence of all cljs-namespaces as strings."
  [] (map str (cljs-all-ns)))

(defn cljs-find-ns 
  "Returns the cljs-namespace as a symbol for the given symbol or string,
  or nil if no namespace can be found."
  [n] 
  (let [s (symbol n)] (when (@cljs.analyzer/namespaces s) s)))

(defn cljs-the-ns 
  "When passed a symbol or string, returns the cljs-namespace named by it, 
  throwing an exception if not found."
  [n] 
  (or (cljs-find-ns n)
      (throw (Exception. (str "No namespace: \"" n "\" found.")))))

(defn cljs-namespace? 
  "Predicate that returns true if the given string or symbol refers 
  to an existing cljs-namespace."
  [s]
  (if (cljs-find-ns s) true false))


(defn ^:private all-ns-vals [] (vals (namespaces)))

(defn ^:private all-defs [] (into [] (for [k (all-ns-vals)] (:defs k))))

(defn ^:private all-ns-defs-vals [] (apply concat (for [k (all-defs)] (vals k))))

(defn ^:private all-ns-var-names [] (map :name (all-ns-defs-vals)))

(defn ^:private all-ns-var-names-docs [] (map (fn [e] [(:name e) (:doc e)]) (all-ns-defs-vals)))


;; (defn ^:private all-ns-metadata-keys [] (into (sorted-set) (flatten (map keys (vals (namespaces))))))
;; 
;; (defn ^:private ns-metadata-map [a-ns] ((namespaces) a-ns))
;; 
;; (defn ^:private defs-for-ns [a-ns] (:defs (ns-metadata-map a-ns)))
;; 
;; (defn ^:private excludes-for-ns [a-ns] (:excludes (ns-metadata-map a-ns)))
;; 
;; (defn ^:private requires-for-ns [a-ns] (:requires (ns-metadata-map a-ns)))
;; 
;; (defn ^:private requires-macros-for-ns [a-ns] (:requires-macros (ns-metadata-map a-ns)))
;; 
;; (defn ^:private name-fqn-for-ns [a-ns] 
;;   (into (sorted-map) (map (fn [e] [(key e) (:name (val e))]) (defs-for-ns a-ns))))
;; 
;; (defn ^:private lnames-for-ns [a-ns] (keys (defs-for-ns a-ns)))
;; 
;; (defn ^:private fqnames-for-ns [a-ns] (map :name (vals (defs-for-ns a-ns))))
;; 
;; (defn ^:private private-lnames-for-ns [a-ns] (filter #(= \- (first (str %)))(lnames-for-ns a-ns)))
;; 
;; (defn ^:private private-fqnames-for-ns [a-ns] (filter #(= \- (first (str (name %))))(fqnames-for-ns a-ns)))
;; 
;; (defn ^:private public-lnames-for-ns [a-ns] (filter #(not= \- (first (str %)))(lnames-for-ns a-ns)))
;; 
;; (defn ^:private public-fqnames-for-ns [a-ns] (filter #(not= \- (first (str (name %))))(fqnames-for-ns a-ns)))

;;

(defn cljs-apropos
  "Given a regular expression or stringable thing, return a seq of
all definitions in all currently-loaded namespaces that match the
str-or-pattern."
  [str-or-pattern]
  (let [matches? (if (instance? java.util.regex.Pattern str-or-pattern)
                   #(re-find str-or-pattern (str %))
                   #(.contains (str %) (str str-or-pattern)))]
    (filter matches? (all-ns-var-names))))

(defn cljs-apropos-doc
  "Given a regular expression or stringable thing, return a seq of
all definitions or docs in all currently-loaded namespaces that match the
str-or-pattern."
  [str-or-pattern]
  (let [matches? (if (instance? java.util.regex.Pattern str-or-pattern)
                   (fn [e] (or (re-find str-or-pattern (str (first e)))
                                (re-find str-or-pattern (str (second e)))))
                   (fn [e] (or (.contains (str (first e)) (str str-or-pattern))
                               (.contains (str (second e)) (str str-or-pattern)))))]
    (map first (filter matches? (all-ns-var-names-docs)))))


;;;;;


(defn cljs-ns-publics
  "Returns a map of the public intern mappings for the cljs-namespace
  as local-name (symbol) to fq-name (symbol)."
  ([] (cljs-ns-publics (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (map (fn [e] [(key e) (:name (val e))]) 
          (filter #(not (:private (val %))) (get-in @cljs.analyzer/namespaces
                                                    [a-ns :defs])))))))


(defn cljs-ns-privates
  "Returns a map of the private intern mappings for the namespace
  as local-name (symbol) to fq-name (symbol)."
  ([] (cljs-ns-privates (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (map (fn [e] [(key e) (:name (val e))]) 
          (filter #(:private (val %)) (get-in @cljs.analyzer/namespaces
                                                    [a-ns :defs])))))))


(defn cljs-ns-refers-wo-core
  "Returns a map of the refer mappings for the namespace.
  All the use:...:only, but without the cljs.core contribution."
  ([] (cljs-ns-refers-wo-core (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (map (fn [e] [(key e) (symbol (str (val e) "/" (key e)))]) 
          (get-in @cljs.analyzer/namespaces [a-ns :uses]))))))


(defn cljs-ns-refers-core 
  "Lists all the refered cljs.core bindings for the given cljs-namespace
  minus those cljs.core variables that are :excludes."
  ([] (cljs-ns-refers-core (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (map (fn [e] [(key e) (:name (val e))]) 
          (filter #(not (:private (val %))) 
            (apply dissoc (get-in @cljs.analyzer/namespaces ['cljs.core :defs]) 
                          (get-in @cljs.analyzer/namespaces [a-ns :excludes]))))))))

(defn cljs-ns-refers
  "Returns a map of the refer mappings for the namespace.
  All the use:...:only, and cljs.core without the :excludes."
  ([] (cljs-ns-refers (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (merge (cljs-ns-refers-core a-ns) (cljs-ns-refers-wo-core a-ns))))))

(defn cljs-ns-map
  "Returns all the variable lname->fqname mappings for the cljs-namespace.
  (currently misses the macros and the javascript class/function bindings)"
  ([] (cljs-ns-map (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (merge (cljs-ns-refers a-ns) 
               (cljs-ns-publics a-ns)
               (cljs-ns-privates a-ns))))))

(defn cljs-ns-interns
  "Returns all the variable lname->fqname mappings for the cljs-namespace.
  (currently misses the macros and the javascript class/function bindings)"
  ([] (cljs-ns-map (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        (merge (cljs-ns-privates a-ns) 
               (cljs-ns-publics a-ns))))))

(defn cljs-ns-requires
  "Returns the set of required cljs-namespaces for the given namespace."
  ([] (cljs-ns-requires (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-set) 
        (vals (get-in @cljs.analyzer/namespaces [a-ns :requires]))))))

(defn cljs-ns-aliases
  "Returns a map of the aliases for the namespace."
  ([] (cljs-ns-aliases (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
        ;; filter out the trivial aliases-entries where the key equals the val
        (filter (fn [e] (not= (key e) (val e))) 
                (get-in @cljs.analyzer/namespaces [a-ns :requires]))))))

(defn cljs-ns-requires-macros
  "Returns a map of the aliases to macro clj-namespaces for this namespace."
  ([] (cljs-ns-requires-macros (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-map) 
            (get-in @cljs.analyzer/namespaces [a-ns :requires-macros])))))

(defn cljs-ns-macros-clj-ns
  "Returns the set of the clj-namespaces that are required 
  for the given cljs-namespace's macros."
  ([] (cljs-ns-macros-clj-ns (cljs-ns)))
  ([a-ns]
    (when-let [a-ns (and (cljs-namespace? a-ns) (symbol a-ns))]
      (into (sorted-set) 
        (vals (get-in @cljs.analyzer/namespaces [a-ns :requires-macros]))))))

(defn cljs-all-ns-requires
  "Returns the set of all required FQ-namespaces for a loaded cljs-namespaces.
  If there are ns that are required but not part of loaded, 
  then those may be shared and/or macro clj-namespaces."
  []
  (into (sorted-set) (apply clojure.set/union (map cljs-ns-requires (cljs-all-ns)))))
  
(defn cljs-all-ns-requires-macros
  "Returns the set of all required clj-namespaces for a loaded cljs-namespaces."
  []
  (into (sorted-set) (apply clojure.set/union (map cljs-ns-requires-macros (cljs-all-ns)))))
  
(defn cljs-all-missing-ns 
  "All the required ns minus the known ns are the missing shared and/or macro clj-ns,
  and the required java-libs.
  We still miss those ns that are required by those clj-ns..."
  [] (into (sorted-set) (clojure.set/difference (cljs-all-ns-requires) (cljs-all-ns))))

(defn cljs-missing-clj-ns
  "The intersection of the set of missing cljs-ns and all the loaded clj-ns
  will give us some of the shared/macro-ns."
  []
  (let [all-ns-str (set (map #(symbol (str %)) (all-ns)))]
    (into (sorted-set) (clojure.set/intersection (cljs-all-missing-ns) all-ns-str))))

(defn cljs-ns-resolve 
  "Returns the var or Class to which a symbol will be resolved in the
  namespace, else nil.  Note that if the symbol is fully qualified, 
  the var/Class to which it resolves need not be present in the namespace."
  ([a-sym] (cljs-ns-resolve (cljs-ns) a-sym))
  ([a-ns a-sym]
    (let [m (binding [cljs.analyzer/*cljs-ns* (symbol a-ns)]
              (cljs.analyzer/resolve-existing-var 
                (cljs.analyzer/empty-env) 
                (symbol a-sym)))]
      (when (= (:name m) (get-in @cljs.analyzer/namespaces 
                           [(:ns m) :defs (symbol (name (:name m))) :name]))
        (:name m)))))

;;;;

(defn cljs-source-fn
  "Returns a string of the source code for the given symbol, if it can
  find it.  This requires that the symbol resolve to a Var defined in
  a cljs-namespace.  Returns nil if it can't find the source.

  Example: (source-fn 'filter)"
  [x]
  (when-let [v (cljs-ns-resolve x)]
    (let [a-ns (symbol (namespace v))
          a-name (symbol (name v))]
      (when-let [filepath (clojure.string/replace-first 
                            (get-in @cljs.analyzer/namespaces
                                  [a-ns :defs a-name :file])
                            "file:"
                            "jar:file:")]
        (when-let [line-no (get-in @cljs.analyzer/namespaces
                                   [a-ns :defs a-name :line])]
          (when-let [strm (clojure.java.io/input-stream filepath)]
            (with-open [rdr (LineNumberReader. (InputStreamReader. strm))]
              (dotimes [_ (dec line-no)] (.readLine rdr))
              (let [text (StringBuilder.)
                    pbr (proxy [PushbackReader] [rdr]
                          (read [] (let [i (proxy-super read)]
                                     (.append text (char i))
                                     i)))]
                (read (PushbackReader. pbr))
                (str text)))))))))


(defmacro cljs-source
  "Prints the source code for the given symbol, if it can find it.
  This requires that the symbol resolve to a Var defined in a
  cljs-namespace for which the .clj is in the classpath.

  Example: (source filter)"
  [n]
  `(println (or (cljs-source-fn '~n) (str "Source not found"))))


;;;;

(defn symbify
  "Hack to provide some robustness on the repl-input
  (the repl crashes too easily when unexpected characters or var-types are entered)"
  [p] 
  (map 
    (fn [n] (symbol (str (if (= (type n) clojure.lang.Cons) (second n) n)))) 
    (rest p)))

(def cljs-ns-special-fns
  "Function mapping table for use with run-repl-listen."
  {
  'cljs-source (fn [& p] (print (cljs-ns/cljs-source-fn (second p))))
  'cljs-apropos (fn [& p] (print (cljs-ns/cljs-apropos (second p))))
  'cljs-apropos-doc (fn [& p] (print (cljs-ns/cljs-apropos-doc (second p))))
  '*cljs-ns* (fn [& p] (print cljs.analyzer/*cljs-ns*))
  'cljs-all-ns (fn [& p] (print (cljs-ns/cljs-all-ns)))
  'cljs-ns-resolve (fn [& p] (print (apply cljs-ns/cljs-ns-resolve (symbify p))))
  'cljs-ns-map (fn [& p] (print (apply cljs-ns/cljs-ns-map (symbify p))))
  'cljs-ns-publics (fn [& p] (print (apply cljs-ns/cljs-ns-publics (symbify p))))
  'cljs-ns-aliases (fn [& p] (print (apply cljs-ns/cljs-ns-aliases (symbify p))))
  'cljs-ns-requires (fn [& p] (print (apply cljs-ns/cljs-ns-requires (symbify p))))
  'cljs-ns-privates (fn [& p] (print (apply cljs-ns/cljs-ns-privates (symbify p))))
  'cljs-ns-refers (fn [& p] (print (apply cljs-ns/cljs-ns-refers (symbify p))))
  'cljs-ns-refers-wo-core 
    (fn [& p] (print (apply cljs-ns/cljs-ns-refers-wo-core (symbify p))))
  'cljs-find-ns (fn [& p] (print (apply cljs-ns/cljs-find-ns (symbify p))))
  })
