# clj-ns-browser

Smalltalk-like namespace/class/var/function browser for clojure based on seesaw.

## Usage

Added browser-button that will bring-up your favorite browser with the associated clojuredoc page.

Added edit-button that will bring up your $EDITOR with the selected FQN's source file+line
(only works with the project's local files, not with the code in the jar-files).

Added Examples/Comments to Documentation choice-list, which will pull-down info from clojuredocs with cd-client for the selected fqn.


Not yet fully baked... pls wait another week...

If you *have* to take a look now:

  1) clone the repo or download the tar

  2) "lein deps"

  3) "lein compile"

  4) "lein repl"

  5) (sdoc)

Instead of cloning, you can download the tar file and work with that.

Seems to work ok on MacOSX... tested the download, install, run on a fresh account.

Works a little bit on Lubuntu - browsing works, but for some unknown reason the unloaded ns don't show up there (?) - have to dive into the completion code, "stolen" from swank-clojure to see if there are any platform dependencies...


## License

Copyright (C) 2011 - Frank Siebenlist

Distributed under the Eclipse Public License, the same as Clojure
uses. See the file COPYING.
