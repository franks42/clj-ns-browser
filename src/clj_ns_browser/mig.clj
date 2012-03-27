;; Copyright (c) Frank Siebenlist. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns clj-ns-browser.mig
  (:use [clj-ns-browser.utils])
  (:require [clj-ns-browser.seesaw :as ss])
  (:use [seesaw core mig]))

;; {
;; 		setName("root-panel");
;; 		setLayout(new MigLayout("", "[][][][grow][][][grow][]", "[][][][][grow][]"));
;;
;; 		JLabel lblNamespace = new JLabel("Namespaces");
;; 		lblNamespace.setName("ns-header-lbl");
;; 		lblNamespace.setFont(new Font("Lucida Grande", Font.BOLD, 15));
;; 		add(lblNamespace, "cell 0 0 3 1,alignx center");
;;
;; 		JLabel lblVarsclasses = new JLabel("Vars/Classes");
;; 		lblVarsclasses.setName("vars-header-lbl");
;; 		lblVarsclasses.setFont(new Font("Lucida Grande", Font.BOLD, 15));
;; 		add(lblVarsclasses, "cell 3 0 3 1,alignx center");
;;
;; 		JLabel lblDocumentation = new JLabel("Documentation");
;; 		lblDocumentation.setName("doc-header-lbl");
;; 		lblDocumentation.setFont(new Font("Lucida Grande", Font.BOLD, 15));
;; 		add(lblDocumentation, "cell 6 0 2 1,alignx center");
;;
;; 		JCheckBox chckbxLoaded = new JCheckBox("Loaded");
;; 		chckbxLoaded.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		chckbxLoaded.setName("ns-loaded-cb");
;; 		add(chckbxLoaded, "cell 1 2");
;;
;; 		JCheckBox chckbxLoaded_1 = new JCheckBox("Unloaded");
;; 		chckbxLoaded_1.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		chckbxLoaded_1.setName("ns-unloaded-cb");
;; 		add(chckbxLoaded_1, "cell 2 2");
;;
;; 		JCheckBox chckbxNewCheckBox = new JCheckBox("publics");
;; 		chckbxNewCheckBox.setName("ns-publics-cb");
;; 		chckbxNewCheckBox.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(chckbxNewCheckBox, "cell 3 1");
;;
;; 		JCheckBox chckbxNewCheckBox_3 = new JCheckBox("aliases");
;; 		chckbxNewCheckBox_3.setName("ns-aliases-cb");
;; 		chckbxNewCheckBox_3.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(chckbxNewCheckBox_3, "cell 4 1");
;;
;; 		JCheckBox chckbxNewCheckBox_1 = new JCheckBox("interns");
;; 		chckbxNewCheckBox_1.setName("ns-interns-cb");
;; 		chckbxNewCheckBox_1.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(chckbxNewCheckBox_1, "cell 3 2");
;;
;; 		JCheckBox chckbxNewCheckBox_4 = new JCheckBox("imports");
;; 		chckbxNewCheckBox_4.setName("ns-imports-cb");
;; 		chckbxNewCheckBox_4.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(chckbxNewCheckBox_4, "cell 4 2");
;;
;; 		JCheckBox chckbxNewCheckBox_5 = new JCheckBox("map");
;; 		chckbxNewCheckBox_5.setName("ns-map-cb");
;; 		chckbxNewCheckBox_5.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(chckbxNewCheckBox_5, "cell 5 2");
;;
;; 		JCheckBox chckbxNewCheckBox_2 = new JCheckBox("refers");
;; 		chckbxNewCheckBox_2.setName("ns-refers-cb");
;; 		chckbxNewCheckBox_2.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(chckbxNewCheckBox_2, "cell 5 1");
;;
;; 		JRadioButton rdbtnDoc = new JRadioButton("Doc");
;; 		rdbtnDoc.setName("doc-rb");
;; 		rdbtnDoc.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(rdbtnDoc, "flowx,cell 7 2,alignx right");
;;
;; 		JRadioButton rdbtnSource = new JRadioButton("Source");
;; 		rdbtnSource.setName("source-rb");
;; 		rdbtnSource.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(rdbtnSource, "cell 7 2");
;;
;; 		JRadioButton rdbtnValue = new JRadioButton("Value");
;; 		rdbtnValue.setName("value-rb");
;; 		rdbtnValue.setFont(new Font("Lucida Grande", Font.PLAIN, 12));
;; 		add(rdbtnValue, "cell 7 2");
;;
;; 		txtNsFilter = new JTextField();
;; 		txtNsFilter.setName("ns-filter-tf");
;; 		txtNsFilter.setFont(new Font("Inconsolata", Font.PLAIN, 13));
;; 		txtNsFilter.setText("NS Filter");
;; 		add(txtNsFilter, "cell 0 3 3 1,growx");
;; 		txtNsFilter.setColumns(10);
;;
;; 		txtVarsFilter = new JTextField();
;; 		txtVarsFilter.setName("vars-filter-tf");
;; 		txtVarsFilter.setFont(new Font("Inconsolata", Font.PLAIN, 13));
;; 		txtVarsFilter.setText("Vars Filter\n");
;; 		add(txtVarsFilter, "cell 3 3 3 1,growx");
;; 		txtVarsFilter.setColumns(10);
;;
;; 		JLabel lblClojurecorereduce = new JLabel("clojure.core/reduce");
;; 		lblClojurecorereduce.setName("doc-header-lbl");
;; 		lblClojurecorereduce.setFont(new Font("Inconsolata", Font.BOLD, 15));
;; 		add(lblClojurecorereduce, "cell 6 3 2 1,alignx left");
;;
;; 		JList list = new JList();
;; 		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
;; 		list.setName("ns-lb");
;; 		list.setFont(new Font("Inconsolata", Font.PLAIN, 13));
;; 		list.setModel(new AbstractListModel() {
;; 			String[] values = new String[] {"clojure.core", "clj.growlnitify.core", "123456789012345678901234567890"};
;; 			public int getSize() {
;; 				return values.length;
;; 			}
;; 			public Object getElementAt(int index) {
;; 				return values[index];
;; 			}
;; 		});
;; 		add(list, "cell 0 4 3 1,grow");
;;
;; 		JList list_1 = new JList();
;; 		list_1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
;; 		list_1.setName("vars-lb");
;; 		add(list_1, "cell 3 4 3 1,grow");
;;
;; 		JScrollBar scrollBar = new JScrollBar();
;; 		add(scrollBar, "flowx,cell 7 4");
;;
;; 		JTextArea txtrDocArea = new JTextArea();
;; 		txtrDocArea.setName("doc-ta");
;; 		txtrDocArea.setFont(new Font("Inconsolata", Font.PLAIN, 14));
;; 		txtrDocArea.setText("Doc Area\n12345678901234567890123456789012345678901234567890123456789012345\n---------------------maximum width ruler------------------------------");
;; 		add(txtrDocArea, "cell 6 4 2 1,grow");
;;
;; 		JLabel lblnsentries = new JLabel("#ns-entries");
;; 		lblnsentries.setName("ns-entries-lbl");
;; 		lblnsentries.setFont(new Font("Inconsolata", Font.BOLD, 12));
;; 		add(lblnsentries, "cell 0 5,alignx left");
;;
;; 		JSeparator separator = new JSeparator();
;; 		add(separator, "cell 0 1 1 2");
;;
;; 		JButton btnRequire = new JButton("require");
;; 		btnRequire.setName("ns-require-btn");
;; 		add(btnRequire, "cell 2 5");
;;
;; 		JLabel lblvarsentries = new JLabel("#vars-entries");
;; 		lblvarsentries.setName("vars-entries-lbl");
;; 		lblvarsentries.setFont(new Font("Inconsolata", Font.BOLD, 12));
;; 		add(lblvarsentries, "cell 3 5,alignx left");
;;
;; 	}

(def doc-rbs-group (button-group))

(defn frame-content []
  (mig-panel :constraints ["", "[][][][grow][][][grow][]", "[][][][][grow][]"]
    :items [
      [ (label :id :ns-header-lbl :text "Namespaces") "cell 0 0 3 1,alignx center"]
      [ (label :id :vars-header-lbl :text "Vars/Classes") "cell 3 0 3 1,alignx center"]
      [ (label :id :doc-header-lbl :text "Documentation") "cell 6 0 2 1,alignx center"]
      [ (label :id :doc-src-header-lbl :text "clojure.core/reduce") "cell 6 3 2 1,alignx left"]

      [ (label :id :#ns-entries :text "0") "cell 0 5,alignx left"]
      [ (label :id :#vars-entries :text "0") "cell 3 5,alignx left"]

      [ (checkbox :id :ns-loaded-cb :text "Loaded" :selected? true) "cell 1 2"]
      [ (checkbox :id :ns-unloaded-cb :text "Unloaded" :selected? false) "cell 2 2"]

      [ (checkbox :id :ns-publics-cb :text "publics" :selected? true) "cell 3 1"]
      [ (checkbox :id :ns-aliases-cb :text "aliases" :selected? false) "cell 4 1"]
      [ (checkbox :id :ns-interns-cb :text "interns" :selected? false) "cell 3 2"]
      [ (checkbox :id :ns-imports-cb :text "imports" :selected? false) "cell 4 2"]
      [ (checkbox :id :ns-map-cb :text "map" :selected? false) "cell 5 2"]
      [ (checkbox :id :ns-refers-cb :text "refers" :selected? false) "cell 5 1"]

      [ (radio :id :doc-rb :text "Doc" :selected? true :group doc-rbs-group) "flowx,cell 7 2,alignx right"]
      [ (radio :id :source-rb :text "Source" :selected? false :group doc-rbs-group) "cell 7 2"]
      [ (radio :id :value-rb :text "Value" :selected? false :group doc-rbs-group) "cell 7 2"]

      [ (text :id :ns-filter-tf :text "") "cell 0 3 3 1,growx"]
      [ (text :id :vars-filter-tf :text "") "cell 3 3 3 1,growx"]

      [ (scrollable (listbox :id :ns-lb :model ["                              "] :font "INCONSOLATA-PLAIN-13")) "cell 0 4 3 1,grow"]
      [ (scrollable (listbox :id :vars-lb :model ["                              "] :font "INCONSOLATA-PLAIN-13")) "cell 3 4 3 1,grow"]
      [ (scrollable (text :id :doc-ta :multi-line? true :font "INCONSOLATA-PLAIN-14"
      :text "                                                                        ")) "cell 6 4 2 1,grow"]

      [ (button :id :require :text "require") "cell 2 5"]

      ]))

