import os
import re

def remove_file(path):
    if os.path.exists(path):
        os.remove(path)

def replace_in_file(path, pattern, repl):
    if not os.path.exists(path): return
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    new_content = re.sub(pattern, repl, content, flags=re.MULTILINE|re.DOTALL)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)

remove_file(r'src\main\java\com\sfw\wholesale\model\Creditor.java')
remove_file(r'src\main\java\com\sfw\wholesale\ui\tab\CreditorsTab.java')

datacache = r'src\main\java\com\sfw\wholesale\database\DataCache.java'
replace_in_file(datacache, r'import com\.sfw\.wholesale\.model\.Creditor;\n', '')
replace_in_file(datacache, r'\s*private final ObservableList<Creditor>\s+creditorList = FXCollections\.observableArrayList\(\);\n', '\n')
replace_in_file(datacache, r'\s*public ObservableList<Creditor>\s+getCreditorList\(\)\s*\{ return creditorList; \}\n', '\n')
replace_in_file(datacache, r'\s*loadCreditors\(conn\);\n', '\n')
replace_in_file(datacache, r'\s*public void refreshCreditors\(Connection conn\) throws SQLException \{\s*loadCreditors\(conn\);\s*\}\n', '\n')
replace_in_file(datacache, r'\s*private void loadCreditors\(Connection conn\) throws SQLException \{.*?\s*creditorList\.setAll\(tmp\);\s*\}\n', '\n')
replace_in_file(datacache, r'\s*private final ObservableList<String>\s+supplierNames = FXCollections\.observableArrayList\(\);\n', '\n')
replace_in_file(datacache, r'\s*public ObservableList<String>\s+getSupplierNames\(\)\s*\{ return supplierNames; \}\n', '\n')
replace_in_file(datacache, r'\s*loadSuppliers\(conn\);\n', '\n')
replace_in_file(datacache, r'\s*public void refreshSuppliers\(Connection conn\) throws SQLException \{\s*loadSuppliers\(conn\);\s*\}\n', '\n')
replace_in_file(datacache, r'\s*private void loadSuppliers\(Connection conn\) throws SQLException \{.*?\s*supplierNames\.setAll\(tmp\);\s*\}\n', '\n')

dbmgr = r'src\main\java\com\sfw\wholesale\database\DatabaseManager.java'
replace_in_file(dbmgr, r'\s*// -- Creditors -----------------------------------------------------\s*st\.execute\(\"\"\"\s*CREATE TABLE IF NOT EXISTS creditors.*?\"\"\"\);\s*st\.execute\(\"CREATE INDEX IF NOT EXISTS idx_creditors_supplier   ON creditors\(supplier_name\)\"\);\s*st\.execute\(\"CREATE INDEX IF NOT EXISTS idx_creditors_lr        ON creditors\(lr_number\)\"\);', '')
replace_in_file(dbmgr, r'\s*// -- Suppliers -----------------------------------------------------\s*st\.execute\(\"\"\"\s*CREATE TABLE IF NOT EXISTS suppliers \(\s*name TEXT PRIMARY KEY\s*\)\s*\"\"\"\);', '')
replace_in_file(dbmgr, r'\s*supplier_name TEXT NOT NULL,', '')
replace_in_file(dbmgr, r'\s*st\.execute\(\"CREATE INDEX IF NOT EXISTS idx_lr_entries_supplier ON lr_entries\(supplier_name\)\"\);', '')

lrlist = r'src\main\java\com\sfw\wholesale\ui\tab\LrListTab.java'
replace_in_file(lrlist, r'\s*TableColumn<LrEntry,String> colSupplier = new TableColumn\<\>\(\"Supplier\"\);\s*colSupplier\.setCellValueFactory\(c -> c\.getValue\(\)\.supplierNameProperty\(\)\);\s*colSupplier\.setMinWidth\(150\);', '')
replace_in_file(lrlist, r'colSupplier, ', '')
replace_in_file(lrlist, r'Search LR / Supplier / Transport\.\.\.', 'Search LR / Transport...')
replace_in_file(lrlist, r'\|\|\s*e\.getSupplierName\(\)\.toLowerCase\(\)\.contains\(q\)', '')

lrentry = r'src\main\java\com\sfw\wholesale\ui\tab\LrEntryTab.java'
replace_in_file(lrentry, r'\s*private AutoSuggestTextField supplierField;\n', '\n')
replace_in_file(lrentry, r'\s*supplierField = new AutoSuggestTextField\(cache\.getSupplierNames\(\)\);\s*supplierField\.setId\(\"supplierComboBox\"\);\s*supplierField\.setPromptText\(\"Supplier name\.\.\.\"\);\s*supplierField\.getStyleClass\(\)\.add\(\"styled-text-field\"\);', '')
replace_in_file(lrentry, r'\s*grid\.add\(labeledField\(\"Supplier Name \*\",\s+supplierField\),\s*1, 1\);', '')
replace_in_file(lrentry, r'setEnterJump\(transportCombo, supplierField\);', 'setEnterJump(transportCombo, billNumberField);')
replace_in_file(lrentry, r'\s*setEnterJump\(supplierField, billNumberField\);', '')
replace_in_file(lrentry, r'\s*supplierField\.setText\(entry\.getSupplierName\(\)\);', '')
replace_in_file(lrentry, r'\s*currentEntry\.setSupplierName\(supplierField\.getText\(\)\.trim\(\)\);', '')
replace_in_file(lrentry, r'\s*supplierField\.clear\(\);', '')
replace_in_file(lrentry, r'\s*if \(supplierField\s*!=\s*null\) \{ supplierField\.setStyle\(style\);\s*supplierField\.setPrefHeight\(h\); \}', '')
replace_in_file(lrentry, r'Row 1: Transport Company \| Supplier Name', 'Row 1: Transport Company')

lrservice = r'src\main\java\com\sfw\wholesale\service\LrService.java'
replace_in_file(lrservice, r'\s*// Ensure supplier \+ transport exist in lookup tables\s*ensureSupplier\(conn, entry\.getSupplierName\(\)\);', '')
replace_in_file(lrservice, r'\s*cache\.refreshSuppliers\(conn\);', '')
replace_in_file(lrservice, r'\s*if \(entry\.getSupplierName\(\) == null \|\| entry\.getSupplierName\(\)\.isBlank\(\)\)\s*throw new IllegalArgumentException\(\"Supplier Name is required\.\"\);', '')
replace_in_file(lrservice, r' supplier_name,', '')
replace_in_file(lrservice, r' supplier_name=\?,', '')
replace_in_file(lrservice, r'\s*ps\.setString\(4, e\.getSupplierName\(\)\);\n', '\n')
replace_in_file(lrservice, r'\s*private void ensureSupplier\(Connection conn, String name\) throws SQLException \{.*?\s*\}\s*\}\n', '\n')
replace_in_file(lrservice, r'\s*5\. Ensure supplier and transport names exist in lookup tables\.', '')

exportservice = r'src\main\java\com\sfw\wholesale\service\ExportImportService.java'
replace_in_file(exportservice, r'\s*exportCreditors\(folder\);\n', '\n')
replace_in_file(exportservice, r'\s*exportSuppliers\(folder\);\n', '\n')
replace_in_file(exportservice, r'\"Supplier Name\",', '')
replace_in_file(exportservice, r' supplier_name,', '')
replace_in_file(exportservice, r'\s*e\[3\]\s*=\s*rs\.getString\(\"supplier_name\"\);', '')
replace_in_file(exportservice, r'\s*private void exportCreditors\(File folder\) throws SQLException \{.*?\s*\}\s*\}\n', '\n')
replace_in_file(exportservice, r'\s*private void exportSuppliers\(File folder\) throws SQLException \{.*?\s*\}\s*\}\n', '\n')
replace_in_file(exportservice, r'\"suppliers\",', '')
replace_in_file(exportservice, r'\"creditors\",', '')
replace_in_file(exportservice, r'supplier_name,', '')
replace_in_file(exportservice, r'\s*st\.execute\(\"INSERT OR IGNORE INTO suppliers \(name\) VALUES \(' + "''\" \+ escapeSql\(cols\[3\]\) \+ \"''\)\"\);\n", '\n')
replace_in_file(exportservice, r'\s*// Ensure supplier and transport exist\n\s*st\.execute\(\"INSERT OR IGNORE INTO suppliers \(name\) VALUES \(' + "''\" \+ escapeSql\(cols\[3\]\) \+ \"''\)\"\);\n", '\n')

exporttest = r'src\main\java\com\sfw\wholesale\service\ExportImportTest.java'
replace_in_file(exporttest, r'\s*stmt\.execute\(\"INSERT OR IGNORE INTO suppliers \(name\) VALUES \(' + "''TEST SUPPLIER''\)\"\);\n", '\n')
replace_in_file(exporttest, r'\"suppliers\.csv\", ', '')
replace_in_file(exporttest, r'\"creditors\.csv\"', '')
replace_in_file(exporttest, r', \"creditors\.csv\"', '')

app = r'src\main\java\com\sfw\wholesale\App.java'
replace_in_file(app, r'import com\.sfw\.wholesale\.ui\.tab\.CreditorsTab;\n', '')
replace_in_file(app, r'\s*private CreditorsTab\s+creditorsTab;\n', '\n')
replace_in_file(app, r'\s*creditorsTab\s*=\s*new CreditorsTab\(\);\n', '\n')
replace_in_file(app, r'\s*createTab\(\"Creditors & Payments\",[^\n]+creditorsTab\),?\n?', '')

model = r'src\main\java\com\sfw\wholesale\model\LrEntry.java'
replace_in_file(model, r'\s*private final StringProperty supplierName = new SimpleStringProperty\(\);\n', '\n')
replace_in_file(model, r'\s*public StringProperty supplierNameProperty\(\) \{ return supplierName; \}\n', '\n')
replace_in_file(model, r'\s*public String getSupplierName\(\)\s*\{ return supplierName\.get\(\); \}\n', '\n')
replace_in_file(model, r'\s*public void setSupplierName\(String v\)\s*\{ supplierName\.set\(v\); \}\n', '\n')
