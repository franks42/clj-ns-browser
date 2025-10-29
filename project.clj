(defproject clj-ns-browser "2.0.0"
  :description "Smalltalk-like namespace/class/var/function browser for Clojure."
  :url "https://github.com/franks42/clj-ns-browser"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org/"
                                    :username :env/CLOJARS_USERNAME
                                    :password :env/CLOJARS_PASSWORD
                                    :sign-releases false}]]
  :dependencies [
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 ;; [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojure "1.12.0"]
                 ;; [clj-http "3.13.0"]
                 ;; [clj-http-lite "0.2.1"]
                 ;; [org.clj-commons/clj-http-lite "1.0.13"]
                 ;; [org.babashka/http-client "0.4.22"] ;; Now provided by clj-info 0.6.0
                 ;; [seesaw "1.4.5"]
                 [seesaw "1.5.0"]
                 ;;[org.clojure/tools.namespace "0.1.3"]
                 [org.clojure/tools.namespace "1.5.0"]
                 ;; [org.clojure/tools.trace "0.7.8"]
                 [org.clojure/tools.trace "0.8.0"]
                 ;; [clojure-complete "0.2.4" :exclusions [org.clojure/clojure]]
                 [clojure-complete "0.2.5" :exclusions [org.clojure/clojure]]
                 ;; [org.thnetos/cd-client "0.3.6"]
                 [hiccup "2.0.0"]
                 [clj-info "0.6.0"]
                 ]
 	:dev-dependencies [
                     ;; [lein-marginalia "0.7.1"]
                     [lein-marginalia "0.9.2"]
 	                   ;[franks42/debug-repl "0.3.1-FS"]
                     ;; [codox "0.6.4"]
                     [codox "0.10.8"]
                     [clj-ns-browser "1.3.2-SNAPSHOT"]]
  :jvm-opts ~(if (= (System/getProperty "os.name") "Mac OS X") ["-Xdock:name=Clj-NS-Browser"] [])
  :java-source-paths ["src-java"]
  ;; Use this for Leiningen version 2
  :resource-paths ["resource"]
  :main clj-ns-browser.core
  )
