(defproject clj-ns-browser "1.1.0"
  :description "Smalltalk-like namespace/class/var/function browser for Clojure."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [seesaw "1.4.0"]
                 [clj-info "0.2.2"]
                 [hiccup "0.3.8"]
                 [lein-repls "1.9.7"]
                 [clojure-complete "0.2.1" :exclusions [org.clojure/clojure]]
                 [org.thnetos/cd-client "0.3.4"]]
 	:dev-dependencies [[lein-marginalia "0.6.0"]
 	                   [codox "0.5.0"]]
  :java-source-path "src"
  :main clj-ns-browser.core)
