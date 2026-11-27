package com.example.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.example.data.model.DailyUsagePoint
import com.example.data.model.LockReason
import com.example.data.model.WatchMode
import com.example.data.model.WeeklyReportUiState
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TvRemoteClient(private val context: Context) {

    fun discoverTvsCombined(): Flow<List<DiscoveredTvDevice>> = callbackFlow {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        val discovered = linkedMapOf<String, DiscoveredTvDevice>()
        val mapMutex = Mutex()

        suspend fun addOrUpdateDevice(device: DiscoveredTvDevice) {
            mapMutex.withLock {
                discovered["${device.host}:${device.port}"] = device
                trySend(discovered.values.toList())
            }
        }

        // 1. NSD mDNS Listener
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains("_kidshield")) return
                try {
                    @Suppress("DEPRECATION")
                    manager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val device = DiscoveredTvDevice(
                                serviceName = info.serviceName,
                                host = host,
                                port = info.port,
                                deviceLabel = info.serviceName
                            )
                            launch { addOrUpdateDevice(device) }
                        }
                    })
                } catch (_: Exception) {
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                launch {
                    mapMutex.withLock {
                        discovered.entries.removeAll { it.value.serviceName == serviceInfo.serviceName }
                        trySend(discovered.values.toList())
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        if (manager != null) {
            runCatching {
                manager.discoverServices(
                    TvRemoteServer.SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener
                )
            }
        }

        // 2. UDP LAN Broadcast Discovery (fast <200ms even if mDNS is filtered)
        val udpJob = launch(Dispatchers.IO) {
            try {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    sock.soTimeout = 450
                    val magicBytes = TvRemoteServer.DISCOVERY_MAGIC.toByteArray(Charsets.UTF_8)
                    val targets = buildBroadcastTargets()

                    repeat(4) {
                        if (!isActive) return@repeat
                        targets.forEach { targetAddr ->
                            runCatching {
                                sock.send(
                                    DatagramPacket(
                                        magicBytes,
                                        magicBytes.size,
                                        targetAddr,
                                        TvRemoteServer.DISCOVERY_PORT
                                    )
                                )
                            }
                        }

                        val deadline = System.currentTimeMillis() + 450L
                        val recvBuf = ByteArray(512)
                        while (isActive && System.currentTimeMillis() < deadline) {
                            val pkt = DatagramPacket(recvBuf, recvBuf.size)
                            try {
                                sock.receive(pkt)
                                val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                                val json = JSONObject(text)
                                if (json.optString("app") == "KidShieldTV") {
                                    val host = pkt.address?.hostAddress ?: continue
                                    val port = json.optInt("port", TvRemoteServer.DEFAULT_PORT)
                                    val label = json.optString("deviceName", "Xiaomi Google TV")
                                    addOrUpdateDevice(
                                        DiscoveredTvDevice(
                                            serviceName = label,
                                            host = host,
                                            port = port,
                                            deviceLabel = label
                                        )
                                    )
                                }
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Fast Parallel /24 LAN Subnet Probe (guaranteed fallback if router blocks multicast/broadcast)
        val subnetScanJob = launch(Dispatchers.IO) {
            val localIp = TvRemoteServer.getLocalIpv4Address() ?: return@launch
            val prefix = localIp.substringBeforeLast('.', "")
            if (prefix.isEmpty()) return@launch
            val semaphore = Semaphore(48)
            coroutineScope {
                (1..254).map { hostOctet ->
                    async {
                        val candidateIp = "$prefix.$hostOctet"
                        if (candidateIp == localIp) return@async
                        semaphore.withPermit {
                            if (probePortOpen(candidateIp, TvRemoteServer.DEFAULT_PORT)) {
                                fetchStatus(candidateIp, TvRemoteServer.DEFAULT_PORT).onSuccess { status ->
                                    addOrUpdateDevice(
                                        DiscoveredTvDevice(
                                            serviceName = status.deviceName,
                                            host = candidateIp,
                                            port = TvRemoteServer.DEFAULT_PORT,
                                            deviceLabel = status.deviceName
                                        )
                                    )
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        awaitClose {
            udpJob.cancel()
            subnetScanJob.cancel()
            if (manager != null) {
                runCatching { manager.stopServiceDiscovery(discoveryListener) }
            }
        }
    }

    private fun probePortOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 320)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildBroadcastTargets(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        runCatching { list.add(InetAddress.getByName("255.255.255.255")) }
        val localIp = TvRemoteServer.getLocalIpv4Address()
        if (!localIp.isNullOrBlank() && localIp.count { it == '.' } == 3) {
            val subnetBroadcast = localIp.substringBeforeLast('.') + ".255"
            runCatching { list.add(InetAddress.getByName(subnetBroadcast)) }
        }
        return list.distinct()
    }

    fun getLocalSubnetPrefix(): String {
        val localIp = TvRemoteServer.getLocalIpv4Address() ?: return "192.168.1."
        val prefix = localIp.substringBeforeLast('.', "")
        return if (prefix.isNotEmpty()) "$prefix." else "192.168.1."
    }

    data class QrPairingPayload(
        val host: String,
        val port: Int,
        val code: String
    )

    fun parseQrPairingPayload(rawText: String): QrPairingPayload? {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("kidshield://pair")) {
            val uri = runCatching { android.net.Uri.parse(trimmed) }.getOrNull() ?: return null
            val host = uri.getQueryParameter("host").orEmpty().trim()
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: TvRemoteServer.DEFAULT_PORT
            val code = uri.getQueryParameter("code").orEmpty().trim()
            if (code.length == 6 && code.all { it.isDigit() }) {
                return QrPairingPayload(host = host, port = port, code = code)
            }
        }
        return null
    }

    suspend fun pairWithHostAndCode(host: String, port: Int, code: String): Result<RemoteTvStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("http://$host:$port/api/pair").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                try {
                    val payload = JSONObject().apply { put("code", code) }.toString()
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload)
                        writer.flush()
                    }
                    val respCode = conn.responseCode
                    val body = readResponseBody(conn, respCode >= 400)
                    val json = JSONObject(body)
                    if (respCode in 200..299 && json.optBoolean("ok", false)) {
                        parseStatusJson(json.getJSONObject("status"), host, port)
                    } else {
                        throw IllegalStateException(json.optString("error", "Mã ghép nối không đúng"))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun connectWithPairingCode(code: String): Result<RemoteTvStatus> = withContext(Dispatchers.IO) {
        val cleanCode = code.trim()
        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            return@withContext Result.failure(IllegalArgumentException("Mã ghép nối phải gồm đúng 6 chữ số"))
        }

        // 1. Fast UDP broadcast lookup by 6-digit pairing code
        try {
            DatagramSocket().use { sock ->
                sock.broadcast = true
                sock.soTimeout = 900
                val msgBytes = "${TvRemoteServer.PAIR_LOOKUP_PREFIX}$cleanCode".toByteArray(Charsets.UTF_8)
                val targets = buildBroadcastTargets()
                repeat(2) {
                    targets.forEach { addr ->
                        runCatching {
                            sock.send(DatagramPacket(msgBytes, msgBytes.size, addr, TvRemoteServer.DISCOVERY_PORT))
                        }
                    }
                    val recvBuf = ByteArray(512)
                    val pkt = DatagramPacket(recvBuf, recvBuf.size)
                    val gotReply = runCatching {
                        sock.receive(pkt)
                        true
                    }.getOrDefault(false)
                    if (gotReply) {
                        val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                        val json = runCatching { JSONObject(text) }.getOrNull()
                        if (json?.optString("app") == "KidShieldTV" && json.optBoolean("paired", false)) {
                            val host = pkt.address?.hostAddress
                            val port = json.optInt("port", TvRemoteServer.DEFAULT_PORT)
                            if (!host.isNullOrBlank()) {
                                val paired = pairWithHostAndCode(host, port, cleanCode)
                                if (paired.isSuccess) return@withContext paired
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Parallel /24 LAN fallback probing /api/pair directly
        val localIp = TvRemoteServer.getLocalIpv4Address()
        val prefix = localIp?.substringBeforeLast('.', "").orEmpty()
        if (prefix.isNotEmpty()) {
            val semaphore = Semaphore(48)
            var matchedStatus: RemoteTvStatus? = null
            coroutineScope {
                (1..254).map { octet ->
                    async {
                        if (matchedStatus != null) return@async
                        val candidateIp = "$prefix.$octet"
                        semaphore.withPermit {
                            if (matchedStatus == null && probePortOpen(candidateIp, TvRemoteServer.DEFAULT_PORT)) {
                                pairWithHostAndCode(candidateIp, TvRemoteServer.DEFAULT_PORT, cleanCode)
                                    .onSuccess { status -> matchedStatus = status }
                            }
                        }
                    }
                }.awaitAll()
            }
            matchedStatus?.let { return@withContext Result.success(it) }
        }

        Result.failure(IllegalStateException("Không tìm thấy Xiaomi TV có mã $cleanCode trên Wi-Fi hiện tại"))
    }

    suspend fun fetchStatus(host: String, port: Int): Result<RemoteTvStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("http://$host:$port/api/status").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2500
                readTimeout = 2500
            }
            try {
                val code = conn.responseCode
                val body = readResponseBody(conn, code >= 400)
                if (code !in 200..299) {
                    throw IllegalStateException("TV trả về lỗi HTTP $code")
                }
                parseStatusJson(JSONObject(body), host, port)
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun fetchWeeklyReport(host: String, port: Int, pin: String): Result<WeeklyReportUiState> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("http://$host:$port/api/report").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("X-KidShield-Pin", pin)
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                try {
                    val code = conn.responseCode
                    val body = readResponseBody(conn, code >= 400)
                    val json = JSONObject(body)
                    if (code !in 200..299) {
                        throw IllegalStateException(json.optString("error", "Không đọc được báo cáo từ TV"))
                    }
                    val daysArr = json.optJSONArray("days")
                    val days = buildList {
                        if (daysArr != null) {
                            for (i in 0 until daysArr.length()) {
                                val d = daysArr.getJSONObject(i)
                                add(
                                    DailyUsagePoint(
                                        dateKey = d.optString("dateKey"),
                                        dayLabel = d.optString("dayLabel"),
                                        fullDateLabel = d.optString("fullDateLabel"),
                                        kidsMinutes = d.optInt("kidsMinutes"),
                                        familyMinutes = d.optInt("familyMinutes"),
                                        blockEventsCount = d.optInt("blockEventsCount"),
                                        sessions = emptyList()
                                    )
                                )
                            }
                        }
                    }
                    WeeklyReportUiState(
                        days = days,
                        totalKidsSeconds = json.optInt("totalKidsSeconds"),
                        totalFamilySeconds = json.optInt("totalFamilySeconds"),
                        totalBlockEvents = json.optInt("totalBlockEvents"),
                        avgDailyKidsMinutes = json.optInt("avgDailyKidsMinutes"),
                        recentSessions = emptyList()
                    )
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun sendCommand(
        host: String,
        port: Int,
        pin: String,
        action: RemoteCommandAction
    ): RemoteCommandResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL("http://$host:$port/api/command").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 3000
                readTimeout = 3000
            }
            try {
                val payload = JSONObject().apply {
                    put("pin", pin)
                    put("action", action.name)
                }.toString()
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload)
                    writer.flush()
                }
                val code = conn.responseCode
                val body = readResponseBody(conn, code >= 400)
                val json = JSONObject(body)
                if (code in 200..299 && json.optBoolean("ok", false)) {
                    val statusObj = json.optJSONObject("status")
                    val updated = statusObj?.let { parseStatusJson(it, host, port) }
                    RemoteCommandResult.Success(
                        message = json.optString("message", "Đã thực hiện trên TV"),
                        updatedStatus = updated
                    )
                } else {
                    RemoteCommandResult.Failure(json.optString("error", "Không thể điều khiển TV"))
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            RemoteCommandResult.Failure("Không kết nối được tới TV ($host:$port): ${e.localizedMessage ?: "Kiểm tra cùng Wi-Fi"}")
        }
    }

    private fun parseStatusJson(json: JSONObject, host: String, port: Int): RemoteTvStatus {
        return RemoteTvStatus(
            deviceName = json.optString("deviceName", "Xiaomi Google TV"),
            host = host,
            port = port,
            activeMode = runCatching {
                WatchMode.valueOf(json.optString("activeMode", WatchMode.KIDS.name))
            }.getOrDefault(WatchMode.KIDS),
            isPinConfigured = json.optBoolean("isPinConfigured", true),
            kidsRemainingSeconds = json.optInt("kidsRemainingSeconds", 0),
            effectiveRemainingSeconds = json.optInt("effectiveRemainingSeconds", 0),
            isKidsLockedOut = json.optBoolean("isKidsLockedOut", false),
            lockReason = runCatching {
                LockReason.valueOf(json.optString("lockReason", LockReason.NONE.name))
            }.getOrDefault(LockReason.NONE),
            isTimerPaused = json.optBoolean("isTimerPaused", false),
            isNetworkBlocked = json.optBoolean("isNetworkBlocked", false),
            currentClockFormatted = json.optString("currentClockFormatted", "--:--"),
            secondsUntilCurfew = json.optInt("secondsUntilCurfew", 0),
            weeklyKidsMinutes = json.optInt("weeklyKidsMinutes", 0),
            weeklyFamilyMinutes = json.optInt("weeklyFamilyMinutes", 0),
            weeklyBlockEvents = json.optInt("weeklyBlockEvents", 0),
            isDeviceAdminActive = json.optBoolean("isDeviceAdminActive", false),
            isTvStandbyBlackout = json.optBoolean("isTvStandbyBlackout", false)
        )
    }

    private fun readResponseBody(conn: HttpURLConnection, isError: Boolean): String {
        val stream = if (isError) conn.errorStream ?: conn.inputStream else conn.inputStream
        if (stream == null) return "{}"
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }
}
