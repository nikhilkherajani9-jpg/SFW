import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'device_utils.dart';
import 'qr_sync_server.dart';
import 'qr_sync_client.dart';

class QrShareScreen extends StatefulWidget {
  const QrShareScreen({super.key});

  @override
  State<QrShareScreen> createState() => _QrShareScreenState();
}

class _QrShareScreenState extends State<QrShareScreen> {
  bool _isSendMode = true;
  QrSyncServer? _server;
  String _status = 'Ready';
  String _qrData = '';
  String _deviceName = '';
  String _deviceShortId = '';
  bool _isReceiving = false;

  @override
  void initState() {
    super.initState();
    _initDeviceInfo();
  }

  Future<void> _initDeviceInfo() async {
    final name = await DeviceUtils.getDeviceName();
    final shortId = await DeviceUtils.getDeviceShortId();
    setState(() {
      _deviceName = name;
      _deviceShortId = shortId;
    });
  }

  void _startSendMode() async {
    setState(() => _status = 'Starting server...');

    _server = QrSyncServer(onStatusChanged: (status) {
      setState(() => _status = status);
    });

    try {
      final info = await _server!.startServer();
      final qrData =
          'phone2phone://${info['ip']}:${info['port']}/${info['deviceId']}/${info['deviceName']}';

      setState(() {
        _qrData = qrData;
        _status = 'Server ready! Scan QR from another phone';
      });
    } catch (e) {
      setState(() => _status = 'Error: $e');
    }
  }

  void _stopSendMode() async {
    if (_server != null) {
      await _server!.stopServer();
      setState(() {
        _qrData = '';
        _status = 'Server stopped';
      });
    }
  }

  void _startReceiveMode() async {
    final String? scannedData = await Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const QrScannerForReceive(),
      ),
    );

    if (scannedData == null || scannedData.isEmpty) {
      setState(() => _status = 'Scan cancelled');
      return;
    }

    final uri = Uri.parse(scannedData);
    if (uri.scheme != 'phone2phone' || uri.host.isEmpty || uri.port == 0) {
      setState(() => _status = 'Invalid QR code');
      return;
    }

    final ip = uri.host;
    final port = uri.port;
    final segments = uri.pathSegments;
    final deviceName = segments.length > 1 ? segments[1] : 'Unknown';

    setState(() {
      _status = 'Connecting to $deviceName ($ip:$port)...';
      _isReceiving = true;
    });

    final client = QrSyncClient(onStatusChanged: (status) {
      setState(() => _status = status);
    });

    final success = await client.connectAndReceiveData(ip, port);

    setState(() => _isReceiving = false);

    if (!success) {
      setState(() => _status = 'Connection failed');
    } else {
      setState(() => _status = 'Data received successfully!');
      Future.delayed(const Duration(seconds: 2), () {
        if (mounted) {
          Navigator.pop(context);
        }
      });
    }
  }

  @override
  void dispose() {
    _server?.stopServer();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Phone-to-Phone Sync'),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Device Info
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('This Device',
                          style: TextStyle(
                              fontSize: 16, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 8),
                      Text('Name: $_deviceName'),
                      Text('ID: $_deviceShortId'),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),

              // Mode Selection – add explicit text style to its labels
              SegmentedButton<bool>(
                segments: const [
                  ButtonSegment(
                    value: true,
                    label: Text('Send Data',
                        style: TextStyle(fontSize: 14)),
                  ),
                  ButtonSegment(
                    value: false,
                    label: Text('Receive Data',
                        style: TextStyle(fontSize: 14)),
                  ),
                ],
                selected: {_isSendMode},
                onSelectionChanged: (Set<bool> newSelection) {
                  setState(() => _isSendMode = newSelection.first);
                },
              ),
              const SizedBox(height: 24),

              // Send Mode UI
              if (_isSendMode) ...[
                const Text('Send Data Mode',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                const Text(
                    'Start the server and share the QR code with the receiver:'),
                const SizedBox(height: 16),
                if (_qrData.isEmpty)
                  Center(
                    child: ElevatedButton.icon(
                      onPressed: _startSendMode,
                      icon: const Icon(Icons.play_arrow),
                      label: const Text('Start Sending'),
                      // FIX: explicit text style to avoid interpolation error
                      style: ElevatedButton.styleFrom(
                        textStyle: const TextStyle(fontSize: 14),
                      ),
                    ),
                  )
                else ...[
                  Center(
                    child: Container(
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        border: Border.all(color: Colors.grey),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: QrImageView(
                        data: _qrData,
                        version: QrVersions.auto,
                        gapless: false,
                        size: 300.0,
                        backgroundColor: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Center(
                    child: ElevatedButton.icon(
                      onPressed: _stopSendMode,
                      icon: const Icon(Icons.stop),
                      label: const Text('Stop Sending'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.red,
                        textStyle: const TextStyle(fontSize: 14),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Scan this QR with the receiver device',
                    style: TextStyle(color: Colors.grey[600]),
                  ),
                ],
              ] else ...[
                // Receive Mode UI
                const Text('Receive Data Mode',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                const Text('Scan the QR code displayed on the sender device:'),
                const SizedBox(height: 24),
                Center(
                  child: ElevatedButton.icon(
                    onPressed: _isReceiving ? null : _startReceiveMode,
                    icon: const Icon(Icons.qr_code_scanner),
                    label: const Text('Scan QR to Receive Data'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 32, vertical: 16),
                      textStyle: const TextStyle(fontSize: 16),
                    ),
                  ),
                ),
                if (_isReceiving)
                  const Padding(
                    padding: EdgeInsets.only(top: 16),
                    child: Center(child: CircularProgressIndicator()),
                  ),
              ],

              const SizedBox(height: 24),
              // Status
              Card(
                color: Colors.blue.shade50,
                child: Padding(
                  padding: const EdgeInsets.all(12.0),
                  child: Row(
                    children: [
                      const Icon(Icons.info, color: Colors.blue),
                      const SizedBox(width: 12),
                      Expanded(
                          child: Text(_status,
                              style: const TextStyle(fontSize: 14))),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// QR scanner screen (unchanged, but we keep it)
class QrScannerForReceive extends StatefulWidget {
  const QrScannerForReceive({super.key});

  @override
  State<QrScannerForReceive> createState() => _QrScannerForReceiveState();
}

class _QrScannerForReceiveState extends State<QrScannerForReceive> {
  bool _scanned = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Scan Sender QR'),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.pop(context, null),
        ),
      ),
      body: MobileScanner(
        onDetect: (capture) {
          if (_scanned) return;
          final barcodes = capture.barcodes;
          if (barcodes.isNotEmpty && barcodes.first.rawValue != null) {
            final data = barcodes.first.rawValue!;
            if (data.startsWith('phone2phone://')) {
              _scanned = true;
              Navigator.pop(context, data);
            }
          }
        },
      ),
    );
  }
}