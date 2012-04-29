;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.utils
  (:require [clojure.set]
            [cd-client.core]
            [clojure.java.shell]
            [clojure.java.io]
            [clojure.string :as str]
            [clojure.tools.namespace]
            [clojure.tools.trace])
  (:use [seesaw.core]
        [clojure.pprint :only [pprint]]
        [clj-info.doc2map :only [get-docs-map]]
        [clj-info.doc2txt :only [doc2txt]]
        [clj-info.doc2html :only [doc2html]]
        [alex-and-georges.debug-repl]))


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


;; "clojure.core//" appears to be a special case not handled well by
;; the function symbol.
(defn better-symbol [fqn]
  (if (= fqn "clojure.core//")
    (symbol "clojure.core" "/")
    (symbol fqn)))


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
  (isa? clojure.lang.Atom (type v)))


(defn multimethod?
  "Predicate that returns true when var v refers to a multi-method, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (multimethod? (resolve-fqname n))"
  [v]
  (isa? clojure.lang.MultiFn (type v)))


(defn protocol?
  "Predicate that returns true when var v refers to a protocol, and false otherwise.
  Note that input is a var - if you want to input a name-string or -symbol, use:
  (protocol? (resolve-fqname n))"
  [v]
  (and (var? v) (isa? clojure.lang.PersistentArrayMap (type @v)) (:on-interface @v) true))


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
  (isa? clojure.lang.Namespace (type maybe-ns)))


;; Two convenience function for clojure.tools.trace
;; that should ideally be part of that library

(defn var-traceable? 
  "Predicate that returns whether a var is traceable or not."
  [v]
  (and (var? v) (ifn? @v) (-> v meta :macro not)))


(defn var-traced?
  "Predicate that returns whether a var is currently being traced.
  (should ideally be part of clojure.tools.trace such that we can
  remain oblivious about the trace-implementation internals)"  
  ([ns s]
     (var-traced? (ns-resolve ns s)))
  ([v]
     (let [vv (if (var? v) v (resolve v))]
       (not (nil? ((meta vv) ::clojure.tools.trace/traced))))))


;; should move to clj-info

(defn get-object-type
  ""
  [fqn]
  (if-let [m (get-docs-map fqn)]
    (:object-type-str m)
    ""))


;; following three clipboard-related functions copied from lib.sfd.clip-utils of
;; https://github.com/francoisdevlin/devlinsf-clojure-utils/
;; library seems a little abandoned, but the following functions just work.
;; Kudos to Sean Devlin.

(defn- get-sys-clip
  "A helper fn to get the clipboard object"
  []
  (. (java.awt.Toolkit/getDefaultToolkit) getSystemClipboard))

(defn get-clip
  "Get the contents of the clipboard.  Currently only supports text."
  []
  (let [clipboard (get-sys-clip)]
    (if clipboard
      (let [contents (. clipboard getContents nil)]
	(cond
	 (nil? contents) nil
	 (not (. contents isDataFlavorSupported java.awt.datatransfer.DataFlavor/stringFlavor)) nil
	 true (. contents getTransferData java.awt.datatransfer.DataFlavor/stringFlavor))))))

(defn set-clip!
  "Set the contents of the clipboard.  Currently only supports text."
  [input-string]
  (if input-string
    (let [clipboard (get-sys-clip)]
      (if clipboard
	(do
	  (let [selection (java.awt.datatransfer.StringSelection. input-string)]
	    (. clipboard setContents selection nil))
	  input-string)))))


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
                                :name-to-search (str sym)})))]
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


(defn ns-is-unloaded?
  "Returns whether a ns is on the classpath but not loaded/required (yet)."
  [ns]
  (let [nss (str ns)] (get (all-ns-unloaded) nss)))


(defn pprint-str
  "Return string with pprint of v, and limit output to prevent blowup."
  ([v & kvs]
    (with-out-str (binding [*print-length* 32 *print-level* 6] (pprint v)))))


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

(defn clojuredocs-text
  [ns-str name-str info-type]
  (case info-type
    :examples (clean-cd-client-examples
               (with-out-str
                 (cd-client.core/pr-examples-core ns-str name-str)))
    :see-alsos (clean-cd-client-see-alsos
                (with-out-str
                  (cd-client.core/pr-see-also-core ns-str name-str)))
    :comments (clean-cd-client-comments
               (with-out-str
                 (cd-client.core/pr-comments-core ns-str name-str)))))


(defn render-clojuredocs-text
  "Obtain and return examples, see alsos, or comments as a string from
  clojuredocs for fqn"
  [real-fqn info-type is-ns?]
  (if is-ns?
    (str "Select individual symbols in the namespace to see " (name info-type))
    (let [fqn (if (some #(= % real-fqn) special-forms)
                (str "clojure.core/" real-fqn)
                real-fqn)
          name-str (name (symbol fqn))
          ns-str (namespace (symbol fqn))]
      (if ns-str
        (if-let [s (clojuredocs-text ns-str name-str info-type)]
          (if (str/blank? s)
            ""
            (str (str/trim-newline s) "\n"))
          (str "Sorry no " (name info-type) " available from clojuredoc for: "
               fqn))))))


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
  (when-let [v (resolve-fqname fqn)]
    (when-let [m (meta v)]
      (pprint-str (meta v)))))


(defn clj-types-str 
  ""
  [v]
  (str
    (when (special-form? v) "<special-form> " )
    (when (macro? v) "<macro> " )
    (when (protocol? v) "<protocol> " )
    (when (protocol-fn? v) "<protocol-fn> " )
    (when (multimethod? v) "<multimethod> " )
    (when (defn? v) "<defn> " )
    (when (fn? v) "<fn> " )
    (when (ifn? v) "<ifn> " )
    (when (char? v) "<char> " )
    (when (namespace? v) "<namespace> " )
    ;(when (var-traced? v) "<var-traced> " )
    (when (atom? v) "<atom> " )
    (when (string? v) "<string> " )
    (when (symbol? v) "<symbol> " )
    (when (var? v) "<var> " )
    (when (class? v) "<class> " )
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
                 "\nVALUE: \n" (pprint-str fqn-v))
                 ;;"\n  @Value: \n" (pprint-str @fqn-v))
          :else
            (str "TYPE:  " (type fqn-v)
                "\n       " (clj-types-str fqn-v)
                 "\nVALUE: \n" (pprint-str fqn-v))
            )))))


(defn render-doc-text
  "Given a FQN, return the doc or source code as string, based on options."
  [fqn doc-opt]
  (when-not (or (nil? fqn) (= fqn "") (nil? doc-opt))
    (let [is-ns? (find-ns (symbol fqn))]
      (case doc-opt
        ;; quick to write, if a little inefficient
        "All" (if is-ns?
                (str (render-doc-text fqn "Doc")
                     "\n\nSource:\n"
                     (render-doc-text fqn "Source"))
                (str (render-doc-text fqn "Doc")
                     "\n\n"
                     (let [s (render-doc-text fqn "Examples")]
                       (if (or (nil? s)(= s ""))
                         ""
                         (str "\nEXAMPLES\n" s)))
                     (let [s (render-doc-text fqn "See alsos")]
                       (if (or (nil? s)(= s ""))
                         ""
                         (str "\nSEE ALSO\n" s)))
                     (let [s (render-doc-text fqn "Comments")]
                       (if (or (nil? s)(= s ""))
                         ""
                         (str "\nCOMMENTS\n" s)))
                     "\n"
                     (render-doc-text fqn "Value")
                     "\nSOURCE\n"
                     (render-doc-text fqn "Source")
                     "\n"
                     (let [s (render-meta fqn)]
                       (if (or (nil? s)(= s ""))
                         ""
                         (str "\nMETA\n" s)))))
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
        "Meta" (render-meta fqn)
        (str "Internal error - clj-ns-browser.utils/render-doc-text got unknown doc-opt='" doc-opt "'")))))


(defn clojuredocs-url
  "Returns the clojuredoc url for given FQN"
  [fqn]
  (let [r (ns-name-class-str fqn)]
    (when (and (first r)(second r))
      (:url (cd-client.core/examples (first r) (second r))))))

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

(defn ns-refers-wo-core 
  "Same as ns-refers, but without the clojure.core contribution."
  [a-ns]
  (let [refers (ns-refers a-ns)
        refers-keys (set (keys refers))
        core-keys (set (keys (ns-publics (find-ns 'clojure.core))))
        refers-keys-wo (clojure.set/difference refers-keys core-keys)]
    (select-keys refers refers-keys-wo)))
