;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.toggle-listbox
  (:import [java.awt Dimension]
           [java.awt.image BufferedImage]
           [javax.swing ImageIcon])
  (:require [seesaw.dnd])
  (:use [clj-ns-browser.utils]
        [seesaw.core]))


;; I learned about the trick of first calling setSize on a Swing
;; component, in order to paint an image of it to a Graphics object
;; without displaying it on the screen, on the following web page:

;; https://forums.oracle.com/forums/thread.jspa?messageID=5697465&

(defn component-icon-image [comp pref-width pref-height]
  (let [pref-siz (Dimension. pref-width pref-height)
        bi (BufferedImage. pref-width pref-height BufferedImage/TYPE_3BYTE_BGR)
        gr (.getGraphics bi)]
    (.fillRect gr 0 0 pref-width pref-height)
    (.setSize comp pref-siz)
    (.paint comp gr)
    (ImageIcon. bi)))




(defn config-as-toggle-listbox! [id label-strs cur-label-str-order-atom]
  (let [label-str-set (set label-strs)
        buttons (map (fn [label]
                       {:label label
                        :button-sel (toggle :text label :selected? true)
                        :button-unsel (toggle :text label :selected? false)})
                     label-strs)
        max-width (apply max (map #(-> % :button-sel .getPreferredSize .width)
                                  buttons))
        max-height (apply max (map #(-> % :button-sel .getPreferredSize .height)
                                   buttons))
        ;; Reduce the sizes a bit.
        max-width (int (* 0.85 max-width))
        max-height (int (* 0.85 max-height))
        label-to-icon-sel (into {}
                           (map (fn [{:keys [label button-sel]}]
                                  [label
                                   (component-icon-image button-sel
                                                         max-width max-height)])
                                buttons))
        label-to-icon-unsel (into {}
                           (map (fn [{:keys [label button-unsel]}]
                                  [label
                                   (component-icon-image button-unsel
                                                         max-width max-height)])
                                buttons))
        render-lb-item (fn [renderer info]
                         (let [{:keys [value selected?]} info
                               m (if selected?
                                   label-to-icon-sel
                                   label-to-icon-unsel)]
                           (config! renderer :icon (m value) :text "")))]
    (reset! cur-label-str-order-atom label-strs)
    (config! id
             :model label-strs
             :renderer render-lb-item
             :drag-enabled? true
             :drop-mode :insert
             :transfer-handler
             (seesaw.dnd/default-transfer-handler
               :import [seesaw.dnd/string-flavor
                        (fn [{:keys [target data drop? drop-location] :as m}]
                          ;; Ignore anything dropped onto the list that is
                          ;; not in the original set of list elements.
                          (if (and drop?
                                   (:insert? drop-location)
                                   (:index drop-location)
                                   (label-str-set data))
                            (let [new-order (list-with-elem-at-index
                                              @cur-label-str-order-atom data
                                              (:index drop-location))]
                              (reset! cur-label-str-order-atom new-order)
                              (config! target :model new-order))))]
               :export {:actions (constantly :copy)
                        :start   (fn [c] [seesaw.dnd/string-flavor
                                          (selection c)])}))))
