import 'dart:io';
import 'dart:convert';
import 'package:encrypt/encrypt.dart' as enc;
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:share_plus/share_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:intl/intl.dart';
import 'database_helper.dart';

class FileSyncService {
  static const String _keyStr = 'SfwMobileAppSyncDataKey123456789'; // 32 bytes
  static const String _ivStr = 'SfwMobileAppIV12'; // 16 bytes

  /// Scans WhatsApp directories for .sfwdata files, decrypts the newest one, 
  /// checks if it's newer than the last sync, and imports it if so.
  /// Returns true if a new file was successfully imported.
  static Future<bool> scanAndImportLatestSyncFile() async {
    // 1. Request permission
    if (await Permission.manageExternalStorage.request().isGranted ||
        await Permission.storage.request().isGranted) {
      
      // 2. Paths to check
      List<String> paths = [
        '/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents/',
        '/storage/emulated/0/WhatsApp/Media/WhatsApp Documents/',
        '/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents/'
      ];

      File? latestFile;
      DateTime? latestFileTime;

      // 3. Scan for .sfwdata files
      for (String p in paths) {
        final dir = Directory(p);
        if (await dir.exists()) {
          final files = dir.listSync().whereType<File>().where((f) => f.path.endsWith('.sfwdata'));
          for (var file in files) {
            try {
              final stat = await file.stat();
              if (latestFileTime == null || stat.modified.isAfter(latestFileTime)) {
                latestFileTime = stat.modified;
                latestFile = file;
              }
            } catch (e) {
              // ignore unreadable files
            }
          }
        }
      }

      if (latestFile != null) {
        // Check against last synced date
        final prefs = await SharedPreferences.getInstance();
        final lastSyncedStr = prefs.getString('last_synced_at');
        DateTime? lastSynced;
        if (lastSyncedStr != null) {
          try { lastSynced = DateTime.parse(lastSyncedStr); } catch (_) {}
        }

        try {
          final encryptedBytes = await latestFile.readAsBytes();
          
          final key = enc.Key.fromUtf8(_keyStr);
          final iv = enc.IV.fromUtf8(_ivStr);
          final encrypter = enc.Encrypter(enc.AES(key, mode: enc.AESMode.cbc, padding: 'PKCS7'));
          
          final encrypted = enc.Encrypted(encryptedBytes);
          final decryptedBytes = encrypter.decryptBytes(encrypted, iv: iv);
          
          final decompressedBytes = gzip.decode(decryptedBytes);
          final jsonString = utf8.decode(decompressedBytes);
          
          final payload = jsonDecode(jsonString);
          if (payload['exported_at'] != null) {
            final exportedAt = DateTime.parse(payload['exported_at']);
            
            if (lastSynced == null || exportedAt.isAfter(lastSynced)) {
              // It's newer! Load it.
              await DatabaseHelper.instance.wipeAndLoadPayload(jsonString);
              await prefs.setString('last_synced_at', exportedAt.toIso8601String());
              return true; // Successfully imported new data
            }
          }
        } catch (e) {
          print("Error processing file sync: $e");
        }
      }
    }
    return false;
  }
  
  static Future<void> updateLastSyncTime() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('last_synced_at', DateTime.now().toIso8601String());
  }

  /// Generates a .sfwdata file containing all current database records and shares it.
  static Future<void> generateAndShareSyncFile() async {
    try {
      final jsonString = await DatabaseHelper.instance.exportToJson();
      final compressedBytes = gzip.encode(utf8.encode(jsonString));
      
      final key = enc.Key.fromUtf8(_keyStr);
      final iv = enc.IV.fromUtf8(_ivStr);
      final encrypter = enc.Encrypter(enc.AES(key, mode: enc.AESMode.cbc, padding: 'PKCS7'));
      
      final encrypted = encrypter.encryptBytes(compressedBytes, iv: iv);
      
      final tempDir = await getTemporaryDirectory();
      final timestamp = DateFormat('yyyyMMdd_HHmmss').format(DateTime.now());
      final filePath = '${tempDir.path}/SFW_Sync_$timestamp.sfwdata';
      
      final file = File(filePath);
      await file.writeAsBytes(encrypted.bytes);
      
      await Share.shareXFiles([XFile(filePath)], text: 'SFW Sync Data');
    } catch (e) {
      print("Error sharing sync file: $e");
    }
  }
}
