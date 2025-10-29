;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.utils
  (:require [clojure.set]
            ;; [cd-client.core]
            [clojure.java.shell]
            [clojure.java.io]
            [clojure.string :as str]
            [clojure.tools.namespace]
            ;; [clj-http.lite.client]
            ;; [babashka.http-client] ;; Now provided by clj-info 0.6.0
            [clojure.edn]
            [clojure.tools.trace])
  (:use [seesaw.core]
        [clojure.pprint :only [pprint]]
        [clj-info.doc2map :only [get-docs-map]]
        [clj-info.doc2txt :only [doc2txt]]
        [clj-info.doc2html :only [doc2html]]
        [clj-info.clojuredocs :only [get-clojuredocs-content format-examples format-see-alsos format-notes]]))
        ;;[alex-and-georges.debug-repl]))


(defn class-hierarchy-list1
  ""
  [c]
  (if-let [s (.getSuperclass (peek c))]
    (concat c (class-hierarchy-list1 [s])) c))

(defn class-hierarchy-list
  ""
  [c]
  (class-hierarchy-list1 [c]))

(defn interface-hierachy-tree1
  ""
  [i] 
  (if-let [l (.getInterfaces i)] 
    (concat [i] (map interface-hierachy-tree1 l))
    i))

(defn interface-hierachy-tree
  ""
  [c]
  (let [c-l (class-hierarchy-list c)
           i-l (doall (filter #(not (nil? %))(map #(.getInterfaces %) c-l)))]
    (let [r (doall (map #(map interface-hierachy-tree1 %) i-l))]
;;       r)))
      (sort-by #(.getName %) (vec (into #{} (doall (flatten r))))))))


;; clojure.core/special-symbol? tests for inclusion in:
;; clj-ns-browser.utils=> (sort (keys (. clojure.lang.Compiler specials)))
;; (& . case* catch def deftype* do finally fn* if let* letfn* loop* monitor-enter
;; monitor-exit new quote recur reify* set! throw try var clojure.core/import*)
;;
;; following list of special forms is from clojure website specs:
;; [. def do fn if let loop monitor-enter monitor-exit new quote recur set! throw try var]
;; some special forms seem to be implemented as macros with help of special-symbols (?)


(def special-forms
  "List of name-strings for those special-form that are not vars also."
  (sort (map name (keys (. clojure.lang.Compiler specials)))))


;; resolve-fqname and fqname are the two basic resolution functions
;; to find objects for names and names for objects, respectively.

(defn resolve-fqname
  "Returns the resolved var/class/namespace for the (fully qualified) name a-fqn 
  (string, symbol, var, class or namespace). Name can be a local name mapped in namespace a-ns.
  Returns nil if name cannot be resolved or if it's a non-var special-form.
  Name is resolved within namespace a-ns (string, symbol or ns), which defaults to *ns*.
  "
  ([a-fqn] (resolve-fqname *ns* a-fqn))
  ([a-ns a-fqn]
    (if (or (var? a-fqn)(= (type a-fqn) clojure.lang.Namespace)(instance? java.lang.Class a-fqn))
      a-fqn
      (when-let [ns-0 (or (and (= (type a-ns) clojure.lang.Namespace) a-ns)
                          (find-ns (symbol a-ns)))]
        (when-let [fqn-str (or (and (string? a-fqn) (not= a-fqn "") a-fqn)
                               (and (symbol? a-fqn) (str a-fqn)))]
          (let [fqn (if (= fqn-str "clojure.core//") "/" fqn-str)] ;; special corner case :-(
            (if-let [ns (find-ns (symbol fqn))]
              ns
              (when-let [var-or-class (try (ns-resolve ns-0 (symbol fqn))(catch Exception e))]
                var-or-class))))))))


(defn fqname
  "Returns the fully qualified name-string of a var, class or ns
  for n within an (optional) namespace, which may or
  may not be used for the resolution, depending whether n is already FQ'ed.
  Input n: string, (quoted-)symbol, var, or actual class or ns.
        ns: (optional) namespace as ns, symbol or string - default to *ns*.
  Output: FQName as string or nil."
  ([n] (cond
          (keyword? n) (if-let [nsp (namespace n)]
                         (str nsp "/" (name n))
                         (name n))
          :else (fqname *ns* n)))
  ([a-ns n]
    (let [n-str (str n)
          n-sym (symbol n-str)]
      (if (= n-str "clojure.core//")
        "clojure.core//"  ;; special corner case :-(
        (if (some #(= % n-str) special-forms) n-str
          (if-let [n-ns (find-ns n-sym)]
            (str n-ns)
            (if (class? n)
              (.getName n)
              (when-let [ns-ns (if (= (type a-ns) clojure.lang.Namespace)
                                 a-ns
                                 (find-ns (symbol (str a-ns))))]
                (if-let [var-n (when (var? n) (str (.ns n) "/" (.sym n)))]
                  var-n
                  (let [n-sym (symbol (str n))]
                    (when-let [v-n (try (ns-resolve ns-ns n-sym)(catch Exception e))]
                      (if (var? v-n)
                        (str (.ns v-n) "/" (.sym v-n))
                        (.getName v-n)))))))))))))


(defn fqname-symbol
  "Returns the fully qualified name-symbol of a var, class or ns
  for n within an (optional) namespace, which may or
  may not be used for the resolution, depending whether n is already FQ'ed.
  Input n: string, (quoted-)symbol, var, or actual class or ns.
        ns: (optional) namespace as ns, symbol or string - default to *ns*.
  Output: FQName as symbol or nil.
  See also: fqname"
  ([n] (when-let [f (fqname n)] (symbol f)))
  ([a-ns n](when-let [f (fqname a-ns n)] (symbol f))))


(defn ns-name-class-str
  "Given a FQN, return the namespace and name in a list as separate strings.
  clojure.core/map => [\"clojure.core\" \"map\"]
  clojure.core => [\"clojure.core\" nil]
  clojure.lang.Var => [nil \"clojure.lang.Var\"]
  any error returns nil."
  [fqn]
  (when-let [fqn-sym (and fqn (string? fqn) (symbol fqn))]
    (let [n-str (name fqn-sym)
          ns-str (namespace fqn-sym)]
      (if ns-str
        [ns-str n-str]
        (if (find-ns (symbol n-str))
          [n-str nil]
          [nil n-str])))))


(defn local-name 
  [fqn]
  (when-let [fqn-sym (fqname-symbol fqn)]
    (let [name-str (name fqn-sym)]
      (if-let [ns-str (namespace fqn-sym)]
        name-str
        (re-find #"[^\.]+$" name-str)))))

;; basic type-predicates
;; note that we already have char? class? coll? decimal? empty? fn? ifn? future? keyword? list?
;; map? nil? number? seq? sequential? set? special-symbol? string? symbol? var? vector?

(defn special-form?
  "Predicate that returns true if given name n (string or symbol) is a special-form and false otherwise.
  Note that some special forms are vars/macros, and some are special-symbols.
  All the none-vars are tested by clojure.core/special-symbol?"
  [n]
  (if (and (var? n) (:special-form (meta n)))
    true
    (when-let [n-str (and n (str n))]
      (or (some #(= % n-str) special-forms)
        (when-let [v (resolve-fqname n-str)]
          (and (var? v) (:special-form (meta v))))))))


(defn macro?
  "Predicate that returns true when var v is a macro, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (macro? (resolve-fqname n))"
  [v]
  (and (var? v) (:macro (meta v))))


(defn atom?
  "Predicate that returns true when var v refers to a atom, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (atom? (resolve-fqname n))"
  [v]
  (isa? (type v) clojure.lang.Atom))


(defn multimethod?
  "Predicate that returns true when var v refers to a multi-method, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (multimethod? (resolve-fqname n))"
  [o]
  (isa? (type o) clojure.lang.MultiFn))


(defn var-multimethod?
  "Predicate that returns true when var v refers to a multi-method, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (multimethod? (resolve-fqname n))"
  [v]
  (and (var? v) (multimethod? @v)))


(defn protocol?
  "Predicate that returns true when var v refers to a protocol, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (protocol? (resolve-fqname n))"
  [v]
  (and (var? v) (isa? (type @v) clojure.lang.PersistentArrayMap) (:on-interface @v) true))


(defn deftype?
  "Predicate that returns true when object o refers to a deftype, and false otherwise."
  [o]
  (isa? o clojure.lang.IType))


(defn defrecord?
  "Predicate that returns true when object o refers to a defrecord, and false otherwise."
  [o]
  (isa? o clojure.lang.IRecord))


(defn protocol-fn?
  "Predicate that returns true when var v refers to a protocol function, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (protocol-fn? (resolve-fqname n))"
  [v]
  (if (and (var? v) (:protocol (meta v)) true) true false))


(defn defn?
  "Predicate that returns true when var v is a function with a specified arglist, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (function? (resolve-fqname n))"
  [v]
  (if-let [m (and (var? v) (meta v))]
    (if (and (not (or (:macro m)(:special-form m))) (fn? @v) (:arglists m))
      true
      false)
    false))


(defn namespace?
  "Predicate that returns true when n refers to a namespace, and false otherwise.
  Note that input is a name referring to a namespace - if you want to input a name-string or -symbol, use:
  (namespace? (resolve-fqname n))"
  [maybe-ns]
  (isa? (type maybe-ns) clojure.lang.Namespace))


;; Two convenience function for clojure.tools.trace
;; that should ideally be part of that library

(defn var-traceable? 
  "Predicate that returns whether a var is traceable or not."
  [v]
  (let [vv (or (and (var? v) v) (and (symbol? v) (resolve v)))]
    (and (var? vv) (ifn? @vv) (-> vv meta :macro not))))


(defn dynamic?
  "Predicate that returns whether a var is dynamic."
  [v] 
  (let [vv (or (and (var? v) v) (and (symbol? v) (resolve v)))]
    (and (var? vv) (meta vv) (:dynamic (meta vv)))))


(defn var-traced?
  "Predicate that returns whether a var is currently being traced.
  (should ideally be part of clojure.tools.trace such that we can
  remain oblivious about the trace-implementation internals)"  
  ([ns s]
     (var-traced? (ns-resolve ns s)))
  ([v]
    (let [vv (or (and (var? v) v) (and (symbol? v) (resolve v)))]
       ;; (and (var? vv) (meta vv) ((meta vv) ::clojure.tools.trace/traced)))))
       (and (var? vv) (meta vv) ((meta vv) (clojure.tools.trace/traced? vv))))))


;; should move to clj-info

(defn get-object-type
  ""
  [fqn]
  (if-let [m (get-docs-map fqn)]
    (:object-type-str m)
    ""))


;; functions to collect different (filtered/sub-) lists of vars, classes, special-forms, etc.

(defn ns-special-forms 
  "Collects the special form string-symbol map from both the special-symbol list 
  and the special-form vars in clojure.core.
  Returns a map that has same format as ns-map, ns-publics and friends."
  [& no-op]
  (let [str-list (concat special-forms (map name (filter special-form? (keys (ns-map *ns*)))))
        sym-list (map symbol str-list)]
    (zipmap str-list sym-list)))


(defn symbols-of-ns-coll [ns-action f ns-coll display-fqn? search-doc-strings?]
  (when-not (or (nil? ns-coll) (empty? ns-coll) (nil? (first ns-coll)))
    (let [g (case ns-action
              (:aliases :special-forms) (fn [[k v]]
                                          {:symbol k
                                           :rough-category ns-action
                                           :var-class-or-ns v
                                           :doc-string nil ; TBD: Fill this in
                                           :fqn-sym k
                                           :fqn-str (str k)
                                           :display-sym k
                                           :display-str (str k)
                                           :name-to-search (str k)})
              :ns-map-subset (fn [[sym var-or-class]]
                               (let [fqn-str-or-nil (fqname var-or-class)
                                     fqn-sym (when fqn-str-or-nil (symbol fqn-str-or-nil))
  ;;                                             (when (instance? clojure.lang.Var
  ;;                                                             var-or-class)
  ;;                                              ;; TBD: There is probably
  ;;                                              ;; a faster way to do
  ;;                                              ;; this.
  ;;                                              (symbol (str (.ns var-or-class))
  ;;                                                      (str sym)))
                                     ds (if (and display-fqn? fqn-sym)
                                          fqn-sym
                                          sym)]
                               {:symbol sym
                                :rough-category ns-action
                                :var-class-or-ns var-or-class
                                :doc-string (when search-doc-strings?
                                              (:doc (meta var-or-class)))
                                :fqn-sym fqn-sym
                                :fqn-str (if fqn-sym
                                           (str fqn-sym)
                                           (str sym))
                                :display-sym ds
                                :display-str (str ds)
                                :name-to-search (str ds)})))]
      (set (if (and (= ns-action :aliases)
                    (> (count ns-coll) 1))
             [ {:name-to-search ""
                :rough-category :msg-to-user
                :display-str "select only one ns for aliases" } ]
             (mapcat (fn [a-ns] (map g (f a-ns)))
                     ns-coll))))))


(defn filter-key [keyfn pred amap]
  (loop [ret {} es (seq amap)]
    (if es
      (if (pred (keyfn (first es)))
        (recur (assoc ret (key (first es)) (val (first es))) (next es))
        (recur ret (next es)))
      ret)))


(defn ns-privates
  "Returns a map of the private (i.e. not public) intern mappings for
  the namespace."
  [ns]
  (let [ns (the-ns ns)]
    (filter-key val (fn [^clojure.lang.Var v]
                      (and (instance? clojure.lang.Var v)
                           (= ns (.ns v))
                           (not (.isPublic v))))
                (ns-map ns))))


(defn ns-interns-protocol
  "Returns a map of the protocols in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(protocol? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-interns-protocol-fn
  "Returns a map of the protocol-fn's in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(protocol-fn? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-interns-macro
  "Returns a map of the macros in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(macro? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-interns-defn
  "Returns a map of the defn's in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(defn? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-interns-var-multimethod
  "Returns a map of the multimethod's in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(var-multimethod? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-interns-var-dynamic
  "Returns a map of the dynamic vars in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(dynamic? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-interns-var-traced
  "Returns a map of the multimethod's in the publics mappings for
  the namespace."
  [a-ns]
  (filter #(var-traced? (val %)) (ns-interns (resolve-fqname a-ns))))

(defn ns-map-deftype
  "Returns a map of the deftype's classes in the mappings for
  the namespace."
  [a-ns]
  (filter #(deftype? (val %)) (ns-map (resolve-fqname a-ns))))

(defn ns-map-defrecord
  "Returns a map of the defrecord's classes in the mappings for
  the namespace."
  [a-ns]
  (filter #(defrecord? (val %)) (ns-map (resolve-fqname a-ns))))

;;

(defn all-ns-classpath
  "Returns a sorted set of the name-strings of all namespaces found on class-path."
  []
  (apply sorted-set
    (map str (clojure.tools.namespace/find-namespaces-on-classpath))))


(defn all-ns-loaded
  "Returns a sorted set of the name-strings of all loaded/required namespaces."
  []
  (apply sorted-set (map str (all-ns))))


(defn all-ns-unloaded
  "Returns a sorted set of the name-strings of all unloaded namespaces."
  []
  (apply sorted-set
    (clojure.set/difference (all-ns-classpath) (all-ns-loaded))))


(defn all-ns-loaded-unloaded
  "Returns a sorted set of the name-strings of all unloaded namespaces."
  []
  (apply sorted-set
    (clojure.set/union (all-ns-unloaded) (all-ns-loaded))))


(defn ns-refers-wo-core 
  "Same as ns-refers, but without the clojure.core contribution."
  [a-ns]
  (let [refers (ns-refers a-ns)
        refers-keys (set (keys refers))
        core-keys (set (keys (ns-publics (find-ns 'clojure.core))))
        refers-keys-wo (clojure.set/difference refers-keys core-keys)]
    (select-keys refers refers-keys-wo)))


(defn ns-is-unloaded?
  "Returns whether a ns is on the classpath but not loaded/required (yet)."
  [ns]
  (let [nss (str ns)] (get (all-ns-unloaded) nss)))


(defn pprint-str
  "Return string with pprint of v, and limit output to prevent blowup."
  ([v & kvs]
     (with-out-str
       ;; Special case for these Vars so that they pprint the correct
       ;; values.  Assume that their values do not need the
       ;; *print-length* or *print-level* restrictions to be short.
       ;; Since their values are almost always integers, this is
       ;; usually the case.
       (if (or (= v #'clojure.core/*print-length*)
               (= v #'clojure.core/*print-level*))
         (pprint v)
         (binding [*print-length* 32 *print-level* 6]
           (pprint v))))))


(defn val-kv-filter
  "For input of a key-value table with a key-truth table,
  collect and return all values for which the corresponding key
  in the truth table is true"
  [kv-val kv-true]
  (if (empty? kv-val)
    ()
    (let [only-k-true (set (map first (filter #(true? (second %)) kv-true)))
          only-val-true (replace kv-val only-k-true)]
    only-val-true)))

;; (val-kv-filter {:a "fn-a" :b "fn-b" :c "fn-c" :d "fn-d"} {:a true :b false :c true})
;; (val-kv-filter {:a "fn-a" :b "fn-b" :c "fn-c" :d "fn-d"} {:a false :b true :c false})
;; (val-kv-filter {:a "fn-a" :b "fn-b" :c "fn-c" :d "fn-d"} {:a true :b true :c true})
;; (val-kv-filter {:a "fn-a" :b "fn-b" :c "fn-c" :d "fn-d"} {:a false :b false :c false})
;; (val-kv-filter {} {:a true :b false :c true})
;; (val-kv-filter {:a "fn-a" :b "fn-b" :c "fn-c" :d "fn-d"} {})
;; (val-kv-filter {} {})


;; The clean-* functions are probably best removed after changing
;; cd-client.core functions so they no longer include the text we're
;; removing here.  They would be harmless even then, except for a bit
;; of inefficiency.
(defn clean-cd-client-examples [s]
  (let [lines (str/split-lines s)
        lines (remove (fn [l]
                        (or (= l "========== vvv Examples ================")
                            (= l "========== ^^^ Examples ================")
                            (re-matches #"\d+ examples? found for .*" l)))
                      lines)]
    (str/join "\n" lines)))

(defn clean-cd-client-see-alsos [s]
  (let [lines (str/split-lines s)
        lines (remove (fn [l]
                        (or (= l "========== vvv See also ================")
                            (= l "========== ^^^ See also ================")
                            (re-matches #"\d+ see-alsos? found for .*" l)))
                      lines)]
    (str/join "\n" lines)))

(defn clean-cd-client-comments [s]
  (let [lines (str/split-lines s)
        lines (remove (fn [l]
                        (or (= l "========== vvv Comments ================")
                            (= l "========== ^^^ Comments ================")
                            (re-matches #"\d+ comments? found for .*" l)))
                      lines)]
    (str/join "\n" lines)))

;; (defn clojuredocs-text
;;   [ns-str name-str info-type]
;;   (case info-type
;;     :examples (clean-cd-client-examples
;;                (with-out-str
;;                  (cd-client.core/pr-examples-core ns-str name-str)))
;;     :see-alsos (clean-cd-client-see-alsos
;;                 (with-out-str
;;                   (cd-client.core/pr-see-also-core ns-str name-str)))
;;     :comments (clean-cd-client-comments
;;                (with-out-str
;;                  (cd-client.core/pr-comments-core ns-str name-str)))))


;; ClojureDocs integration now provided by clj-info 0.6.0

(defn render-clojuredocs-text
  "Obtain and return examples, see alsos, or comments as a string from
  clojuredocs for fqn using clj-info 0.6.0 API"
  [real-fqn info-type is-ns?]
  (if is-ns?
    (str "Select individual symbols in the namespace to see " (name info-type))
    (let [fqn (if (some #(= % real-fqn) special-forms)
                (str "clojure.core/" real-fqn)
                real-fqn)]
      (if-let [content (get-clojuredocs-content fqn info-type)]
        (if (empty? content)
          ""
          (case info-type
            :examples (format-examples content)
            :see-alsos (format-see-alsos content)
            :comments (format-notes content)
            (str content)))
        (str "Sorry no " (name info-type) " available from ClojureDocs for: " fqn)))))


(def ^:dynamic *max-value-display-size* 2500)

;; TBD: Problems with these, probably because of problems with
;; cd-client.core behavior:
;; (render-doc-text "clojure.core/+" "Examples")
;; (render-doc-text "clojure.core/+" "See alsos")
;; (render-doc-text "clojure.core/+" "Comments")
;; These seem to work fine:
;; clojure.core/-
;; clojure.core/*

;; TBD: What to show for atoms, refs?  It might be useful some day to
;; "unwrap" and show the value inside, or at least have an easy way in
;; the GUI to do so.

;; TBD:
;; atom?
;; class?
;; future?  Will eval above force a future?  Should we avoid that?
;; realized?
;; special-symbol?
;; symbol?
;; var?

;; (in-ns 'clj-ns-browser.utils)


(defn render-meta
  "Returns a pprint'ed string of the fqn's object's meta-data map or nil if none."
  [fqn]
  (when-let [v (if (or (string? fqn)(symbol? fqn)) (resolve-fqname fqn) fqn)]
    (when-let [m (meta v)]
      (pprint-str (meta v)))))


(defn clj-types-str 
  ""
  [v]
  (str
    (when (namespace? v) "<namespace> " )
    (when (class? v) "<class> " )
    (when (var? v) "<var> " )
    (when (dynamic? v) "<dynamic> " )
    (when (special-form? v) "<special-form> " )
    (when (macro? v) "<macro> " )
    (when (protocol? v) "<protocol> " )
    (when (protocol-fn? v) "<protocol-fn> " )
    (when (multimethod? v) "<multimethod> " )
    (when (defn? v) "<defn> " )
    (when (fn? v) "<fn> " )
    (when (ifn? v) "<ifn> " )
    (when (char? v) "<char> " )
    ;(when (var-traced? v) "<var-traced> " )
    (when (atom? v) "<atom> " )
    (when (string? v) "<string> " )
    (when (symbol? v) "<symbol> " )
    (when (deftype? v) "<deftype> " )
    (when (defrecord? v) "<defrecord> " )
    (when (keyword? v) "<keyword> " )
    (when (coll? v) "<coll> " )
    (when (list? v) "<list> " )
    (when (map? v) "<map> " )
    (when (seq? v) "<seq> " )
    (when (sequential? v) "<sequential> " )
    (when (set? v) "<set> " )
    (when (vector? v) "<vector> " )
    (when (number? v) "<number> " )
    (when (decimal? v) "<decimal> " )
    ))

(defn render-fqn-value
  ""
  [fqn]
  (let [fqn-str (fqname fqn)
        fqn-sym (fqname-symbol fqn)]
    (if (special-symbol? fqn-sym)
      (str "TYPE:   <Special-Symbol>")
      (let [fqn-v (resolve-fqname fqn)]
        (cond
          (var? fqn-v)
            (str  "TYPE:  " (type fqn-v)
                "\n       " (clj-types-str fqn-v)
                  \newline
                 "\n@TYPE: " (type @fqn-v)
                 "\n       " (clj-types-str @fqn-v)
                  \newline
                 "\nVALUE: \n" (pprint-str fqn-v)
                 "\n@Value: \n" (pprint-str @fqn-v))
          (class? fqn-v)
            (str "TYPE:  " (type fqn-v)
                 "\n       " (clj-types-str fqn-v)
                 "\n\nVALUE: \n" (pprint-str fqn-v)
                 "\nCLASS-INHERITANCE:\n"
                 (pprint-str (class-hierarchy-list fqn-v))
                 "\nINTERFACES:\n"
                 (pprint-str (interface-hierachy-tree fqn-v))
              )
          :else
            (str "TYPE:  " (type fqn-v)
                 "\n       " (clj-types-str fqn-v)
                 "\nVALUE: \n" (pprint-str fqn-v))
            )))))


(defn render-one-doc-text
  "Given a FQN, return the doc or source code as string, based on options."
  [fqn doc-opt]
  (when-not (or (nil? fqn) (= fqn "") (nil? doc-opt))
    (let [is-ns? (find-ns (symbol fqn))]
      (case doc-opt
        "Doc"
        (let [m (if (= fqn "clojure.core//")
                  {:title "clojure.core//   -   Function",
                   :message (with-out-str (clojure.repl/doc clojure.core//))}
                  (doc2txt fqn))
              ;;m (doc2txt (str (selection ns-lb) "/" s))
              ;;m-html (doc2html (str (selection ns-lb) "/" s))
              txt (str (:title m) \newline (:message m))]
          txt)

        "Source"
        (if is-ns?
          (str "Select individual symbols in the namespace to see source")
          (if-let [source-str (try (clojure.repl/source-fn (fqname-symbol fqn))
                                   (catch Exception e))]
            source-str
            (str "Sorry - no source code available for " fqn)))

        "Examples" (render-clojuredocs-text fqn :examples is-ns?)
        "Comments" (render-clojuredocs-text fqn :comments is-ns?)
        "See alsos" (render-clojuredocs-text fqn :see-alsos is-ns?)
        "Value" (render-fqn-value fqn)
        "Meta" (let [s (render-meta fqn)]
                 (str 
                   (if s (str "META:\n" s) "")
                     (let [v (resolve-fqname fqn)]
                       (if-let [s (and (var? v) (render-meta @v))]
                         (str "\n@META:\n" s)
                           ""))))
        (str "Internal error - clj-ns-browser.utils/render-doc-text got unknown doc-opt='" doc-opt "'")))))


(defn render-doc-text
  "Given a FQN, return the doc or source code as string, based on options."
  [fqn doc-opt-lst]
  (when-not (or (nil? fqn) (= fqn "") (nil? doc-opt-lst))
    (let [is-ns? (find-ns (symbol fqn))]
      (if (= 1 (count doc-opt-lst))
        (render-one-doc-text fqn (first doc-opt-lst))
        (let [headings {"Doc" ""
                        "Source" "SOURCE:"
                        "Examples" "EXAMPLES:"
                        "Comments" "COMMENTS:"
                        "See alsos" "SEE ALSO:"
                        "Value" ""
                        "Meta" ""}
              doc-lst (->> doc-opt-lst
                           (map (fn [doc-opt]
                                  {:doc-opt doc-opt
                                   :doc (str/trim-newline (render-one-doc-text fqn doc-opt))
                                   :heading (headings doc-opt)}))
                           (remove #(or (nil? (:doc %)) (str/blank? (:doc %))))
                           (map #(if (str/blank? (:heading %))
                                   (:doc %)
                                   (str (:heading %) "\n" (:doc %)))))]
          (str/join "\n\n\n" doc-lst))))))


;; (defn clojuredocs-url
;;   "Returns the clojuredoc url for given FQN"
;;   [fqn]
;;   (let [r (ns-name-class-str fqn)]
;;     (when (and (first r) (second r))
;;       (:url (cd-client.core/examples (first r) (second r))))))

(defn clojuredocs-url
  "Returns the clojuredoc url for given FQN"
  [fqn]
  (let [r (ns-name-class-str fqn)]
    ;; (ns-name-class-str "clojure.core/map") => ["clojure.core" "map"]
    (when (and (first r) (second r))
      ;; https://clojuredocs.org/clojure.core/map
      (str "https://clojuredocs.org/" (first r) "/" (second r)))))

(defn meta-when-file
  "Returns the path to the source file for fqn,
  or nil if none applies."
  [fqn]
  (when-let [v (resolve-fqname fqn)]
    (when-let [m (meta v)]
      (when-let [f (:file m)]
        (if (= (first f) \/)
          m ;; absolute path is good
          (let [p (str "src/" f)] ;; assume we start from project-dir
            ;; check for file existence of p
            (when (.exists (clojure.java.io/as-file p))
              (assoc m :file p))))))))


(defn unresolve
  "Given a var, return a sequence of all symbols that resolve to the
  var from the current namespace *ns*."
  [var]
  (when-not (instance? clojure.lang.Var var)
    (throw (Exception. (format "unresolve: first arg must be Var"))))
  (let [home-ns (.ns var)
        sym-name-str (second (re-find #"/(.*)$" (str var)))]
    (sort-by
     #(count (str %))
     (concat
      ;; The symbols in the current namespace that map to the var, if
      ;; any
      (->> (ns-map *ns*)
           (filter (fn [[k v]] (= var v)))
           (map first))
      ;; This is the "canonical" symbol that resolves to the var, with
      ;; full namespace/symbol-name
      (list (symbol (str home-ns) sym-name-str))
      ;; There might be one or more aliases for the symbol's home
      ;; namespace defined in the current namespace.
      (->> (ns-aliases *ns*)
           (filter (fn [[ns-alias ns]] (= ns home-ns)))
           (map first)
           (map (fn [ns-alias-symbol]
                  (symbol (str ns-alias-symbol) sym-name-str))))))))


(defn apropos
  "Given a regular expression or stringable thing, calculate a
  sequence of all symbols in all currently-loaded namespaces such that
  it matches the str-or-pattern, with at most one such symbol per Var.
  The sequence returned contains symbols that map to those Vars, and are
  the shortest symbols that map to the Var, when qualified with the
  namespace name or alias, if that qualification is necessary to name
  the Var.  Note that it is possible the symbol returned does not match
  the str-or-pattern itself, e.g. if the symbol-to-var mapping was
  created with :rename.
  
  Searches through all non-Java symbols in the current namespace, but
  only public symbols of other namespaces."
  [str-or-pattern & opts]
  (let [matches? (if (instance? java.util.regex.Pattern str-or-pattern)
                   #(re-find str-or-pattern (str %))
                   #(.contains (str %) (str str-or-pattern)))]
    (map #(first (unresolve %))
         (set
          (mapcat (fn [ns]
                    (map second
                         (filter (fn [[s v]] (matches? s))
                                 (if (= ns *ns*)
                                   (concat (ns-interns ns) (ns-refers ns))
                                   (ns-publics ns)))))
                  (all-ns))))))


(defn apro
  "Shorter-name version of apropos that also sorts and pretty-prints
  the results."
  [str-or-pattern & opts]
  (pprint (sort (apply apropos str-or-pattern opts))))


(defn read-safely
  "Read Clojure data from the source specified by the args (passed on
to clojure.java.io/reader) with *read-eval* false, to avoid executing
any code."
  [x & opts]
  (with-open [r (java.io.PushbackReader. (apply clojure.java.io/reader x opts))]
    (binding [*read-eval* false]
      (read r))))


(def default-settings
  {:clojuredocs-online true
   :vars-categorized-listing false
   :vars-fqn-listing false
   :vars-search-doc-also false
   })

(defn settings-filename []
  (str (System/getProperty "user.home") "/.clj-ns-browser-settings.txt"))

(defn read-settings []
  (let [f (settings-filename)]
    (if (.exists (clojure.java.io/as-file f))
      ;; merge of settings read from file with default-settings allows
      ;; clj-ns-browser developers to add new key/value pairs to
      ;; default-settings in later versions, and users of previous
      ;; versions will automatically pick up those new default
      ;; settings.
      (merge default-settings (read-safely f))
      default-settings)))

(defn write-settings! [settings]
  ;; TBD: What is most reliable way to print settings so it can be
  ;; read back in with read-safely?
  (spit (settings-filename) (with-out-str (clojure.pprint/pprint settings))))


(defn list-with-elem-at-index
  "Given a sequence cur-order and elem-to-move is one of the items
within it, return a vector that has all of the elements in the same
order, except that elem-to-move has been moved to just before the
index new-idx.

Examples:
user=> (def l [\"a\" \"b\" \"c\" \"d\"])
user=> (list-with-elem-at-index l \"b\" 0)
[\"b\" \"a\" \"c\" \"d\"]
user=> (list-with-elem-at-index l \"b\" 1)
[\"a\" \"b\" \"c\" \"d\"]
user=> (list-with-elem-at-index l \"b\" 2)
[\"a\" \"b\" \"c\" \"d\"]
user=> (list-with-elem-at-index l \"b\" 3)
[\"a\" \"c\" \"b\" \"d\"]
user=> (list-with-elem-at-index l \"b\" 4)
[\"a\" \"c\" \"d\" \"b\"]"
  [cur-order elem-to-move new-idx]
  (let [cur-order (vec cur-order)
        cur-idx (.indexOf cur-order elem-to-move)]
    (if (= new-idx cur-idx)
      cur-order
      (if (< new-idx cur-idx)
        (vec (concat (subvec cur-order 0 new-idx)
                     [ elem-to-move ]
                     (subvec cur-order new-idx cur-idx)
                     (subvec cur-order (inc cur-idx))))
        ;; else new-idx > cur-idx
        (vec (concat (subvec cur-order 0 cur-idx)
                     (subvec cur-order (inc cur-idx) new-idx)
                     [ elem-to-move ]
                     (subvec cur-order new-idx)))))))
