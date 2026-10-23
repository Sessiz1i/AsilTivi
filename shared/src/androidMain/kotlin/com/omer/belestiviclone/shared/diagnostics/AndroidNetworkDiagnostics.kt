package com.omer.belestiviclone.shared.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

object AndroidNetworkDiagnostics {

    suspend fun runFullDiagnostics(
        context: Context,
        testStreamUrl: String = "https://tv-trt1.medya.trt.com.tr/master.m3u8",
        onProgress: (DiagnosticsReport) -> Unit
    ): DiagnosticsReport = withContext(Dispatchers.IO) {
        var current = DiagnosticsReport(inProgress = true)
        onProgress(current)

        // Step 1: Network & Local IP
        val netState = checkNetworkState(context)
        current = current.copy(network = netState)
        onProgress(current)

        if (!netState.isConnected) {
            val failed = current.copy(
                dns = DnsState(isSuccess = false, error = "Ağ bağlantısı yok"),
                stream = StreamServerState(isSuccess = false, error = "Ağ bağlantısı yok"),
                inProgress = false
            )
            onProgress(failed)
            return@withContext failed
        }

        // Step 2: DNS Resolution
        val dnsState = checkDnsResolution("google.com")
        current = current.copy(dns = dnsState)
        onProgress(current)

        // Step 3: Stream Server Probe
        val streamState = checkStreamServer(testStreamUrl)
        val finalReport = current.copy(stream = streamState, inProgress = false)
        onProgress(finalReport)

        finalReport
    }

    fun checkNetworkState(context: Context): NetworkState {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkState(typeName = "Bağlantı Yok", isConnected = false, details = "Ağ yöneticisi alınamadı")

            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            val typeName = when {
                caps == null -> "Bağlantı Yok"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobil Ağ"
                else -> "Ağ Bağlantısı"
            }

            val localIp = findLocalIp() ?: "0.0.0.0"
            val details = if (isConnected) "$typeName · IP: $localIp" else "İnternet erişimi yok"

            NetworkState(typeName = typeName, localIp = localIp, isConnected = isConnected, details = details)
        } catch (e: Exception) {
            NetworkState(typeName = "Hata", isConnected = false, details = e.message ?: "Ağ kontrol edilemedi")
        }
    }

    fun checkDnsResolution(host: String): DnsState {
        val start = System.currentTimeMillis()
        return try {
            val address = InetAddress.getByName(host)
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1)
            DnsState(
                host = host,
                resolvedIp = address.hostAddress,
                latencyMs = latency,
                isSuccess = true
            )
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1)
            DnsState(
                host = host,
                latencyMs = latency,
                isSuccess = false,
                error = e.localizedMessage ?: "DNS sorgusu zaman aşımına uğradı"
            )
        }
    }

    fun checkStreamServer(urlStr: String): StreamServerState {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            val targetUrl = if (urlStr.isBlank()) "https://tv-trt1.medya.trt.com.tr/master.m3u8" else urlStr
            val url = URL(targetUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BelesTivi/4.1 (Linux; Android TV)")
            }

            val code = conn.responseCode
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1)
            val isSuccess = code in 200..399

            StreamServerState(
                url = targetUrl,
                httpCode = code,
                latencyMs = latency,
                isSuccess = isSuccess,
                error = if (!isSuccess) "HTTP $code" else null
            )
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1)
            StreamServerState(
                url = urlStr,
                latencyMs = latency,
                isSuccess = false,
                error = e.localizedMessage ?: "Sunucuya bağlanılamadı"
            )
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun findLocalIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces.asSequence()) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses.asSequence()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
