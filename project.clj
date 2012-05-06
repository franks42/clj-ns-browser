(defproject clj-ns-browser "1.3.0-SNAPSHOT"
  :description "Smalltalk-like namespace/class/var/function browser for Clojure."
  :url "https://github.com/franks42/clj-ns-browser"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [seesaw "1.4.1"]
                 [org.clojure/tools.namespace "0.1.2"]
                 [clj-info "0.2.3"]
                 [hiccup "0.3.8"]
                 [org.clojure/tools.trace "0.7.3"]
                 [clojure-complete "0.2.1" :exclusions [org.clojure/clojure]]
                 [org.fife.ui/rsyntaxtextarea "2.0.3"]
                 [org.thnetos/cd-client "0.3.4"]]
 	:dev-dependencies [[lein-marginalia "0.6.0"]
 	                   ;[franks42/debug-repl "0.3.1-FS"]
                     [codox "0.5.0"]]
  :jvm-opts ~(if (= (System/getProperty "os.name") "Mac OS X") ["-Xdock:name=Clj-NS-Browser"] [])
  :java-source-paths ["src"]
  :java-source-path "src"
  :main clj-ns-browser.core)
