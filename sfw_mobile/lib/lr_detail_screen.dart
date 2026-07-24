import 'package:flutter/material.dart';
import 'database_helper.dart';
import 'models.dart';

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
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        backgroundColor: Colors.blueGrey,
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _items.isEmpty
              ? const Center(child: Text('No items found for this LR.'))
              : SingleChildScrollView(
                  scrollDirection: Axis.vertical,
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: DataTable(
                      headingRowColor: WidgetStateProperty.all(Colors.grey[200]),
                      columns: const [
                        DataColumn(label: Text('Line', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Product Name', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Loc', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Ctns', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Shop Ctns', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Prs/Ctn', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Rate', style: TextStyle(fontWeight: FontWeight.bold))),
                        DataColumn(label: Text('Amount', style: TextStyle(fontWeight: FontWeight.bold))),
                      ],
                      rows: _items.map((item) {
                        return DataRow(
                          cells: [
                            DataCell(Text(item.lineNo.toString())),
                            DataCell(Text(item.product)),
                            DataCell(Text(item.location)),
                            DataCell(Text(item.cartons.toString())),
                            DataCell(Text(item.shopCartons.toString())),
                            DataCell(Text(item.ppc.toString())),
                            DataCell(Text(item.rate > 0 ? '₹${item.rate}' : '')),
                            DataCell(Text(item.amount > 0 ? '₹${item.amount}' : '')),
                          ],
                        );
                      }).toList(),
                    ),
                  ),
                ),
    );
  }
}
