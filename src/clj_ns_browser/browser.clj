;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.browser
  (:require [seesaw.selector]
            [seesaw.bind :as b]
            [clojure.java.browse]
            [clojure.java.shell]
            [seesaw.meta]
            [clojure.java.javadoc])
  (:use [clj-ns-browser.utils]
        [seesaw.core]
        [seesaw.border]
        [seesaw.mig]
        [seesaw.dev]
        [clj-info]))


;; general util fns

;; Copied from seesaw example to generate :id's from externally build GUIs.
;; ("should" become part of seesaw.core...)
(defn identify
  "Given a root widget, find all the named widgets and set their Seesaw :id
   so they can play nicely with select and everything."
  [root]
  (doseq [w (select root [:*])]
    (when-let [n (.getName w)]
      (seesaw.selector/id-of! w (keyword n))))
  root)


(defn id-values-as-symbols
  "Given a root widget, return a sorted vector of symbols
  for all the :id values in the tree.
  Can be used at the REPL to generate the restructure let-list."
  [root]
  (vec (apply sorted-set
    (map (fn [k] (symbol (name k)))(keys (group-by-id root))))))

;;

;; seesaw docs say to call this early
(native!)


;; constants and global maps

(def ns-cbx-value-list ["loaded" "unloaded"])
(def ns-cbx-value-fn-map {  "loaded"    all-ns-loaded
                            "unloaded"  all-ns-unloaded})
(def vars-cbx-value-list ["aliases" "imports" "interns" "map" "publics"
                          "refers" "special-forms" "all-publics"
                          "search-all-docs"])
(def vars-cbx-value-fn-map {"aliases"   ns-aliases
                            "imports"   ns-imports
                            "interns"   ns-interns
                            "map"       ns-map
                            "publics"   ns-publics
                            "refers"    ns-refers
                            "special-forms" ns-special-forms
                            "all-publics" all-publics
                            "search-all-docs"  identity  ; ret value not used
                            })

(def doc-cbx-value-list ["All" "Doc" "Source" "Examples"
                         "Comments" "See alsos" "Value"])


;; "global" atoms

(def all-ns-loaded-atom (atom nil))
(def all-ns-unloaded-atom (atom nil))
(defn ns-loaded [] @all-ns-loaded-atom)
(defn ns-unloaded [] @all-ns-unloaded-atom)
(swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
(swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))


;; bind specific fns

;;(b/transform regx-tf-filter :ns-filter-tf)
(defn regx-tf-filter
  "filter for use in bind that filters string-list s-l with regex of text-field t-f"
  [{:keys [string-seq already-filtered]} t-f]
  (if already-filtered
    string-seq
    (when-let [r (try (re-pattern (config t-f :text)) (catch Exception e nil))]
      (filter #(re-find r %) string-seq))))


(defn find-in-doc-strings
  [pat-in-str]
  (when-let [r (try (re-pattern pat-in-str)
                    (catch Exception e nil))]
    (->> (all-ns)
         (mapcat #(map meta (vals (ns-interns %))))
         (filter #(and (:doc %)
                       (or (re-find r (:doc %))
                           (re-find r (str (:name %))))))
         (map #(str (:ns %) "/" (:name %))))))


(defn widget-model-count
  "Return the number of items of widget w's model"
  [_ w]
  (.getSize (config w :model)))


(defn init-before-bind
  [root]
  (let [atm-map {:ns-require-btn-atom (atom true)
                 :browse-btn-atom (atom true)
                 :edit-btn-atom (atom true)}
        {:keys [browse-btn doc-cbx doc-header-lbl doc-ta doc-ta-sp
                doc-tf edit-btn ns-cbx ns-entries-lbl ns-filter-tf
                ns-header-lbl ns-lb ns-lb-sp ns-require-btn root-panel
                var-trace-btn vars-cbx vars-entries-lbl vars-filter-tf
                vars-header-lbl vars-lb vars-lb-sp]}
          (group-by-id root)
        {:keys [ns-require-btn-atom browse-btn-atom edit-btn-atom]}
          atm-map]
    (let [l (select root [:#vars-lb-sp])]
      (config! l :preferred-size (config l :size)))
    (seesaw.meta/put-meta! root :atom-map atm-map)
    ;; ns
    (config! ns-lb :model @all-ns-loaded-atom)
    (config! vars-lb :model [])
    (config! ns-entries-lbl :text "0")
    (config! ns-require-btn :enabled? false)
    (config! doc-cbx :model doc-cbx-value-list)
    (config! edit-btn :enabled? false)
    (config! browse-btn :enabled? false)
    (config! var-trace-btn :enabled? false)
    (listen ns-require-btn
      :action (fn [event] (swap! ns-require-btn-atom not)))
    (listen browse-btn
      :action (fn [event] (swap! browse-btn-atom not)))
    (listen edit-btn
      :action (fn [event] (swap! edit-btn-atom not)))
    ;; vars
    (config! vars-entries-lbl :text "0")
    ; doc
    (config! doc-tf :text "")
    (config! doc-ta :text "                                                                        ")
    (selection! ns-cbx "loaded")
    (selection! vars-cbx "publics")
    (selection! vars-cbx "Doc")))


(defn init-after-bind
  [root]
  (let [{:keys [browse-btn doc-cbx doc-header-lbl doc-ta doc-ta-sp
                doc-tf edit-btn ns-cbx ns-entries-lbl ns-filter-tf
                ns-header-lbl ns-lb ns-lb-sp ns-require-btn root-panel
                var-trace-btn vars-cbx vars-entries-lbl vars-filter-tf
                vars-header-lbl vars-lb vars-lb-sp]}
          (group-by-id root)
        {:keys [ns-require-btn-atom browse-btn-atom edit-btn-atom]}
          (seesaw.meta/get-meta root :atom-map)]
    (invoke-soon (selection! ns-cbx "loaded"))
    (invoke-soon (selection! vars-cbx "publics"))
    (invoke-soon (selection! doc-cbx "Doc"))))


(defn bind-all
  "Collection of all the bind-statements that wire the clj-ns-browser events and widgets. (pretty amazing how easy it is to express those dependency-graphs!)"
  [root]
  (let [{:keys [browse-btn doc-cbx doc-header-lbl doc-ta doc-ta-sp
                doc-tf edit-btn ns-cbx ns-entries-lbl ns-filter-tf
                ns-header-lbl ns-lb ns-lb-sp ns-require-btn root-panel
                var-trace-btn vars-cbx vars-entries-lbl vars-filter-tf
                vars-header-lbl vars-lb vars-lb-sp]}
          (group-by-id root)
        {:keys [ns-require-btn-atom browse-btn-atom edit-btn-atom]}
          (seesaw.meta/get-meta root :atom-map)]
    ;; # of entries in ns-lb => ns-entries-lbl
    (b/bind
      (b/property ns-lb :model)
      (b/transform widget-model-count ns-lb)
      ns-entries-lbl)
    ;; # of entries in vars-lb => vars-entries-lbl
    (b/bind
      (b/property vars-lb :model)
      (b/transform widget-model-count vars-lb)
      vars-entries-lbl)
    ;; new ns selected in ns-lb =>
    ;; dis/enable require-btn, update fqn in doc-tf
    (b/bind
      (b/selection ns-lb)
        (b/tee
          (b/bind
            (b/transform (fn [ns]
              (if (and ns (some #(= ns %) @all-ns-unloaded-atom))
                true
                false)))
            (b/property ns-require-btn :enabled?))
        (b/bind
          (b/transform (fn [ns]
            (if (and ns (find-ns (symbol ns)))
              (fqname ns)
              "")))
          (b/tee
            (b/property doc-tf :text)))))
    ;; require-btn pressed =>
    ;; (require ns), update (un-)loaded atoms, select loaded, select ns.
    (b/bind
      ns-require-btn-atom
      (b/transform (fn [& b]
        (when-let [n (selection ns-lb)]
          (require (symbol n))
          (swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
          (swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
          (let [i (.indexOf (seq @all-ns-loaded-atom) n)]
            (if (pos? i)
              (do
                (selection! ns-cbx "loaded")
                (selection! ns-lb n)
                (scroll! ns-lb :to [:row i]))
              (alert (str "Hmmm... seems that namespace \"" n "\" cannot be required (?)"))))))))
    ;;
    ;; select item in vars-lb => set associated fqn in doc-tf
    (b/bind
      (b/selection vars-lb)
      (b/transform (fn [v]
        (if v
          (let [fqn (fqname (selection ns-lb) v)]
            (if (and fqn (not= fqn ""))
              fqn
              (when (= (selection vars-cbx) "aliases")
                (when-let [n (get (ns-aliases (symbol (selection ns-lb))) (symbol v))]
                  (str n))))))))
      (b/tee
          (b/property doc-tf :text)))
    ;;
    ;; (un-)loaded ns-cbx and regex filter tf =>
    ;; updated ns-list in ns-lb
    (b/bind
      (apply b/funnel
        [ns-cbx
         ns-filter-tf])
      (b/transform (fn [o]
        (let [v (selection ns-cbx)]
          {:already-filtered false :string-seq ((get ns-cbx-value-fn-map v))})))
      (b/transform regx-tf-filter ns-filter-tf)
      (b/notify-soon)
      (b/property ns-lb :model))
    ;;
    ;; (un-)loaded vars-cbx and regex filter tf =>
    ;; updated vars-list in vars-lb
    (b/bind
      (apply b/funnel
        [(b/selection vars-cbx)
         (b/selection ns-lb)
         vars-filter-tf])
      (b/transform (fn [o]
        (let [n-s (selection ns-lb)
              n (and n-s (find-ns (symbol n-s)))
              v (selection vars-cbx)
              f (get vars-cbx-value-fn-map v)]
          (if n
            (if (= v "search-all-docs")
              {:already-filtered true
               :string-seq (seq (sort (find-in-doc-strings
                                       (config vars-filter-tf :text))))}
              {:already-filtered false
               :string-seq (seq (sort (map str (keys (f n)))))})
            {:already-filtered false :string-seq []}))))
      (b/transform regx-tf-filter vars-filter-tf)
      (b/notify-soon)
      (b/property vars-lb :model))
    ;;
    ;; typed regex in ns-filter-tf => visual feedback about validity
    (b/bind
      ; As the text of the textbox changes ...
      ns-filter-tf
      ; Convert it to a regex, or nil if it's invalid
      (b/transform #(try (re-pattern %) (catch Exception e nil)))
      ; Now split into two paths ...
      (b/bind
        (b/transform #(if % "white" "lightcoral"))
        (b/notify-soon)
        (b/property ns-filter-tf :background)))
    ;;
    ;; typed regex in vars-filter-tf => visual feedback about validity
    (b/bind
      ; As the text of the textbox changes ...
      vars-filter-tf
      ; Convert it to a regex, or nil if it's invalid
      (b/transform #(try (re-pattern %) (catch Exception e nil)))
      ; Now split into two paths ...
      (b/bind
        (b/transform #(if % "white" "lightcoral"))
        (b/notify-soon)
        (b/property vars-filter-tf :background)))
    ;;
    ;; updated fqn in doc-tf or doc-cbx =>
    ;; new render-doc-text in doc-ta
    (b/bind
      ; As the text of the fqn text field changes ...
      (apply b/funnel [doc-tf doc-cbx])
      (b/filter (fn [[doc-tf doc-cbx]]
                  (not (or (nil? doc-tf)  (= "" doc-tf)
                           (nil? doc-cbx) (= "" doc-cbx)))))
      (b/transform
        (fn [[doc-tf doc-cbx]]
          (future
            (let [s (render-doc-text doc-tf doc-cbx)]
              (invoke-soon (config! doc-ta :text s)))))))
    ;;
    ;; new text in doc-ta => scroll to top
    (b/bind
      doc-ta
      (b/notify-later)
      (b/transform (fn [t] (scroll! doc-ta :to :top))))
    ;;
    ;; new text in doc-ta => dis/enable browser button
    (b/bind
      doc-ta
      (b/transform (fn [o] (selection doc-cbx)))
      (b/transform (fn [o]
        (case o
          ("All" "Doc") (invoke-soon (config! browse-btn :enabled? true))

          ("Examples" "See alsos" "Comments")
          (if-let [fqn (config doc-tf :text)]
            (future
              (let [url (clojuredocs-url fqn)
                    r (if url true false)]
                (invoke-soon
                 (config! browse-btn :enabled? r)))))

          nil))))  ; do nothing if no match
    ;
    (b/bind
      (apply b/funnel [doc-tf doc-cbx])
      (b/transform (fn [o] (selection doc-cbx)))
      (b/transform (fn [o] (if (get #{"Source" "All"} o) true false)))
      (b/transform (fn [o]
        (when o (if (meta-when-file (config doc-tf :text))
                  true
                  false))))
      (b/notify-soon)
      (b/property edit-btn :enabled?))
    ;;
    ;; browser-btn pressed =>
    ;; bring up browser with url
    (b/bind
      browse-btn-atom
      (b/notify-soon)
      (b/transform (fn [& oo]
        (let [o (selection doc-cbx)]
          (when-let [fqn (config doc-tf :text)]
            (future
              (case o
                ("All" "Doc") (bdoc* fqn)

                ("Examples" "See alsos" "Comments")
                (when-let [url (clojuredocs-url fqn)]
                  (clojure.java.browse/browse-url url))

                nil)))))))  ; do nothing if no match
    ;;
    ;; edit-btn pressed =>
    ;; if we find a local file (not inside jar), then send to $EDITOR.
    (b/bind
      edit-btn-atom
      (b/transform (fn [& o]
        (future
          (when-let [m (meta-when-file (config doc-tf :text))]
            (when-let [e (:out (clojure.java.shell/sh "bash" "-c" (str "echo -n $EDITOR")))]
              (:exit (clojure.java.shell/sh "bash" "-c" (str e " +" (:line m) " " (:file m))))))))))
    ;;
    )) ; end of bind-all


;; init and browser-window management

(declare browser-root-frms)
(when-not (bound? #'browser-root-frms)
  (def browser-root-frms (atom [])))


(defn refresh-clj-ns-browser
  "Refresh all or the given browser-window (pack! and show!)"
;;   ([] (map #(invoke-later (show! (pack! %))) @browser-root-frms))
;;   ([root] (invoke-later (show! (pack! root)))))
  ([] (map #(invoke-later (show! %)) @browser-root-frms))
  ([root] (invoke-later (show! root))))


(defn new-clj-ns-browser
  "Returns a new browser root frame with an embedded browser form.
  Add new frame to atom-list browser-root-frms"
  []
  (let [root (frame :title "Clojure Namespace Browser")
        b-form (identify (clj_ns_browser.BrowserForm.))]
        ;;b-form (identify (clj_ns_browser.BrowserFormHTML.))]
    (config! root :content b-form)
    (pack! root)
    (init-before-bind root)
    (bind-all root)
    (init-after-bind root)
    (swap! browser-root-frms (fn [a] (conj @browser-root-frms root)))
    (refresh-clj-ns-browser root)
    root))


(defn get-clj-ns-browser
  "Returns the first browser root frame from browser-root-frms,
  or if none, create one first."
  []
  (if-let [root (first @browser-root-frms)]
    root
    (new-clj-ns-browser)))

