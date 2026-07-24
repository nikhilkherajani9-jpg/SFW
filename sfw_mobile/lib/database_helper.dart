import 'dart:convert';
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';
import 'models.dart';

class DatabaseHelper {
  static final DatabaseHelper instance = DatabaseHelper._init();
  static Database? _database;

  DatabaseHelper._init();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDB('sfw_mobile.db');
    return _database!;
  }

  Future<Database> _initDB(String filePath) async {
    final dbPath = await getDatabasesPath();
    final path = join(dbPath, filePath);

    return await openDatabase(
      path, 
      version: 3, 
      onCreate: _createDB,
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute('ALTER TABLE lr_entries ADD COLUMN total_shop_cartons INTEGER NOT NULL DEFAULT 0');
        }
        if (oldVersion < 3) {
          await db.execute('''
            CREATE TABLE lr_items (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              lr_number TEXT NOT NULL,
              line_no INTEGER NOT NULL,
              product TEXT NOT NULL,
              cartons INTEGER NOT NULL,
              shop_cartons INTEGER NOT NULL DEFAULT 0,
              ppc INTEGER NOT NULL,
              rate REAL NOT NULL,
              amount REAL NOT NULL,
              location TEXT NOT NULL
            )
          ''');
        }
      }
    );
  }

  Future _createDB(Database db, int version) async {
    await db.execute('''
      CREATE TABLE stock (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        product TEXT NOT NULL,
        location TEXT NOT NULL,
        cartons INTEGER NOT NULL,
        ppc INTEGER NOT NULL,
        total_pairs INTEGER NOT NULL,
        lr_source TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE lr_entries (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        lr_number TEXT NOT NULL,
        lr_date TEXT NOT NULL,
        transport TEXT NOT NULL,
        bill_number TEXT NOT NULL,
        status TEXT NOT NULL,
        total_cartons INTEGER NOT NULL,
        total_shop_cartons INTEGER NOT NULL DEFAULT 0,
        total_amount REAL NOT NULL
      )
    ''');

    await db.execute('''
      CREATE TABLE lr_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        lr_number TEXT NOT NULL,
        line_no INTEGER NOT NULL,
        product TEXT NOT NULL,
        cartons INTEGER NOT NULL,
        shop_cartons INTEGER NOT NULL DEFAULT 0,
        ppc INTEGER NOT NULL,
        rate REAL NOT NULL,
        amount REAL NOT NULL,
        location TEXT NOT NULL
      )
    ''');


    await db.execute('''
      CREATE TABLE stock_ledger (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        product TEXT NOT NULL,
        location TEXT NOT NULL,
        date TEXT NOT NULL,
        inward INTEGER NOT NULL,
        outward INTEGER NOT NULL,
        balance INTEGER NOT NULL,
        ppc INTEGER NOT NULL,
        lr_source TEXT,
        type TEXT NOT NULL
      )
    ''');
  }

  Future<void> wipeAndLoadPayload(String jsonString) async {
    final db = await instance.database;
    final payload = jsonDecode(jsonString);

    await db.transaction((txn) async {
      // Wipe old data
      await txn.delete('stock');
      await txn.delete('lr_entries');
      await txn.delete('lr_items');
      await txn.delete('stock_ledger');

      // Load Stock
      if (payload['stock'] != null) {
        for (var item in payload['stock']) {
          await txn.insert('stock', {
            'product': item['product'] ?? '',
            'location': item['location'] ?? '',
            'cartons': item['cartons'] ?? 0,
            'ppc': item['ppc'] ?? 1,
            'total_pairs': item['total_pairs'] ?? 0,
            'lr_source': item['lr_source'] ?? '',
          });
        }
      }

      // Load LRs
      if (payload['lr_entries'] != null) {
        for (var item in payload['lr_entries']) {
          await txn.insert('lr_entries', {
            'lr_number': item['lr_number'] ?? '',
            'lr_date': item['lr_date'] ?? '',
            'transport': item['transport'] ?? '',
            'bill_number': item['bill_number'] ?? '',
            'status': item['status'] ?? '',
            'total_cartons': item['total_cartons'] ?? 0,
            'total_shop_cartons': item['total_shop_cartons'] ?? 0,
            'total_amount': item['total_amount'] ?? 0.0,
          });
        }
      }

      // Load LR Items
      if (payload['lr_items'] != null) {
        for (var item in payload['lr_items']) {
          await txn.insert('lr_items', {
            'lr_number': item['lr_number'] ?? '',
            'line_no': item['line_no'] ?? 0,
            'product': item['product'] ?? '',
            'cartons': item['cartons'] ?? 0,
            'shop_cartons': item['shop_cartons'] ?? 0,
            'ppc': item['ppc'] ?? 0,
            'rate': item['rate'] ?? 0.0,
            'amount': item['amount'] ?? 0.0,
            'location': item['location'] ?? '',
          });
        }
      }


      // Load Stock Ledger
      if (payload['stock_ledger'] != null) {
        for (var item in payload['stock_ledger']) {
          await txn.insert('stock_ledger', {
            'product': item['product'] ?? '',
            'location': item['location'] ?? '',
            'date': item['date'] ?? '',
            'inward': item['inward'] ?? 0,
            'outward': item['outward'] ?? 0,
            'balance': item['balance'] ?? 0,
            'ppc': item['ppc'] ?? 1,
            'lr_source': item['lr_source'] ?? '',
            'type': item['type'] ?? '',
          });
        }
      }
    });
  }
  Future<String> exportToJson() async {
    final db = await instance.database;
    final stock = await db.query('stock', orderBy: 'id ASC');
    final lrs = await db.query('lr_entries', orderBy: 'id ASC');
    final lrItems = await db.query('lr_items', orderBy: 'id ASC');
    final stockLedger = await db.query('stock_ledger', orderBy: 'id ASC');

    final map = {
      'exported_at': DateTime.now().toIso8601String(),
      'stock': stock,
      'lr_entries': lrs,
      'lr_items': lrItems,
      'stock_ledger': stockLedger,
    };
    return jsonEncode(map);
  }

  Future<List<StockRow>> getStock() async {
    final db = await instance.database;
    final result = await db.query('stock', orderBy: 'product ASC, location ASC');
    return result.map((json) => StockRow.fromJson(json)).toList();
  }

  Future<List<StockLedger>> getStockLedger(String product, String location) async {
    final db = await instance.database;
    final result = await db.query(
      'stock_ledger',
      where: 'product = ? AND location = ?',
      whereArgs: [product, location],
      orderBy: 'id ASC',
    );
    return result.map((json) => StockLedger.fromJson(json)).toList();
  }

  Future<List<LrEntry>> getLrs() async {
    final db = await instance.database;
    final result = await db.query('lr_entries', orderBy: 'lr_date DESC');
    return result.map((json) => LrEntry.fromJson(json)).toList();
  }

  Future<List<LrItem>> getLrItems(String lrNumber) async {
    final db = await instance.database;
    final result = await db.query(
      'lr_items',
      where: 'lr_number = ?',
      whereArgs: [lrNumber],
      orderBy: 'line_no ASC',
    );
    return result.map((json) => LrItem.fromJson(json)).toList();
  }

}
