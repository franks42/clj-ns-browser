;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.browser
  (:require [seesaw.selector]
            [seesaw.dnd]
            [seesaw.bind :as b]
            [seesaw.rsyntax]
            [clj-ns-browser.web]
            [clojure.java.shell]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.set :as set]
            [clojure.string :as str]
            [clj-info.doc2map :as d2m]
            [clj-ns-browser.inspector]
            [clj-ns-browser.toggle-listbox :as tl]
            [seesaw.meta]
            [seesaw.clipboard]
            [clojure.java.javadoc]
            [cd-client.core]
            [clojure.tools.trace])
  (:use [clj-ns-browser.utils]
        [seesaw.core]
        [seesaw.border]
        [seesaw.mig]
        [seesaw.dev]
        [clj-info]))
        ;;[alex-and-georges.debug-repl]))


;; Much of the processing depends on selecting widgets/atoms/actions from frame-trees.
;; to ease the selection, the function (select-id root kw) is used, which looks up
;; the widget/action/atom in root associated with keyword kw.
;; because a lot of the processing is done for a certain root, the select-id function
;; is curried often like (letfn [(id [kw] (select-id root kw))] ...), such
;; that within the let form, the widgets can be selected with (id kw)
;; note that the actions that are searched for with select-id are maintained in
;; the global app-action-map and actions are added with (add-app-action kw actn)
;; the atoms associated with buttons are maintained in a map stored in the user-data
;; of a frame - as long as you maintain the buttons in the var "all-buttons-with-atoms"
;; then the atoms are auto-generated and can be accessed with select-id with the
;; same keyword as the button but with"-atom" appended



(def clj-ns-browser-version "1.3.2-SNAPSHOT")


;; forward declarations... ough that clojure compiler should be smarter...
(declare new-clj-ns-browser)
(declare get-clj-ns-browser)
(declare browser-with-fqn)
(declare refresh-clj-ns-browser)
(declare get-next-clj-ns-browser)
(declare auto-refresh-browser-handler)

;; seesaw docs say to call this early
(native!)


;; convenience functions for seesaw interaction

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


(defn font-size+
  "Increase the font-size of the widget w by 1."
  [w]
  (let [f (config w :font)]
    (config! w :font {:name (.getName f) :size (inc (.getSize f))})))


(defn font-size-
  "Decrease the font-size of the widget w by 1."
  [w]
  (let [f (config w :font)]
    (config! w :font {:name (.getName f) :size (dec (.getSize f))})))


;; constants and global maps shared by all seesaw widgets

;; maintain all top-level browser frames in global list & map @browser-root-frms
(declare browser-root-frm-map)
(when-not (bound? #'browser-root-frm-map)
  (def browser-root-frm-map (atom (sorted-map))))

(declare browser-root-frms)
(when-not (bound? #'browser-root-frms)
  (def browser-root-frms (atom [])))


(def app-action-map "Maintain all actions for the app in a global map with keyword-keys." (atom {}))

(defn add-app-action
  "Add the action \"actn\" to the global \"app-action-map\" with the key \"kw\".
  Get the actions later thru the keyword: (get app-action-map kw).
  See also select-id."
  [kw actn]
  (swap! app-action-map (fn [m k a] (assoc m k a)) kw actn)
  actn)


(defn select-id
  "Convenience function for seesaw.core/select to identify a widget easily by its :id value.
  When not found in widget-tree, looks into (seesaw.meta/get-meta root :atom-map).
  Input: root - widget-root
  Input: ss-id is keyword, symbol or string referring to widget :id value
  Output: reference to widget with :id equal to ss-id
  Usage: (select-id root :ns-lb))
  or use with partial like: (letfn [(id [kw] (select-id root kw))] (config (id :ns-lb) ...))"
  ([ss-id] (select-id nil ss-id))
  ([root ss-id]
  (let [str-id (if (keyword? ss-id) (name ss-id) (str ss-id))
        kw-id (keyword str-id)]
    (or
      ;; first look in the global app-action-map for a matching action
      (when-let [actn (get @app-action-map kw-id)] actn)
      ;; then look in the root's tree for a widget with that :id of ss-id
      (when root (seesaw.core/select root [ (keyword (str "#" str-id)) ]))
      ;; next look in the root's atom-map for a matching root-specific atom
      (when root (when-let [m (seesaw.meta/get-meta root :atom-map)]
        (get m kw-id)))
      (do (println "id not found: " ss-id ) nil)))))


;; app specific constants


(def ns-cbx-value-list ["loaded" "unloaded"])
(def ns-cbx-value-fn-map {  "loaded"    all-ns-loaded
                            "unloaded"  all-ns-unloaded})
(def vars-cbx-value-list [
  "Vars - all"
  "Vars - public"
  "Vars - private"
  "Vars - macro"
  "Vars - defn"
  "Vars - protocol"
  "Vars - protocol-fn"
  "Vars - multimethod"
  "Vars - dynamic"
  "Vars - traced"
  "Classes - all"
  "Classes - deftype"
  "Classes - defrecord"
  "Refers - all"
  "Refers w/o core"
  "All"
  "Aliases"
  "Special-Forms"
  ])

(defn sonc [ns-action f]
  (fn [ns-coll display-fqn? search-places]
    (symbols-of-ns-coll ns-action f ns-coll display-fqn? search-places)))

(def vars-cbx-value-fn-map
  {"Aliases"             (sonc :aliases ns-aliases)
   "Classes - all"       (sonc :ns-map-subset ns-imports)
   "Vars - all"          (sonc :ns-map-subset ns-interns)
   "All"                 (sonc :ns-map-subset ns-map)
   "Classes - deftype"   (sonc :ns-map-subset ns-map-deftype)
   "Classes - defrecord" (sonc :ns-map-subset ns-map-defrecord)
   "Vars - public"       (sonc :ns-map-subset ns-publics)
   "Vars - macro"        (sonc :ns-map-subset ns-interns-macro)
   "Vars - defn"         (sonc :ns-map-subset ns-interns-defn)
   "Vars - protocol"     (sonc :ns-map-subset ns-interns-protocol)
   "Vars - protocol-fn"  (sonc :ns-map-subset ns-interns-protocol-fn)
   "Vars - multimethod"  (sonc :ns-map-subset ns-interns-var-multimethod)
   "Vars - dynamic"      (sonc :ns-map-subset ns-interns-var-dynamic)
   "Vars - traced"       (sonc :ns-map-subset ns-interns-var-traced)
   "Vars - private"      (sonc :ns-map-subset ns-privates)
   "Refers - all"        (sonc :ns-map-subset ns-refers)
   "Refers w/o core"     (sonc :ns-map-subset ns-refers-wo-core)
   "Special-Forms"       (sonc :special-forms ns-special-forms)
   })


(def doc-lb-value-list ["Doc" "Source" "Examples"
                        "Comments" "See alsos" "Value" "Meta"])
(def doc-lb-cur-order (atom doc-lb-value-list))


(def all-buttons-with-atoms
  "Used to auto-generate atoms and '-atom' keywords"
  [:ns-require-btn :browse-btn :edit-btn :clojuredocs-offline-rb :clojuredocs-online-rb 
   :update-clojuredocs-btn :var-trace-btn :inspect-btn :color-coding-cb :vars-fqn-listing-cb])

(defn make-atom-kw [kw] (keyword (str (clj-ns-browser.utils/fqname kw) "-atom")))

(defn make-button-atom-map
  [btn-list]
  (let [btn-atom-kw-list (map make-atom-kw btn-list)]
    (into {} (map (fn [k] [k (atom true)]) btn-atom-kw-list))))


;; "global" atoms

(def settings-atom (atom {}))
(def all-ns-loaded-atom (atom nil))
(def all-ns-unloaded-atom (atom nil))
(defn ns-loaded [] @all-ns-loaded-atom)
(defn ns-unloaded [] @all-ns-unloaded-atom)
(swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
(swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
(def group-vars-by-object-type-atom (atom false))
(def vars-search-doc-also-cb-atom (atom false))
(def ns-lb-refresh-atom (atom true))
(def vars-lb-refresh-atom (atom true))

;; bind specific fns

(defn clojuredocs-snapshot-filename []
  (str (System/getProperty "user.home") "/.clojuredocs-snapshot.txt"))

(defn clojuredocs-offline-mode! []
  (let [f (clojuredocs-snapshot-filename)]
    (if (.exists (io/as-file f))
      ;; TBD: Consider trying to handle errors while reading the file.
      (let [s (with-out-str (cd-client.core/set-local-mode! f))]
        [:ok s])
      [:snapshot-file-not-found f])))


(defn update-settings! [maybe-new-partial-settings]
  (let [maybe-new-settings (merge @settings-atom maybe-new-partial-settings)]
    ;; Only write settings file if something has changed.
    (swap! settings-atom
           (fn [cur-settings next-settings]
             (when (not= cur-settings next-settings)
               ;;(printf "Writing settings file with new partial settings: ")
               ;;(clojure.pprint/pprint maybe-new-partial-settings)
               ;;(flush)
               (write-settings! next-settings))
             next-settings)
           maybe-new-settings)))


;;(b/transform regx-tf-filter :ns-filter-tf)

;; When filtering namespaces, symbol-info is a collection of strings.
;; When filtering symbols within one or more namespaces, symbol-info
;; is a collection of maps, each having string values for the keys
;; :name-to-search and, if (deref search-doc-strings-atom) is true,
;; :doc-string.

(defn regx-tf-filter
  "filter for use in bind that filters string-list s-l with regex of text-field t-f"
  [symbol-info t-f search-doc-strings-atom]
  (let [search-doc-strings? (deref search-doc-strings-atom)]
    (when-let [r (try (re-pattern (config t-f :text))
                      (catch Exception e nil))]
      (filter (fn [info]
                (if (string? info)
                  (re-find r info)
                  (or (re-find r (:name-to-search info))
                      (and search-doc-strings?
                           (not (nil? (:doc-string info)))
                           (re-find r (:doc-string info))))))
              symbol-info))))

(defn ensure-selection-visible
  "Scroll the selection of a listbox within view.
  Input lbx is a swing listbox reference.
  or input: root and keyword-id lbx-kw for the listbox."
  ([lbx] (.ensureIndexIsVisible lbx (.getSelectedIndex lbx)))
  ([root lbx-kw]
    (letfn [(id [kw] (select-id root kw))]
      (.ensureIndexIsVisible (id lbx-kw) (.getSelectedIndex (id lbx-kw))))))

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


(defn better-get-docs-map [fqn]
  (if (= fqn "clojure.core//")
    {:object-type-str "Function"}
    (d2m/get-docs-map fqn)))


(defn simplify-object-type [obj-type-str]
  (cond
   (nil? obj-type-str) "(nil)"
   (re-find #"^Var .*" obj-type-str) "Var"
   (re-find #"^clojure\.lang\.Atom@" obj-type-str) "Atom"
   :else obj-type-str))


(defn group-by-object-type [symbol-info]
  (let [symbol-info (map (fn [info]
                           (merge info {:obj-type-str
                                        (case (:rough-category info)
                                          :msg-to-user "Warning message"
                                          :aliases "Alias"
                                          :special-forms "Special Form"
                                          (-> (:fqn-str info)
                                              better-get-docs-map
                                              :object-type-str
                                              simplify-object-type))}))
                         symbol-info)
        groups (group-by :obj-type-str symbol-info)]
    (apply concat (interpose
                   [ " " ]
                   (map (fn [[obj-type-str info]]
                          (concat [ (str "        " obj-type-str) ]
                                  (sort (map :display-str info))))
                        (sort-by first (seq groups)))))))


(defn widget-model-count
  "Return the number of items of widget w's model - used to get number of items in a listbox"
  [_ w]
  (.getSize (config w :model)))


;; Initial "global" action definitions shared by all browser instances

;; actions to be used in the menus
(add-app-action :new-browser-action
  (action :name "New Browser (with selection/FQN)"
          :key  "menu N"
          :handler (fn [e]
            (let [id (partial select-id (to-root e))]
              (if-let [s (selection (id :doc-ta))]
                (let [fqn (subs (config (id :doc-ta) :text) (first s) (second s))]
                  (invoke-soon (browser-with-fqn *ns* fqn (new-clj-ns-browser))))
                (if-let [fqn (config (id :doc-tf) :text)]
                  (invoke-soon (browser-with-fqn *ns* fqn (new-clj-ns-browser)))
                  (invoke-soon (new-clj-ns-browser))))))))

(add-app-action :go-github-wiki-action
  (action :name "Clj-NS-Browser Wiki..."
          :handler (fn [a] (future (clj-ns-browser.web/browse-url
            "https://github.com/franks42/clj-ns-browser/wiki")))))
(add-app-action :go-github-action
  (action :name "Clj-NS-Browser GitHub..."
          :handler (fn [a] (future (clj-ns-browser.web/browse-url
            "https://github.com/franks42/clj-ns-browser")))))
(add-app-action :go-clojure.org-action
  (action :name "Clojure.org..."
          :handler (fn [a] (future (clj-ns-browser.web/browse-url "http://clojure.org")))))
(add-app-action :go-clojuredocs-action
  (action :name "ClojureDocs..."
          :handler (fn [a] (future (clj-ns-browser.web/browse-url "http://clojuredocs.org")))))
(add-app-action :go-cheatsheet-action
  (action :name "Clojure CheatSheet..."
          :handler (fn [a] (future (clj-ns-browser.web/browse-url "http://jafingerhut.github.com/cheatsheet-clj-1.3/cheatsheet-tiptip-no-cdocs-summary.html")))))
(add-app-action :go-jira-action
  (action :name "JIRA..."
          :handler (fn [a] (future (clj-ns-browser.web/browse-url
            "http://dev.clojure.org/jira/browse/CLJ")))))
(add-app-action :go-stackoverflow-action
  (action :name "Stackoverflow..."
          :handler (fn [e] 
            (let [id (partial select-id (to-root e))]
              (let [fqn (config (id :doc-tf) :text)]
                (if-let [fqn-name (fqname fqn)]
                  (let [ns-n-class (ns-name-class-str fqn-name)
                        sname (if (nil? (second ns-n-class)) (first ns-n-class)(second ns-n-class))]
                    (future (clj-ns-browser.web/browse-url
                      (str "http://stackoverflow.com/search?q=clojure+" sname))))))))))
(add-app-action :go-about-action
  (action :name "About..."
          :handler (fn [a] (invoke-later (alert (str "Clojure Namespace Browser (" clj-ns-browser-version ")" \newline
            "Copyright (C) 2012 - Frank Siebenlist and Andy Fingerhut" \newline
            "Distributed under the Eclipse Public License"))))))

(add-app-action :zoom-in-action
  (action :name "Zoom in"
          :key  "menu U"
          :handler (fn [e] (let [id (partial select-id (to-root e))]
            (invoke-later (font-size+ (id :doc-ta))
                          (font-size+ (id :doc-tf))
                          (font-size+ (id :ns-lb))
                          (font-size+ (id :vars-lb)))))))
          
(add-app-action :zoom-out-action
  (action :name "Zoom out"
          :key  "menu D"
          :handler (fn [e] (let [id (partial select-id (to-root e))]
            (invoke-later (font-size- (id :doc-ta))
                          (font-size- (id :doc-tf))
                          (font-size- (id :ns-lb))
                          (font-size- (id :vars-lb)))))))
          
(add-app-action :bring-all-windows-to-front-action
  (action :name "Bring All to Front"
          :handler (fn [e] (refresh-clj-ns-browser))))
          
(add-app-action :cycle-through-windows-action
  (action :name "Cycle Through Windows"
          :key  "menu M"
          :handler (fn [e] (refresh-clj-ns-browser (get-next-clj-ns-browser (to-root e))))))

(add-app-action :copy-fqn-action
  (action :name "Copy Selection/FQN"
          :key  "menu C"
          :handler (fn [e]
            (let [id (partial select-id (to-root e))]
              (if-let [s (selection (id :doc-ta))]
                (let [fqn (subs (config (id :doc-ta) :text) (first s) (second s))]
                  (seesaw.clipboard/contents! fqn))
                (if-let [fqn (config (id :doc-tf) :text)]
                  (seesaw.clipboard/contents! fqn)))))))
                  
(add-app-action :fqn-from-clipboard-action
  (action :name "Paste - FQN from clipboard"
          :key  "menu V"
          :handler (fn [e] (if-let [fqn (seesaw.clipboard/contents)] (invoke-soon (browser-with-fqn *ns* fqn (to-root e)))))))
          
(add-app-action :fqn-from-selection-action
  (action :name "FQN from selection"
          :key  "menu F"
          :handler (fn [e]
            (let [id (partial select-id (to-root e))]
              (if-let [s (selection (id :doc-ta))]
                (let [fqn (subs (config (id :doc-ta) :text) (first s) (second s))]
                  (invoke-soon (browser-with-fqn *ns* fqn (to-root e)))))))))
(add-app-action :open-url-from-selection-action
  (action :name "Open URL from selection"
          :key  "menu I"
          :handler (fn [e]
            (let [id (partial select-id (to-root e))]
              (when-let [s (selection (id :doc-ta))]
                (let [url (subs (config (id :doc-ta) :text) (first s) (second s))]
                  (future (doall (clj-ns-browser.web/browse-url url)))))))))

(add-app-action :manual-refresh-browser-action
  (action :name "Manual Refresh"
          :key  "menu R"
          :handler (fn [e]
            (let [root (to-root e)
                  id (partial select-id root)]
              (show! (pack! root))
              (auto-refresh-browser-handler {:root root})
              (ensure-selection-visible (id :ns-lb))
              (ensure-selection-visible (id :vars-lb))))))

(add-app-action :auto-refresh-browser-action
  (action :name "Auto-Refresh"
          :handler (fn [e] (let [root (to-root e)
                                 id (partial select-id root)]
                             (if (config (id :auto-refresh-browser-cb) :selected?) 
                               (.start (seesaw.meta/get-meta root :auto-refresh-browser-timer))
                               (.stop (seesaw.meta/get-meta root :auto-refresh-browser-timer)))))))

(add-app-action :var-trace-btn-action
  (action :name "Trace Var"
          :enabled? false
          :handler (fn [e] (let [root (to-root e)]
                             (letfn [(id [kw] (select-id root kw))] 
                               (swap! (id :var-trace-btn-atom) not))))))

(add-app-action :ns-trace-btn-action
  (action :name "Trace NS"
          :enabled? false
          :handler (fn [e] (let [root (to-root e)]
                             (letfn [(id [kw] (select-id root kw))]
                               (when-let [ns-str (selection (id :ns-lb))]
                                 (if-let [ns-ns (find-ns (symbol ns-str))]
                                   (do (clojure.tools.trace/trace-ns* ns-ns)
                                       (alert (str "Tracing all traceable vars in namespace: " ns-str)))
                                   (alert (str "Not a valid/loaded namespace: " ns-str)))))))))

(add-app-action :ns-untrace-btn-action
  (action :name "Untrace NS"
          :enabled? false
          :handler (fn [e] (let [root (to-root e)]
                             (letfn [(id [kw] (select-id root kw))]
                               (when-let [ns-str (selection (id :ns-lb))]
                                 (if-let [ns-ns (find-ns (symbol ns-str))]
                                   (do (clojure.tools.trace/untrace-ns* ns-ns)
                                       (alert (str "Untraced all traced-vars in namespace: " ns-str)))
                                   (alert (str "Not a valid/loaded namespace: " ns-str)))))))))

(defn update-vars-categorized-cb [cb-action-id new-val]
  (reset! group-vars-by-object-type-atom new-val)
  (config! cb-action-id :selected? new-val)
  (update-settings! {:vars-categorized-listing new-val}))

(add-app-action :vars-categorized-cb-action
  (action :name "Categorized Listing"
          :selected? false
          :handler
            (fn [e] (let [root (to-root e)]
                      (letfn [(id [kw] (select-id root kw))]
                        (update-vars-categorized-cb
                         (id :vars-categorized-cb-action)
                         (config (id :vars-categorized-cb-action) :selected?)))))))

(defn update-vars-fqn-listing-cb [atm cb-action-id new-val]
  (reset! atm new-val)
  (config! cb-action-id :selected? new-val)
  (update-settings! {:vars-fqn-listing new-val}))

(add-app-action :vars-fqn-listing-cb-action
  (action :name "FQN Listing"
          :selected? false
          :handler 
            (fn [e] (let [root (to-root e)]
                      (letfn [(id [kw] (select-id root kw))]
                        (update-vars-fqn-listing-cb
                         (id :vars-fqn-listing-cb-atom)
                         (id :vars-fqn-listing-cb-action)
                         (config (id :vars-fqn-listing-cb-action) :selected?)))))))

(defn update-vars-search-doc-also [cb-action-id new-val]
  (reset! vars-search-doc-also-cb-atom new-val)
  (config! cb-action-id :selected? new-val)
  (update-settings! {:vars-search-doc-also new-val}))


(add-app-action :vars-unmap-btn-action
  (action :name "Un-Map"
          :enabled? false))

(add-app-action :vars-unalias-btn-action
  (action :name "Un-Alias"
          :enabled? false))

(add-app-action :ns-require-btn-action
  (action :name "Require"
          :enabled? false
          :handler (fn [e] 
                     (let [root (to-root e)]
                       (letfn [(id [kw] (select-id root kw))]
                         (swap! (id :ns-require-btn-atom) not))))))

(add-app-action :fqn-history-back-action
  (action :name "Previous FQN"
          :key  "menu B"
          :enabled? true
          :handler (fn [e] 
           (let [root (to-root e)]
             (letfn [(id [kw] (select-id root kw))]
               (let [atm (seesaw.meta/get-meta root :fqn-history-list-atom)
                     stck @atm]
                 (when-let [p (and (not-empty stck) (pop stck))]
                   (when-let [fqn (peek p)]
                      (invoke-soon 
                        (swap! atm (fn [ll] 
                          (if (and (not-empty ll) (not-empty (pop ll)))
                            (pop (pop ll)) 
                            [])))
                        (browser-with-fqn *ns* fqn root))))))))))


;; Init functions called during construction of a frame with its
;; widget hierarchy

(defn init-before-bind
  [root]
  (letfn [(id [kw] (select-id root kw))]
    (seesaw.meta/put-meta! root :atom-map (make-button-atom-map all-buttons-with-atoms))
    (seesaw.meta/put-meta! root :fqn-history-list-atom (atom []))
    (swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
    (swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
    (config! (id :vars-lb-sp) :preferred-size (config (id :vars-lb-sp) :size))
    (config! (id :vars-cbx) :model vars-cbx-value-list)
    (config! (id :ns-lb) :model @all-ns-loaded-atom)
    (config! (id :ns-lb) :selection-mode :multi-interval) ;; experimental...
    (config! (id :vars-lb) :model [])
    (config! (id :ns-entries-lbl) :text "0")
    (tl/config-as-toggle-listbox! (id :doc-lb)
                                  doc-lb-value-list doc-lb-cur-order)
    (config! (id :edit-btn) :enabled? false)
    (config! (id :browse-btn) :enabled? false)
    (config! (id :clojuredocs-online-rb) :selected? true)
    (config! (id :inspect-btn) :enabled? false)
    (config! (id :vars-fqn-listing-cb-action) :selected? false)
    (listen (id :inspect-btn)
      :action (fn [event] (swap! (id :inspect-btn-atom) not)))
    (config! (id :ns-require-btn) :action (id :ns-require-btn-action))
    (config! (id :ns-require-btn-action) :enabled? false)
    (config! (id :var-trace-btn)
      :action (id :var-trace-btn-action))
    (listen (id :browse-btn)
      :action (fn [event] (swap! (id :browse-btn-atom) not)))
    (listen (id :edit-btn)
      :action (fn [event] (swap! (id :edit-btn-atom) not)))
    (listen (id :update-clojuredocs-btn)
      :action (fn [event] (swap! (id :update-clojuredocs-btn-atom) not)))
    (listen (id :clojuredocs-offline-rb)
      :action (fn [event] (swap! (id :clojuredocs-offline-rb-atom) not)))
    (listen (id :clojuredocs-online-rb)
      :action (fn [event] (swap! (id :clojuredocs-online-rb-atom) not)))
    (listen (id :color-coding-cb)
      :action (fn [event] (swap! (id :color-coding-cb-atom) not)))
    ;; vars
    (config! (id :vars-entries-lbl) :text "0")
    ;; doc
    (config! (id :doc-tf) :text "")
    (config! (id :doc-ta) :text "                                                                                            ")
    (selection! (id :ns-cbx) "loaded")
    (selection! (id :vars-cbx) "Vars - public")
    (selection! (id :doc-lb) "Doc")))


(defn init-after-bind
  [root read-and-apply-saved-settings?]
  (when read-and-apply-saved-settings?
    (swap! settings-atom
           (fn [cur-settings settings-read]
             ;;(printf "read-settings result=")
             ;;(clojure.pprint/pprint settings-read)
             ;;(flush)
             settings-read)
           (read-settings)))
  (letfn [(id [kw] (select-id root kw))]
    (invoke-soon
     (when read-and-apply-saved-settings?
       (let [settings @settings-atom]
         (if (:clojuredocs-online settings)
           (config! (id :clojuredocs-online-rb) :selected? true)
           (let [[status msg] (clojuredocs-offline-mode!)]
             (case status
               :ok (config! (id :clojuredocs-offline-rb) :selected? true)
               :snapshot-file-not-found
               (config! (id :clojuredocs-online-rb) :selected? true))))
         (update-vars-categorized-cb (id :vars-categorized-cb-action)
                                     (:vars-categorized-listing settings))
         (update-vars-fqn-listing-cb (id :vars-fqn-listing-cb-atom)
                                     (id :vars-fqn-listing-cb-action)
                                     (:vars-fqn-listing settings))
         (update-vars-search-doc-also (id :vars-search-doc-also-cb)
                                      (:vars-search-doc-also settings))
         ))
     (selection! (id :ns-cbx) "loaded")
     (selection! (id :vars-cbx) "Vars - public")
     (selection! (id :doc-lb) "Doc"))))


(defn bind-all
  "Collection of all the bind-statements that wire the clj-ns-browser events and widgets. (pretty amazing how easy it is to express those dependency-graphs!)"
  [root]
  (letfn [(id [kw] (select-id root kw))]
    ;; # of entries in ns-lb => ns-entries-lbl
    (b/bind
      (b/property (id :ns-lb) :model)
      (b/transform widget-model-count (id :ns-lb))
      (id :ns-entries-lbl))
    ;; following doesn't work with categorized display
    ;;   - update instead when vars-entries-lbl when vars-lb is written
    ;; # of entries in vars-lb => vars-entries-lbl
;;     (b/bind
;;       (b/property (id :vars-lb) :model)
;;       (b/transform widget-model-count (id :vars-lb))
;;       (id :vars-entries-lbl))
    ;; new ns selected in ns-lb =>
    (b/bind
      (b/selection (id :doc-tf))
      (b/transform
        (fn [v]
          (if-let [fqn (config (id :doc-tf) :text)]
            (let [vr (resolve-fqname fqn)]
              (if (var-traceable? vr)
                (do (if (var-traced? vr)
                      (config! (id :var-trace-btn-action) :name "Untrace Var")
                      (config! (id :var-trace-btn-action) :name "Trace Var"))
                    true)
                (do (config! (id :var-trace-btn-action) :name "Trace Var")
                    false)))
            (do (config! (id :var-trace-btn-action) :name "Trace Var")
                false))))
      (b/property (id :var-trace-btn-action) :enabled?))
    ;; dis/enable require-btn, update fqn in doc-tf, scroll within view
    (b/bind
      (b/selection (id :ns-lb) {:multi? true})
        (b/tee
          (b/bind
            (b/transform (fn [ns-list]
              (if (and ns-list (= 1 (count ns-list)) (some #(= (first ns-list) %) @all-ns-unloaded-atom))
                true
                false)))
            (b/property (id :ns-require-btn-action) :enabled?))
          (b/bind
            (b/transform (fn [ns-list]
              (let [ns-str (and ns-list (first ns-list))]
                (if (and ns-str (find-ns (symbol ns-str)))
                  true
                  false))))
            (b/property (id :ns-trace-btn-action) :enabled?))
          (b/bind
            (b/transform (fn [ns-list]
              (let [ns-str (and ns-list (first ns-list))]
                (if (and ns-str (find-ns (symbol ns-str)))
                  true
                  false))))
            (b/property (id :ns-untrace-btn-action) :enabled?))
          (b/bind
            (b/transform (fn [ns-list]
              (let [ns-str (and ns-list (first ns-list))]
                (if (and ns-str (find-ns (symbol ns-str)))
                  (fqname ns-str)
                  ""))))
            (b/property (id :doc-tf) :text))))
    ;; require-btn pressed =>
    ;; (require ns), update (un-)loaded atoms, select loaded, select ns.
    (b/bind
      (id :ns-require-btn-atom)
      (b/transform (fn [& b]
        (when-let [n (selection (id :ns-lb))]
          (require (symbol n))
          (swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
          (swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
          (let [i (.indexOf (seq @all-ns-loaded-atom) n)]
            (if (pos? i)
              (do
                (selection! (id :ns-cbx) "loaded")
                (selection! (id :ns-lb) n)
                (scroll! (id :ns-lb) :to [:row i]))
              (alert (str "Hmmm... seems that namespace \"" n "\" cannot be required (?)"))))))))
    ;; (un)trace-btn pressed =>
    (b/bind
      (id :var-trace-btn-atom)
      (b/transform
        (fn [_]
          (when-let [fqn (config (id :doc-tf) :text)]
            (let [vr (resolve-fqname fqn)]
              (when (var-traceable? vr)
                (if (var-traced? vr)
                  (do (clojure.tools.trace/untrace-var* vr)
                      (config! (id :var-trace-btn-action) :name "Trace Var"))
                  (do (clojure.tools.trace/trace-var* vr)
                      (config! (id :var-trace-btn-action) :name "Untrace Var")))
                (selection! (id :vars-lb) (selection (id :vars-lb)))))))))
    ;;
    ;; select item in vars-lb => set associated fqn in doc-tf
    (b/bind
      (b/selection (id :vars-lb))
      (b/transform (fn [v]
        (if v
          (let [fqn (if (= "Aliases" (selection (id :vars-cbx)))
                      (when-let [n (get (ns-aliases
                                         (the-ns (symbol
                                                  (selection (id :ns-lb)))))
                                        (symbol v))]
                        (str n))
                      (fqname (selection (id :ns-lb)) v))]
            (when (not= fqn "")
              ;; update fqn-history-list
              (let [l (seesaw.meta/get-meta root :fqn-history-list-atom)]
                (when-not (= (peek @l) fqn)
                      (swap! l (fn [ll] (conj ll fqn)))))
              fqn)))))
      (b/tee
          (b/property (id :doc-tf) :text)))
    ;;
    ;; dis/enable prev fqn button/menu when no history available
    (b/bind
      (seesaw.meta/get-meta root :fqn-history-list-atom)
      (b/transform (fn [o]
        (if (> (count @(seesaw.meta/get-meta root :fqn-history-list-atom)) 1)
          true
          false)))
      (b/property (id :fqn-history-back-action) :enabled?))
    ;;
    ;; (un-)loaded ns-cbx and regex filter tf =>
    ;; updated ns-list in ns-lb
    (b/bind
      (apply b/funnel
        [(id :ns-cbx)
         (id :ns-filter-tf)
         ns-lb-refresh-atom])
      (b/transform (fn [o]
        (let [v (selection (id :ns-cbx))]
          (when (= v "unloaded") (config! (id :doc-tf) :text "")(config! (id :doc-ta) :text ""))
          ((get ns-cbx-value-fn-map v)))))
      (b/transform regx-tf-filter (id :ns-filter-tf) (atom false))
      (b/filter (fn [l]  ;; only refresh if the list really has changed
        (if (= l (seesaw.meta/get-meta root :last-ns-lb))
          false
          (do 
            (seesaw.meta/put-meta! root :last-ns-lb l)
            true))))
      (b/filter (fn [symbol-info]
                  (sort (map :display-str symbol-info))))
      (b/notify-soon)
      (b/property (id :ns-lb) :model))
    ;;
    ;; (un-)loaded vars-cbx and regex filter tf =>
    ;; updated vars-list in vars-lb
    (b/bind
      (apply b/funnel
        [(b/selection (id :vars-cbx))
         (b/selection (id :ns-lb) {:multi? true})
         (id :vars-filter-tf)
         vars-search-doc-also-cb-atom
         vars-lb-refresh-atom
         group-vars-by-object-type-atom
         (id :vars-fqn-listing-cb-atom)])
      (b/transform (fn [o]
        (let [ns-list (selection (id :ns-lb) {:multi? true})
              n (and ns-list (map #(find-ns (symbol %)) ns-list))
              always-display-fqn? (config (id :vars-fqn-listing-cb-action) :selected?)
              v (selection (id :vars-cbx))
              f (get vars-cbx-value-fn-map v)]
          (if n
            (f n (or always-display-fqn? (> (count n) 1))
               @vars-search-doc-also-cb-atom)
            []))))
      (b/transform regx-tf-filter (id :vars-filter-tf)
                   vars-search-doc-also-cb-atom)
      (b/transform (fn [symbol-info]
        (config! (id :vars-entries-lbl) :text (count symbol-info))
        (if @group-vars-by-object-type-atom
          (group-by-object-type symbol-info)
          (sort (map :display-str symbol-info)))))
      (b/filter (fn [l]  ;; only refresh if the list really has changed
        (if (= l (seesaw.meta/get-meta root :last-vars-lb))
          false
          (do 
            (seesaw.meta/put-meta! root :last-vars-lb l)
            true))))
      (b/notify-soon)
      (b/property (id :vars-lb) :model))
    ;;
    ;; typed regex in ns-filter-tf => visual feedback about validity
    (b/bind
      ;; As the text of the textbox changes ...
      (id :ns-filter-tf)
      ;; Convert it to a regex, or nil if it's invalid
      (b/transform #(try (re-pattern %) (catch Exception e nil)))
      ;; Now split into two paths ...
      (b/bind
        (b/transform #(if % "white" "lightcoral"))
        (b/notify-soon)
        (b/property (id :ns-filter-tf) :background)))
    ;;
    ;; typed regex in vars-filter-tf => visual feedback about validity
    (b/bind
      ;; As the text of the textbox changes ...
      (id :vars-filter-tf)
      ;; Convert it to a regex, or nil if it's invalid
      (b/transform #(try (re-pattern %) (catch Exception e nil)))
      ;; Now split into two paths ...
      (b/bind
        (b/transform #(if % "white" "lightcoral"))
        (b/notify-soon)
        (b/property (id :vars-filter-tf) :background)))
    ;;
    ;; updated fqn in doc-tf or doc-lb =>
    ;; new render-doc-text in doc-ta
    (b/bind
      ;; As the text of the fqn text field changes ...
      (apply b/funnel [(id :doc-tf)
                       (b/selection (id :doc-lb) {:multi? true})
                       vars-lb-refresh-atom])
      (b/filter (fn [[doc-tf doc-lb]]
;;                  (printf "(class doc-lb)=%s (seq doc-lb)='%s' (map ... doc-lb)='%s'\n"
;;                          (class doc-lb) (seq doc-lb)
;;                          (seq doc-lb))
;;                  (flush)
                  (not (or (nil? doc-tf)  (= "" doc-tf)
                           (nil? doc-lb) (= "" doc-lb)))))
      (b/transform
        (fn [[doc-tf doc-lb]]
          (future
            (let [s (render-doc-text doc-tf doc-lb)]
              (when-not (= s (config (id :doc-ta) :text))
                (invoke-soon (config! (id :doc-ta) :text s))))))))
    ;;
    ;; new text in doc-ta => scroll to top
    (b/bind
      (id :doc-ta)
      (b/notify-later)
      (b/transform (fn [t] (scroll! (id :doc-ta) :to :top))))
    ;; color doc-tf based on macro/fn/etc.
    (b/bind
      (apply b/funnel [(id :doc-tf) (id :color-coding-cb-atom)])
      (b/transform (fn [o] 
        (let [fqn (config (id :doc-tf) :text)]
          (if (config (id :color-coding-cb) :selected?)
            (let [type-str (get-object-type fqn)]
              (cond 
                (or (nil? fqn) (= "" fqn)) :white
                (= type-str "Macro") :tomato
                (= type-str "Function") :lightgreen
                (= type-str "Multimethod") :lightgreen
                (= type-str "Protocol Interface/Function") :lightgreen
                (= type-str "Special Form") :lightsalmon
                (= type-str "Special Symbol") :lightsalmon
                (= type-str "Protocol") :lightblue
                (= type-str "Namespace") :blanchedalmond
                (= type-str "Class") :wheat
                (= type-str "java.lang.Class") :wheat
                true :white))
            :white))))
      (b/property (id :doc-tf) :background))
    ;;
    ;; new text in doc-ta => dis/enable browser button
    (b/bind
      (id :doc-ta)
      (b/transform (fn [o] (selection (id :doc-lb) {:multi? true})))
      (b/transform (fn [o]
        (if (empty? (set/intersection (set o)
                                      #{"Examples" "See alsos" "Comments"}))
          (invoke-soon (config! (id :browse-btn) :enabled? true))
          (if-let [fqn (config (id :doc-tf) :text)]
            (future
              (let [url (clojuredocs-url fqn)
                    r (if url true false)]
                (invoke-soon
                 (config! (id :browse-btn) :enabled? r)))))))))
    ;;
    (b/bind
      (apply b/funnel [(id :doc-tf)
                       (b/selection (id :doc-lb) {:multi? true})])
      ;;(b/transform (fn [o] (selection (id :doc-lb))))
      (b/transform (fn [o]
;;                     (printf "update edit-btn: (class o)=%s o='%s'\n"
;;                             (class o) (seq o))
;;                     (flush)
                     (let [doc-set (set o)]
                       (or (doc-set "Source") (doc-set "All")))))
      (b/transform (fn [o]
        (when o (if (meta-when-file (config (id :doc-tf) :text))
                  true
                  false))))
      (b/notify-soon)
      (b/property (id :edit-btn) :enabled?))
    ;;
    ;; browser-btn pressed =>
    ;;
    (b/bind
      (apply b/funnel [(id :doc-tf)
                       (b/selection (id :doc-lb) {:multi? true})])
      (b/transform (fn [[doc-tf doc-lb]] [(config (id :doc-tf) :text) doc-lb]))
      (b/transform (fn [[fqn doc-lb-sel]]
                     (let [doc-set (set doc-lb-sel)]
                       (if (or (doc-set "Value") (doc-set "All"))
                         fqn
                         false))))
      (b/transform (fn [fqn]
        (when fqn
          (when-let [val (resolve-fqname fqn)]
            (and (var? val)(coll? @val))))))
      (b/notify-soon)
      (b/property (id :inspect-btn) :enabled?))
    ;;
    ;; turn syntax-highlighting on/off - only on for Source
    (b/bind
      (b/selection (id :doc-lb) {:multi? true})
      (b/transform (fn [o] 
        (when (= (.getName (type (id :doc-ta))) "org.fife.ui.rsyntaxtextarea.RSyntaxTextArea")
          (let [selected-set (set o)]
            (if (= selected-set
                   (set/intersection selected-set
                                     #{"Source" "Examples" "Meta"}))
              (config! (id :doc-ta) :syntax :clojure)
              (config! (id :doc-ta) :syntax :none)))))))
    ;;
    ;; bring up browser with url
    (b/bind
      (id :browse-btn-atom)
      (b/notify-soon)
      (b/transform (fn [& oo]
        (let [o (selection (id :doc-lb))]
          (when-let [fqn (config (id :doc-tf) :text)]
            (future
              (case o
                ("Examples" "See alsos" "Comments")
                (when-let [url (clojuredocs-url fqn)]
                  (clj-ns-browser.web/browse-url url))

                (with-redefs [clojure.java.browse/browse-url clj-ns-browser.web/browse-url]
                  (bdoc* fqn)))))))))
    ;;
    ;; edit-btn pressed =>
    ;; if we find a local file (not inside jar), then send to $EDITOR.
    (b/bind
      (id :edit-btn-atom)
      (b/transform (fn [& o]
        (future
          (when-let [m (meta-when-file (config (id :doc-tf) :text))]
            (when-let [e (:out (clojure.java.shell/sh "bash" "-c" (str "echo -n $EDITOR")))]
              (:exit (clojure.java.shell/sh "bash" "-c" (str e " +" (:line m) " " (:file m))))))))))
    ;;
    ;; menu buttons
    ;;
    ;; use local copy for clojuredocs lookup of comments/examples
    (b/bind
      (b/funnel
        (id :clojuredocs-offline-rb-atom)
        (id :clojuredocs-online-rb-atom))
      (b/transform (fn [& o]
        (if (config (id :clojuredocs-offline-rb) :selected?)
          (let [[status msg] (clojuredocs-offline-mode!)]
            (case status
              :ok (alert (str "Note: Locally cached ClojureDocs copy will be used"
                              \newline msg))
              :snapshot-file-not-found
              (do (alert "No locally cached ClojureDocs repo found - update first")
                  (config! (id :clojuredocs-online-rb) :selected? true))))
          (let [s (with-out-str (cd-client.core/set-web-mode!))]
            (alert (str "Note: Online ClojureDocs will be used" \newline s))))
        (update-settings! {:clojuredocs-online
                           (config (id :clojuredocs-online-rb) :selected?)}))))
    ;;
    ;; update locally cached clojuredocs repo
    (b/bind
      (id :update-clojuredocs-btn-atom)
      (b/transform (fn [& o]
        (future
          (let [f (clojuredocs-snapshot-filename)]
            (spit f (slurp
        "https://raw.github.com/jafingerhut/cd-client/develop/snapshots/clojuredocs-snapshot-latest.txt"))
            (invoke-soon (alert (str "Locally cached copy of ClojureDocs updated at:" \newline f))))))))
    ;;
    ;; inspect-btn pressed =>
    ;; If var is selected and its value is a collection, create inspector.
    (b/bind
      (id :inspect-btn-atom)
      (b/transform (fn [& o]
        (when-let [fqn (config (id :doc-tf) :text)]
          (when-let [val (resolve-fqname fqn)]
            (when (and (var? val)(coll? @val))
              (future
                (clj-ns-browser.inspector/inspect-tree
                 @val (str "Inspector for value of " fqn)))))))))
    ;;
    )) ; end of bind-all


(defn set-font-handler! [root f]
  (letfn [(id [kw] (select-id root kw))]
    (config! (id :doc-ta) :font {:name f :size (.getSize (config (id :doc-ta) :font))})
    (config! (id :doc-tf) :font {:name f :style :bold :size (.getSize (config (id :doc-tf) :font))})
    (config! (id :ns-lb) :font {:name f :size (.getSize (config (id :ns-lb) :font))})
    (config! (id :vars-lb) :font {:name f :size (.getSize (config (id :vars-lb) :font))})
    ))


(defn auto-refresh-browser-handler
  ""
  [m]
  (let [root (:root m)
        id (partial select-id root)
        prev-ns (:selected-ns m)
        prev-var (:selected-ns m)
        selected-ns (selection (id :ns-lb))
        selected-var (selection (id :vars-lb))]
    (swap! ns-lb-refresh-atom not)
    (swap! vars-lb-refresh-atom not)
    (when-not (= (selection (id :ns-lb)) selected-ns) 
      (selection! (id :ns-lb) selected-ns)
      (ensure-selection-visible (id :ns-lb))
      (selection! (id :vars-lb) selected-var)
      (ensure-selection-visible (id :vars-lb)))
    (when-not (= (selection (id :vars-lb)) selected-var) 
      (selection! (id :vars-lb) selected-var)
      (ensure-selection-visible (id :vars-lb)))
    {:root root :selected-ns selected-ns :selected-var selected-var}))

;;seesaw.core/toggle-full-screen! (locks up computer!!! don't use)
;;(seesaw.dev/show-options) and (seesaw.dev/show-events)
(defn init-menu-before-bind
  "Built menu for given browser-root-frame.
  Note that each frame has its own menu, which will be active when frame is in-focus."
  [root]
  (letfn [(id [kw] (select-id root kw))]

    ;; built-up menu-bar
    (let [main-menu (menubar :id :main-menu)
          edit-menu (menu :text "Edit"  :id :edit-menu)
          file-menu (menu :text "File"  :id :file-menu)
          ns-menu (menu :text "Namespaces" :id :ns-menu)
          vars-menu (menu :text "Vars" :id :vars-menu)
          docs-menu (menu :text "Docs" :id :docs-menu)
          clojuredocs-menu (menu :text "ClojureDocs" :id :clojuredocs-menu)
          window-menu (menu :text "Window" :id :window-menu)
          help-menu (menu :text "Help"  :id :help-menu)
          
          font-menu (menu :text "Font"  :id :font-menu)
          font-btn-group (button-group)
          font-Monospaced-rb (radio-menu-item :text "Monospaced" :id :font-Monospaced-rb :group font-btn-group)
          font-Menlo-rb (radio-menu-item :text "Menlo" :id :font-Menlo-rb :group font-btn-group)
          font-Inconsolata-rb (radio-menu-item :text "Inconsolata" :id :font-Inconsolata-rb :group font-btn-group)
        
          update-clojuredocs (menu-item :text "ClojureDocs Update local repo" :id :update-clojuredocs-btn)
          clojuredocs-access-btn-group (button-group)
          clojuredocs-online-rb (radio-menu-item :text "ClojureDocs Online" :id :clojuredocs-online-rb :group clojuredocs-access-btn-group)
          clojuredocs-offline-rb (radio-menu-item :text "ClojureDocs Offline/Local" :id :clojuredocs-offline-rb :group clojuredocs-access-btn-group)

          vars-fqn-listing-cb (checkbox-menu-item :action (id :vars-fqn-listing-cb-action) :id :vars-fqn-listing-cb)
          
          vars-categorized-cb (checkbox-menu-item :action (id :vars-categorized-cb-action) 
                                                  :id :vars-categorized-cb)
          
          vars-search-doc-also-cb (checkbox-menu-item :text "Search Docs Also" :id :vars-search-doc-also-cb)
          
          auto-refresh-browser-cb (checkbox-menu-item :action (id :auto-refresh-browser-action) :id :auto-refresh-browser-cb)
          auto-refresh-browser-timer (timer auto-refresh-browser-handler  :initial-value {:root root} 
                                            :initial-delay 1000   :start? false)
          color-coding-cb (checkbox-menu-item :text "Color Coding" :id :color-coding-cb)
          
          ;; popup's
          doc-ta-popup (popup :id :doc-ta-popup)
          ns-lb-popup (popup :id :ns-lb-popup)
          vars-lb-popup (popup :id :vars-lb-popup)

          ]
          
      (seesaw.meta/put-meta! root :auto-refresh-browser-timer auto-refresh-browser-timer)
      
      (config! root :menubar main-menu)

      (config! main-menu
        :items [file-menu edit-menu ns-menu vars-menu docs-menu window-menu help-menu])

      (config! file-menu :items [(id :new-browser-action)])

      (config! edit-menu :items [(id :copy-fqn-action) (id :fqn-from-clipboard-action)
                                 (id :fqn-from-selection-action) (id :open-url-from-selection-action)
                                  :separator (id :fqn-history-back-action)])

      (config! ns-menu :items [(id :ns-require-btn-action) :separator (id :ns-trace-btn-action) (id :ns-untrace-btn-action)])

      (config! vars-menu :items 
               [(id :var-trace-btn-action)
                ;;:separator
                ;;(id :vars-unmap-btn-action) (id :vars-unalias-btn-action)
                :separator
                vars-categorized-cb 
         vars-fqn-listing-cb vars-search-doc-also-cb])

      
      (config! auto-refresh-browser-cb
        :listen [:action (fn [e] (if (config auto-refresh-browser-cb :selected?) 
                                   (.start auto-refresh-browser-timer) 
                                   (.stop auto-refresh-browser-timer)))]
        :selected? false)

      (config! vars-search-doc-also-cb 
        :listen [:action (fn [e] (update-vars-search-doc-also
                                  vars-search-doc-also-cb
                                  (config vars-search-doc-also-cb :selected?)))]
        :selected? false)
      
      (config! color-coding-cb :selected? true)

      (config! font-Monospaced-rb :listen [:action (fn [e] (set-font-handler! root "Monospaced"))] :selected? true)
      (config! font-Menlo-rb :listen [:action (fn [e] (set-font-handler! root "Menlo"))])
      (config! font-Inconsolata-rb :listen [:action (fn [e] (set-font-handler! root "Inconsolata"))])
      (config! font-menu :items [font-Monospaced-rb  font-Menlo-rb font-Inconsolata-rb])

      (config! window-menu :items [(id :zoom-in-action) (id :zoom-out-action) font-menu color-coding-cb :separator
        (id :manual-refresh-browser-action) auto-refresh-browser-cb :separator (id :bring-all-windows-to-front-action) 
        (id :cycle-through-windows-action)])

      (config! help-menu :items [(id :go-github-wiki-action) (id :go-github-action) (id :go-clojure.org-action) (id :go-clojuredocs-action) (id :go-cheatsheet-action) (id :go-stackoverflow-action) (id :go-jira-action) (id :go-about-action)])
      (config! docs-menu :items [update-clojuredocs :separator
                                 clojuredocs-online-rb clojuredocs-offline-rb
                                 :separator])
      
      ;; popup's
      (config! (id :doc-ta) :popup doc-ta-popup)
      (config! doc-ta-popup :items [(id :new-browser-action) :separator (id :copy-fqn-action) 
                                    (id :fqn-from-clipboard-action) (id :fqn-from-selection-action)
                                    (id :open-url-from-selection-action)
                                    :separator (id :fqn-history-back-action) :separator
                                    ;;auto-refresh-browser-cb :separator
                                    (id :zoom-in-action) (id :zoom-out-action)])
                                    
      (config! (id :ns-lb) :popup ns-lb-popup)
      (config! ns-lb-popup :items [ (id :new-browser-action) 
                                    :separator
                                    (id :copy-fqn-action) 
                                    (id :fqn-from-clipboard-action) 
                                    (id :fqn-from-selection-action)
                                    (id :open-url-from-selection-action)
                                    :separator (id :fqn-history-back-action)
                                    :separator
                                    (id :ns-require-btn-action)
                                    :separator 
                                    (id :ns-trace-btn-action) (id :ns-untrace-btn-action) 
                                    ;;:separator auto-refresh-browser-cb
                                    ])
                                    
      (config! (id :vars-lb) :popup vars-lb-popup)
      (config! vars-lb-popup :items [(id :new-browser-action) 
                                    :separator 
                                    (id :copy-fqn-action) (id :fqn-from-clipboard-action)
                                    (id :fqn-from-selection-action) (id :open-url-from-selection-action)
                                    :separator (id :fqn-history-back-action)
                                    :separator
                                    (id :var-trace-btn-action) 
                                    ;;:separator 
                                    ;;(id :vars-unmap-btn-action) (id :vars-unalias-btn-action) 
                                    :separator 
                                    (checkbox-menu-item :action (id :vars-categorized-cb-action))
                                    (checkbox-menu-item :action (id :vars-fqn-listing-cb-action))
                                    ;; vars-search-doc-also-cb
                                    ;;:separator auto-refresh-browser-cb
                                    ])

      )))

;; init and browser-window management



(defn refresh-clj-ns-browser
  "Refresh all or the given browser-window (pack! and show!)"
;;   ([] (map #(invoke-later (show! (pack! %))) @browser-root-frms))
;;   ([root] (invoke-later (show! (pack! root)))))
  ([] 
    (doall (map #(invoke-soon (show! %)) @clj-ns-browser.browser/browser-root-frms)))
  ([root] 
    (invoke-soon (show! root))))


(defn new-clj-ns-browser
  "Returns a new browser root frame with an embedded browser form.
  Add new frame to atom-list browser-root-frms"
  []
  (let [root (frame)
        b-form (identify (clj_ns_browser.BrowserForm.))]
    (config! root :content b-form)
    (pack! root)
    (init-menu-before-bind root)
    (init-before-bind root)
    (bind-all root)
    (init-after-bind root (empty? @browser-root-frms))
    (swap! browser-root-frms (fn [a] (conj @browser-root-frms root)))
    (config! root :title (str "Clojure Namespace Browser - " (.indexOf @browser-root-frms root)))
    (config! root :id (keyword (str "browser-frame-" (.indexOf @browser-root-frms root))))
    (swap! browser-root-frm-map (fn [a] (assoc @browser-root-frm-map (config root :id) root)))
    (config! root :transfer-handler (seesaw.dnd/default-transfer-handler
      :import [seesaw.dnd/string-flavor (fn [{:keys [data]}] (browser-with-fqn nil data root))]))
    (config! (select-id (get-clj-ns-browser) :vars-lb) 
        :drag-enabled? true
        :transfer-handler (seesaw.dnd/default-transfer-handler :export 
          { :actions (fn [_] :copy) 
            :start   (fn [w] [seesaw.dnd/string-flavor (seesaw.core/selection w)]) }))
    (listen root :component-hidden 
      (fn [e] (when (every? (fn [f] (not (config f :visible?))) @browser-root-frms) 
        (refresh-clj-ns-browser root)
        (alert "Sorry... cannot close/hide last browser window."))))
    (refresh-clj-ns-browser root)
    root))


(defn get-clj-ns-browser
  "Returns the first browser root frame from browser-root-frms,
  or if none, create one first."
  []
  (or (first @browser-root-frms) (new-clj-ns-browser)))

(defn get-next-clj-ns-browser
  [root]
  (if-let [i (.indexOf @browser-root-frms root)]
    (if (> (count @browser-root-frms) (inc i))
      (nth @browser-root-frms (inc i))
      (nth @browser-root-frms 0))))

(defn browser-with-fqn
  "Display a-ns/a-name in browser-frame."
  ([a-name] (browser-with-fqn *ns* a-name (get-clj-ns-browser)))
  ([a-ns a-name] (browser-with-fqn a-ns a-name (get-clj-ns-browser)))
  ([a-ns a-name browser-frame]
    (let [root browser-frame
          id (partial select-id root)
          n-str (selection (id :ns-lb))]
      (if-let [fqn (and a-name 
                        (or (string? a-name)(symbol? a-name))
                        (or (fqname (or a-ns n-str *ns*) a-name)
                            (fqname (or n-str *ns*) a-name)))
                        ]
        (let [lname-str (local-name fqn)
              fqn-listing (config (id :vars-fqn-listing-cb-action) :selected?)
              sym1 (symbol fqn)
              name1 (name sym1)
              ns1 (try (namespace sym1)(catch Exception e))]
          (if ns1
            ;; we have a fq-var as a-ns/a-name
            (invoke-soon
              (selection! (id :ns-cbx) "loaded")
              (selection! (id :vars-cbx) "Vars - all")
              (selection! (id :ns-lb) ns1)
              (invoke-later 
                (if fqn-listing
                  (selection! (id :vars-lb) fqn)
                  (selection! (id :vars-lb) lname-str))
                (ensure-selection-visible (id :ns-lb))
                (ensure-selection-visible (id :vars-lb))))
              
            (if (find-ns (symbol name1))
              ;; should be namespace
              (invoke-soon
                (selection! (id :ns-lb) name1)
                (ensure-selection-visible (id :ns-lb))
                (ensure-selection-visible (id :vars-lb)))
                
              ;; else must be special-form or class
              (invoke-soon
                (if (special-form? fqn)
                  (do
                    (selection! (id :ns-cbx) "loaded")
                    (selection! (id :ns-lb) (str *ns*))
                    (selection! (id :vars-cbx) "Special-Forms")
                    (selection! (id :vars-lb) name1))
                  (do
                    (selection! (id :ns-lb) (str *ns*))
                    (selection! (id :vars-cbx) "Classes - all")
                    (if fqn-listing
                      (selection! (id :vars-lb) fqn)
                      (selection! (id :vars-lb) lname-str))))
                (invoke-later 
                  (ensure-selection-visible (id :ns-lb))
                  (ensure-selection-visible (id :vars-lb))))))
            (refresh-clj-ns-browser root)
            fqn)
        (when (nil? a-ns)
            (browser-with-fqn *ns* a-name browser-frame))))))

