;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.browser
  (:require [clj-ns-browser.seesaw :as ss]
            [seesaw.selector]
            [seesaw.bind :as b]
            [clojure.java.browse]
            [clojure.java.shell]
            [clojure.java.javadoc])
  (:use [clj-ns-browser.utils]
        [seesaw.core]
        [seesaw.border]
        [seesaw.mig]
        [seesaw.dev]
        [clj-info]))


;; seesaw docs say to call this early
(native!)


(declare browser-root-frms)
(when-not (bound? #'browser-root-frms)
  (def browser-root-frms (atom [])))

(defn new-browser-root-frm
  "Returns a new browser root frame with an embedded browser form.
  Add new frame to atom-list browser-root-frms"
  []
  (let [root-frm (frame :title "Clojure Namespace Browser")
        b-form (ss/identify (clj_ns_browser.BrowserForm.))]
    (config! root-frm :content b-form)
    (swap! browser-root-frms (fn [a] (conj @browser-root-frms root-frm)))
    root-frm))


(defn get-browser-root-frm
  "Returns the first browser root frame from browser-root-frms,
  or if none, create one first."
  []
  (if-let [root-frm (first @browser-root-frms)]
    root-frm
    (new-browser-root-frm)))


;; initialize a new root frame when we're here the first time
(when-not (bound? #'ss/*root-frm*)
  (ss/set-root-frm! (new-browser-root-frm)))


(defn refresh
  "Refresh the current browser-window (pack! and show!)"
  [& frms]
  (if frms
    (map #(invoke-later (show! (pack! %))) frms)
    (invoke-later (show! (pack! (get-browser-root-frm))))))


;; bind-related filters and transforms

;;(b/transform regx-tf-filter :ns-filter-tf)
(defn regx-tf-filter
  "filter for use in bind that filters string-list s-l with regex of text-field t-f"
  [s-l t-f]
  (when-let [r (try (re-pattern (ss/config t-f :text)) (catch Exception e nil))]
    (filter #(re-find r %) s-l)))


(defn b-model-count [_ o]
  (.getSize (ss/config o :model)))


;; Widget initialization part

;; ns
(def all-ns-loaded-atom (atom nil))
(def all-ns-unloaded-atom (atom nil))
(def ns-require-btn-atom (atom true))
(def browse-btn-atom (atom true))
(def edit-btn-atom (atom true))

(def ns-cbx-value-list ["loaded" "unloaded"])
(defn ns-loaded [] @all-ns-loaded-atom)
(defn ns-unloaded [] @all-ns-unloaded-atom)
;; (def ns-cbx-value-fn-map {  "loaded"    ns-loaded
;;                             "unloaded"  ns-unloaded})
(def ns-cbx-value-fn-map {  "loaded"    all-ns-loaded
                            "unloaded"  all-ns-unloaded})
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
  (ss/config! :edit-btn :enabled? false)
  (ss/config! :browse-btn :enabled? false)
  (listen (ss/select :ns-require-btn)
    :action (fn [event] (swap! ns-require-btn-atom not)))
  (listen (ss/select :browse-btn)
    :action (fn [event] (swap! browse-btn-atom not)))
  (listen (ss/select :edit-btn)
    :action (fn [event] (swap! edit-btn-atom not)))
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


(defn bind-all
  "Collection of all the bind-statements that wire the clj-ns-browser events and widgets. (pretty amazing how easy it is to express those dependency-graphs!)"
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
  ;; new ns selected in ns-lb =>
  ;; dis/enable require-btn, update fqn in doc-tf
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
  ;; require-btn pressed =>
  ;; (require ns), update (un-)loaded atoms, select loaded, select ns.
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
  ;; select item in vars-lb => set associated fqn in doc-tf
  (b/bind
    (b/selection (ss/select :vars-lb))
    (b/transform (fn [v]
      (if v
        (let [fqn (fqname (ss/selection :ns-lb) v)]
          (if (and fqn (not= fqn ""))
            fqn
            (when (= (ss/selection :vars-cbx) "aliases")
              (when-let [n (get (ns-aliases (symbol (ss/selection :ns-lb))) (symbol v))]
                (str n))))))))
    (b/tee
        (b/property (ss/select :doc-tf) :text)))
  ;;
  ;; (un-)loaded ns-cbx and regex filter tf =>
  ;; updated ns-list in ns-lb
  (b/bind
    (ss/funnel
      [(ss/select :ns-cbx)
       (ss/select :ns-filter-tf)])
    (b/transform (fn [o]
      (let [v (ss/selection (ss/select :ns-cbx))]
        ((get ns-cbx-value-fn-map v)))))
    (b/transform regx-tf-filter :ns-filter-tf)
    (b/notify-soon)
    (b/property (ss/select :ns-lb) :model))
  ;;
  ;; (un-)loaded vars-cbx and regex filter tf =>
  ;; updated vars-list in vars-lb
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
  ;; typed regex in ns-filter-tf => visual feedback about validity
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
  ;; typed regex in ns-filter-tf => visual feedback about validity
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
  ;; updated fqn in doc-tf or doc-cbx =>
  ;; new render-doc-text in doc-ta
  (b/bind
    ; As the text of the fqn text field changes ...
    (ss/funnel [(ss/select :doc-tf) (ss/select :doc-cbx)])
    (b/filter (fn [o] (not (or (nil? (first o)) (= "" (first o))
                               (nil? (second o))(= "" (second o))))))
    (b/transform
      (fn [o]
        (future
          (let [s (render-doc-text (first o) (second o))]
            (invoke-soon (ss/config! :doc-ta :text s)))))))
  ;;
  ;; new text in doc-ta => scroll to top
  (b/bind
    (ss/select :doc-ta)
    (b/notify-later)
    (b/transform (fn [t] (scroll! (ss/select :doc-ta) :to :top))))
  ;;
  ;; new text in doc-ta => dis/enable browser button
  (b/bind
    (ss/select :doc-ta)
    (b/transform (fn [o] (ss/selection :doc-cbx)))
    (b/transform (fn [o]
      (if (= "Doc" o)
        (invoke-soon (ss/config! :browse-btn :enabled? true))
          (if (or (= "Examples" o)(= "Comments" o))
            (if-let [fqn (ss/config :doc-tf :text)]
              (future
                (let [url (clojuredocs-url fqn)
                      r (if url true false)]
                  (invoke-soon
                    (ss/config! :browse-btn :enabled? r))))))))))
  ;
  (b/bind
    (ss/funnel [(ss/select :doc-tf) (ss/select :doc-cbx)])
    (b/transform (fn [o] (ss/selection :doc-cbx)))
    (b/transform (fn [o] (if (= "Source" o) true false)))
    (b/transform (fn [o]
      (when o (if (meta-when-file (ss/config :doc-tf :text))
                true
                false))))
    (b/notify-soon)
    (b/property (ss/select :edit-btn) :enabled?))
  ;
    ;; browser-btn pressed =>
  ;; bring up browser with url
  (b/bind
    browse-btn-atom
    (b/notify-soon)
    (b/transform (fn [& oo]
      (let [o (ss/selection :doc-cbx)]
        (when-let [fqn (ss/config :doc-tf :text)]
          (future
            (cond
              (= "Doc" o) (bdoc* fqn)
              (or (= "Examples" o)(= "Comments" o))
                (when-let [url (clojuredocs-url fqn)]
                  (clojure.java.browse/browse-url url)))))))))
  ;;
  ;; edit-btn pressed =>
  ;; if we find a local file (not inside jar), then send to $EDITOR.
  (b/bind
    edit-btn-atom
    (b/transform (fn [& o]
      (future
        (when-let [m (meta-when-file (ss/config :doc-tf :text))]
          (when-let [e (:out (clojure.java.shell/sh "bash" "-c" (str "echo -n $EDITOR")))]
            (:exit (clojure.java.shell/sh "bash" "-c" (str e " +" (:line m) " " (:file m))))))))))
  ;;
  ) ; end of bind-all


(defn init-browser-root
  "Initializes the browser frame with all its widgets."
  [root]
  (binding [ss/*root-frm* root]
    (init-before-bind)
    (bind-all)
    (init-after-bind)))

;; start-up & initialize a browser frame
(init-browser-root (get-browser-root-frm))
(ss/set-root-frm! (get-browser-root-frm))
