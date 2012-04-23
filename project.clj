(defproject clj-ns-browser "1.2.0-SNAPSHOT"
  :description "Smalltalk-like namespace/class/var/function browser for Clojure."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [seesaw "1.4.1-SNAPSHOT"]
                 [org.clojure/tools.namespace "0.1.2"]
                 [clj-info "0.2.3-SNAPSHOT"]
                 [hiccup "0.3.8"]
                 [org.clojure/tools.trace "0.7.3"]
                 [clojure-complete "0.2.1" :exclusions [org.clojure/clojure]]
                 [org.thnetos/cd-client "0.3.4"]]
 	:dev-dependencies [[lein-marginalia "0.6.0"]
 	                   [codox "0.5.0"]]
  :jvm-opts ~(if (= (System/getProperty "os.name") "Mac OS X") ["-Xdock:name=Clj-NS-Browser"] [])
  :java-source-path "src"
  :main clj-ns-browser.core)
