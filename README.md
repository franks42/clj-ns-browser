# clj-ns-browser

Smalltalk-like namespace/class/var/function browser for Clojure-code's doc-strings, source-code, clojuredocs-examples & comments, pprint'ing of values based on seesaw.

Look for the last released version on clojars.org: [clj-ns-browser "1.2.0"].


## Install

For any project where you want to add the ability to browse your currently loaded/unloaded namespaces for the available functions/macros/vars with their docs/source, you should add clj-ns-browser to your :dev-dependencies list:

* in your project.clj, add:

```
    :dev-dependencies [[clj-ns-browser "1.2.0"]]
```

Then:

    $ lein deps  
    $ lein repl  
    user=> (use 'clj-ns-browser.sdoc)  
    user=> (sdoc)  

Very easy!

It should actually work from any repl - tested with repls, emacs and Sublime Text 2.



## Usage

### REPL

From the REPL, use macro "sdoc" like you use "doc" but with some more flexibility:

    user=> (sdoc map) 
    user=> (sdoc 'clojure.core/map) 
    user=> (sdoc "replace") 
    user=> (sdoc clojure.string/replace) 

either will bring the browser window into view while showing the docs for the requested var.

You can have multiple browser windows with:

    user > (new-clj-ns-browser)

but only the first browser opened, will "listen" for sdoc requests - the others can only be used for interactive browsing with the mouse.

### clj-ns-browser app

![Clojure Namespace Browser](https://github.com/franks42/clj-ns-browser/raw/master/clj-ns-browser.png "Clojure Namespace Browser")

There are three panes, for namespaces, vars/classes/aliases, and for documentation/source-code/examples.

You can choose to see the "loaded", i.e. require'ed, or the unloaded namespaces thru the choicebox.

If you select a loaded namespace, the ns-documentation will show in the doc's text-area.

If you select an unloaded namespace, no info is available as it's ...unloaded. However, the "require" button is enabled, which allows you to require the selected namespace. (this doesn't seem to work for all unloaded namespaces... just try and see).

You can use the ns' text-field to filter the ns-list with a regex.

Once you've selected a loaded namespace, the publics, interns, aliases, imports or complete ns-map is show in the var's listbox, depending on the choicebox' selection.

Again, the var's textfield allows for filtering of (potentially) long lists of vars/classes with a regex.

Once you've selected a namespace and a var, the documentation panel will show you the fully qualified name and either the documentation, source-code, clojuredocs-examples or -comments, based on the selection of the doc's choice-box.

The documentation information is slightly enhanced compared to the standard (doc...),
and will show you additional info about protocols, extenders, possible alternative fqn's with the same name but different namespaces, etc.

When you look at the documentation, the browser button allows you to see the same doc-info in you browser but nicer rendered in color.

When you look at the examples or comments, the browser button will direct you to the associated clojuredocs-page in your browser.

![Clojure Namespace Browser](https://github.com/franks42/clj-ns-browser/raw/master/clj-ns-browser-source.png "Clojure Namespace Browser")

When you look at the source code of those vars in your local project, then the edit-button will bring up your favorite $EDITOR with the file+line-number. (tested with EDITOR=bbedit and EDITOR="emacsclient -n"). (Does not work for source code inside of jar-files...)


## Issues

* Code examples seem to refer to Clojure 1.2 instead of 1.3, which is obvious when you bring clojuredocs up in the browser.  Workaround: Use Docs -> ClojureDocs Update local repo to get a snapshot of ClojureDocs.org contents, then Docs -> ClojureDocs Offline/local to use that local snapshot instead of interactively querying the ClojureDocs.org contents.  This also speeds up browsing with examples displayed, and works even when you don't have Internet access.  The issue is due to the current behavior of the cd-client library when querying the clojuredocs.org site live.


## Acknowledgment

Seesaw and its main author Dave Ray - fantastic tool and near real-time support on the mailing list!

(Seesaw turns the Horror of Swing into a friendly, well-documented, Clojure library: "https://github.com/daveray/seesaw")

Andy Fingerhut has commit rights and uses them well - thanks for all the contributions!

All the open source libraries that the clj-ns-browser depends on, like: seesaw, clj-info, hiccup,  clojure-complete, tools.namespace, cd-client, clojuredocs, swank-clojure, leiningen, lib.devlinsf.clip-utils....and all the libs they depend on, ... and clojure of course.


## License

Copyright (C) 2012 - Frank Siebenlist

Distributed under the Eclipse Public License, the same as Clojure
uses. See the file COPYING.
