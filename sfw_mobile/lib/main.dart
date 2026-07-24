import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:http/http.dart' as http;
import 'models.dart';
import 'database_helper.dart';
import 'package:intl/intl.dart';
import 'lr_detail_screen.dart';
import 'qr_share_screen.dart';
import 'file_sync_service.dart';

void main() {
  runApp(const SfwMobileApp());
}

class SfwMobileApp extends StatelessWidget {
  const SfwMobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SFW Mobile',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blueGrey, brightness: Brightness.light),
        useMaterial3: true,
      ),
      home: const MainScreen(),
    );
  }
}

class MainScreen extends StatefulWidget {
  const MainScreen({super.key});

  @override
  State<MainScreen> createState() => _MainScreenState();
}

class _MainScreenState extends State<MainScreen> {
  int _currentIndex = 0;
  List<StockRow> _stock = [];
  List<LrEntry> _lrs = [];
  bool _isLoading = true;
  String _searchQuery = "";

  @override
  void initState() {
    super.initState();
    _initApp();
  }

  Future<void> _initApp() async {
    bool imported = await FileSyncService.scanAndImportLatestSyncFile();
    if (imported && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Auto-synced from WhatsApp file!')));
    }
    await _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    _stock = await DatabaseHelper.instance.getStock();
    _lrs = await DatabaseHelper.instance.getLrs();
    setState(() => _isLoading = false);
  }

  Future<void> _syncData(String url) async {
    setState(() => _isLoading = true);
    try {
      final response = await http.get(Uri.parse(url));
      if (response.statusCode == 200) {
        // We might need to handle gzip, but dart:io http handles gzip automatically if Content-Encoding is gzip!
        // So response.body is already decompressed!
        await DatabaseHelper.instance.wipeAndLoadPayload(response.body);
        await FileSyncService.updateLastSyncTime();
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Sync successful!')));
        await _loadData();
      } else {
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Sync failed: ${response.statusCode}')));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Connection error: $e')));
    } finally {
      setState(() => _isLoading = false);
    }
  }

  void _showSyncOptions() {
    showModalBottomSheet(
      context: context,
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.usb),
                title: const Text('Sync via USB'),
                subtitle: const Text('Requires Desktop App to be in USB Sync mode'),
                onTap: () {
                  Navigator.pop(context);
                  _syncData('http://127.0.0.1:8742/api/sync?pin=USB');
                },
              ),
              ListTile(
                leading: const Icon(Icons.qr_code_scanner),
                title: const Text('Sync via WiFi (QR Code)'),
                subtitle: const Text('Scan QR from Desktop App'),
                onTap: () {
                  Navigator.pop(context);
                  _openQrScanner();
                },
              ),
              ListTile(
                leading: const Icon(Icons.phone_android),
                title: const Text('Sync with Another Phone'),
                subtitle: const Text('Send/Receive data via WiFi'),
                onTap: () {
                  Navigator.pop(context);
                  Navigator.of(context).push(MaterialPageRoute(
                    builder: (context) => const QrShareScreen(),
                  ));
                },
              ),
              ListTile(
                leading: const Icon(Icons.share),
                title: const Text('Export & Share Data'),
                subtitle: const Text('Generate a file and share via apps (e.g. WhatsApp)'),
                onTap: () {
                  Navigator.pop(context);
                  FileSyncService.generateAndShareSyncFile();
                },
              ),
            ],
          ),
        );
      },
    );
  }

  void _openQrScanner() {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (context) => QrScannerScreen(onScan: (url) {
        Navigator.pop(context);
        _syncData(url);
      }),
    ));
  }

  @override
  Widget build(BuildContext context) {
    Widget body;
    if (_isLoading) {
      body = const Center(child: CircularProgressIndicator());
    } else {
      switch (_currentIndex) {
        case 0:
          body = _buildStockTab();
          break;
        case 1:
          body = _buildLrsTab();
          break;
        default:
          body = Container();
      }
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('SFW Mobile'),
        actions: [
          IconButton(
            icon: const Icon(Icons.sync),
            onPressed: _showSyncOptions,
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: TextField(
              decoration: const InputDecoration(
                hintText: 'Search...',
                prefixIcon: Icon(Icons.search),
                border: OutlineInputBorder(),
              ),
              onChanged: (val) {
                setState(() {
                  _searchQuery = val.toLowerCase();
                });
              },
            ),
          ),
          Expanded(child: body),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (idx) => setState(() { _currentIndex = idx; _searchQuery = ""; }),
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.inventory), label: 'Stock'),
          BottomNavigationBarItem(icon: Icon(Icons.receipt), label: 'LRs'),
        ],
      ),
    );
  }

  List<StockRow> _groupStock(List<StockRow> rows) {
    final Map<String, StockRow> grouped = {};
    for (var r in rows) {
      final key = '${r.product}|${r.location}';
      if (grouped.containsKey(key)) {
        final existing = grouped[key]!;
        existing.cartons += r.cartons;
        existing.totalPairs += r.totalPairs;
        existing.ppc = 0; // Mixed
      } else {
        grouped[key] = StockRow(
          product: r.product,
          location: r.location,
          cartons: r.cartons,
          ppc: r.ppc,
          totalPairs: r.totalPairs,
          lrSource: r.lrSource,
        );
      }
    }
    return grouped.values.toList();
  }

  Widget _buildStockTab() {
    final filtered = _groupStock(_stock.where((s) => s.product.toLowerCase().contains(_searchQuery) || s.location.toLowerCase().contains(_searchQuery)).toList());
    return ListView.builder(
      itemCount: filtered.length,
      itemBuilder: (context, index) {
        final s = filtered[index];
        return ListTile(
          title: Text(s.product, style: const TextStyle(fontWeight: FontWeight.bold)),
          subtitle: Text('${s.location} • ${s.totalPairs} pairs (${s.ppc} ppc)'),
          trailing: Text('${s.cartons} ctn', style: const TextStyle(fontSize: 16)),
          onTap: () {
            Navigator.push(context, MaterialPageRoute(builder: (context) => StockLedgerScreen(product: s.product, location: s.location)));
          },
        );
      },
    );
  }

  Widget _buildLrsTab() {
    final filtered = _lrs.where((l) => l.lrNumber.toLowerCase().contains(_searchQuery)).toList();
    return ListView.builder(
      itemCount: filtered.length,
      itemBuilder: (context, index) {
        final l = filtered[index];
        return ListTile(
          title: Text(l.lrNumber),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('${l.transport} • ${l.status}'),
              Text('Total: ${l.totalCartons} ctn | Shop: ${l.totalShopCartons} ctn', style: const TextStyle(fontSize: 13, color: Colors.blueGrey)),
            ],
          ),
          trailing: Text('₹${l.totalAmount}'),
          onTap: () {
            Navigator.push(context, MaterialPageRoute(builder: (context) => LrDetailScreen(lrNumber: l.lrNumber, title: l.lrNumber)));
          },
        );
      },
    );
  }


}

class StockLedgerScreen extends StatefulWidget {
  final String product;
  final String location;
  const StockLedgerScreen({super.key, required this.product, required this.location});

  @override
  State<StockLedgerScreen> createState() => _StockLedgerScreenState();
}

class _StockLedgerScreenState extends State<StockLedgerScreen> {
  List<StockLedger> _ledgers = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final l = await DatabaseHelper.instance.getStockLedger(widget.product, widget.location);
    setState(() {
      _ledgers = l;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('${widget.product} (${widget.location}) Ledger')),
      body: _loading 
        ? const Center(child: CircularProgressIndicator())
        : SingleChildScrollView(
            scrollDirection: Axis.vertical,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: DataTable(
                columnSpacing: 16,
                headingRowColor: MaterialStateProperty.all(Colors.grey.shade200),
                columns: const [
                  DataColumn(label: Text('Date', style: TextStyle(fontWeight: FontWeight.bold))),
                  DataColumn(label: Text('Type', style: TextStyle(fontWeight: FontWeight.bold))),
                  DataColumn(label: Text('In', style: TextStyle(fontWeight: FontWeight.bold))),
                  DataColumn(label: Text('Out', style: TextStyle(fontWeight: FontWeight.bold))),
                  DataColumn(label: Text('Balance', style: TextStyle(fontWeight: FontWeight.bold))),
                  DataColumn(label: Text('Ref/LR', style: TextStyle(fontWeight: FontWeight.bold))),
                ],
                rows: _ledgers.map((l) {
                  return DataRow(
                    cells: [
                      DataCell(Text(l.date)),
                      DataCell(Text(l.type, style: TextStyle(
                        color: l.type == 'INWARD' ? Colors.green : (l.type == 'OPENING_BALANCE' ? Colors.blue : Colors.red),
                        fontWeight: FontWeight.w500
                      ))),
                      DataCell(Text(l.inward > 0 ? '${l.inward}' : '')),
                      DataCell(Text(l.outward > 0 ? '${l.outward}' : '')),
                      DataCell(Text('${l.balance}', style: const TextStyle(fontWeight: FontWeight.bold))),
                      DataCell(Text(l.lrSource)),
                    ],
                  );
                }).toList(),
              ),
            ),
          ),
    );
  }
}

class QrScannerScreen extends StatefulWidget {
  final Function(String) onScan;
  const QrScannerScreen({super.key, required this.onScan});

  @override
  State<QrScannerScreen> createState() => _QrScannerScreenState();
}

class _QrScannerScreenState extends State<QrScannerScreen> {
  bool _scanned = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Scan Sync QR Code')),
      body: MobileScanner(
        onDetect: (capture) {
          if (_scanned) return;
          final List<Barcode> barcodes = capture.barcodes;
          if (barcodes.isNotEmpty && barcodes.first.rawValue != null) {
            final url = barcodes.first.rawValue!;
            if (url.startsWith('http')) {
              _scanned = true;
              widget.onScan(url);
            }
          }
        },
      ),
    );
  }
}
