(ns clj-ns-browser.test.core
  (:require [clj-ns-browser.browser :as b]
            [clj-ns-browser.utils :as u])
  (:use [clj-ns-browser.core])
  (:use [clojure.test]))

(deftest slash-symbol-fixup-checks
  (is (= "Function"
         (:object-type-str (b/better-get-docs-map "clojure.core//"))))
  (is (= #'clojure.core// (u/resolve-fqname "clojure.core//")))
  (is (= #'clojure.core// (u/resolve-fqname "clojure.core" "/")))
  (is (= "clojure.core//" (u/fqname 'clojure.core//)))
  (is (= "clojure.core//" (u/fqname '/)))
  (is (= "clojure.core//"
         (re-find #"^clojure\.core//"
                  (u/render-one-doc-text "clojure.core//" "Doc"))))
  )
