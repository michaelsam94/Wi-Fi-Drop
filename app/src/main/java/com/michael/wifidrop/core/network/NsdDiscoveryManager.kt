package com.michael.wifidrop.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.michael.wifidrop.core.common.DispatcherProvider
import com.michael.wifidrop.core.domain.DiscoveryRepository
import com.michael.wifidrop.core.domain.DiscoveredDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.util.UUID

class NsdDiscoveryManager(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) : DiscoveryRepository {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _discoveredDevicesList = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var activeKtorPort: Int = 8080

    fun setServerPort(port: Int) {
        activeKtorPort = port
    }

    override fun observeNearbyDevices(): Flow<List<DiscoveredDevice>> = callbackFlow {
        _discoveredDevicesList.value = emptyList()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Ignore
            }

            override fun onDiscoveryStarted(serviceType: String) {
                // Started successfully
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _discoveredDevicesList.value = emptyList()
                trySend(emptyList())
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_wifidrop")) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            // ignore resolution errors
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val hostAddress = resolvedInfo.host?.hostAddress ?: return
                            val resolvedPort = resolvedInfo.port
                            val deviceName = resolvedInfo.serviceName ?: "Unknown Device"
                            val id = resolvedInfo.serviceName ?: UUID.randomUUID().toString()

                            val device = DiscoveredDevice(
                                id = id,
                                displayName = deviceName,
                                localIp = hostAddress,
                                port = resolvedPort,
                                sharePort = resolvedPort,
                                supportsClient = true
                            )

                            _discoveredDevicesList.update { list ->
                                if (list.any { it.id == device.id }) {
                                    list.map { if (it.id == device.id) device else it }
                                } else {
                                    list + device
                                }
                            }
                            trySend(_discoveredDevicesList.value)
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName
                _discoveredDevicesList.update { list ->
                    list.filterNot { it.id == serviceName }
                }
                trySend(_discoveredDevicesList.value)
            }
        }

        try {
            nsdManager.discoverServices("_wifidrop._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }.flowOn(dispatchers.io)

    override suspend fun startAdvertising(deviceName: String): Result<Unit> {
        stopAdvertising()

        return runCatching {
            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = deviceName
                this.serviceType = "_wifidrop._tcp"
                port = activeKtorPort
            }

            val listener = object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    registrationListener = null
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    // Ignore
                }

                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    // Registered successfully
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    registrationListener = null
                }
            }

            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            registrationListener = listener
        }
    }

    override suspend fun stopAdvertising(): Result<Unit> {
        return runCatching {
            registrationListener?.let {
                try {
                    nsdManager.unregisterService(it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                registrationListener = null
            }
        }
    }
}
