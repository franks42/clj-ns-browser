;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.core
  (:require cljsh.completion)
  (:require clojure.java.javadoc)
  (:require clojure.repl)
  (:require clojure.set)
  (:use clj-ns-browser.utils)
  (:require [clj-ns-browser.seesaw :as ss])
  ;(:require [clj-ns-browser.mig :as m])
  ;(:use clj-ns-browser.seesaw)
  ;(:use [seesaw.core :exclude [config config! select]])
  (:use seesaw.core)
  (:use seesaw.border)
  (:use seesaw.mig)
  (:use seesaw.dev)
  (:require seesaw.selector)
  (:require [seesaw.bind :as b])
  (:use [clj-info])
  (:use [clj-info.doc2txt :only [doc2txt]])
  (:use [clj-info.doc2html :only [doc2html]]))


;(declare ^:dynamic *root-frm*)


; This is the interesting part. Note that in MyPanel.java, the widgets we're
; interested in have their name set with setName().
(defn identify
  "Given a root widget, find all the named widgets and set their Seesaw :id
   so they can play nicely with select and everything."
  [root]
  (doseq [w (select root [:*])]
    (when-let [n (.getName w)]
      (seesaw.selector/id-of! w (keyword n))))
  root)


(native!)

;;(b/transform regx-tf-filter :ns-filter-tf)
(defn regx-tf-filter
  "filter for use in bind that filters string-list s-l with regex of text-field t-f"
  [s-l t-f]
  (when-let [r (try (re-pattern (ss/config t-f :text)) (catch Exception e nil))]
    (filter #(re-find r %) s-l)))



(defn display [f content]
  (config! f :content content)
  content)

(defn new-root-frm [] (frame :title "Clojure Namespace Browser"))

(defn new-browser-form [] (identify (clj_ns_fn_browser.BrowserForm.)))

(when-not (var? ss/*root-frm*)
  (ss/set-root-frm! (new-root-frm))
  ;(def ^:dynamic ss/*root-frm* (new-root-frm))
  (config! ss/*root-frm* :content (new-browser-form)))
  ;(config! ss/*root-frm* :content (m/frame-content)))


(defn init-root []
  (ss/set-root-frm! (new-root-frm))
  ;(def ^:dynamic ss/*root-frm* (new-root-frm))
  (config! ss/*root-frm* :content (new-browser-form)))
  ;(config! ss/*root-frm* :content (m/frame-content)))


(defn refresh [& not-used] (invoke-later (show! (pack! (ss/get-root-frm)))))


(defn -main [& args])

;; ns-cbx, vars-cbx, doc-cbx, doc-tf
;; ns
(def all-ns-loaded-atom (atom nil))
(def all-ns-unloaded-atom (atom nil))
(def ns-cbx-value-list ["loaded" "unloaded"])
(defn ns-loaded [] @all-ns-loaded-atom)
(defn ns-unloaded [] @all-ns-unloaded-atom)
(def ns-cbx-value-fn-map {  "loaded"    ns-loaded
                            "unloaded"  ns-unloaded})
(def ns-require-btn-atom (atom true))
;; vars
(def vars-cbx-value-list ["aliases" "imports" "interns"
                          "map" "publics" "refers" "special-forms"])
(def vars-cbx-value-fn-map {"aliases"   ns-aliases
                            "imports"   ns-imports
                            "interns"   ns-interns
                            "map"       ns-map
                            "publics"   ns-publics
                            "refers"    ns-refers
                            "special-forms" ns-special-forms})
;; doc
(def doc-cbx-value-list ["Doc" "Examples" "Source" "Value"])
(def doc-cbx-value-fn-map { "Doc"       'doc-text
                            "Examples"  'examples-text
                            "Source"    'source-text
                            "Value"     'value-text})


(defn init-before-bind
  []
  ;; ns
  (swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
  (ss/config! :ns-lb :model @all-ns-loaded-atom)
  (ss/config! :vars-lb :model [])
  (ss/config! :ns-entries-lbl :text "0")
  (ss/config! :ns-require-btn :enabled? false)
  (listen (ss/select :ns-require-btn)
    :action (fn [event] (swap! ns-require-btn-atom #(not %))))
  ;; vars
  (ss/config! :vars-entries-lbl :text "0")
  ; doc
  (ss/config! :doc-tf :text "")
  (ss/config! :doc-ta :text "                                                                        ")
  (ss/selection! (ss/select :ns-cbx) "loaded")
  (ss/selection! (ss/select :vars-cbx) "publics")
  (ss/selection! (ss/select :vars-cbx) "Doc"))


(defn init-after-bind
  []
  (swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
  (ss/selection! (ss/select :ns-cbx) "loaded"))


(defn b-model-count [_ o]
  (.getSize (ss/config o :model)))

(defn bind-all
  []
  ;; # of entries in ns-lb => ns-entries-lbl
  (b/bind
    (b/property (ss/select :ns-lb) :model)
    (b/transform b-model-count :ns-lb)
    (ss/select :ns-entries-lbl))
  ;; # of entries in vars-lb => vars-entries-lbl
  (b/bind
    (b/property (ss/select :vars-lb) :model)
    (b/transform b-model-count :vars-lb)
    (ss/select :vars-entries-lbl))
  ;;
  (b/bind
    (b/selection (ss/select :ns-lb))
      (b/tee
        (b/bind
          (b/transform (fn [ns]
            (if (and ns (some #(= ns %) @all-ns-unloaded-atom))
              true
              false)))
          (b/property (ss/select :ns-require-btn) :enabled?))
      (b/bind
        (b/transform (fn [ns]
          (if (and ns (find-ns (symbol ns)))
            (fqname ns)
            "")))
        (b/tee
          (b/property (ss/select :doc-tf) :text)))))
  ;;
  (b/bind
    ns-require-btn-atom
    (b/transform (fn [& b]
      (when-let [n (ss/selection :ns-lb)]
        (require (symbol n))
        (swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
        (swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
        (let [i (.indexOf (seq @all-ns-loaded-atom) n)]
          (if (pos? i)
            (do
              (ss/selection! (ss/select :ns-cbx) "loaded")
              (ss/selection! :ns-lb n)
              (scroll! (ss/select :ns-lb) :to [:row i]))
            (alert (str "Hmmm... seems that namespace \"" n "\" cannot be required (?)"))))))))
  ;;
  ;; vars
  (b/bind
    (b/selection (ss/select :vars-lb))
    (b/transform (fn [v]
      (if v
        (let [fqn (fqname (ss/selection :ns-lb) v)]
          (if (and fqn (not (= fqn "")))
            fqn
            (when (= (ss/selection :vars-cbx) "aliases")
              (when-let [n (get (ns-aliases (symbol (ss/selection :ns-lb))) (symbol v))]
                (str n))))))))
    (b/tee
        (b/property (ss/select :doc-tf) :text)))
;;
;;
;; ;;     (b/selection (ss/select :vars-lb))
;; ;;     (b/transform (fn [s]
;; ;;       (when-let [s (selection e)]
;; ;;         (let [doc-opt (config (selection doc-rbs-group) :id)]
;; ;;           (render-doc-text s doc-opt)))))
;; ;;     (b/property (ss/select :doc-ta) :text))
;;   ;;
  ;; (un-)loaded cbs and regex filter tf => ns-lb
  (b/bind
    (ss/funnel
;;       [(b/selection (ss/select :ns-cbx))
      [(ss/select :ns-cbx)
       (ss/select :ns-filter-tf)])
    (b/transform (fn [o]
      (let [v (ss/selection (ss/select :ns-cbx))]
        ((get ns-cbx-value-fn-map v)))))
    (b/transform regx-tf-filter :ns-filter-tf)
    (b/notify-soon)
    (b/property (ss/select :ns-lb) :model))
  ;;
  (b/bind
    (apply b/funnel
      [(b/selection (ss/select :vars-cbx))
       (b/selection (ss/select :ns-lb))
       (ss/select :vars-filter-tf)])
    (b/transform (fn [o]
      (let [n-s (ss/selection :ns-lb)
            n (and n-s (find-ns (symbol n-s)))
            v (ss/selection (ss/select :vars-cbx))
            f (get vars-cbx-value-fn-map v)]
        (if n
          (seq (sort (map str (keys (f n)))))
          []))))
    (b/transform regx-tf-filter :vars-filter-tf)
    (b/notify-soon)
    (b/property (ss/select :vars-lb) :model))
  ;;
  (b/bind
    ; As the text of the textbox changes ...
    (ss/select :ns-filter-tf)
    ; Convert it to a regex, or nil if it's invalid
    (b/transform #(try (re-pattern %) (catch Exception e nil)))
    ; Now split into two paths ...
    (b/bind
      (b/transform #(if % "white" "lightcoral"))
      (b/notify-soon)
      (b/property (ss/select :ns-filter-tf) :background)))
  ;;
  (b/bind
    ; As the text of the textbox changes ...
    (ss/select :vars-filter-tf)
    ; Convert it to a regex, or nil if it's invalid
    (b/transform #(try (re-pattern %) (catch Exception e nil)))
    ; Now split into two paths ...
    (b/bind
      (b/transform #(if % "white" "lightcoral"))
      (b/notify-soon)
      (b/property (ss/select :vars-filter-tf) :background)))
  ;;
  (b/bind
    ; As the text of the fqn text field changes ...
    (ss/funnel [(ss/select :doc-tf)
                (b/selection (ss/select :doc-cbx))])
    (b/transform #(render-doc-text (first %) (ss/selection :doc-cbx)))
    (b/notify-soon)
    (b/property (ss/select :doc-ta) :text))
  ;
  (b/bind
    (ss/select :doc-ta)
    (b/notify-later)
    (b/transform (fn [t] (scroll! (ss/select :doc-ta) :to :top))))
  ;
  )



(init-before-bind)
(bind-all)
(init-after-bind)


(defn sdoc*
  "Brings up the clj-ns-browser documentation for a var, namespace,
  or special form given its name.
  (generates more info than clojure.core/doc)
  Name n is string or (quoted-)symbol."
  ([a-fqn] (sdoc* (str *ns*) a-fqn))
  ([a-ns a-fqn]
  (if-let [fqn (and a-fqn (or (string? a-fqn)(symbol? a-fqn)) (fqname a-fqn))]
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
      (println fqn))
    (println "Sorry, no info for give name: " a-fqn))))


(defmacro sdoc
  "Brings up the clj-ns-browser documentation for a var, namespace,
  or special form given its name.
  (generates more info than clojure.core/doc)
  Name n is string, symbol, or quoted symbol."
  ([n]
  (cond (string? n) `(sdoc* ~n)
        (symbol? n) `(sdoc* ~(str n))
        (= (type n) clojure.lang.Cons) `(sdoc* ~(str (second n))))))

