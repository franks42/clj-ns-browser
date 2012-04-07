;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.utils
  (:require [cljsh.completion]
            [clojure.set]
            [cd-client.core]
            [clojure.java.shell]
            [clojure.tools.namespace])
  (:use [seesaw.core]
        [clojure.pprint :only [pprint]]
        [clj-info.doc2txt :only [doc2txt]]
        [clj-info.doc2html :only [doc2html]]))

(def special-forms
  (sort (map name '[def if do let quote var fn loop recur throw try monitor-enter monitor-exit dot new set!])))

(defn ns-special-forms [& no-op]
  '{"def" def "if" if "do" do "let" let "quote" quote "var" var "fn" fn "loop" loop "recur" recur "throw" throw "try" try "monitor-enter" monitor-enter "monitor-exit" monitor-exit "dot" dot "new" new "set!" set!})

(defn all-ns-classpath
  "Returns a sorted set of the name-strings of all namespaces found on class-path."
  []
  (apply sorted-set
    (map str (clojure.tools.namespace/find-namespaces-on-classpath))))
;;   (apply sorted-set
;;     (map  (comp cljsh.completion/class-or-ns-name :name)
;;           (filter cljsh.completion/clojure-ns?
;;             cljsh.completion/available-classes))))


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


(defn fqname
  "Returns the fully qualified name-string of a var, class or ns
  for a name n within an (optional) namespace, which may or
  may not be used for the resolution, depending whether n is already FQ'ed.
  Input n: string, (quoted-)symbol, var, or actual class or ns.
        ns: (optional) namespace as ns, symbol or string - default *ns*.
  Output: FQName as string or nil."
  ([n] (cond
          (keyword? n) (if-let [nsp (namespace n)]
                         (str nsp "/" (name n))
                         (name n))
          :else (fqname *ns* n)))
  ([ns n]
    (let [n-str (str n)
          n-sym (symbol n-str)]
      (if (some #(= % n-str) special-forms) n-str
        (if-let [n-ns (find-ns n-sym)]
          (str n-ns)
          (if-let [n-class (= (type n) java.lang.Class)]
            (.getName n)
            (when-let [ns-ns (if (= (type ns) clojure.lang.Namespace)
                               ns
                               (find-ns (symbol (str ns))))]
              (if-let [var-n (when (var? n) (str (.ns n) "/" (.sym n)))]
                var-n
                (let [n-sym (symbol (str n))]
                  (when-let [v-n (try (ns-resolve ns-ns n-sym)(catch Exception e))]
                    (if (var? v-n)
                      (str (.ns v-n) "/" (.sym v-n))
                      (.getName v-n))))))))))))

(defn fqname-kw
  "Returns the fqn-string for a keyword (without the ':')"
  [k]
  (if-let [n (namespace k)] (str n "/" (name k)) (name k)))

(defn ns-name-class-str
  "Given a FQN, return the namespace and name in a list as separate strings.
  clojure.core/map => [\"clojure.core\" \"map\"]
  clojure.core => [\"clojure.core\" nil]
  clojure.lang.Var => [nil \"clojure.lang.Var\"]
  any error returns nil."
  [fqn]
  (when-let [fqn-sym (and fqn (= (type fqn) java.lang.String) (symbol fqn))]
    (let [n-str (name fqn-sym)
          ns-str (namespace fqn-sym)]
      (if ns-str
        [ns-str n-str]
        (if (find-ns (symbol n-str))
          [n-str nil]
          [nil n-str])))))


(defn resolve-fqname
  "Returns the resolved fully qualified name fqn (string) or nil."
  [fqn]
  (when (and (string? fqn) (not= fqn ""))
    (if-let [ns (find-ns (symbol fqn))]
      ns
      (when-let [var-or-class (try (resolve *ns* (symbol fqn))(catch Exception e))]
        var-or-class))))

(defn pprint-str
  "Return string with pprint of v, and limit output to prevent blowup."
  ([v & kvs]
    (with-out-str (binding [*print-length* 6 *print-level* 6] (pprint v)))))


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

(defn no-nils [coll] (filter #(not (nil? %)) coll))


(defn clojuredocs-text
  [ns-str name-str info-type]
  (with-out-str
    (case info-type
      :examples (cd-client.core/pr-examples-core ns-str name-str)
      :see-alsos (cd-client.core/pr-see-also-core ns-str name-str)
      :comments (cd-client.core/pr-comments-core ns-str name-str))))


(defn render-clojuredocs-text
  "Obtain and return examples, see alsos, comments, or all as a string
from clojuredocs for fqn"
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
          s
          (str "Sorry no " (name info-type) " available from clojuredoc for: "
               fqn))
        (str "Sorry, not a fully qualified clojure name: " fqn)))))


(defn render-doc-text
  "Given a FQN, return the doc or source code as string, based on options."
  [fqn doc-opt]
  (when-not (or (nil? fqn) (= fqn "") (nil? doc-opt))
    (let [is-ns? (find-ns (symbol fqn))]
      (case doc-opt
        ;; quick to write, if a little inefficient
        "All" (if is-ns?
                (str (render-doc-text fqn "Doc") "\n\n"
                     (render-doc-text fqn "Source"))
                (str (render-doc-text fqn "Doc") "\n\n"
                     (render-doc-text fqn "Examples") "\n"
                     (render-doc-text fqn "See alsos") "\n"
                     (render-doc-text fqn "Source") "\n\n"
                     (render-doc-text fqn "Comments") "\n"
                     (render-doc-text fqn "Value")))
        "Doc"
        (let [m (doc2txt fqn)
              ;;m (doc2txt (str (selection ns-lb) "/" s))
              ;;m-html (doc2html (str (selection ns-lb) "/" s))
              txt (str (:title m) \newline (:message m))]
          txt)

        "Source"
        (if is-ns?
          (str "Select individual symbols in the namespace to see source")
          (if-let [source-str (try (clojure.repl/source-fn (symbol fqn))
                                   (catch Exception e))]
            source-str
            (str "Sorry - no source code available for " fqn)))

        "Examples" (render-clojuredocs-text fqn :examples is-ns?)
        "Comments" (render-clojuredocs-text fqn :comments is-ns?)
        "See alsos" (render-clojuredocs-text fqn :see-alsos is-ns?)
        "Value" (str "Sorry - no value available for " fqn)
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
  (let [fqn-sym (symbol fqn)]
    (when-let [v (and (namespace fqn-sym) (find-ns (symbol (namespace fqn-sym))) (find-var fqn-sym))]
      (when-let [m (meta v)]
        (when-let [f (:file m)]
          (if (= (first f) \/)
            m ;; absolute path is good
            (let [p (str "src/" f)] ;; assume we start from project-dir
              ;; check for file existence of p
              (when (zero? (:exit (clojure.java.shell/sh "bash" "-c" (str "[ -f " p " ]"))))
                (assoc m :file p)))))))))

