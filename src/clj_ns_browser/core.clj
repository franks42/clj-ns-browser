;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.core
  (:require [clj-ns-browser.seesaw :as ss]
            [seesaw.selector]
            [seesaw.bind :as b]
            [clojure.java.javadoc]
            [clojure.repl])
  ;(:use clj-ns-browser.seesaw)
  ;(:use [seesaw.core :exclude [config config! select]])
  (:use [clj-ns-browser.browser]
        [clj-ns-browser.utils]
        [clj-ns-browser.sdoc]
        [seesaw.core]
        [seesaw.border]
        [seesaw.mig]
        [seesaw.dev]
        [clj-info]))


(defn -main [& args])

(init-browser-root (get-browser-root-frm))
(ss/set-root-frm! (get-browser-root-frm))
