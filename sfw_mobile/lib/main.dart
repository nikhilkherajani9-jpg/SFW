import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:http/http.dart' as http;
import 'dart:ui';
import 'models.dart';
import 'database_helper.dart';
import 'lr_detail_screen.dart';
import 'qr_share_screen.dart';
import 'file_sync_service.dart';
import 'theme/app_theme.dart';
import 'widgets/mesh_background.dart';
import 'widgets/glass_bottom_nav.dart';
import 'widgets/glass_card.dart';
import 'widgets/search_bar_delegate.dart';

void main() {
  runApp(const SfwMobileApp());
}

class SfwMobileApp extends StatelessWidget {
  const SfwMobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LogiFlow',
      theme: AppTheme.lightTheme,
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
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (context) {
        return BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: Container(
            padding: EdgeInsets.only(bottom: MediaQuery.of(context).padding.bottom),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.85),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(32)),
              border: Border(top: BorderSide(color: Colors.white.withValues(alpha: 0.9))),
            ),
            child: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 48,
                      height: 6,
                      decoration: BoxDecoration(
                        color: AppTheme.outlineVariant,
                        borderRadius: BorderRadius.circular(3),
                      ),
                    ),
                    const SizedBox(height: 24),
                    Text('Sync Data', style: AppTheme.lightTheme.textTheme.headlineMedium),
                    const SizedBox(height: 24),
                    _buildSyncOption(Icons.usb, 'USB Sync', 'Requires Desktop App', () {
                      Navigator.pop(context);
                      _syncData('http://127.0.0.1:8742/api/sync?pin=USB');
                    }),
                    _buildSyncOption(Icons.qr_code_scanner, 'WiFi Sync (QR)', 'Scan from Desktop', () {
                      Navigator.pop(context);
                      _openQrScanner();
                    }),
                    _buildSyncOption(Icons.phone_android, 'Phone-to-Phone', 'Send/Receive via WiFi', () {
                      Navigator.pop(context);
                      Navigator.of(context).push(MaterialPageRoute(builder: (context) => const QrShareScreen()));
                    }),
                    _buildSyncOption(Icons.share, 'Export Data', 'Share via WhatsApp', () {
                      Navigator.pop(context);
                      FileSyncService.generateAndShareSyncFile();
                    }),
                    const SizedBox(height: 24),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildSyncOption(IconData icon, String title, String subtitle, VoidCallback onTap) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: GlassCard(
        isSubCard: true,
        padding: EdgeInsets.zero, // Padding is moved inside Material
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            onTap: onTap,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: AppTheme.primaryContainer.withValues(alpha: 0.2), shape: BoxShape.circle),
                  child: Icon(icon, color: AppTheme.primary),
                ),
                title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Text(subtitle, style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 12)),
                trailing: const Icon(Icons.chevron_right, color: AppTheme.outline),
              ),
            ),
          ),
        ),
      ),
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
    return MeshBackground(
      child: Scaffold(
        extendBody: true,
        backgroundColor: Colors.transparent,
        body: _isLoading 
            ? const Center(child: CircularProgressIndicator()) 
            : (_currentIndex == 0 ? _buildStockTab() : _buildLrsTab()),
        bottomNavigationBar: GlassBottomNav(
          currentIndex: _currentIndex,
          onTap: (idx) => setState(() { _currentIndex = idx; _searchQuery = ""; }),
        ),
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

    return CustomScrollView(
      slivers: [
        SliverAppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          pinned: true,
          flexibleSpace: ClipRect(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
              child: Container(
                decoration: BoxDecoration(
                  color: AppTheme.surface.withValues(alpha: 0.8),
                  border: Border(bottom: BorderSide(color: AppTheme.outlineVariant.withValues(alpha: 0.3))),
                ),
              ),
            ),
          ),
          title: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 36, height: 36,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.7),
                      shape: BoxShape.circle,
                      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4)],
                    ),
                    child: const Icon(Icons.warehouse_outlined, color: AppTheme.primary, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Text('LogiFlow', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                ],
              ),
              InkWell(
                onTap: _openQrScanner,
                child: Container(
                  width: 36, height: 36,
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.7),
                    shape: BoxShape.circle,
                    boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4)],
                  ),
                  child: const Icon(Icons.qr_code_scanner, color: AppTheme.primary, size: 20),
                ),
              ),
            ],
          ),
        ),
        SliverPersistentHeader(
          pinned: true,
          delegate: SearchBarDelegate(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: Container(
                height: 50,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.75),
                  borderRadius: BorderRadius.circular(25),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.9)),
                  boxShadow: [BoxShadow(color: AppTheme.primary.withValues(alpha: 0.05), blurRadius: 10, spreadRadius: 2)],
                ),
                child: TextField(
                  decoration: InputDecoration(
                    hintText: 'Search inventory, SKU, or rack...',
                    hintStyle: TextStyle(color: AppTheme.outline),
                    prefixIcon: const Icon(Icons.search, color: AppTheme.outline),
                    suffixIcon: const Icon(Icons.qr_code_scanner, color: AppTheme.primary),
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.symmetric(vertical: 15),
                  ),
                  onChanged: (val) => setState(() => _searchQuery = val.toLowerCase()),
                ),
              ),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
            child: Column(
              children: [
                _buildStockMetricsGrid(filtered),
                const SizedBox(height: 16),
                _buildStockFilterChips(filtered),
              ],
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.only(left: 20, right: 20, bottom: 100),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) => _buildStockItemCard(filtered[index]),
              childCount: filtered.length,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStockMetricsGrid(List<StockRow> stock) {
    int totalPcs = 0;
    int totalCartons = 0;
    for (var s in stock) {
      totalPcs += s.totalPairs;
      totalCartons += s.cartons;
    }
    
    return Row(
      children: [
        Expanded(
          child: GlassCard(
            padding: const EdgeInsets.all(12),
            borderRadius: 12,
            hasBlur: false, // Performance
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.layers, size: 16, color: AppTheme.primary),
                    const SizedBox(width: 4),
                    Text('Units', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 13, fontWeight: FontWeight.w600)),
                  ],
                ),
                const SizedBox(height: 4),
                Text('$totalPcs', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                Text('Total Pieces', style: TextStyle(color: AppTheme.outline, fontSize: 10)),
              ],
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: GlassCard(
            padding: const EdgeInsets.all(12),
            borderRadius: 12,
            hasBlur: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.inventory, size: 16, color: AppTheme.secondary),
                    const SizedBox(width: 4),
                    Text('Cartons', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 13, fontWeight: FontWeight.w600)),
                  ],
                ),
                const SizedBox(height: 4),
                Text('$totalCartons', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                Text('Total Cartons', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 10)),
              ],
            ),
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: GlassCard(
            padding: const EdgeInsets.all(12),
            borderRadius: 12,
            hasBlur: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.tag, size: 16, color: AppTheme.tertiary),
                    const SizedBox(width: 4),
                    Text('SKUs', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 13, fontWeight: FontWeight.w600)),
                  ],
                ),
                const SizedBox(height: 4),
                Text('${stock.length}', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold, color: AppTheme.tertiary)),
                Text('Unique lines', style: TextStyle(color: AppTheme.tertiary, fontSize: 10, fontWeight: FontWeight.bold)),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildStockFilterChips(List<StockRow> stock) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      clipBehavior: Clip.none,
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            decoration: BoxDecoration(
              color: AppTheme.primary,
              borderRadius: BorderRadius.circular(20),
              boxShadow: [BoxShadow(color: AppTheme.primary.withValues(alpha: 0.3), blurRadius: 4)],
            ),
            child: Row(
              children: [
                Text('All Items', style: TextStyle(color: AppTheme.onPrimary, fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(10)),
                  child: Text('${stock.length}', style: TextStyle(color: AppTheme.onPrimary, fontSize: 11, fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }



  Widget _buildStockItemCard(StockRow s) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: GestureDetector(
        onTap: () {
          Navigator.push(context, MaterialPageRoute(builder: (context) => StockLedgerScreen(product: s.product, location: s.location)));
        },
        child: GlassCard(
          hasBlur: false, // CRITICAL FOR PERFORMANCE
          padding: const EdgeInsets.all(20),
          borderRadius: 24,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.85),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: Colors.white.withValues(alpha: 0.9)),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.warehouse, size: 13, color: AppTheme.primary),
                              const SizedBox(width: 4),
                              Text(s.location, style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 11, fontWeight: FontWeight.bold)),
                            ],
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(s.product, style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold), maxLines: 1, overflow: TextOverflow.ellipsis),
                        const SizedBox(height: 2),
                        Text('SKU: ${s.product.length >= 3 ? s.product.substring(0, 3).toUpperCase() : 'ITM'}-9920 • Category', style: TextStyle(color: AppTheme.outline, fontSize: 12, fontFamily: 'monospace')),
                      ],
                    ),
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppTheme.tertiaryContainer.withValues(alpha: 0.3),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppTheme.tertiaryContainer.withValues(alpha: 0.4)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(
                          width: 6, height: 6,
                          decoration: const BoxDecoration(color: AppTheme.tertiary, shape: BoxShape.circle),
                        ),
                        const SizedBox(width: 6),
                        Text('In Stock', style: TextStyle(color: AppTheme.onTertiaryContainer, fontSize: 13, fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.6)),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Total Pairs', style: TextStyle(color: AppTheme.outline, fontSize: 13, fontWeight: FontWeight.w600)),
                          Text('${s.totalPairs}', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Cartons', style: TextStyle(color: AppTheme.outline, fontSize: 13, fontWeight: FontWeight.w600)),
                          Text('${s.cartons} ctn', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('PPC', style: TextStyle(color: AppTheme.outline, fontSize: 13, fontWeight: FontWeight.w600)),
                          Text('${s.ppc} ppc', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.only(top: 12),
                decoration: BoxDecoration(
                  border: Border(top: BorderSide(color: Colors.white.withValues(alpha: 0.6))),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.schedule, size: 14, color: AppTheme.outline),
                        const SizedBox(width: 4),
                        Text('Updated 12m ago', style: TextStyle(color: AppTheme.outline, fontSize: 11, fontFamily: 'monospace')),
                      ],
                    ),
                    Row(
                      children: [
                        Text('View Manifest', style: TextStyle(color: AppTheme.primary, fontSize: 13, fontWeight: FontWeight.bold)),
                        const Icon(Icons.chevron_right, size: 16, color: AppTheme.primary),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLrsTab() {
    final filtered = _lrs.where((l) => l.lrNumber.toLowerCase().contains(_searchQuery)).toList();
    
    return CustomScrollView(
      slivers: [
        SliverAppBar(
          backgroundColor: Colors.transparent,
          elevation: 0,
          pinned: true,
          flexibleSpace: ClipRect(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
              child: Container(
                decoration: BoxDecoration(
                  color: AppTheme.surface.withValues(alpha: 0.8),
                  border: Border(bottom: BorderSide(color: AppTheme.outlineVariant.withValues(alpha: 0.3))),
                ),
              ),
            ),
          ),
          title: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 40, height: 40,
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.7),
                      borderRadius: BorderRadius.circular(12),
                      boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4)],
                    ),
                    child: const Icon(Icons.warehouse_outlined, color: AppTheme.primary, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('LogiFlow', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                      Text('DISPATCH & FREIGHT', style: TextStyle(color: AppTheme.outline, fontSize: 10, letterSpacing: 1.2, fontWeight: FontWeight.bold)),
                    ],
                  ),
                ],
              ),
              Row(
                children: [
                  Stack(
                    alignment: Alignment.topRight,
                    children: [
                      Container(
                        width: 40, height: 40,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.7),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(Icons.notifications_none, color: AppTheme.onSurfaceVariant, size: 20),
                      ),
                      Positioned(
                        top: 8, right: 8,
                        child: Container(
                          width: 8, height: 8,
                          decoration: const BoxDecoration(color: AppTheme.secondary, shape: BoxShape.circle),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(width: 8),
                  InkWell(
                    onTap: _openQrScanner,
                    child: Container(
                      width: 40, height: 40,
                      decoration: BoxDecoration(
                        color: AppTheme.primaryContainer.withValues(alpha: 0.3),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Icon(Icons.qr_code_scanner, color: AppTheme.primary, size: 20),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('FIELD OPERATIONS', style: TextStyle(color: AppTheme.primary, fontSize: 11, fontWeight: FontWeight.bold, letterSpacing: 1.2)),
                        Text('Lorry Receipts (LRs)', style: AppTheme.lightTheme.textTheme.headlineLarge?.copyWith(fontWeight: FontWeight.bold)),
                      ],
                    ),
                    InkWell(
                      onTap: _showSyncOptions,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.8),
                          border: Border.all(color: AppTheme.outlineVariant.withValues(alpha: 0.4)),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.sync, color: AppTheme.primary, size: 16),
                            const SizedBox(width: 4),
                            Text('Live Sync', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 12, fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        SliverPersistentHeader(
          pinned: true,
          delegate: SearchBarDelegate(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: Container(
                height: 50,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.75),
                  borderRadius: BorderRadius.circular(25),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.9)),
                  boxShadow: [BoxShadow(color: AppTheme.primary.withValues(alpha: 0.05), blurRadius: 10, spreadRadius: 2)],
                ),
                child: Row(
                  children: [
                    const SizedBox(width: 16),
                    const Icon(Icons.search, color: AppTheme.outline, size: 20),
                    const SizedBox(width: 12),
                    Expanded(
                      child: TextField(
                        decoration: InputDecoration(
                          hintText: 'Search LR number, transporter...',
                          hintStyle: TextStyle(color: AppTheme.outline, fontSize: 14),
                          border: InputBorder.none,
                        ),
                        onChanged: (val) => setState(() => _searchQuery = val.toLowerCase()),
                      ),
                    ),
                    const Icon(Icons.tune, color: AppTheme.outline, size: 20),
                    const SizedBox(width: 12),
                    Container(
                      width: 34, height: 34,
                      decoration: const BoxDecoration(
                        color: AppTheme.primaryContainer,
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.qr_code_scanner, color: AppTheme.onPrimaryContainer, size: 16),
                    ),
                    const SizedBox(width: 8),
                  ],
                ),
              ),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 20, 16),
            child: Column(
              children: [
                _buildLrsMetricsGrid(filtered),
                const SizedBox(height: 16),
                _buildLrsFilterChips(filtered),
              ],
            ),
          ),
        ),
        SliverPadding(
          padding: const EdgeInsets.only(left: 20, right: 20, bottom: 100),
          sliver: SliverList(
            delegate: SliverChildBuilderDelegate(
              (context, index) => _buildLrItemCard(filtered[index]),
              childCount: filtered.length,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildLrsMetricsGrid(List<LrEntry> lrs) {
    return Row(
      children: [
        Expanded(
          child: GlassCard(
            padding: const EdgeInsets.all(12),
            borderRadius: 16,
            hasBlur: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Total LRs', style: TextStyle(color: AppTheme.outline, fontSize: 12, fontWeight: FontWeight.bold)),
                    Container(width: 8, height: 8, decoration: const BoxDecoration(color: AppTheme.primaryContainer, shape: BoxShape.circle)),
                  ],
                ),
                const SizedBox(height: 8),
                Text('${lrs.length}', style: AppTheme.lightTheme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
                Text('All shipments', style: TextStyle(color: AppTheme.outline, fontSize: 10)),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildLrsFilterChips(List<LrEntry> lrs) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      clipBehavior: Clip.none,
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            decoration: BoxDecoration(
              color: AppTheme.primary,
              borderRadius: BorderRadius.circular(20),
              boxShadow: [BoxShadow(color: AppTheme.primary.withValues(alpha: 0.3), blurRadius: 4)],
            ),
            child: Row(
              children: [
                Text('All LRs', style: TextStyle(color: AppTheme.onPrimary, fontWeight: FontWeight.w600, fontSize: 13)),
                const SizedBox(width: 6),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.2), borderRadius: BorderRadius.circular(10)),
                  child: Text('${lrs.length}', style: TextStyle(color: AppTheme.onPrimary, fontSize: 11, fontWeight: FontWeight.bold)),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }



  Widget _buildLrItemCard(LrEntry l) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: GestureDetector(
        onTap: () {
          Navigator.push(context, MaterialPageRoute(builder: (context) => LrDetailScreen(lrNumber: l.lrNumber, title: l.lrNumber)));
        },
        child: GlassCard(
          hasBlur: false, // CRITICAL FOR PERFORMANCE
          padding: const EdgeInsets.all(16),
          borderRadius: 24,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 36, height: 36,
                        decoration: BoxDecoration(
                          color: AppTheme.tertiaryContainer.withValues(alpha: 0.3),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Icon(Icons.task_alt, size: 20, color: AppTheme.tertiary),
                      ),
                      const SizedBox(width: 12),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(l.lrNumber, style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                          Text('RECEIVED', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 12)),
                        ],
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.green.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: Colors.green.withValues(alpha: 0.3)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Container(width: 6, height: 6, decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle)),
                        const SizedBox(width: 6),
                        Text('Received', style: TextStyle(color: Colors.green[800], fontSize: 12, fontWeight: FontWeight.bold)),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.business, size: 16, color: AppTheme.primary),
                      const SizedBox(width: 6),
                      Text(l.transport, style: TextStyle(color: AppTheme.onSurface, fontWeight: FontWeight.w500)),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.6)),
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Total Cartons', style: TextStyle(color: AppTheme.outline, fontSize: 11, fontFamily: 'monospace')),
                          Text('${l.totalCartons} ctn', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold, fontSize: 16)),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Shop Cartons', style: TextStyle(color: AppTheme.outline, fontSize: 11, fontFamily: 'monospace')),
                          Text('${l.totalShopCartons} ctn', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold, fontSize: 16)),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text('Total Amount (₹)', style: TextStyle(color: AppTheme.outline, fontSize: 11, fontFamily: 'monospace')),
                          Text('₹${l.totalAmount}', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold, color: AppTheme.primary, fontSize: 16)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
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
    return MeshBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          title: Text('${widget.product} Ledger', style: AppTheme.lightTheme.textTheme.headlineMedium),
        ),
        body: _loading 
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(16.0),
              child: GlassCard(
                hasBlur: false,
                padding: EdgeInsets.zero,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SingleChildScrollView(
                      child: DataTable(
                        columnSpacing: 24,
                        headingRowColor: WidgetStateProperty.all(AppTheme.primary.withValues(alpha: 0.1)),
                        dataRowMinHeight: 56,
                        dataRowMaxHeight: 56,
                        columns: [
                          DataColumn(label: Text('Date', style: AppTheme.lightTheme.textTheme.labelLarge)),
                          DataColumn(label: Text('Type', style: AppTheme.lightTheme.textTheme.labelLarge)),
                          DataColumn(label: Text('In (ctn)', style: AppTheme.lightTheme.textTheme.labelLarge)),
                          DataColumn(label: Text('Out (ctn)', style: AppTheme.lightTheme.textTheme.labelLarge)),
                          DataColumn(label: Text('Balance (ctn)', style: AppTheme.lightTheme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800))),
                          DataColumn(label: Text('Ref/LR', style: AppTheme.lightTheme.textTheme.labelLarge)),
                        ],
                        rows: _ledgers.map((l) {
                          return DataRow(
                            cells: [
                              DataCell(Text(l.date, style: AppTheme.lightTheme.textTheme.bodyMedium)),
                              DataCell(
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                  decoration: BoxDecoration(
                                    color: l.type == 'INWARD' ? AppTheme.tertiaryContainer.withValues(alpha: 0.2) : (l.type == 'OPENING_BALANCE' ? AppTheme.primaryContainer.withValues(alpha: 0.2) : AppTheme.errorContainer.withValues(alpha: 0.5)),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(l.type, style: AppTheme.lightTheme.textTheme.labelSmall?.copyWith(
                                    color: l.type == 'INWARD' ? AppTheme.tertiary : (l.type == 'OPENING_BALANCE' ? AppTheme.primary : AppTheme.error),
                                  )),
                                )
                              ),
                              DataCell(Text(l.inward > 0 ? '${l.inward}' : '', style: AppTheme.lightTheme.textTheme.labelLarge)),
                              DataCell(Text(l.outward > 0 ? '${l.outward}' : '', style: AppTheme.lightTheme.textTheme.labelLarge)),
                              DataCell(Text('${l.balance}', style: AppTheme.lightTheme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w800))),
                              DataCell(Text(l.lrSource, style: AppTheme.lightTheme.textTheme.labelSmall?.copyWith(color: AppTheme.outline))),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                ),
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
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          MobileScanner(
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
          // Reticle Overlay
          Center(
            child: Container(
              width: 250,
              height: 250,
              decoration: BoxDecoration(
                border: Border.all(color: AppTheme.primaryContainer, width: 2),
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(color: AppTheme.primaryContainer.withValues(alpha: 0.5), blurRadius: 20, spreadRadius: 5),
                ],
              ),
            ),
          ),
          Positioned(
            top: 50,
            left: 20,
            child: IconButton(
              icon: const Icon(Icons.close, color: Colors.white, size: 30),
              onPressed: () => Navigator.pop(context),
            ),
          ),
          Positioned(
            bottom: 50,
            left: 0,
            right: 0,
            child: Center(
              child: GlassCard(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                borderRadius: 30,
                child: Text('Align QR code within reticle', style: AppTheme.lightTheme.textTheme.labelLarge),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
