# Clojure Namespace Browser (clj-ns-browser)

Clj-ns-browser is a namespace/class/var browser for Clojure's doc strings, source code, ClojureDocs.org examples & comments, and values.  The browser's GUI is inspired by Smalltalk class library browsers, and is based on the Seesaw library.

Look for the last released version on clojars.org: [clj-ns-browser "1.2.0"].

![Clojure Namespace Browser](https://github.com/franks42/clj-ns-browser/raw/master/clj-ns-browser.png "Clojure Namespace Browser")

## Introduction

The "it starts to look like a real app" release, "1.2.0", adds a long list of new features to clj-ns-browser compared to the legacy "1.1.0" release that is already a few weeks old. 

```
    :dev-dependencies [[clj-ns-browser "1.2.0"]]
```

One important distinction between the new and the old version, is the use of menus, popup-menus, keyboard short-cuts, copy&paste, drag&drop... all thanks to seesaw of course.

A few of the highlights of the browser's feature set are:

* seeing the loaded and unloaded namespaces with a one-click (require ...) option

* see the doc-string of a selected var/namespace plus optionally the clojuredocs' examples, comments and see alsos.

* seeing the list of vars/classes either as local names or as fully qualified ones.

* the ability to select more than one namespace and to use a regex filter to display the var-list... even with optional searching/matching of the associated doc-strings.

* Predefined var/class-filters to see only macros/functions/protocols/multimethods/dynamic or deftypes/defrecords, or refers with and w/o clojure.core. - of course you can still use a regex-filter in addition on those results

* adding a trace to a function with a simple button click and seeing the invocation information in your repl as the function is called - tools.trace is an underused gem of a tool!

* (optional) color-coding based on the var-type: macros are red... functions are green.

* optional download of the most recent clojuredocs-repo copy with on/off-line clojuredocs-info retrieval through simple menu commands

* see basic info about (java-)classes like their class-inheritance list and the set of implemented interfaces

* see the type and values of vars and the type and value of the @var... with their associated meta-maps.

* see live-updates in realtime of new namespaces, new vars, and changed values... as they are defined by you at the repl or by your running program.

* instructional, professional looking videos where Andy walks you thru the browser's features one-by-one (see "" if you can't wait)

* too much to list here...

That partial feature list should be more than enough to make you want to read the next section...


## Install & Start-up

For any project where you want to add the ability to browse your currently loaded/unloaded namespaces for the available classes/vars/functions/macros/etc. with their docs/source/values, you should add clj-ns-browser to your :dev-dependencies list by adding to your project.clj:

```
    :dev-dependencies [[clj-ns-browser "1.2.0"]]
```

Then fire-up your repl, refer to the sdoc macro in clj-ns-browser.sdoc, and start the browser with (sdoc):

    $ lein deps  
    $ lein repl  
    user=> (use 'clj-ns-browser.sdoc)  
    user=> (sdoc)  

... and the browser window should popup on your screen - very easy!

It should actually work from any repl - tested on MacOSX, Linux, even Windows... with leiningen 1&2, repls, repl-y, emacs and Sublime Text 2.


## Documentation, Usage, Issues, Futures...

Please take a look at the clj-ns-browser's wiki for detailed information about features, usage, and feedback: "https://github.com/franks42/clj-ns-browser/wiki"


## Authors

* franks42 (Frank Siebenlist)

* jafingerhut (Andy Fingerhut)


## Acknowledgment

Seesaw and its main author Dave Ray - fantastic tool and near real-time support on the mailing list!  
(Seesaw turns the Horror of Swing into a friendly, well-documented, Clojure library: "https://github.com/daveray/seesaw")

All the open source libraries that the clj-ns-browser depends on, like: seesaw, clj-info, hiccup,  clojure-complete, tools.namespace, tools.trace, cd-client, clojuredocs, swank-clojure, leiningen, lib.devlinsf.clip-utils....and all the libs they depend on, ... and clojure of course.


## License

Copyright (C) 2012 - Frank Siebenlist and Andy Fingerhut

Distributed under the Eclipse Public License, the same as Clojure
uses. See the file COPYING.
