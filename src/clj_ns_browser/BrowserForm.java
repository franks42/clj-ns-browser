package clj_ns_browser;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JList;
import net.miginfocom.swing.MigLayout;
import java.awt.GridLayout;
import javax.swing.JTextArea;
import javax.swing.AbstractListModel;
import javax.swing.JLabel;
import javax.swing.JCheckBox;
import javax.swing.BoxLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JTextField;
import javax.swing.JSeparator;
import javax.swing.JRadioButton;
import javax.swing.JButton;
import javax.swing.SwingConstants;
import java.awt.Font;
import javax.swing.ListSelectionModel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;

public class BrowserForm extends JPanel {
	private JTextField txtNsFilter;
	private JTextField txtVarsFilter;
	private JTextField txtClojurecoremap;

	/**
	 * Create the panel.
	 */
	public BrowserForm() {
		setName("root-panel");
		setLayout(new MigLayout("", "[][][][][][grow]", "[][][][grow][]"));
		
		JLabel lblNamespace = new JLabel("Namespaces");
		lblNamespace.setName("ns-header-lbl");
		lblNamespace.setFont(new Font("Lucida Grande", Font.BOLD, 15));
		add(lblNamespace, "cell 0 0 3 1,alignx center");
		
		JLabel lblVarsclasses = new JLabel("Vars/Classes");
		lblVarsclasses.setName("vars-header-lbl");
		lblVarsclasses.setFont(new Font("Lucida Grande", Font.BOLD, 15));
		add(lblVarsclasses, "cell 3 0 2 1,alignx center");
		
		JLabel lblDocumentation = new JLabel("Documentation");
		lblDocumentation.setName("doc-header-lbl");
		lblDocumentation.setFont(new Font("Lucida Grande", Font.BOLD, 15));
		add(lblDocumentation, "cell 5 0,alignx center");
		
		JComboBox comboBox_1 = new JComboBox();
		comboBox_1.setName("ns-cbx");
		comboBox_1.setModel(new DefaultComboBoxModel(new String[] {"loaded", "unloaded"}));
		add(comboBox_1, "cell 1 1");
		
		JComboBox comboBox = new JComboBox();
		comboBox.setModel(new DefaultComboBoxModel(new String[] {"publics", "interns", "refers", "imports", "map", "aliases", "special-forms"}));
		comboBox.setName("vars-cbx");
		add(comboBox, "cell 3 1");
		
		JComboBox comboBox_2 = new JComboBox();
		comboBox_2.setName("doc-cbx");
		comboBox_2.setModel(new DefaultComboBoxModel(new String[] {"Doc", "Examples", "Source", "Value"}));
		add(comboBox_2, "cell 5 1");
		
		txtNsFilter = new JTextField();
		txtNsFilter.setName("ns-filter-tf");
		txtNsFilter.setFont(new Font("Inconsolata", Font.PLAIN, 13));
		add(txtNsFilter, "cell 0 2 3 1,growx");
		txtNsFilter.setColumns(10);
		
		txtVarsFilter = new JTextField();
		txtVarsFilter.setName("vars-filter-tf");
		txtVarsFilter.setFont(new Font("Inconsolata", Font.PLAIN, 13));
		add(txtVarsFilter, "cell 3 2 2 1,growx");
		txtVarsFilter.setColumns(10);
		
		txtClojurecoremap = new JTextField();
		txtClojurecoremap.setFont(new Font("Inconsolata", Font.BOLD, 15));
		txtClojurecoremap.setName("doc-tf");
		txtClojurecoremap.setEditable(false);
		txtClojurecoremap.setText("clojure.core/map");
		add(txtClojurecoremap, "cell 5 2,growx");
		txtClojurecoremap.setColumns(10);
		
		JScrollPane scrollPane = new JScrollPane();
		add(scrollPane, "cell 0 3 3 1,grow");
		
		JList list = new JList();
		scrollPane.setViewportView(list);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setName("ns-lb");
		list.setFont(new Font("Inconsolata", Font.PLAIN, 13));
		list.setModel(new AbstractListModel() {
			String[] values = new String[] {"clojure.core", "clj.growlnitify.core", "123456789012345678901234567890123456"};
			public int getSize() {
				return values.length;
			}
			public Object getElementAt(int index) {
				return values[index];
			}
		});
		
		JScrollPane scrollPane_1 = new JScrollPane();
		add(scrollPane_1, "cell 3 3 2 1,grow");
		
		JList list_1 = new JList();
		list_1.setFont(new Font("Inconsolata", Font.PLAIN, 13));
		list_1.setModel(new AbstractListModel() {
			String[] values = new String[] {"123456789012345678901234567890123456"};
			public int getSize() {
				return values.length;
			}
			public Object getElementAt(int index) {
				return values[index];
			}
		});
		scrollPane_1.setViewportView(list_1);
		list_1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list_1.setName("vars-lb");
		
		JScrollPane scrollPane_2 = new JScrollPane();
		add(scrollPane_2, "cell 5 3,grow");
		
		JTextArea txtrDocArea = new JTextArea();
		scrollPane_2.setViewportView(txtrDocArea);
		txtrDocArea.setEditable(false);
		txtrDocArea.setName("doc-ta");
		txtrDocArea.setFont(new Font("Inconsolata", Font.PLAIN, 14));
		txtrDocArea.setText("Doc Area\n123456789012345678901234567890123456789012345678901234567890123456789012\n---------------------maximum width ruler------------------------------");
		
		JLabel lblnsentries = new JLabel("9999");
		lblnsentries.setName("ns-entries-lbl");
		lblnsentries.setFont(new Font("Inconsolata", Font.BOLD, 12));
		add(lblnsentries, "cell 0 4 2 1,alignx left");
		
		JButton btnRequire = new JButton("require");
		btnRequire.setName("ns-require-btn");
		add(btnRequire, "cell 2 4");
		
		JLabel lblvarsentries = new JLabel("9999");
		lblvarsentries.setName("vars-entries-lbl");
		lblvarsentries.setFont(new Font("Inconsolata", Font.BOLD, 12));
		add(lblvarsentries, "cell 3 4,alignx left");

	}
}
