package com.michael.wifidrop.core.network

import java.net.NetworkInterface
import java.net.Inet4Address

object NetworkUtils {
    fun getActiveLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (i in interfaces) {
                if (i.isLoopback || !i.isUp) continue
                val addresses = i.inetAddresses.toList()
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
            // Fallback to active non-loopback IPv4
            for (i in interfaces) {
                if (i.isLoopback || !i.isUp) continue
                val addresses = i.inetAddresses.toList()
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
