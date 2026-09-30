package com.dashwroom.f1telemetry.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

enum class WifiBand(val label: String) { GHZ_2_4("2.4 GHz"), GHZ_5("5 GHz"), GHZ_6("6 GHz") }

data class LocalAddress(val address: String, val interfaceName: String)

data class NetworkInfo(
    /** The address to type into the game: the Wi-Fi (or hotspot) IPv4 of this phone. */
    val primaryAddress: String? = null,
    val otherAddresses: List<LocalAddress> = emptyList(),
    val onWifi: Boolean = false,
    val frequencyMhz: Int? = null,
) {
    val band: WifiBand? = frequencyMhz?.let {
        when (it) {
            in 2400..2500 -> WifiBand.GHZ_2_4
            in 4900..5900 -> WifiBand.GHZ_5
            in 5925..7125 -> WifiBand.GHZ_6
            else -> null
        }
    }
}

/** Observes the phone's LAN address and Wi-Fi band so the Connect screen can guide setup. */
@Singleton
class NetworkMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val info: Flow<NetworkInfo> = callbackFlow {
        fun publish(network: Network?) {
            trySend(snapshot(network))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish(network)
            override fun onLost(network: Network) = publish(null)
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish(network)
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = publish(network)
        }
        publish(connectivity.activeNetwork)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun snapshot(network: Network?): NetworkInfo {
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val linkAddress = network?.let { connectivity.getLinkProperties(it) }
            ?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
        val all = interfaceAddresses()
        // When the phone is a hotspot for the console, the useful address is on the AP interface.
        val primary = if (onWifi) linkAddress else all.firstOrNull { it.interfaceName.contains("ap") || it.interfaceName.startsWith("wlan") }?.address ?: linkAddress
        return NetworkInfo(
            primaryAddress = primary,
            otherAddresses = all.filter { it.address != primary },
            onWifi = onWifi,
            frequencyMhz = if (onWifi) frequency(caps) else null,
        )
    }

    @Suppress("DEPRECATION")
    private fun frequency(caps: NetworkCapabilities?): Int? {
        val mhz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (caps?.transportInfo as? WifiInfo)?.frequency
        } else {
            context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.frequency
        }
        return mhz?.takeIf { it > 0 }
    }

    private fun interfaceAddresses(): List<LocalAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { it.isSiteLocalAddress }
                    .map { LocalAddress(it.hostAddress.orEmpty(), nif.name) }
            }
    }.getOrDefault(emptyList())
}
