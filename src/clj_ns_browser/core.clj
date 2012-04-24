;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.core
  (:use [clj-ns-browser.sdoc]
        [seesaw.core]
        [seesaw.dev]
        [clj-info]
        [clj-ns-browser.browser]))


(defn -main [& args]
  (get-clj-ns-browser))
