import 'dart:io';
import 'dart:convert';
import 'dart:typed_data';
import 'database_helper.dart';

typedef StatusCallback = void Function(String status);

class QrSyncClient {
  final StatusCallback onStatusChanged;
  Socket? _socket;

  QrSyncClient({required this.onStatusChanged});

  /// Reads exactly [count] bytes from the socket, handling fragmentation.
  Future<List<int>> _readExactly(int count) async {
    final bytes = <int>[];
    int received = 0;
    while (received < count) {
      final chunk = await _socket!.first;
      if (chunk.isEmpty) {
        throw Exception('Socket closed prematurely');
      }
      bytes.addAll(chunk);
      received += chunk.length;
    }
    return bytes;
  }

  Future<bool> connectAndReceiveData(String ip, int port) async {
    try {
      onStatusChanged('Connecting to $ip:$port...');
      _socket = await Socket.connect(ip, port, timeout: const Duration(seconds: 10));
      onStatusChanged('Connected, receiving data...');

      // 1. Read exactly 8 bytes for the length prefix (big-endian)
      final lengthBytes = await _readExactly(8);
      // Convert to Uint8List for ByteData
      final lengthUint8 = Uint8List.fromList(lengthBytes);
      final length = ByteData.sublistView(lengthUint8, 0, 8).getUint64(0);

      // 2. Read exactly `length` bytes of JSON data
      final dataBytes = await _readExactly(length);

      // 3. Decode and apply to database
      final jsonString = utf8.decode(dataBytes);
      await DatabaseHelper.instance.wipeAndLoadPayload(jsonString);

      onStatusChanged('Data received and applied successfully');
      return true;
    } catch (e) {
      onStatusChanged('Error: $e');
      return false;
    } finally {
      _socket?.close();
    }
  }

  void disconnect() {
    _socket?.close();
    _socket = null;
  }
}