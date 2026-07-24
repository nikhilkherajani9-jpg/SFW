package com.sfw.wholesale.util;

import java.net.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Utility to detect local IPv4 addresses suitable for the QR Sync hotspot server.
 * Filters out loopback (127.x), link-local (169.254.x), and IPv6 addresses.
 *
 * The user must have already created a Windows Mobile Hotspot manually via
 * Windows Settings before calling this.
 */
public class NetworkUtil {

    private static final Logger LOG = Logger.getLogger(NetworkUtil.class.getName());

    private NetworkUtil() {}

    /**
     * Returns all non-loopback, non-link-local IPv4 addresses found on this machine.
     * On a machine with an active Windows hotspot, the hotspot adapter will appear here.
     *
     * @return list of InetAddress objects (may be empty if no suitable adapter found)
     */
    public static List<InetAddress> getLocalAddresses() {
        List<InetAddress> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return result;

            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        result.add(addr);
                        LOG.fine("Found candidate address: " + addr.getHostAddress()
                                + " on " + iface.getDisplayName());
                    }
                }
            }
        } catch (SocketException e) {
            LOG.warning("Error enumerating network interfaces: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns a list of human-readable strings for display in a ChoiceDialog.
     * Format: "192.168.137.1 (Microsoft Wi-Fi Direct Virtual Adapter)"
     */
    public static List<String> getAddressDisplayList() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return result;

            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        result.add(addr.getHostAddress() + "  (" + iface.getDisplayName() + ")");
                    }
                }
            }
        } catch (SocketException e) {
            LOG.warning("Error enumerating network interfaces: " + e.getMessage());
        }
        return result;
    }

    /**
     * Extracts just the IP portion from an address display string.
     * e.g. "192.168.137.1  (Microsoft ...)" → "192.168.137.1"
     */
    public static String extractIp(String displayString) {
        if (displayString == null) return null;
        return displayString.split("\\s")[0].trim();
    }
}
