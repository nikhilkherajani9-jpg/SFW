class StockRow {
  String product;
  String location;
  int cartons;
  int ppc;
  int totalPairs;
  String lrSource;

  StockRow({
    required this.product,
    required this.location,
    required this.cartons,
    required this.ppc,
    required this.totalPairs,
    required this.lrSource,
  });

  factory StockRow.fromJson(Map<String, dynamic> json) {
    return StockRow(
      product: json['product'] ?? '',
      location: json['location'] ?? '',
      cartons: json['cartons'] ?? 0,
      ppc: json['ppc'] ?? 1,
      totalPairs: json['total_pairs'] ?? 0,
      lrSource: json['lr_source'] ?? '',
    );
  }
}

class LrEntry {
  final String lrNumber;
  final String lrDate;
  final String transport;
  final int totalCartons;
  final int totalShopCartons;

  // Added for UI backward compatibility
  String get status => 'IN TRANSIT';
  int get totalAmount => 0;

  LrEntry({
    required this.lrNumber,
    required this.lrDate,
    required this.transport,
    required this.totalCartons,
    required this.totalShopCartons,
  });

  factory LrEntry.fromJson(Map<String, dynamic> json) {
    return LrEntry(
      lrNumber: json['lr_number'] ?? '',
      lrDate: json['lr_date'] ?? '',
      transport: json['transport'] ?? '',
      totalCartons: json['total_cartons'] ?? 0,
      totalShopCartons: json['total_shop_cartons'] ?? 0,
    );
  }
}


class StockLedger {
  final String product;
  final String location;
  final String date;
  final int inward;
  final int outward;
  final int balance;
  final int ppc;
  final String lrSource;
  final String type;

  StockLedger({
    required this.product,
    required this.location,
    required this.date,
    required this.inward,
    required this.outward,
    required this.balance,
    required this.ppc,
    required this.lrSource,
    required this.type,
  });

  factory StockLedger.fromJson(Map<String, dynamic> json) {
    return StockLedger(
      product: json['product'] ?? '',
      location: json['location'] ?? '',
      date: json['date'] ?? '',
      inward: json['inward'] ?? 0,
      outward: json['outward'] ?? 0,
      balance: json['balance'] ?? 0,
      ppc: json['ppc'] ?? 1,
      lrSource: json['lr_source'] ?? '',
      type: json['type'] ?? '',
    );
  }
}

class LrItem {
  final String lrNumber;
  final int lineNo;
  final String product;
  final int cartons;
  final int shopCartons;
  final int ppc;
  final String location;

  // Added for UI backward compatibility
  int get amount => 0;
  int get rate => 0;

  LrItem({
    required this.lrNumber,
    required this.lineNo,
    required this.product,
    required this.cartons,
    required this.shopCartons,
    required this.ppc,
    required this.location,
  });

  factory LrItem.fromJson(Map<String, dynamic> json) {
    return LrItem(
      lrNumber: json['lr_number'] ?? '',
      lineNo: json['line_no'] ?? 0,
      product: json['product'] ?? '',
      cartons: json['cartons'] ?? 0,
      shopCartons: json['shop_cartons'] ?? 0,
      ppc: json['ppc'] ?? 0,
      location: json['location'] ?? '',
    );
  }
}
