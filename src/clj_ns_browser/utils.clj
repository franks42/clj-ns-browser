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
            [clojure.java.shell])
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
    (map  (comp cljsh.completion/class-or-ns-name :name)
          (filter cljsh.completion/clojure-ns?
            cljsh.completion/available-classes))))


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
  ([n] (fqname *ns* n))
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


(defn render-examples-text
  "Obtain and return example string from clojuredoc for fqn"
  [fqn]
  (let [name-str (name (symbol fqn))
        ns-str (namespace (symbol fqn))]
    (if ns-str
      (if-let [example-str (with-out-str
                             (cd-client.core/pr-examples-core ns-str name-str))]
        example-str
        (str "Sorry no examples available from clojuredoc for: " fqn))
      (str "Sorry, not a fully qualified clojure name: " fqn))))


(defn render-comments-text
  "Obtain and return comments string from clojuredoc for fqn"
  [fqn]
  (let [name-str (name (symbol fqn))
        ns-str (namespace (symbol fqn))]
    (if ns-str
      (if-let [comments-str (with-out-str
                             (cd-client.core/pr-comments-core ns-str name-str))]
        comments-str
        (str "Sorry no comments available from clojuredoc for: " fqn))
      (str "Sorry, not a fully qualified clojure name: " fqn))))


(defn render-doc-text
  "Given a FQN, return the doc or source code as string, based on options."
  [fqn doc-opt]
  (when-not (or (nil? fqn) (= fqn "")(nil? doc-opt))
    (cond
      (= doc-opt "Doc")
        (let [m (doc2txt fqn)
              ;m (doc2txt (str (selection ns-lb) "/" s))
              ;m-html (doc2html (str (selection ns-lb) "/" s))
              txt (str (:title m) \newline (:message m))]
          txt)
      (= doc-opt "Source")
        (if-let [source-str (try (clojure.repl/source-fn (symbol fqn))
                              (catch Exception e))]
          source-str
          (str "Sorry - no source code available for " fqn))
      (= doc-opt "Examples")
          (render-examples-text fqn)
      (= doc-opt "Comments")
          (render-comments-text fqn)
      (= doc-opt "Value")
          (str "Sorry - no value available for " fqn))))


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

