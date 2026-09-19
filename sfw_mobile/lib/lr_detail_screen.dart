import 'package:flutter/material.dart';
import 'database_helper.dart';
import 'models.dart';
import 'theme/app_theme.dart';
import 'widgets/mesh_background.dart';
import 'widgets/glass_card.dart';

class LrDetailScreen extends StatefulWidget {
  final String lrNumber;
  final String title;

  const LrDetailScreen({super.key, required this.lrNumber, required this.title});

  @override
  State<LrDetailScreen> createState() => _LrDetailScreenState();
}

class _LrDetailScreenState extends State<LrDetailScreen> {
  List<LrItem> _items = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadItems();
  }

  Future<void> _loadItems() async {
    final items = await DatabaseHelper.instance.getLrItems(widget.lrNumber);
    setState(() {
      _items = items;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    // Calculate totals for hero card
    int totalCartons = 0;
    int totalShopCartons = 0;
    double totalValue = 0;
    for (var item in _items) {
      totalCartons += item.cartons;
      totalShopCartons += item.shopCartons;
      totalValue += item.amount;
    }

    return MeshBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        body: SafeArea(
          child: Column(
            children: [
              // Custom Header
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        InkWell(
                          onTap: () => Navigator.pop(context),
                          child: Container(
                            width: 40, height: 40,
                            decoration: BoxDecoration(
                              color: Colors.white.withValues(alpha: 0.7),
                              shape: BoxShape.circle,
                              border: Border.all(color: Colors.white.withValues(alpha: 0.6)),
                              boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4)],
                            ),
                            child: const Icon(Icons.arrow_back, color: AppTheme.primary),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.local_shipping, color: AppTheme.primary, size: 18),
                                const SizedBox(width: 4),
                                Text(widget.title, style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                              ],
                            ),
                            Text('RECEIPT BREAKDOWN & DISPATCH', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 10, letterSpacing: 1.2, fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ],
                    ),
                    Row(
                      children: [
                        Container(
                          width: 36, height: 36,
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.7),
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white.withValues(alpha: 0.6)),
                          ),
                          child: const Icon(Icons.share, color: AppTheme.onSurfaceVariant, size: 18),
                        ),
                        const SizedBox(width: 8),
                        Container(
                          width: 36, height: 36,
                          decoration: BoxDecoration(
                            color: Colors.white.withValues(alpha: 0.7),
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white.withValues(alpha: 0.6)),
                          ),
                          child: const Icon(Icons.print, color: AppTheme.onSurfaceVariant, size: 18),
                        ),
                      ],
                    ),
                  ],
                ),
              ),

              Expanded(
                child: _loading
                    ? const Center(child: CircularProgressIndicator())
                    : ListView(
                        padding: const EdgeInsets.all(16),
                        children: [
                          // Hero Card
                          GlassCard(
                            hasBlur: true,
                            padding: const EdgeInsets.all(20),
                            borderRadius: 24,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text('CONSIGNMENT LR', style: TextStyle(color: AppTheme.outline, fontSize: 10, fontWeight: FontWeight.bold, letterSpacing: 1.2)),
                                        Row(
                                          children: [
                                            Text(widget.title, style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                                            const SizedBox(width: 8),
                                            const Icon(Icons.content_copy, color: AppTheme.outline, size: 16),
                                          ],
                                        ),
                                      ],
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                                      decoration: BoxDecoration(
                                        color: Colors.green.withValues(alpha: 0.15),
                                        borderRadius: BorderRadius.circular(16),
                                        border: Border.all(color: Colors.green.withValues(alpha: 0.3)),
                                      ),
                                      child: Row(
                                        children: [
                                          Container(width: 8, height: 8, decoration: const BoxDecoration(color: Colors.green, shape: BoxShape.circle)),
                                          const SizedBox(width: 6),
                                          Text('Received', style: TextStyle(color: Colors.green[800], fontSize: 12, fontWeight: FontWeight.bold)),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                                Row(
                                  children: [
                                    Expanded(
                                      child: Container(
                                        padding: const EdgeInsets.all(12),
                                        decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.7), borderRadius: BorderRadius.circular(16), border: Border.all(color: Colors.white.withValues(alpha: 0.7))),
                                        child: Column(
                                          children: [
                                            Text('Total Cartons', style: TextStyle(color: AppTheme.outline, fontSize: 11)),
                                            Text('$totalCartons ctn', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                                          ],
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Container(
                                        padding: const EdgeInsets.all(12),
                                        decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.7), borderRadius: BorderRadius.circular(16), border: Border.all(color: Colors.white.withValues(alpha: 0.7))),
                                        child: Column(
                                          children: [
                                            Text('Shop Cartons', style: TextStyle(color: AppTheme.outline, fontSize: 11)),
                                            Text('$totalShopCartons ctn', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                                          ],
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Expanded(
                                      child: Container(
                                        padding: const EdgeInsets.all(12),
                                        decoration: BoxDecoration(color: AppTheme.primaryContainer.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(16), border: Border.all(color: AppTheme.primaryContainer.withValues(alpha: 0.3))),
                                        child: Column(
                                          children: [
                                            Text('Consignment Val', style: TextStyle(color: AppTheme.primary, fontSize: 11, fontWeight: FontWeight.bold)),
                                            Text('₹$totalValue', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold, color: AppTheme.primary)),
                                          ],
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),

                          const SizedBox(height: 16),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  Text('Receipt Manifest', style: AppTheme.lightTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                                  const SizedBox(width: 8),
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
                                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.75), borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.white.withValues(alpha: 0.8))),
                                    child: Text('${_items.length} Items', style: TextStyle(color: AppTheme.onSurface, fontSize: 12, fontWeight: FontWeight.bold)),
                                  ),
                                ],
                              ),
                              Row(
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.7), borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.white.withValues(alpha: 0.8))),
                                    child: Row(
                                      children: [
                                        const Icon(Icons.tune, color: AppTheme.onSurfaceVariant, size: 16),
                                        const SizedBox(width: 4),
                                        Text('Filter', style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 12, fontWeight: FontWeight.bold)),
                                      ],
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Container(
                                    padding: const EdgeInsets.all(6),
                                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.7), borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.white.withValues(alpha: 0.8))),
                                    child: const Icon(Icons.swap_vert, color: AppTheme.onSurfaceVariant, size: 16),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),

                          ..._items.map((item) => _buildItemCard(item)),
                        ],
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildItemCard(LrItem item) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: GlassCard(
        hasBlur: false, // Performance
        padding: const EdgeInsets.all(16),
        borderRadius: 16,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(color: Colors.blue[50], borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.blue[200]!)),
                            child: Text('Category', style: TextStyle(color: AppTheme.primary, fontSize: 10, fontWeight: FontWeight.bold)),
                          ),
                          const SizedBox(width: 8),
                          Text('SKU: ${item.product.substring(0, item.product.length > 3 ? 3 : item.product.length).toUpperCase()}-9920', style: TextStyle(color: AppTheme.outline, fontSize: 12, fontFamily: 'monospace')),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(item.product, style: AppTheme.lightTheme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                    ],
                  ),
                ),
                Container(
                  width: 36, height: 36,
                  decoration: BoxDecoration(color: AppTheme.surfaceContainerHigh.withValues(alpha: 0.6), borderRadius: BorderRadius.circular(12)),
                  child: const Icon(Icons.inventory_2, color: AppTheme.primary, size: 20),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(color: AppTheme.surfaceContainer.withValues(alpha: 0.4), borderRadius: BorderRadius.circular(8), border: Border.all(color: Colors.white.withValues(alpha: 0.6))),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.pin_drop, color: AppTheme.primary, size: 14),
                  const SizedBox(width: 6),
                  Text(item.location, style: TextStyle(color: AppTheme.onSurfaceVariant, fontSize: 11, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.5), borderRadius: BorderRadius.circular(12), border: Border.all(color: Colors.white.withValues(alpha: 0.7))),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('CARTONS', style: TextStyle(color: AppTheme.outline, fontSize: 10, fontWeight: FontWeight.bold)),
                            Text('${item.cartons} ctn', style: AppTheme.lightTheme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('SHOP CTNS', style: TextStyle(color: AppTheme.outline, fontSize: 10, fontWeight: FontWeight.bold)),
                            Text('${item.shopCartons} ctn', style: AppTheme.lightTheme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('PRS / CTN', style: TextStyle(color: AppTheme.outline, fontSize: 10, fontWeight: FontWeight.bold)),
                            Text('${item.ppc} prs', style: AppTheme.lightTheme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.only(top: 8),
                    decoration: BoxDecoration(border: Border(top: BorderSide(color: AppTheme.outlineVariant.withValues(alpha: 0.3)))),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('UNIT RATE', style: TextStyle(color: AppTheme.outline, fontSize: 10, fontWeight: FontWeight.bold)),
                            Text('₹${item.rate} / ctn', style: TextStyle(color: AppTheme.onSurface, fontSize: 12, fontWeight: FontWeight.bold)),
                          ],
                        ),
                        Column(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text('TOTAL VALUE', style: TextStyle(color: AppTheme.outline, fontSize: 10, fontWeight: FontWeight.bold)),
                            Text('₹${item.amount}', style: TextStyle(color: AppTheme.onSurface, fontSize: 14, fontWeight: FontWeight.bold)),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
