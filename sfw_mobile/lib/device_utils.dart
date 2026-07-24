import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

class DeviceUtils {
  static const String _deviceIdKey = 'device_id';
  static const String _deviceNameKey = 'device_name';

  /// Get or create a unique device ID
  static Future<String> getDeviceId() async {
    final prefs = await SharedPreferences.getInstance();
    String? deviceId = prefs.getString(_deviceIdKey);
    
    if (deviceId == null || deviceId.isEmpty) {
      deviceId = const Uuid().v4();
      await prefs.setString(_deviceIdKey, deviceId);
    }
    
    return deviceId;
  }

  /// Get or set device name
  static Future<String> getDeviceName() async {
    final prefs = await SharedPreferences.getInstance();
    String? deviceName = prefs.getString(_deviceNameKey);
    
    if (deviceName == null || deviceName.isEmpty) {
      deviceName = 'SFW Mobile Device';
      await prefs.setString(_deviceNameKey, deviceName);
    }
    
    return deviceName;
  }

  /// Set custom device name
  static Future<void> setDeviceName(String name) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_deviceNameKey, name);
  }

  /// Get first 8 characters of device ID for short reference
  static Future<String> getDeviceShortId() async {
    final id = await getDeviceId();
    return id.substring(0, 8).toUpperCase();
  }
}
