# clj-ns-browser

Smalltalk-like namespace/class/var/function browser for clojure based on seesaw.


## Install

* clone the repo or download the tar from "https://github.com/franks42/clj-ns-browser"
* "lein deps"
* "lein compile"
* "lein repl" (or lein repls if you have cljsh&repls installed)
* evaluate in the repl: (sdoc)


## Usage

### REPL

From within REPL, use macro "sdoc" like you use "doc":

  user > (sdoc map)

will bring the browser window into view while showing the docs for clojure.core/map.

### Clj-ns-browser app

There are three panes, for namespaces, vars/classes/aliases, and for documentation/source-code/examples.

You can choose to see the "loaded", i.e. require'ed, or the unloaded namespaces thru the choicebox.

If you select a loaded namespace, the ns-documentation will show in the doc's text-area.

If you select an unloaded namespace, no info is available as it's ...unloaded. However, the "require" button is enabled, which allows you to require the selected namespace. (this doesn't seem to work for all unloaded namespaces... just try and see).

You can use the ns' text-field to filter the ns-list with a regex.

Once you've selected a loaded namespace, the publics, interns, aliases, imports or complete ns-map is show in the var's listbox, depending on the choicebox' selection.

Again, the var's textfield allows for filtering of (potentially) long lists of vars/classes with a regex.

Once you've selected a namespace and a var, the documentation panel will show you the fully qualified name and either the documentation, source-code, clojuredocs-examples or -comments.

The documentation information is slightly enhanced compared to the standard (doc...),
and will show you additional info about protocols, extenders, possible alternative fqn's with the same name but different namespaces, etc.

When you look at the documentation, the browser button allows you to see the same doc-info in you browser.

When you look at the examples or comments, the browser button will direct you to the associated clojuredocs-page in your browser.

When you look at the source code of those vars in your local project, then the edit-button will bring up your favorite $EDITOR with the file+line-number. (tested with EDITOR=bbedit and EDITOR="emacsclient -n"). (Does not work for source code inside of jar-files...)


## Issues

* Seems to work ok on MacOSX... tested the download/install/run in a fresh account.

* Works "more than less" a on Lubuntu - browsing works, but for some unknown reason the "unloaded" ns don't show up there (?) - have to dive into the completion code, "stolen" from swank-clojure to see if there are any platform dependencies...

* Window is a bit "jittery" - sometimes rescales window for different content of text-area - gota find out how to stop automatic vertical grow

* Sometimes the button event seem to fire off multiple times for a single click... not sure what to do about that...

* code examples seem to refer to clojure 1.2 instead of 1.3, which is obvious when you bring clojuredocs up in the browser - probably issue in either clojuredocs or cd-client (?)


## Todo

* allow installation as leiningen plugin or development-dependency - currently only tested as stand-alone, but should be used as a "companion browser tool" for your clojure projects (shouldn't be too difficult...)

* get swing html-text widget to work properly and replace the text-area (looks nicer, will have links working, code-highlighting, etc...) - rendering works in stand-alone browser window, but swing-widget doesn't look very nice...

* auto-scroll selected ns and var in view of their listbox - evident from (sdoc...), which does select the ns&var, but many times items are not shown in visible list.

* copy&paste, refresh as popup menus and/or keystroke shortcuts

* try different window layout with ns and var list-boxes above doc text-area

* remove cljsh/repls dependency - cleanup the utility and clj-info


## Acknowledgment

Seesaw and its main author Dave Ray - fantastic tool and near real-time support on the mailing list!


## License

Copyright (C) 2011 - Frank Siebenlist

Distributed under the Eclipse Public License, the same as Clojure
uses. See the file COPYING.
