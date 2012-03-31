;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.seesaw
  "Set of convenience functions to make seesaw even more friendly"
  (:require seesaw.core)
  (:require seesaw.bind)
  (:require clojure.pprint)
)


;; the default (seesaw-swing) tree-root for select-searches.
(declare ^:dynamic *root-frm*)

(defn set-root-frm!
  "Set the clj-ns-browser.seesaw/*root-frm* to passed root.
  Convenience function to set variable across namespaces easily.
  (don't use this fn, but use (binding ss/*root-frm* ...) instead!)"
  [root]
  (def ^:dynamic *root-frm* root))


(defn get-root-frm
  "Returns clj-ns-browser.seesaw/*root-frm*.
  Convenience function to get variable across namespaces easily."
  []
  *root-frm*)


(defn select
  "[selector]: uses *root-frame* for default root.
  [root selector]: if selector is keyword, it's assumed to be the :id,
  otherwise selector is seesaw's selector's type."
  ([selector] (select *root-frm* selector))
  ([root selector]
    (when-not root (throw (Exception.
      "seesaw/select: *root-frm* is nil - should assign explicitly or in (binding...)")))
    (if (keyword? selector)
      (seesaw.core/select root
                          [(keyword (str "#" (clojure.core/name selector)))])
      (seesaw.core/select root selector))))


(defn config
  "See seesaw.core/config for basic doc.
  Enhancements:
  target: keyword, swing-object, or list thereof
  if keyword, it must be the :id of the object and
  the root must be defaulted to *root-frame*.
  name: swing-object's property or list thereof.
  Returns either the property's value, or the list
  of property values for all targets and properties."
  [target name]
  (if (sequential? target)
    (map #(config % name) target)
    (if (sequential? name)
      (map #(config target %) name)
      (if (keyword? target)
        (seesaw.core/config (select target) name)
        (seesaw.core/config target name)))))


(defn config!
  "See seesaw.core/config! for basic doc.
  Enhancements:
  targets: keyword, swing-object, or list thereof
  if keyword, it must be the :id of the object and
  the root must be defaulted to *root-frame*."
  [targets & args]
  (if (sequential? targets)
    (apply seesaw.core/config!
      (map #(if (keyword %) (select %) %) targets)
           args)
    (apply seesaw.core/config!
      (if (keyword targets) (select targets) targets)
      args)))


(defn selection
  "See seesaw.core/selection for basic doc.
  Enhancements:
  target: keyword or swing-object.
  if keyword, it must be the :id of the object and
  the root must be defaulted to *root-frame*."
  ([target] (selection target {}))
  ([target options]
    (if (keyword? target)
      (seesaw.core/selection (select target) options)
      (seesaw.core/selection target options))))


(defn selection!
  "See seesaw.core/selection! for basic doc.
  Enhancements:
  target: keyword or swing-object.
  if keyword, it must be the :id of the object and
  the root must be defaulted to *root-frame*."
  ([target new-selection] (selection! target {} new-selection))
  ([target opts new-selection]
    (if (keyword? target)
      (seesaw.core/selection! (select target) opts new-selection)
      (seesaw.core/selection! target opts new-selection))))


(defn funnel
  "Same as seesaw.bind/funnel, except that it also allows
  lists of bindables inside of the bindables argument.
  Useful to add a list of check-boxes in the bind-sequence, where
  any of the list's checkboxes should kick-off the bind-processing.
  (assumes that the next bindable in the process \"knows\" about this
  aggregated event generation...)."
  [& bindables]
  (apply seesaw.bind/funnel (flatten bindables)))


;; Copied from seesaw example to generate :id's from externally build GUIs.
(defn identify
  "Given a root widget, find all the named widgets and set their Seesaw :id
   so they can play nicely with select and everything."
  [root]
  (doseq [w (select root [:*])]
    (when-let [n (.getName w)]
      (seesaw.selector/id-of! w (keyword n))))
  root)


(defn all-id-values
  "Return list of all :id values given a (root-)widget.
  Facilitates introspection of WindowBuilder widgets. "
  ([] (all-id-values (get-root-frm)))
  ([root]
    (sort
      (filter #(not(nil? %))
              (map
                (fn [o] (try (seesaw.core/config o :id)
                        (catch Exception e)))
                (seesaw.core/select root [:*]))))))


(defn bind-debug
  ([o] (clojure.pprint/pprint o) o)
  ([o t] (print t ":") (clojure.pprint/pprint o) o))
