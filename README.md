# Clojure Namespace Browser (clj-ns-browser)

Clj-ns-browser is a graphical namespace/class/var browser for Clojure's doc strings, source code, ClojureDocs.org examples & comments, and values.  The browser's GUI is inspired by Smalltalk class library browsers, and is based on the Seesaw library.

Look for the last released version on clojars.org: [clj-ns-browser "2.0.0-SNAPSHOT"].

![Clojure Namespace Browser](https://github.com/franks42/clj-ns-browser/raw/master/clj-ns-browser.png "Clojure Namespace Browser")

## Introduction

The 2.0.0-SNAPSHOT release upgrades the dependencies to clojure 1.12, and a latest on all others. Furthermore, it uses the ClojureDocs edn-file from "https://github.com/clojure-emacs/clojuredocs-export-edn/raw/refs/heads/master/exports/export.compact.min.edn", which seems to be regularly updated.


A few of the highlights of the  browser's feature set are:

* Andy concocted a cool, new, button-row widget that allows for flexible display of var/class/namespace information.

* syntax highlighting of source code thru use of rsyntaxtextarea

* improved invocation of external browser

* many invisible improvements

While the "old" features still work of course:

* see the loaded and unloaded namespaces, and "require" with one-click

* see the doc-string of a selected var/namespace plus optionally the clojuredocs' examples, comments and see alsos, as well as the source code.

* seeing the list of vars/classes either as local names or as fully qualified ones.

* select one or more namespaces and use a regex filter to display the var-list... even with optional searching/matching of the vars' doc-strings.

* Predefined var/class-filters to see only macros/functions/protocols/multimethods/dynamic or deftypes/defrecords, or refers with and w/o clojure.core. - in addition you can still use a regex-filter on those results

* adding a trace to a function with a simple button click and seeing the invocation information in your repl as the function is called - tools.trace is an underused gem of a tool!

* (optional) color-coding based on the var-type: macros are red... functions are green.

* optional download and caching of the most recent clojuredocs-repo copy with on/off-line clojuredocs-info retrieval through simple menu commands

* see basic info about (java-)classes like their class-inheritance list and the set of implemented interfaces

* see the type and values of vars and the type and value of the @var... with their associated meta-data.

* see live-updates in realtime of new namespaces, new vars, and changed values... as they are defined by you at the repl or by your running program.

* on top of all that, we also have demo videos where Andy walks you thru the browser's features one-by-one ([Part 1](http://www.youtube.com/watch?v=wz3lD5zD8ag) and [Part 2](http://www.youtube.com/watch?v=aYvegaFVKHw))


## Flexible Display of Var's Doc/Source/Value

Above the Documentation panel's text area, we have a (very cool) new row of buttons: Doc, Source, Examples, Comments, See alsos, and Value.

By clicking any of those buttons, the associated information is displayed about the selected symbol/var/class/namespace. 

By Command/CTRL clicking, multiple button can be selected, and the info for all those buttons will be displayed. 

Lastly, the buttons can be dragged to change the order in which the info is displayed in the text area.


![Var's flexible doc/source/value view thru Andy's concocted button-row widget!] (https://github.com/franks42/clj-ns-browser/raw/master/clj-ns-browser-source.png "Var's flexible doc/source/value view")


## Install & Start-up

For any project where you want to add the ability to browse your currently loaded/unloaded namespaces for the available classes/vars/functions/macros/etc. with their docs/source/values, you should add clj-ns-browser to your :dev-dependencies list by adding to your project.clj:

```
    ;; Leiningen version 1
    :dev-dependencies [[clj-ns-browser "1.3.1"]]

    ;; Leiningen version 2
    :profiles {:dev {:dependencies [[clj-ns-browser "1.3.1"]]}}
```

Then fire-up your repl, refer to the sdoc macro in clj-ns-browser.sdoc, and start the browser with (sdoc):

    $ lein deps  
    $ lein repl  
    user=> (use 'clj-ns-browser.sdoc)  
    user=> (sdoc)  

... and the browser window should popup on your screen - very easy!

It should actually work from any repl - tested on MacOSX, Linux, even Windows... with Leiningen 1.7.1 & 2.0.0, repls, repl-y, emacs and Sublime Text 2.


## Documentation, Usage, Issues, Futures...

Please take a look at the clj-ns-browser's wiki for detailed information about features, usage, and feedback: "https://github.com/franks42/clj-ns-browser/wiki"  (a work in progress...)


## Authors

* franks42 (Frank Siebenlist)

* jafingerhut (Andy Fingerhut)


## Acknowledgment

Seesaw and its main author Dave Ray - fantastic tool and near real-time support on the mailing list!  
(Seesaw turns the Horror of Swing into a friendly, well-documented, Clojure library: "https://github.com/daveray/seesaw")

All the open source libraries that the clj-ns-browser depends on, like: seesaw, clj-info, hiccup,  clojure-complete, tools.namespace, tools.trace, cd-client, clojuredocs, swank-clojure, leiningen, lib.devlinsf.clip-utils....and all the libs they depend on, ... and clojure of course.


## License

Copyright (C) 2013 - Frank Siebenlist and Andy Fingerhut

Distributed under the Eclipse Public License, the same as Clojure
uses. See the file COPYING.
