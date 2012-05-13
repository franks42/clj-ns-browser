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
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;

// import org.fife.ui.RSyntaxTextArea;
import org.fife.ui.rtextarea.*;
import org.fife.ui.rsyntaxtextarea.*;

public class BrowserForm extends JPanel {
	private JTextField txtNsFilter;
	private JTextField txtVarsFilter;
	private JTextField txtClojurecoremap;

	/**
	 * Create the panel.
	 */
	public BrowserForm() {
		setName("root-panel");
		
		JPopupMenu popupMenu = new JPopupMenu();
		popupMenu.setName("main-pmenu");
		addPopup(this, popupMenu);
		
		JMenuItem mntmCopy = new JMenuItem("Copy");
		mntmCopy.setName("copy-btn");
		popupMenu.add(mntmCopy);
		
		JMenuItem mntmPasteFqn = new JMenuItem("Paste fqn");
		mntmPasteFqn.setName("paste-fqn-btn");
		popupMenu.add(mntmPasteFqn);
		
		JSeparator separator = new JSeparator();
		popupMenu.add(separator);
		
		JRadioButtonMenuItem rdbtnmntmOnline = new JRadioButtonMenuItem("Online");
		rdbtnmntmOnline.setName("online-rb");
		popupMenu.add(rdbtnmntmOnline);
		
		JRadioButtonMenuItem rdbtnmntmOffline = new JRadioButtonMenuItem("Offline");
		rdbtnmntmOffline.setName("offline-btn");
		popupMenu.add(rdbtnmntmOffline);
		
		JSeparator separator_1 = new JSeparator();
		popupMenu.add(separator_1);
		
		JMenuItem mntmUpdateOfflineRepo = new JMenuItem("Update offline repo");
		mntmUpdateOfflineRepo.setName("update-offline-repo-btn");
		popupMenu.add(mntmUpdateOfflineRepo);
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
		add(lblDocumentation, "cell 5 0 2 1,alignx center");
		
		JComboBox comboBox_1 = new JComboBox();
		comboBox_1.setName("ns-cbx");
		comboBox_1.setModel(new DefaultComboBoxModel(new String[] {"loaded", "unloaded"}));
		add(comboBox_1, "cell 1 1");
		
		JComboBox comboBox = new JComboBox();
		comboBox.setModel(new DefaultComboBoxModel(new String[] {"publics", "privates", "interns", "interns-macro", "interns-defn", "interns-protocol", "interns-protocol-fn", "interns-var-multimethod", "interns-var-traced", "refers", "refers w/o core", "imports", "map", "map-deftype", "map-defrecord", "aliases", "special-forms"}));
		comboBox.setName("vars-cbx");
		add(comboBox, "cell 3 1");
		
		JScrollPane docScrollPane = new JScrollPane();
		docScrollPane.setName("doc-lb-sp");
		add(docScrollPane, "cell 5 1 2 1,growx");
		
		JList docList = new JList();
		docScrollPane.setViewportView(docList);
		docList.setName("doc-lb");
		//docList.setModel(new DefaultComboBoxModel(new String[] {"All", "Doc", "Examples", "See alsos", "Comments", "Source", "Value"}));
		docList.setModel(new AbstractListModel() {
			String[] values = new String[] {"Doc", "Source", "Examples", "Comments", "See alsos", "Value"};
			public int getSize() {
				return values.length;
			}
			public Object getElementAt(int index) {
				return values[index];
			}
		});
		docList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
		docList.setVisibleRowCount(-1);
		
		txtNsFilter = new JTextField();
		txtNsFilter.setName("ns-filter-tf");
		txtNsFilter.setFont(new Font("Monospaced", Font.PLAIN, 13));
		add(txtNsFilter, "cell 0 2 3 1,growx");
		txtNsFilter.setColumns(10);
		
		txtVarsFilter = new JTextField();
		txtVarsFilter.setName("vars-filter-tf");
		txtVarsFilter.setFont(new Font("Monospaced", Font.PLAIN, 13));
		add(txtVarsFilter, "cell 3 2 2 1,growx");
		txtVarsFilter.setColumns(10);
		
		txtClojurecoremap = new JTextField();
		txtClojurecoremap.setFont(new Font("Monospaced", Font.BOLD, 15));
		txtClojurecoremap.setName("doc-tf");
		txtClojurecoremap.setEditable(false);
		txtClojurecoremap.setText("clojure.core/map");
		add(txtClojurecoremap, "cell 5 2 2 1,growx");
		txtClojurecoremap.setColumns(10);
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setName("ns-lb-sp");
		add(scrollPane, "cell 0 3 3 1,grow");
		
		JList list = new JList();
		scrollPane.setViewportView(list);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setName("ns-lb");
		list.setFont(new Font("Monospaced", Font.PLAIN, 13));
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
		scrollPane_1.setName("vars-lb-sp");
		add(scrollPane_1, "cell 3 3 2 1,grow");
		
		JList list_1 = new JList();
		list_1.setFont(new Font("Monospaced", Font.PLAIN, 13));
		list_1.setModel(new AbstractListModel() {
			String[] values = new String[] {"123456789012345678901234567890123456", "2", "3", "4", "5", "6", "7", "8", "9", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
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
		scrollPane_2.setName("doc-ta-sp");
		add(scrollPane_2, "cell 5 3 3 1,grow");
		
// 		JTextArea txtrDocArea = new JTextArea();
		JTextArea txtrDocArea = new RSyntaxTextArea();
		scrollPane_2.setViewportView(txtrDocArea);
		txtrDocArea.setEditable(false);
		txtrDocArea.setName("doc-ta");
		txtrDocArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
		txtrDocArea.setText("Doc Area\n123456789012345678901234567890123456789012345678901234567890123456789012\n---------------------maximum width ruler------------------------------");
		
		JLabel lblnsentries = new JLabel("9999");
		lblnsentries.setName("ns-entries-lbl");
		lblnsentries.setFont(new Font("Monospaced", Font.BOLD, 12));
		add(lblnsentries, "cell 0 4 2 1,alignx left");
		
		JButton btnRequire = new JButton("require");
		btnRequire.setName("ns-require-btn");
		add(btnRequire, "cell 2 4");
		
		JLabel lblvarsentries = new JLabel("9999");
		lblvarsentries.setName("vars-entries-lbl");
		lblvarsentries.setFont(new Font("Monospaced", Font.BOLD, 12));
		add(lblvarsentries, "cell 3 4,alignx left");
		
		JButton btnTrace = new JButton("trace");
		btnTrace.setName("var-trace-btn");
		add(btnTrace, "cell 4 4");
		
		JButton btnInspect = new JButton("Inspect Coll");
		btnInspect.setName("inspect-btn");
		add(btnInspect, "flowx,cell 5 4,alignx right");
		
		JButton btnEdit = new JButton("Edit");
		btnEdit.setName("edit-btn");
		add(btnEdit, "flowx,cell 6 4,alignx right");
		
		JButton btnBrowse = new JButton("Browse");
		btnBrowse.setName("browse-btn");
		add(btnBrowse, "cell 6 4,alignx right");

	}
	private static void addPopup(Component component, final JPopupMenu popup) {
		component.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}
			public void mouseReleased(MouseEvent e) {
				if (e.isPopupTrigger()) {
					showMenu(e);
				}
			}
			private void showMenu(MouseEvent e) {
				popup.show(e.getComponent(), e.getX(), e.getY());
			}
		});
	}
}
