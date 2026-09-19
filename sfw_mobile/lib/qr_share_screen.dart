import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'qr_sync_server.dart';
import 'qr_sync_client.dart';
import 'theme/app_theme.dart';
import 'widgets/mesh_background.dart';
import 'widgets/glass_card.dart';

class QrShareScreen extends StatefulWidget {
  const QrShareScreen({super.key});

  @override
  State<QrShareScreen> createState() => _QrShareScreenState();
}

class _QrShareScreenState extends State<QrShareScreen> with SingleTickerProviderStateMixin {
  bool _isSendMode = true;
  QrSyncServer? _server;
  String _status = 'Ready for connection';
  String _qrData = '';
  bool _isReceiving = false;
  late AnimationController _pulseController;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 3),
    )..repeat();
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
    _pulseController.dispose();
    _server?.stopServer();
    super.dispose();
  }

  Widget _buildGlassSegmentedControl() {
    return GlassCard(
      padding: const EdgeInsets.all(4),
      borderRadius: 16,
      child: Row(
        children: [
          Expanded(
            child: GestureDetector(
              onTap: () {
                if (!_isSendMode) {
                  setState(() {
                    _isSendMode = true;
                    _status = 'Ready for connection';
                    _qrData = '';
                  });
                }
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(vertical: 12),
                decoration: BoxDecoration(
                  color: _isSendMode ? Colors.white.withValues(alpha: 0.9) : Colors.transparent,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: _isSendMode ? [
                    BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4, offset: const Offset(0, 2))
                  ] : [],
                ),
                child: Center(
                  child: Text('Send Data', style: AppTheme.lightTheme.textTheme.labelLarge?.copyWith(
                    color: _isSendMode ? AppTheme.primary : AppTheme.outline
                  )),
                ),
              ),
            ),
          ),
          Expanded(
            child: GestureDetector(
              onTap: () {
                if (_isSendMode) {
                  _stopSendMode();
                  setState(() {
                    _isSendMode = false;
                    _status = 'Ready to scan';
                  });
                }
              },
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 200),
                padding: const EdgeInsets.symmetric(vertical: 12),
                decoration: BoxDecoration(
                  color: !_isSendMode ? Colors.white.withValues(alpha: 0.9) : Colors.transparent,
                  borderRadius: BorderRadius.circular(12),
                  boxShadow: !_isSendMode ? [
                    BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 4, offset: const Offset(0, 2))
                  ] : [],
                ),
                child: Center(
                  child: Text('Receive Data', style: AppTheme.lightTheme.textTheme.labelLarge?.copyWith(
                    color: !_isSendMode ? AppTheme.primary : AppTheme.outline
                  )),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MeshBackground(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          title: Text('P2P Sync', style: AppTheme.lightTheme.textTheme.headlineMedium),
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _buildGlassSegmentedControl(),
              const SizedBox(height: 24),
              
              if (_isSendMode) ...[
                GlassCard(
                  child: Column(
                    children: [
                      Stack(
                        alignment: Alignment.center,
                        children: [
                          AnimatedBuilder(
                            animation: _pulseController,
                            builder: (context, child) {
                              final scale = 0.95 + 0.13 * (_pulseController.value > 0.5 ? (1 - _pulseController.value) * 2 : _pulseController.value * 2);
                              final opacity = 0.3 + 0.5 * (_pulseController.value > 0.5 ? _pulseController.value * 2 - 1 : 1 - _pulseController.value * 2).abs();
                              return Transform.scale(
                                scale: scale,
                                child: Container(
                                  width: 80,
                                  height: 80,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: AppTheme.primary.withValues(alpha: opacity * 0.2),
                                  ),
                                ),
                              );
                            },
                          ),
                          Icon(Icons.wifi_tethering, size: 48, color: AppTheme.primary),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Text('Broadcast Database', style: AppTheme.lightTheme.textTheme.headlineSmall),
                      const SizedBox(height: 8),
                      Text('Other devices can scan your QR code to download your latest stock and LRs.', 
                        textAlign: TextAlign.center,
                        style: AppTheme.lightTheme.textTheme.bodyMedium?.copyWith(color: AppTheme.onSurfaceVariant)
                      ),
                      const SizedBox(height: 24),
                      if (_qrData.isEmpty)
                        ElevatedButton(
                          onPressed: _startSendMode,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: AppTheme.primary,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                          ),
                          child: Text('Generate QR Code', style: AppTheme.lightTheme.textTheme.labelLarge?.copyWith(color: Colors.white)),
                        )
                      else ...[
                        Stack(
                          alignment: Alignment.center,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(16),
                              margin: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: Colors.white,
                                borderRadius: BorderRadius.circular(16),
                                boxShadow: [
                                  BoxShadow(color: AppTheme.primary.withValues(alpha: 0.1), blurRadius: 20, spreadRadius: 5)
                                ],
                              ),
                              child: QrImageView(
                                data: _qrData,
                                version: QrVersions.auto,
                                size: 200.0,
                                backgroundColor: Colors.white,
                              ),
                            ),
                            // Reticle Accents
                            Positioned(top: 0, left: 0, child: _buildReticleCorner(AppTheme.primaryContainer, true, true)),
                            Positioned(top: 0, right: 0, child: _buildReticleCorner(AppTheme.primaryContainer, true, false)),
                            Positioned(bottom: 0, left: 0, child: _buildReticleCorner(AppTheme.primaryContainer, false, true)),
                            Positioned(bottom: 0, right: 0, child: _buildReticleCorner(AppTheme.primaryContainer, false, false)),
                          ],
                        ),
                        const SizedBox(height: 24),
                        TextButton.icon(
                          onPressed: _stopSendMode,
                          icon: const Icon(Icons.stop_circle_outlined, color: AppTheme.error),
                          label: Text('Stop Broadcast', style: TextStyle(color: AppTheme.error)),
                        )
                      ]
                    ],
                  ),
                ),
              ] else ...[
                GlassCard(
                  child: Column(
                    children: [
                      Icon(Icons.qr_code_scanner, size: 48, color: AppTheme.tertiary),
                      const SizedBox(height: 16),
                      Text('Receive Database', style: AppTheme.lightTheme.textTheme.headlineSmall),
                      const SizedBox(height: 8),
                      Text('Scan a QR code from another device to overwrite your local data with their database.', 
                        textAlign: TextAlign.center,
                        style: AppTheme.lightTheme.textTheme.bodyMedium?.copyWith(color: AppTheme.onSurfaceVariant)
                      ),
                      const SizedBox(height: 24),
                      ElevatedButton(
                        onPressed: _isReceiving ? null : _startReceiveMode,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppTheme.tertiary,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                        ),
                        child: _isReceiving
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                            : Text('Open Scanner', style: AppTheme.lightTheme.textTheme.labelLarge?.copyWith(color: Colors.white)),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 24),
              GlassCard(
                isSubCard: true,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Row(
                  children: [
                    Icon(Icons.info_outline, color: AppTheme.primary, size: 20),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(_status, style: AppTheme.lightTheme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500)),
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

  Widget _buildReticleCorner(Color color, bool isTop, bool isLeft) {
    return Container(
      width: 20,
      height: 20,
      decoration: BoxDecoration(
        border: Border(
          top: isTop ? BorderSide(color: color, width: 3) : BorderSide.none,
          bottom: !isTop ? BorderSide(color: color, width: 3) : BorderSide.none,
          left: isLeft ? BorderSide(color: color, width: 3) : BorderSide.none,
          right: !isLeft ? BorderSide(color: color, width: 3) : BorderSide.none,
        ),
        borderRadius: BorderRadius.only(
          topLeft: isTop && isLeft ? const Radius.circular(8) : Radius.zero,
          topRight: isTop && !isLeft ? const Radius.circular(8) : Radius.zero,
          bottomLeft: !isTop && isLeft ? const Radius.circular(8) : Radius.zero,
          bottomRight: !isTop && !isLeft ? const Radius.circular(8) : Radius.zero,
        ),
      ),
    );
  }
}

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
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          MobileScanner(
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
          Center(
            child: Container(
              width: 250,
              height: 250,
              decoration: BoxDecoration(
                border: Border.all(color: AppTheme.tertiaryContainer, width: 2),
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(color: AppTheme.tertiaryContainer.withValues(alpha: 0.5), blurRadius: 20, spreadRadius: 5),
                ],
              ),
            ),
          ),
          Positioned(
            top: 50,
            left: 20,
            child: IconButton(
              icon: const Icon(Icons.close, color: Colors.white, size: 30),
              onPressed: () => Navigator.pop(context, null),
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
