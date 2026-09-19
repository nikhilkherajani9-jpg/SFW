import 'dart:io';
import 'dart:convert';
import 'dart:typed_data';
import 'database_helper.dart';
import 'device_utils.dart';

typedef StatusCallback = void Function(String status);

class QrSyncServer {
  final StatusCallback onStatusChanged;
  ServerSocket? _server;
  bool _isRunning = false;

  QrSyncServer({required this.onStatusChanged});

  Future<Map<String, dynamic>> startServer() async {
    if (_isRunning) {
      throw Exception('Server already running');
    }

    _server = await ServerSocket.bind(InternetAddress.anyIPv4, 0);
    _isRunning = true;

    final port = _server!.port;
    final ip = await _getLocalIp();

    onStatusChanged('Server listening on $ip:$port');

    _server!.listen(_handleClient, onError: (e) {
      onStatusChanged('Error: $e');
    });

    return {
      'ip': ip,
      'port': port,
      'deviceId': await DeviceUtils.getDeviceShortId(),
      'deviceName': await DeviceUtils.getDeviceName(),
    };
  }

  Future<String> _getLocalIp() async {
    for (var interface in await NetworkInterface.list()) {
      for (var addr in interface.addresses) {
        if (addr.type == InternetAddressType.IPv4 && !addr.isLoopback) {
          return addr.address;
        }
      }
    }
    return '127.0.0.1';
  }

  void _handleClient(Socket client) async {
    onStatusChanged('Client connected, preparing data...');

    try {
      // 1. Export database to JSON string
      final jsonData = await DatabaseHelper.instance.exportToJson();
      final bytes = utf8.encode(jsonData);
      final length = bytes.length;

      // 2. Send length (8 bytes, big-endian)
      final lengthBytes = ByteData(8)..setUint64(0, length);
      client.add(lengthBytes.buffer.asUint8List());

      // 3. Send data
      client.add(bytes);
      await client.flush();

      onStatusChanged('Data sent to client (${bytes.length} bytes)');
    } catch (e) {
      onStatusChanged('Error sending data: $e');
    } finally {
      await client.close();
    }
  }

  Future<void> stopServer() async {
    if (_server != null) {
      await _server!.close();
      _isRunning = false;
      onStatusChanged('Server stopped');
    }
  }
}
