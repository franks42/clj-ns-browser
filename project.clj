
; ATTENTION: with java 1.8 you dont need: [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
; see: https://stackoverflow.com/questions/52502189/java-11-package-javax-xml-bind-does-not-exist

(defproject org.clojars.bel/clj-ns-browser "1.3.4"
  :description "Smalltalk-like namespace/class/var/function browser for Clojure. (I added javax.xml.bind/jaxb-api to make it work with Java 11)"
  :url "https://github.com/franks42/clj-ns-browser"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.formdev/flatlaf "1.6.1"] ; HDPI
                 [seesaw "1.5.0"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"] ; java 11!
                 [org.clojure/tools.namespace "1.1.0"]
                 [org.clojure/tools.trace "0.7.11"]
                 [clojure-complete "0.2.5" :exclusions [org.clojure/clojure]]
                 [org.thnetos/cd-client "0.3.6"]
                 [hiccup "1.0.5"]
                 [clj-info "0.3.1"]]

  :dev-dependencies [[lein-marginalia "0.7.1"]
                     ;[franks42/debug-repl "0.3.1-FS"]
                     [codox "0.6.4"]]
  :jvm-opts ~(if (= (System/getProperty "os.name") "Mac OS X") ["-Xdock:name=Clj-NS-Browser"] [])
  :java-source-paths ["src-java"]
  ;; Use this for Leiningen version 2
  :resource-paths ["resource"])
  ;:main clj-ns-browser.core

