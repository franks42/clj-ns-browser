;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.core
  (:require
   [clj-ns-browser.sdoc]
  ;;  [seesaw.core]
  ;;  [seesaw.dev]
  ;;  [clj-info]
  ;;  [clojure.pprint :as pp]
   [clj-ns-browser.browser])
  (:gen-class))


(defn -main [& args]
  (clj-ns-browser.browser/get-clj-ns-browser))

(comment
  ;;

  (def cljdocs-export-edn-url "https://github.com/clojure-emacs/clojuredocs-export-edn/raw/refs/heads/master/exports/export.compact.min.edn")
  (def r (clj-http.lite.client/get cljdocs-export-edn-url))
  (def cljdocs-export
    (if (= (:status r) 200)
      (clojure.edn/read-string (:body r))
      nil))

  (def a (slurp "cljdocs-export.edn"))
  (def b (clojure.edn/read-string a))
  (def c (:vars b))


  ;;
  )