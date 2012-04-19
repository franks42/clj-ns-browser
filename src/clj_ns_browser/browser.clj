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
            [clojure.java.browse]
            [clojure.java.shell]
            [clojure.string :as str]
            [clj-info.doc2map :as d2m]
            [clj-ns-browser.inspector]
            [seesaw.meta]
            [clojure.java.javadoc]
            [cd-client.core])
  (:use [clj-ns-browser.utils]
        [seesaw.core]
        [seesaw.border]
        [seesaw.mig]
        [seesaw.dev]
        [clj-info]))


;; Much of the processing depends on selecting widgets/atoms/actions from frame-trees.
;; to ease the selection, the function (select-id root kw) is used, which looks up
;; the widget/action/atom in root associated with keyword kw.
;; because a lot of the processing is done for a certain root, the select-id function
;; is curried often like (let [id (partial select-id root)] ...), such
;; that within the let form, the widgets can be selected with (id kw)
;; note that the actions that are searched for with select-id are maintained in
;; the global app-action-map and actions are added with (add-app-action kw actn)
;; the atoms associated with buttons are maintained in a map stored in the user-data
;; of a frame - as long as you maintain the buttons in the var "all-buttons-with-atoms"
;; then the atoms are auto-generated and can be accessed with select-id with the
;; same keyword as the button but with"-atom" appended



(def clj-ns-browser-version "1.2.0-SNAPSHOT")


;; forward declarations... ough that clojure compiler should be smarter...
(declare new-clj-ns-browser)
(declare get-clj-ns-browser)
(declare browser-with-fqn)
(declare refresh-clj-ns-browser)
(declare get-next-clj-ns-browser)


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
  "Increase the fint-size of the widget w by 1."
  [w]
  (config! w :font {:size (+ 1 (.getSize (config w :font)))}))


(defn font-size-
  "Decrease the fint-size of the widget w by 1."
  [w]
  (config! w :font {:size (- (.getSize (config w :font)) 1)}))


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
  (swap! app-action-map (fn [m k a] (assoc m k a)) kw actn))


(defn select-id
  "Convenience function for seesaw.core/select to identify a widget easily by its :id value.
  When not found in widget-tree, looks into (seesaw.meta/get-meta root :atom-map).
  Input: root - widget-root
  Input: ss-id is keyword, symbol or string referring to widget :id value
  Output: reference to widget with :id equal to ss-id
  Usage: (select-id root :ns-lb))
  or use with partial like: (let [id (partial select-id root)] (config (id :ns-lb) ...))"
  [root ss-id]
  (let [str-id (if (keyword? ss-id) (name ss-id) (str ss-id))]
    (or
      ;; first look in the root's tree for a widget with that :id of ss-id
      (seesaw.core/select root [ (keyword (str "#" str-id)) ])
      ;; next look in the root's atom-map for a matching root-specific atom
      (when-let [m (seesaw.meta/get-meta root :atom-map)]
        (get m (keyword str-id)))
      ;; last look in the global app-action-map for a matching action
      (when-let [actn (get @app-action-map (keyword str-id))] actn)
      (do (println "id not found: " ss-id ) nil))))


;; app specific constants


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
                         "Comments" "See alsos" "Value" "Meta"])

(def all-buttons-with-atoms
  "Used to auto-generate atoms and '-atom' keywords"
  [:ns-require-btn :browse-btn :edit-btn :clojuredocs-offline-rb :clojuredocs-online-rb :update-clojuredocs-btn :var-trace-btn :inspect-btn])

(defn make-atom-kw [kw] (keyword (str (clj-ns-browser.utils/fqname kw) "-atom")))

(defn make-button-atom-map
  [btn-list]
  (let [btn-atom-kw-list (map make-atom-kw btn-list)]
    (into {} (map (fn [k] [k (atom true)]) btn-atom-kw-list))))


;; "global" atoms

(def all-ns-loaded-atom (atom nil))
(def all-ns-unloaded-atom (atom nil))
(defn ns-loaded [] @all-ns-loaded-atom)
(defn ns-unloaded [] @all-ns-unloaded-atom)
(swap! all-ns-unloaded-atom (fn [& a] (all-ns-unloaded)))
(swap! all-ns-loaded-atom (fn [& a] (all-ns-loaded)))
(def group-vars-by-object-type (atom true))


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


(defn better-get-docs-map [fqn]
  (if (= fqn "clojure.core//")
    {:object-type-str "Function"}
    (d2m/get-docs-map fqn)))


(defn simplify-object-type [obj-type-str]
  (cond
   (nil? obj-type-str) "(nil)"
   (re-find #"^Var .*" obj-type-str) "Var"
   :else obj-type-str))


(defn group-by-object-type [symbol-str-seq fqns? ns-str]
  (let [symbol-info (map (fn [s]
                               (let [fqn (if fqns? s (str ns-str "/" s))
                                     obj-type-str (-> fqn
                                                      ;;d2m/get-docs-map
                                                      better-get-docs-map
                                                      :object-type-str
                                                      simplify-object-type)]
                                 {:orig-str s
                                  :fqn-str fqn
                                  :obj-type-str obj-type-str}))
                             symbol-str-seq)
        groups (group-by :obj-type-str symbol-info)]
    (apply concat (interpose
                   [ " " ]
                   (map (fn [[obj-type-str l]]
                          (concat [ (str "        " obj-type-str) ]
                                  (sort (map :orig-str l))))
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
                  (invoke-soon (browser-with-fqn "" fqn (new-clj-ns-browser))))
                (if-let [fqn (config (id :doc-tf) :text)]
                  (invoke-soon (browser-with-fqn "" fqn (new-clj-ns-browser)))
                  (invoke-soon (new-clj-ns-browser))))))))

(add-app-action :go-github-action
  (action :name "Clj-NS-Browser GitHub..."
          :handler (fn [a] (future (clojure.java.browse/browse-url
            "https://github.com/franks42/clj-ns-browser")))))
(add-app-action :go-clojure.org-action
  (action :name "Clojure.org..."
          :handler (fn [a] (future (clojure.java.browse/browse-url "http://clojure.org")))))
(add-app-action :go-clojuredocs-action
  (action :name "ClojureDocs..."
          :handler (fn [a] (future (clojure.java.browse/browse-url "http://clojuredocs.org")))))
(add-app-action :go-cheatsheet-action
  (action :name "Clojure CheatSheet..."
          :handler (fn [a] (future (clojure.java.browse/browse-url "http://homepage.mac.com/jafingerhut/files/cheatsheet-clj-1.3.0-v1.4-tooltips/cheatsheet-full.html")))))
(add-app-action :go-jira-action
  (action :name "JIRA..."
          :handler (fn [a] (future (clojure.java.browse/browse-url
            "http://dev.clojure.org/jira/browse/CLJ")))))
(add-app-action :go-about-action
  (action :name "About..."
          :handler (fn [a] (invoke-later (alert (str "Clojure Namespace Browser (" clj-ns-browser-version ")" \newline
            "Copyright (C) 2012 - Frank Siebenlist" \newline
            "Distributed under the Eclipse Public License"))))))

(add-app-action :zoom-in-action
  (action :name "Zoom in"
          :key  "menu U"
          :handler (fn [e] (let [id (partial select-id (to-root e))](invoke-later (font-size+ (id :doc-ta)))))))
(add-app-action :zoom-out-action
  (action :name "Zoom out"
          :key  "menu D"
          :handler (fn [e] (let [id (partial select-id (to-root e))](invoke-later (font-size- (id :doc-ta)))))))
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
                  (set-clip! fqn))
                (if-let [fqn (config (id :doc-tf) :text)]
                  (set-clip! fqn)))))))
(add-app-action :fqn-from-clipboard-action
  (action :name "Paste - FQN from clipboard"
          :key  "menu V"
          :handler (fn [e] (if-let [fqn (get-clip)] (invoke-soon (browser-with-fqn "" fqn (to-root e)))))))
(add-app-action :fqn-from-selection-action
  (action :name "FQN from selection"
          :key  "menu F"
          :handler (fn [e]
            (let [id (partial select-id (to-root e))]
              (if-let [s (selection (id :doc-ta))]
                (let [fqn (subs (config (id :doc-ta) :text) (first s) (second s))]
                  (invoke-soon (browser-with-fqn "" fqn (to-root e)))))))))



;; Init functions called during construction of a frame with its widget hierarchy

(defn init-before-bind
  [root]
  (let [id (partial select-id root)]
    (seesaw.meta/put-meta! root :atom-map (make-button-atom-map all-buttons-with-atoms))
    (config! (id :vars-lb-sp) :preferred-size (config (id :vars-lb-sp) :size))
    ;; ns
    (config! (id :ns-lb) :model @all-ns-loaded-atom)
    (config! (id :vars-lb) :model [])
    (config! (id :ns-entries-lbl) :text "0")
    (config! (id :ns-require-btn) :enabled? false)
    (config! (id :doc-cbx) :model doc-cbx-value-list)
    (config! (id :edit-btn) :enabled? false)
    (config! (id :browse-btn) :enabled? false)
    (config! (id :var-trace-btn) :enabled? false)
    (config! (id :clojuredocs-online-rb) :selected? true)
    (config! (id :inspect-btn) :enabled? false)
    (listen (id :inspect-btn)
      :action (fn [event] (swap! (id :inspect-btn-atom) not)))
    (listen (id :ns-require-btn)
      :action (fn [event] (swap! (id :ns-require-btn-atom) not)))
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
    ;; vars
    (config! (id :vars-entries-lbl) :text "0")
    ; doc
    (config! (id :doc-tf) :text "")
    (config! (id :doc-ta) :text "                                                                        ")
    (selection! (id :ns-cbx) "loaded")
    (selection! (id :vars-cbx) "publics")
    (selection! (id :vars-cbx) "Doc")))


(defn init-after-bind
  [root]
  (let [id (partial select-id root)]
    (invoke-soon
      (selection! (id :ns-cbx) "loaded")
      (selection! (id :vars-cbx) "publics")
      (selection! (id :doc-cbx) "Doc"))))


(defn bind-all
  "Collection of all the bind-statements that wire the clj-ns-browser events and widgets. (pretty amazing how easy it is to express those dependency-graphs!)"
  [root]
  (let [id (partial select-id root)]
    ;; # of entries in ns-lb => ns-entries-lbl
    (b/bind
      (b/property (id :ns-lb) :model)
      (b/transform widget-model-count (id :ns-lb))
      (id :ns-entries-lbl))
    ;; # of entries in vars-lb => vars-entries-lbl
    (b/bind
      (b/property (id :vars-lb) :model)
      (b/transform widget-model-count (id :vars-lb))
      (id :vars-entries-lbl))
    ;; new ns selected in ns-lb =>
    ;; dis/enable require-btn, update fqn in doc-tf
    (b/bind
      (b/selection (id :ns-lb))
        (b/tee
          (b/bind
            (b/transform (fn [ns]
              (if (and ns (some #(= ns %) @all-ns-unloaded-atom))
                true
                false)))
            (b/property (id :ns-require-btn) :enabled?))
        (b/bind
          (b/transform (fn [ns]
            (if (and ns (find-ns (symbol ns)))
              (fqname ns)
              "")))
          (b/tee
            (b/property (id :doc-tf) :text)))))
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
    ;;
    ;; select item in vars-lb => set associated fqn in doc-tf
    (b/bind
      (b/selection (id :vars-lb))
      (b/transform (fn [v]
        (if v
          (let [fqn (fqname (selection (id :ns-lb)) v)]
            (if (and fqn (not= fqn ""))
              fqn
              (when (= (selection (id :vars-cbx)) "aliases")
                (when-let [n (get (ns-aliases (symbol (selection (id :ns-lb)))) (symbol v))]
                  (str n))))))))
      (b/tee
          (b/property (id :doc-tf) :text)))
    ;;
    ;; (un-)loaded ns-cbx and regex filter tf =>
    ;; updated ns-list in ns-lb
    (b/bind
      (apply b/funnel
        [(id :ns-cbx)
         (id :ns-filter-tf)])
      (b/transform (fn [o]
        (let [v (selection (id :ns-cbx))]
          {:already-filtered false :string-seq ((get ns-cbx-value-fn-map v))})))
      (b/transform regx-tf-filter (id :ns-filter-tf))
      (b/notify-soon)
      (b/property (id :ns-lb) :model))
    ;;
    ;; (un-)loaded vars-cbx and regex filter tf =>
    ;; updated vars-list in vars-lb
    (b/bind
      (apply b/funnel
        [(b/selection (id :vars-cbx))
         (b/selection (id :ns-lb))
         (id :vars-filter-tf)])
      (b/transform (fn [o]
        (let [n-s (selection (id :ns-lb))
              n (and n-s (find-ns (symbol n-s)))
              v (selection (id :vars-cbx))
              f (get vars-cbx-value-fn-map v)]
          (if n
            (let [already-filtered (= v "search-all-docs")
                  string-seq (if already-filtered
                               (find-in-doc-strings (config (id :vars-filter-tf)
                                                            :text))
                               (map str (keys (f n))))]
              {:already-filtered already-filtered :string-seq string-seq})
            {:already-filtered false :string-seq []}))))
      (b/transform regx-tf-filter (id :vars-filter-tf))
      (b/transform (fn [symbol-str-seq]
        (if @group-vars-by-object-type
          (let [n-s (selection (id :ns-lb))
                n (and n-s (find-ns (symbol n-s)))
                v (selection (id :vars-cbx))
                fqns? (#{"all-publics" "search-all-docs"} v)]
            (group-by-object-type symbol-str-seq fqns? n-s))
          symbol-str-seq)))
      (b/notify-soon)
      (b/property (id :vars-lb) :model))
    ;;
    ;; typed regex in ns-filter-tf => visual feedback about validity
    (b/bind
      ; As the text of the textbox changes ...
      (id :ns-filter-tf)
      ; Convert it to a regex, or nil if it's invalid
      (b/transform #(try (re-pattern %) (catch Exception e nil)))
      ; Now split into two paths ...
      (b/bind
        (b/transform #(if % "white" "lightcoral"))
        (b/notify-soon)
        (b/property (id :ns-filter-tf) :background)))
    ;;
    ;; typed regex in vars-filter-tf => visual feedback about validity
    (b/bind
      ; As the text of the textbox changes ...
      (id :vars-filter-tf)
      ; Convert it to a regex, or nil if it's invalid
      (b/transform #(try (re-pattern %) (catch Exception e nil)))
      ; Now split into two paths ...
      (b/bind
        (b/transform #(if % "white" "lightcoral"))
        (b/notify-soon)
        (b/property (id :vars-filter-tf) :background)))
    ;;
    ;; updated fqn in doc-tf or doc-cbx =>
    ;; new render-doc-text in doc-ta
    (b/bind
      ; As the text of the fqn text field changes ...
      (apply b/funnel [(id :doc-tf) (id :doc-cbx)])
      (b/filter (fn [[doc-tf doc-cbx]]
                  (not (or (nil? doc-tf)  (= "" doc-tf)
                           (nil? doc-cbx) (= "" doc-cbx)))))
      (b/transform
        (fn [[doc-tf doc-cbx]]
          (future
            (let [s (render-doc-text doc-tf doc-cbx)]
              (invoke-soon (config! (id :doc-ta) :text s)))))))
    ;;
    ;; new text in doc-ta => scroll to top
    (b/bind
      (id :doc-ta)
      (b/notify-later)
      (b/transform (fn [t] (scroll! (id :doc-ta) :to :top))))
    ;;
    ;; new text in doc-ta => dis/enable browser button
    (b/bind
      (id :doc-ta)
      (b/transform (fn [o] (selection (id :doc-cbx))))
      (b/transform (fn [o]
        (case o
          ("All" "Doc") (invoke-soon (config! (id :browse-btn) :enabled? true))

          ("Examples" "See alsos" "Comments")
          (if-let [fqn (config (id :doc-tf) :text)]
            (future
              (let [url (clojuredocs-url fqn)
                    r (if url true false)]
                (invoke-soon
                 (config! (id :browse-btn) :enabled? r)))))

          nil))))  ; do nothing if no match
    ;;
    (b/bind
      (apply b/funnel [(id :doc-tf) (id :doc-cbx)])
      (b/transform (fn [o] (selection (id :doc-cbx))))
      (b/transform (fn [o] (if (get #{"Source" "All"} o) true false)))
      (b/transform (fn [o]
        (when o (if (meta-when-file (config (id :doc-tf) :text))
                  true
                  false))))
      (b/notify-soon)
      (b/property (id :edit-btn) :enabled?))
    ;
    ;; browser-btn pressed =>
    ;;
    (b/bind
      (apply b/funnel [(id :doc-tf) (id :doc-cbx)])
      (b/transform (fn [[doc-tf doc-cbx]] [(config (id :doc-tf) :text) (selection (id :doc-cbx))]))
      (b/transform (fn [[fqn doc-cbx-sel]]
                     (if (get #{"Value" "All"} doc-cbx-sel) fqn false)))
      (b/transform (fn [fqn]
        (when fqn
          (let [sym (better-symbol fqn)
                [status val] (eval-sym sym)]
            (when (and (= status :eval-ok)
                       (coll? val))
              true)))))
      (b/notify-soon)
      (b/property (id :inspect-btn) :enabled?))
    ;;
    ;; bring up browser with url
    (b/bind
      (id :browse-btn-atom)
      (b/notify-soon)
      (b/transform (fn [& oo]
        (let [o (selection (id :doc-cbx))]
          (when-let [fqn (config (id :doc-tf) :text)]
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
      (id :edit-btn-atom)
      (b/transform (fn [& o]
        (future
          (when-let [m (meta-when-file (config (id :doc-tf) :text))]
            (when-let [e (:out (clojure.java.shell/sh "bash" "-c" (str "echo -n $EDITOR")))]
              (:exit (clojure.java.shell/sh "bash" "-c" (str e " +" (:line m) " " (:file m))))))))))
    ;
    ; menu buttons
    ;;
    ; use local copy for clojuredocs lookup of comments/examples
    (b/bind
      (b/funnel
        (id :clojuredocs-offline-rb-atom)
        (id :clojuredocs-online-rb-atom))
      (b/transform (fn [& o]
        (if (config (id :clojuredocs-offline-rb) :selected?)
          (let [f (str (System/getProperty "user.home") "/.clojuredocs-snapshot.txt")]
            (if (= 0 (:exit (clojure.java.shell/sh "bash" "-c" (str "[ -f " f " ];" ))))
              (let [s (with-out-str (cd-client.core/set-local-mode! f))]
                (alert (str "Note: Locally cached ClojureDocs copy will be used" \newline s)))
              (do (alert "No locally cached ClojureDocs repo found - update first")
                  (config! (id :clojuredocs-online-rb) :selected? true))))
            (let [s (with-out-str (cd-client.core/set-web-mode!))]
                (alert (str "Note: Online ClojureDocs will be used" \newline s)))))))
    ;;
    ; update locally cached clojuredocs repo
    (b/bind
      (id :update-clojuredocs-btn-atom)
      (b/transform (fn [& o]
        (future
          (let [f (str (System/getProperty "user.home") "/.clojuredocs-snapshot.txt")]
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
          (let [sym (better-symbol fqn)
                [status val] (eval-sym sym)]
            (when (and (= status :eval-ok)
                       (coll? val))
              (future
                (clj-ns-browser.inspector/inspect-tree
                 val (str "Inspector for value of " fqn)))))))))
    ;;
    )) ; end of bind-all


(defn set-font-handler! [root f]
  (let [id (partial select-id root)]
    (config! (id :doc-ta) :font {:name f :size (.getSize (config (id :doc-ta) :font))})
    (config! (id :doc-tf) :font {:name f :style :bold :size (.getSize (config (id :doc-tf) :font))})
    (config! (id :ns-lb) :font {:name f :size (.getSize (config (id :ns-lb) :font))})
    (config! (id :vars-lb) :font {:name f :size (.getSize (config (id :vars-lb) :font))})
    ))

;;seesaw.core/toggle-full-screen! (locks up computer!!! don't use)
;;(seesaw.dev/show-options) and (seesaw.dev/show-events)
(defn init-menu-before-bind
  "Built menu for given browser-root-frame.
  Note that each frame has its own menu, which will be active when frame is in-focus."
  [root]
  (let [id (partial select-id root)]

    ;; built-up menu-bar
    (let [main-menu (menubar :id :main-menu)
          edit-menu (menu :text "Edit"  :id :edit-menu)
          file-menu (menu :text "File"  :id :file-menu)
          ns-menu (menu :text "Namespace" :id :ns-menu)
          vars-menu (menu :text "Var" :id :vars-menu)
          docs-menu (menu :text "Doc" :id :docs-menu)
          clojuredocs-menu (menu :text "ClojureDocs" :id :clojuredocs-menu)
          window-menu (menu :text "Window" :id :window-menu)
          help-menu (menu :text "Help"  :id :help-menu)
          
          font-menu (menu :text "Font"  :id :font-menu)
          ;;(config! (id :doc-ta) :font "Menlo")
          ;;(config! (id :doc-ta) :font "Monospaced")
          ;;(config! (id :doc-ta) :font "Inconsolata")
          font-btn-group (button-group)
          font-Monospaced-rb (radio-menu-item :text "Monospaced" :id :font-Monospaced-rb :group font-btn-group)
          font-Menlo-rb (radio-menu-item :text "Menlo" :id :font-Menlo-rb :group font-btn-group)
          font-Inconsolata-rb (radio-menu-item :text "Inconsolata" :id :font-Inconsolata-rb :group font-btn-group)
        
          update-clojuredocs (menu-item :text "ClojureDocs Update local repo" :id :update-clojuredocs-btn)
          clojuredocs-access-btn-group (button-group)
          clojuredocs-online-rb (radio-menu-item :text "ClojureDocs Online" :id :clojuredocs-online-rb :group clojuredocs-access-btn-group)
          clojuredocs-offline-rb (radio-menu-item :text "ClojureDocs Offline/Local" :id :clojuredocs-offline-rb :group clojuredocs-access-btn-group)

          ]
      (config! root :menubar main-menu)

      (config! main-menu
        :items [file-menu edit-menu ns-menu vars-menu docs-menu window-menu help-menu])

      (config! file-menu :items [(id :new-browser-action)])

      (config! edit-menu :items [(id :copy-fqn-action) (id :fqn-from-clipboard-action)
                                 (id :fqn-from-selection-action)])

      (config! ns-menu :items ["Load" "Trace"])

      (config! vars-menu :items ["Trace" "Unmap"])

      (config! font-Monospaced-rb :listen [:action (fn [e] (set-font-handler! root "Monospaced"))] :selected? true)
      (config! font-Menlo-rb :listen [:action (fn [e] (set-font-handler! root "Menlo"))])
      (config! font-Inconsolata-rb :listen [:action (fn [e] (set-font-handler! root "Inconsolata"))])
      (config! font-menu :items [font-Monospaced-rb  font-Menlo-rb font-Inconsolata-rb])

      (config! window-menu :items [(id :zoom-in-action) (id :zoom-out-action) font-menu :separator
        (id :bring-all-windows-to-front-action) (id :cycle-through-windows-action)])

      (config! help-menu :items [(id :go-github-action) (id :go-clojure.org-action) (id :go-clojuredocs-action) (id :go-cheatsheet-action) (id :go-jira-action) (id :go-about-action)])
      (config! docs-menu :items [update-clojuredocs :separator
                                 clojuredocs-online-rb clojuredocs-offline-rb
                                 :separator])
      )))

;; init and browser-window management



(defn refresh-clj-ns-browser
  "Refresh all or the given browser-window (pack! and show!)"
;;   ([] (map #(invoke-later (show! (pack! %))) @browser-root-frms))
;;   ([root] (invoke-later (show! (pack! root)))))
  ([] (doall (map #(invoke-soon (show! %)) @clj-ns-browser.browser/browser-root-frms)))
  ([root] (invoke-soon (show! root))))


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
    (init-after-bind root)
    (swap! browser-root-frms (fn [a] (conj @browser-root-frms root)))
    (config! root :title (str "Clojure Namespace Browser - " (.indexOf @browser-root-frms root)))
    (config! root :id (keyword (str "browser-frame-" (.indexOf @browser-root-frms root))))
    (swap! browser-root-frm-map (fn [a] (assoc @browser-root-frm-map (config root :id) root)))
    (config! root :transfer-handler (seesaw.dnd/default-transfer-handler
      :import [seesaw.dnd/string-flavor (fn [{:keys [data]}] (browser-with-fqn "" data root))]))
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
  ([a-ns a-name] (browser-with-fqn a-ns a-name (get-clj-ns-browser)))
  ([a-ns a-name browser-frame]
    (let [root browser-frame
          id (partial select-id root)]
      (if-let [fqn (and a-name (or (string? a-name)(symbol? a-name)) (fqname a-name))]
        (let [sym1 (symbol fqn)
              name1 (name sym1)
              ns1 (try (namespace sym1)(catch Exception e))]
          (if ns1
            ;; we have a fq-var as a-ns/a-name
            (invoke-soon
              (selection! (id :ns-cbx) "loaded")
              (selection! (id :ns-lb) ns1)
              (selection! (id :vars-cbx) "publics")
              (selection! (id :vars-lb) name1))
            (if (find-ns (symbol name1))
              ;; should be namespace
              (invoke-soon
                (selection! (id :ns-lb) name1)
                (selection! (id :doc-cbx) "Doc"))
              ;; else must be special-form or class
              (invoke-soon
                (if (special-form? fqn)
                  (do
                    (selection! (id :ns-cbx) "loaded")
                    (selection! (id :ns-lb) (str *ns*))
                    (selection! (id :vars-cbx) "special-forms")
                    (selection! (id :vars-lb) name1))
                  (do
                    (selection! (id :ns-lb) (str *ns*))
                    (selection! (id :doc-cbx) "Doc"))))))
            (refresh-clj-ns-browser root)
          fqn)))))

