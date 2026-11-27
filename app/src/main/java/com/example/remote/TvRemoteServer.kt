package com.example.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.example.data.model.PinVerifyResult
import com.example.data.repository.WatchGuardRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class TvRemoteServer(
    private val context: Context,
    private val repository: WatchGuardRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private var acceptJob: Job? = null
    private var udpJob: Job? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile
    var listeningPort: Int = DEFAULT_PORT
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        acceptJob = scope.launch {
            try {
                val socket = runCatching { ServerSocket(DEFAULT_PORT) }.getOrElse { ServerSocket(0) }
                serverSocket = socket
                listeningPort = socket.localPort
                registerNsdService(listeningPort)
                startUdpDiscoveryResponder(listeningPort)

                while (isActive && !socket.isClosed) {
                    val client = socket.accept()
                    launch { handleClient(client) }
                }
            } catch (_: Exception) {
                // Socket closed cleanly on stop
            } finally {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        unregisterNsdService()
        acceptJob?.cancel()
        acceptJob = null
        udpJob?.cancel()
        udpJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        runCatching { udpSocket?.close() }
        udpSocket = null
    }

    private fun startUdpDiscoveryResponder(httpPort: Int) {
        udpJob?.cancel()
        udpJob = scope.launch {
            try {
                val sock = DatagramSocket(DISCOVERY_PORT).apply {
                    reuseAddress = true
                    broadcast = true
                }
                udpSocket = sock
                val buffer = ByteArray(256)
                while (isActive && !sock.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    sock.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    val isDiscover = msg == DISCOVERY_MAGIC
                    val isPairLookup = msg.startsWith(PAIR_LOOKUP_PREFIX) &&
                        msg.removePrefix(PAIR_LOOKUP_PREFIX) == repository.getPairingCode()

                    if (isDiscover || isPairLookup) {
                        val replyPayload = JSONObject().apply {
                            put("app", "KidShieldTV")
                            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                            put("port", httpPort)
                            put("mode", repository.guardState.value.activeMode.name)
                            if (isPairLookup) {
                                put("paired", true)
                            }
                        }.toString().toByteArray(Charsets.UTF_8)
                        val replyPacket = DatagramPacket(
                            replyPayload,
                            replyPayload.size,
                            packet.address,
                            packet.port
                        )
                        runCatching { sock.send(replyPacket) }
                    }
                }
            } catch (_: Exception) {
                // Closed on stop
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val writer = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val path = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val colon = line!!.indexOf(':')
                if (colon > 0) {
                    val key = line!!.substring(0, colon).trim().lowercase()
                    val value = line!!.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
            val bodyStr = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(buf, readTotal, contentLength - readTotal)
                    if (r <= 0) break
                    readTotal += r
                }
                String(buf, 0, readTotal)
            } else {
                ""
            }

            val (statusCode, jsonResponse) = when {
                method == "GET" && path == "/api/status" -> {
                    200 to buildStatusJson()
                }
                method == "GET" && path == "/api/report" -> {
                    val pin = headers["x-kidshield-pin"].orEmpty()
                    when (val verify = repository.verifyPin(pin)) {
                        is PinVerifyResult.Success -> 200 to buildReportJson()
                        is PinVerifyResult.Cooldown -> 429 to errorJson("Đang tạm khóa ${verify.secondsRemaining}s do nhập sai nhiều lần")
                        PinVerifyResult.NotConfigured -> 400 to errorJson("TV chưa thiết lập mã PIN 8 số")
                        is PinVerifyResult.WrongPin -> 401 to errorJson("Sai mã PIN 8 số (còn ${verify.remainingAttemptsBeforeCooldown} lần)")
                    }
                }
                method == "POST" && path == "/api/pair" -> {
                    handlePair(bodyStr)
                }
                method == "POST" && path == "/api/command" -> {
                    handleCommand(bodyStr)
                }
                else -> 404 to errorJson("Không tìm thấy endpoint")
            }

            val payload = jsonResponse.toString()
            val statusText = when (statusCode) {
                200 -> "OK"
                400 -> "Bad Request"
                401 -> "Unauthorized"
                429 -> "Too Many Requests"
                else -> "Not Found"
            }
            writer.write("HTTP/1.1 $statusCode $statusText\r\n")
            writer.write("Content-Type: application/json; charset=utf-8\r\n")
            writer.write("Content-Length: ${payload.toByteArray(Charsets.UTF_8).size}\r\n")
            writer.write("Connection: close\r\n\r\n")
            writer.write(payload)
            writer.flush()
        }
    }

    private fun handlePair(bodyStr: String): Pair<Int, JSONObject> {
        val json = runCatching { JSONObject(bodyStr) }.getOrNull()
            ?: return 400 to errorJson("Dữ liệu ghép nối không hợp lệ")
        val code = json.optString("code", "").trim()
        if (code.length != 6 || code != repository.getPairingCode()) {
            return 401 to errorJson("Mã ghép nối 6 số không đúng")
        }
        return 200 to JSONObject().apply {
            put("ok", true)
            put("status", buildStatusJson())
        }
    }

    private suspend fun handleCommand(bodyStr: String): Pair<Int, JSONObject> {
        val json = runCatching { JSONObject(bodyStr) }.getOrNull()
            ?: return 400 to errorJson("Dữ liệu yêu cầu không hợp lệ")

        val pin = json.optString("pin", "")
        val actionName = json.optString("action", "")
        val action = runCatching { RemoteCommandAction.valueOf(actionName) }.getOrNull()
            ?: return 400 to errorJson("Lệnh điều khiển không hợp lệ")

        if (action != RemoteCommandAction.POWER_OFF_TV) {
            when (val verify = repository.verifyPin(pin)) {
                is PinVerifyResult.Success -> Unit
                is PinVerifyResult.Cooldown -> return 429 to errorJson("Đang tạm khóa ${verify.secondsRemaining}s do nhập sai PIN")
                PinVerifyResult.NotConfigured -> return 400 to errorJson("TV chưa thiết lập mã PIN 8 số")
                is PinVerifyResult.WrongPin -> return 401 to errorJson("Sai mã PIN 8 số (còn ${verify.remainingAttemptsBeforeCooldown} lần)")
            }
        }

        val message = when (action) {
            RemoteCommandAction.SWITCH_FAMILY -> {
                repository.switchToFamilyMode()
                "TV đã chuyển sang Chế độ Gia đình"
            }
            RemoteCommandAction.SWITCH_KIDS -> {
                repository.switchToKidsMode()
                "TV đã chuyển sang Chế độ Trẻ em"
            }
            RemoteCommandAction.GRANT_1H -> {
                repository.grantNewKidsOneHourSession()
                "Đã cấp thêm 1 giờ xem cho Trẻ em trên TV"
            }
            RemoteCommandAction.TOGGLE_PAUSE -> {
                repository.toggleKidsTimerPaused()
                "Đã cập nhật trạng thái tạm dừng trên TV"
            }
            RemoteCommandAction.POWER_OFF_TV -> {
                val hwSleep = repository.turnOffTvRemotely()
                if (hwSleep) {
                    "Đã tắt màn hình TV (chế độ ngủ), dừng âm thanh và ngắt mạng"
                } else {
                    "Đã ngắt mạng, dừng âm thanh và đưa TV vào màn hình tắt"
                }
            }
            RemoteCommandAction.WAKE_TV -> {
                repository.wakeTvFromStandby()
                "Đã mở lại màn hình TV khỏi trạng thái tắt"
            }
        }

        return 200 to JSONObject().apply {
            put("ok", true)
            put("message", message)
            put("status", buildStatusJson())
        }
    }

    private fun buildStatusJson(): JSONObject {
        val state = repository.guardState.value
        val report = repository.getWeeklyReportSnapshot()
        return JSONObject().apply {
            put("app", "KidShieldTV")
            put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("activeMode", state.activeMode.name)
            put("isPinConfigured", state.isPinConfigured)
            put("kidsRemainingSeconds", state.kidsRemainingSeconds)
            put("effectiveRemainingSeconds", state.effectiveRemainingSeconds)
            put("isKidsLockedOut", state.isKidsLockedOut)
            put("lockReason", state.lockReason.name)
            put("isTimerPaused", state.isTimerPaused)
            put("isNetworkBlocked", state.isNetworkBlocked)
            put("currentClockFormatted", state.currentClockFormatted)
            put("secondsUntilCurfew", state.secondsUntilCurfew)
            put("weeklyKidsMinutes", report.totalKidsSeconds / 60)
            put("weeklyFamilyMinutes", report.totalFamilySeconds / 60)
            put("weeklyBlockEvents", report.totalBlockEvents)
            put("isDeviceAdminActive", state.isDeviceAdminActive)
            put("isTvStandbyBlackout", state.isTvStandbyBlackout)
        }
    }

    private fun buildReportJson(): JSONObject {
        val report = repository.getWeeklyReportSnapshot()
        val daysArr = JSONArray()
        report.days.forEach { d ->
            daysArr.put(
                JSONObject().apply {
                    put("dateKey", d.dateKey)
                    put("dayLabel", d.dayLabel)
                    put("fullDateLabel", d.fullDateLabel)
                    put("kidsMinutes", d.kidsMinutes)
                    put("familyMinutes", d.familyMinutes)
                    put("blockEventsCount", d.blockEventsCount)
                }
            )
        }
        return JSONObject().apply {
            put("totalKidsSeconds", report.totalKidsSeconds)
            put("totalFamilySeconds", report.totalFamilySeconds)
            put("totalBlockEvents", report.totalBlockEvents)
            put("avgDailyKidsMinutes", report.avgDailyKidsMinutes)
            put("days", daysArr)
        }
    }

    private fun errorJson(msg: String): JSONObject = JSONObject().apply {
        put("ok", false)
        put("error", msg)
    }

    private fun registerNsdService(port: Int) {
        try {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "KidShieldTV-${Build.MODEL.take(18)}"
                serviceType = SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) = Unit
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            }
            registrationListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
        }
    }

    private fun unregisterNsdService() {
        try {
            registrationListener?.let { listener ->
                nsdManager?.unregisterService(listener)
            }
        } catch (_: Exception) {
        } finally {
            registrationListener = null
        }
    }

    companion object {
        const val DEFAULT_PORT = 8745
        const val DISCOVERY_PORT = 8746
        const val DISCOVERY_MAGIC = "KIDSHIELD_DISCOVER"
        const val PAIR_LOOKUP_PREFIX = "KIDSHIELD_PAIR_LOOKUP|"
        const val SERVICE_TYPE = "_kidshield._tcp."

        fun getLocalIpv4Address(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { addr ->
                        !addr.isLoopbackAddress && addr is Inet4Address
                    }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
