package com.michael.wifidrop.core.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import com.michael.wifidrop.core.common.DispatcherProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class WifiDirectManager(
    private val context: Context,
    private val dispatchers: DispatcherProvider
) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    suspend fun discoverPeers(): Result<List<WifiP2pDevice>> = withContext(dispatchers.io) {
        val mgr = manager ?: return@withContext Result.failure(IllegalStateException("Wi-Fi Direct not supported on this device"))
        val ch = channel ?: return@withContext Result.failure(IllegalStateException("Wi-Fi Direct channel not initialized"))

        suspendCancellableCoroutine { cont ->
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (cont.isActive) cont.resume(Result.success(emptyList()))
                }
                override fun onFailure(reason: Int) {
                    if (cont.isActive) cont.resume(Result.failure(Exception("Wi-Fi Direct peer discovery failed with reason code: $reason")))
                }
            })
            cont.invokeOnCancellation {
                try {
                    mgr.stopPeerDiscovery(ch, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun observePeers(): Flow<List<WifiP2pDevice>> = callbackFlow {
        val mgr = this@WifiDirectManager.manager ?: return@callbackFlow
        val ch = this@WifiDirectManager.channel ?: return@callbackFlow

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                    try {
                        mgr.requestPeers(ch) { peers ->
                            trySend(peers.deviceList.toList())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        val filter = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }.flowOn(dispatchers.io)
}
