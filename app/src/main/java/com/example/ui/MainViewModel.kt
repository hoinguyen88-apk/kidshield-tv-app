package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.KidShieldApp
import com.example.R
import com.example.data.model.GuardState
import com.example.data.model.PinVerifyResult
import com.example.data.model.WeeklyReportUiState
import com.example.data.security.PinCryptoHelper
import com.example.remote.DiscoveredTvDevice
import com.example.remote.RemoteCommandAction
import com.example.remote.RemoteCommandResult
import com.example.remote.RemoteTvStatus
import com.example.remote.TvRemoteClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class PinAction {
    SETUP_INITIAL,
    VERIFY_TO_FAMILY,
    VERIFY_GRANT_HOUR,
    VERIFY_OPEN_REPORTS,
    VERIFY_OPEN_SETTINGS,
    VERIFY_CHANGE_PIN,
    VERIFY_WAKE_LOCAL_TV,
    REMOTE_SWITCH_FAMILY,
    REMOTE_SWITCH_KIDS,
    REMOTE_GRANT_1H,
    REMOTE_TOGGLE_PAUSE,
    REMOTE_FETCH_REPORT,
    REMOTE_WAKE_TV
}

enum class PinStep {
    ENTER_CURRENT,
    SETUP_NEW,
    CONFIRM_NEW
}

data class PinModalState(
    val isVisible: Boolean = false,
    val action: PinAction = PinAction.VERIFY_TO_FAMILY,
    val step: PinStep = PinStep.ENTER_CURRENT,
    val enteredInput: String = "",
    val pendingNewPin: String = "",
    val maskedDigestPreview: String = "AES/SHA256 • CHỜ NHẬP",
    val shuffledDigits: List<Int> = (0..9).toList(),
    val errorMessage: String? = null,
    val sessionNonce: String = "",
    val isRemoteVerification: Boolean = false
)

data class PhoneRemoteUiState(
    val discoveredTvs: List<DiscoveredTvDevice> = emptyList(),
    val isScanning: Boolean = false,
    val manualHost: String = "",
    val manualPort: String = "8745",
    val pairingCodeInput: String = "",
    val connectedHost: String? = null,
    val connectedPort: Int = 8745,
    val remoteStatus: RemoteTvStatus? = null,
    val remoteWeeklyReport: WeeklyReportUiState? = null,
    val isConnecting: Boolean = false,
    val connectionError: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as KidShieldApp
    private val repository = app.repository
    private val remoteClient = TvRemoteClient(application)

    val guardState: StateFlow<GuardState> = repository.guardState

    val weeklyReport: StateFlow<WeeklyReportUiState> = repository.observeWeeklyReport()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = WeeklyReportUiState()
        )

    private val _pinModalState = MutableStateFlow(PinModalState())
    val pinModalState: StateFlow<PinModalState> = _pinModalState.asStateFlow()

    private val _selectedDayIndex = MutableStateFlow(6)
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _phoneRemoteState = MutableStateFlow(
        PhoneRemoteUiState(
            manualHost = repository.guardState.value.lastRemoteTvHost.ifBlank {
                remoteClient.getLocalSubnetPrefix()
            },
            manualPort = repository.guardState.value.lastRemoteTvPort.toString()
        )
    )
    val phoneRemoteState: StateFlow<PhoneRemoteUiState> = _phoneRemoteState.asStateFlow()

    private var discoveryJob: Job? = null
    private var pollingJob: Job? = null
    private var autoConnectSuppressed: Boolean = false

    fun selectReportDay(index: Int) {
        _selectedDayIndex.value = index.coerceIn(0, 6)
    }

    fun clearStatusBanner() {
        _statusBannerMessage.value = null
    }

    fun showBannerMessage(message: String) {
        _statusBannerMessage.value = message
    }

    fun refreshVpnPermission() {
        repository.refreshVpnPermissionState()
    }

    fun refreshDeviceAdmin() {
        repository.refreshDeviceAdminState()
    }

    fun triggerImmediateTick() {
        viewModelScope.launch {
            repository.onTick()
        }
    }

    fun startLanTvDiscovery(forceAutoConnect: Boolean = false) {
        if (forceAutoConnect) {
            autoConnectSuppressed = false
        }
        discoveryJob?.cancel()
        val savedHost = repository.guardState.value.lastRemoteTvHost
        val savedPort = repository.guardState.value.lastRemoteTvPort
        if (_phoneRemoteState.value.manualHost.isBlank()) {
            _phoneRemoteState.update { it.copy(manualHost = remoteClient.getLocalSubnetPrefix()) }
        }
        _phoneRemoteState.update { it.copy(isScanning = true) }

        // Silent auto-reconnect to last saved TV
        if (!autoConnectSuppressed &&
            savedHost.isNotBlank() &&
            _phoneRemoteState.value.connectedHost == null &&
            !_phoneRemoteState.value.isConnecting
        ) {
            connectToTv(savedHost, savedPort, silentOnFailure = true)
        }

        discoveryJob = viewModelScope.launch {
            launch {
                delay(2500L)
                _phoneRemoteState.update { it.copy(isScanning = false) }
            }
            remoteClient.discoverTvsCombined().collect { devices ->
                _phoneRemoteState.update { it.copy(discoveredTvs = devices) }
                if (!autoConnectSuppressed &&
                    devices.size == 1 &&
                    _phoneRemoteState.value.connectedHost == null &&
                    !_phoneRemoteState.value.isConnecting
                ) {
                    val onlyTv = devices.first()
                    connectToTv(onlyTv.host, onlyTv.port, silentOnFailure = true)
                }
            }
        }
    }

    fun stopLanTvDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _phoneRemoteState.update { it.copy(isScanning = false) }
    }

    fun updateManualHost(host: String) {
        _phoneRemoteState.update { it.copy(manualHost = host, connectionError = null) }
    }

    fun updateManualPort(port: String) {
        val filtered = port.filter { it.isDigit() }.take(5)
        _phoneRemoteState.update { it.copy(manualPort = filtered, connectionError = null) }
    }

    fun updatePairingCodeInput(code: String) {
        val filtered = code.filter { it.isDigit() }.take(6)
        _phoneRemoteState.update { it.copy(pairingCodeInput = filtered, connectionError = null) }
    }

    fun setRemoteConnectionError(message: String) {
        _phoneRemoteState.update { it.copy(connectionError = message) }
    }

    fun connectWithPairingCode(code: String = _phoneRemoteState.value.pairingCodeInput) {
        val cleanCode = code.filter { it.isDigit() }.take(6)
        if (cleanCode.length != 6) {
            _phoneRemoteState.update { it.copy(connectionError = "Vui lòng nhập đủ 6 chữ số ghép nối trên màn hình TV") }
            return
        }
        autoConnectSuppressed = false
        viewModelScope.launch {
            _phoneRemoteState.update { it.copy(isConnecting = true, connectionError = null) }
            val result = remoteClient.connectWithPairingCode(cleanCode)
            result.onSuccess { status ->
                repository.saveLastRemoteTv(status.host, status.port)
                _phoneRemoteState.update {
                    it.copy(
                        isConnecting = false,
                        connectedHost = status.host,
                        connectedPort = status.port,
                        manualHost = status.host,
                        manualPort = status.port.toString(),
                        pairingCodeInput = cleanCode,
                        remoteStatus = status,
                        connectionError = null
                    )
                }
                _statusBannerMessage.value = "Đã ghép nối thành công với ${status.deviceName}"
                startRemotePolling(status.host, status.port)
            }.onFailure { err ->
                _phoneRemoteState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = err.localizedMessage
                            ?: "Không tìm thấy TV có mã $cleanCode trên Wi-Fi hiện tại"
                    )
                }
            }
        }
    }

    fun connectFromQrText(rawQr: String) {
        val payload = remoteClient.parseQrPairingPayload(rawQr)
        if (payload == null) {
            _phoneRemoteState.update {
                it.copy(connectionError = "Mã QR không hợp lệ hoặc không phải mã của KidShield TV")
            }
            return
        }
        autoConnectSuppressed = false
        viewModelScope.launch {
            _phoneRemoteState.update {
                it.copy(
                    isConnecting = true,
                    pairingCodeInput = payload.code,
                    connectionError = null
                )
            }
            val directResult = if (payload.host.isNotBlank()) {
                remoteClient.pairWithHostAndCode(payload.host, payload.port, payload.code)
            } else {
                Result.failure(IllegalStateException("Thiếu IP trong QR"))
            }

            val finalResult = if (directResult.isSuccess) {
                directResult
            } else {
                remoteClient.connectWithPairingCode(payload.code)
            }

            finalResult.onSuccess { status ->
                repository.saveLastRemoteTv(status.host, status.port)
                _phoneRemoteState.update {
                    it.copy(
                        isConnecting = false,
                        connectedHost = status.host,
                        connectedPort = status.port,
                        manualHost = status.host,
                        manualPort = status.port.toString(),
                        pairingCodeInput = payload.code,
                        remoteStatus = status,
                        connectionError = null
                    )
                }
                _statusBannerMessage.value = "Đã quét QR & kết nối với ${status.deviceName}"
                startRemotePolling(status.host, status.port)
            }.onFailure { err ->
                _phoneRemoteState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = err.localizedMessage
                            ?: "Không kết nối được tới TV qua mã QR. Đảm bảo ĐT và TV dùng chung Wi-Fi."
                    )
                }
            }
        }
    }

    fun regeneratePairingCode() {
        viewModelScope.launch {
            repository.regeneratePairingCode()
            _statusBannerMessage.value = "Đã tạo mã ghép nối 6 số mới"
        }
    }

    fun connectToTv(host: String, port: Int, silentOnFailure: Boolean = false) {
        val cleanHost = host.trim()
        if (cleanHost.isEmpty()) {
            if (!silentOnFailure) {
                _phoneRemoteState.update { it.copy(connectionError = "Vui lòng nhập địa chỉ IP của TV") }
            }
            return
        }
        autoConnectSuppressed = false
        viewModelScope.launch {
            _phoneRemoteState.update { it.copy(isConnecting = true, connectionError = null) }
            val result = remoteClient.fetchStatus(cleanHost, port)
            result.onSuccess { status ->
                repository.saveLastRemoteTv(cleanHost, port)
                _phoneRemoteState.update {
                    it.copy(
                        isConnecting = false,
                        connectedHost = cleanHost,
                        connectedPort = port,
                        manualHost = cleanHost,
                        manualPort = port.toString(),
                        remoteStatus = status,
                        connectionError = null
                    )
                }
                startRemotePolling(cleanHost, port)
            }.onFailure { err ->
                _phoneRemoteState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = if (silentOnFailure) {
                            null
                        } else {
                            "Không kết nối được tới $cleanHost:$port (${err.localizedMessage ?: "Kiểm tra cùng Wi-Fi"})"
                        }
                    )
                }
            }
        }
    }

    fun disconnectRemoteTv() {
        autoConnectSuppressed = true
        pollingJob?.cancel()
        pollingJob = null
        _phoneRemoteState.update {
            it.copy(
                connectedHost = null,
                remoteStatus = null,
                remoteWeeklyReport = null,
                connectionError = null
            )
        }
    }

    private fun startRemotePolling(host: String, port: Int) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(2000L)
                remoteClient.fetchStatus(host, port).onSuccess { status ->
                    _phoneRemoteState.update { it.copy(remoteStatus = status, connectionError = null) }
                }.onFailure {
                    _phoneRemoteState.update {
                        it.copy(connectionError = "Mất tín hiệu tạm thời từ TV ($host:$port)")
                    }
                }
            }
        }
    }

    fun requestPinAction(action: PinAction) {
        val isRemoteAction = action in setOf(
            PinAction.REMOTE_SWITCH_FAMILY,
            PinAction.REMOTE_SWITCH_KIDS,
            PinAction.REMOTE_GRANT_1H,
            PinAction.REMOTE_TOGGLE_PAUSE,
            PinAction.REMOTE_FETCH_REPORT,
            PinAction.REMOTE_WAKE_TV
        )
        val currentGuard = guardState.value
        val actualAction = if (!isRemoteAction && !currentGuard.isPinConfigured) {
            PinAction.SETUP_INITIAL
        } else {
            action
        }
        val step = if (actualAction == PinAction.SETUP_INITIAL) {
            PinStep.SETUP_NEW
        } else {
            PinStep.ENTER_CURRENT
        }
        val nonce = PinCryptoHelper.generateSessionNonce()
        val digits = if (currentGuard.shuffledKeypadEnabled) {
            PinCryptoHelper.generateShuffledDigits()
        } else {
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        }
        _pinModalState.value = PinModalState(
            isVisible = true,
            action = actualAction,
            step = step,
            enteredInput = "",
            pendingNewPin = "",
            maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
            shuffledDigits = digits,
            errorMessage = null,
            sessionNonce = nonce,
            isRemoteVerification = isRemoteAction
        )
    }

    fun dismissPinModal() {
        _pinModalState.value = PinModalState(isVisible = false)
    }

    fun onPinDigitPressed(digit: Int) {
        val state = _pinModalState.value
        if (!state.isVisible) return
        if (!state.isRemoteVerification &&
            guardState.value.pinCooldownRemainingSeconds > 0 &&
            state.step == PinStep.ENTER_CURRENT
        ) return
        if (state.enteredInput.length >= PinCryptoHelper.PIN_LENGTH) return

        val nextInput = state.enteredInput + digit.toString()
        val digest = PinCryptoHelper.computeMaskedVisualDigest(nextInput, state.sessionNonce)
        _pinModalState.update {
            it.copy(
                enteredInput = nextInput,
                maskedDigestPreview = digest,
                errorMessage = null
            )
        }

        if (nextInput.length == PinCryptoHelper.PIN_LENGTH) {
            handleCompletePin(nextInput)
        }
    }

    fun onPinBackspace() {
        val state = _pinModalState.value
        if (!state.isVisible || state.enteredInput.isEmpty()) return
        val nextInput = state.enteredInput.dropLast(1)
        _pinModalState.update {
            it.copy(
                enteredInput = nextInput,
                maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest(nextInput, state.sessionNonce),
                errorMessage = null
            )
        }
    }

    fun onPinClear() {
        val state = _pinModalState.value
        if (!state.isVisible) return
        _pinModalState.update {
            it.copy(
                enteredInput = "",
                maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", state.sessionNonce),
                errorMessage = null
            )
        }
    }

    private fun handleCompletePin(pin: String) {
        val modal = _pinModalState.value
        viewModelScope.launch {
            if (modal.isRemoteVerification) {
                handleRemotePinVerification(modal.action, pin)
                return@launch
            }

            when (modal.step) {
                PinStep.SETUP_NEW -> {
                    val nonce = PinCryptoHelper.generateSessionNonce()
                    _pinModalState.update {
                        it.copy(
                            step = PinStep.CONFIRM_NEW,
                            pendingNewPin = pin,
                            enteredInput = "",
                            maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                            shuffledDigits = nextKeypadDigits(),
                            errorMessage = null,
                            sessionNonce = nonce
                        )
                    }
                }

                PinStep.CONFIRM_NEW -> {
                    if (pin == modal.pendingNewPin) {
                        repository.setupPin(pin)
                        _pinModalState.value = PinModalState(isVisible = false)
                        _statusBannerMessage.value = "Đã thiết lập mã PIN 8 số mã hóa thành công"
                        executePostVerificationAction(modal.action)
                    } else {
                        val nonce = PinCryptoHelper.generateSessionNonce()
                        _pinModalState.update {
                            it.copy(
                                step = PinStep.SETUP_NEW,
                                pendingNewPin = "",
                                enteredInput = "",
                                maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                                shuffledDigits = nextKeypadDigits(),
                                errorMessage = app.getString(R.string.pin_error_mismatch),
                                sessionNonce = nonce
                            )
                        }
                    }
                }

                PinStep.ENTER_CURRENT -> {
                    when (val result = repository.verifyPin(pin)) {
                        is PinVerifyResult.Success -> {
                            if (modal.action == PinAction.VERIFY_CHANGE_PIN) {
                                val nonce = PinCryptoHelper.generateSessionNonce()
                                _pinModalState.update {
                                    it.copy(
                                        step = PinStep.SETUP_NEW,
                                        pendingNewPin = "",
                                        enteredInput = "",
                                        maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                                        shuffledDigits = nextKeypadDigits(),
                                        errorMessage = null,
                                        sessionNonce = nonce
                                    )
                                }
                            } else {
                                _pinModalState.value = PinModalState(isVisible = false)
                                executePostVerificationAction(modal.action)
                            }
                        }

                        is PinVerifyResult.WrongPin -> {
                            val nonce = PinCryptoHelper.generateSessionNonce()
                            _pinModalState.update {
                                it.copy(
                                    enteredInput = "",
                                    maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                                    shuffledDigits = nextKeypadDigits(),
                                    errorMessage = "${app.getString(R.string.pin_error_wrong)} (Còn ${result.remainingAttemptsBeforeCooldown} lần)",
                                    sessionNonce = nonce
                                )
                            }
                        }

                        is PinVerifyResult.Cooldown -> {
                            val nonce = PinCryptoHelper.generateSessionNonce()
                            _pinModalState.update {
                                it.copy(
                                    enteredInput = "",
                                    maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                                    errorMessage = app.getString(R.string.pin_error_cooldown, result.secondsRemaining),
                                    sessionNonce = nonce
                                )
                            }
                        }

                        PinVerifyResult.NotConfigured -> {
                            val nonce = PinCryptoHelper.generateSessionNonce()
                            _pinModalState.update {
                                it.copy(
                                    action = PinAction.SETUP_INITIAL,
                                    step = PinStep.SETUP_NEW,
                                    enteredInput = "",
                                    maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                                    shuffledDigits = nextKeypadDigits(),
                                    sessionNonce = nonce
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleRemotePinVerification(action: PinAction, pin: String) {
        val remote = _phoneRemoteState.value
        val host = remote.connectedHost ?: run {
            _pinModalState.value = PinModalState(isVisible = false)
            _statusBannerMessage.value = "Chưa kết nối tới TV"
            return
        }
        val port = remote.connectedPort

        if (action == PinAction.REMOTE_FETCH_REPORT) {
            val reportResult = remoteClient.fetchWeeklyReport(host, port, pin)
            reportResult.onSuccess { report ->
                _phoneRemoteState.update { it.copy(remoteWeeklyReport = report) }
                _pinModalState.value = PinModalState(isVisible = false)
                _statusBannerMessage.value = "Đã tải báo cáo tuần từ TV"
            }.onFailure { err ->
                val nonce = PinCryptoHelper.generateSessionNonce()
                _pinModalState.update {
                    it.copy(
                        enteredInput = "",
                        maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                        shuffledDigits = nextKeypadDigits(),
                        errorMessage = err.localizedMessage ?: "Sai mã PIN 8 số",
                        sessionNonce = nonce
                    )
                }
            }
            return
        }

        val cmdAction = when (action) {
            PinAction.REMOTE_SWITCH_FAMILY -> RemoteCommandAction.SWITCH_FAMILY
            PinAction.REMOTE_SWITCH_KIDS -> RemoteCommandAction.SWITCH_KIDS
            PinAction.REMOTE_GRANT_1H -> RemoteCommandAction.GRANT_1H
            PinAction.REMOTE_TOGGLE_PAUSE -> RemoteCommandAction.TOGGLE_PAUSE
            PinAction.REMOTE_WAKE_TV -> RemoteCommandAction.WAKE_TV
            else -> return
        }

        when (val result = remoteClient.sendCommand(host, port, pin, cmdAction)) {
            is RemoteCommandResult.Success -> {
                _pinModalState.value = PinModalState(isVisible = false)
                result.updatedStatus?.let { updated ->
                    _phoneRemoteState.update { it.copy(remoteStatus = updated) }
                }
                _statusBannerMessage.value = result.message
            }
            is RemoteCommandResult.Failure -> {
                val nonce = PinCryptoHelper.generateSessionNonce()
                _pinModalState.update {
                    it.copy(
                        enteredInput = "",
                        maskedDigestPreview = PinCryptoHelper.computeMaskedVisualDigest("", nonce),
                        shuffledDigits = nextKeypadDigits(),
                        errorMessage = result.message,
                        sessionNonce = nonce
                    )
                }
            }
        }
    }

    fun powerOffRemoteTvDirectly() {
        val remote = _phoneRemoteState.value
        val host = remote.connectedHost ?: run {
            _statusBannerMessage.value = "Chưa kết nối tới TV"
            return
        }
        val port = remote.connectedPort
        viewModelScope.launch {
            _phoneRemoteState.update { it.copy(isConnecting = true, connectionError = null) }
            when (val result = remoteClient.sendCommand(host, port, "", RemoteCommandAction.POWER_OFF_TV)) {
                is RemoteCommandResult.Success -> {
                    _phoneRemoteState.update {
                        it.copy(
                            isConnecting = false,
                            remoteStatus = result.updatedStatus ?: it.remoteStatus,
                            connectionError = null
                        )
                    }
                    _statusBannerMessage.value = result.message
                }
                is RemoteCommandResult.Failure -> {
                    _phoneRemoteState.update {
                        it.copy(isConnecting = false, connectionError = result.message)
                    }
                }
            }
        }
    }

    private suspend fun executePostVerificationAction(action: PinAction) {
        when (action) {
            PinAction.VERIFY_TO_FAMILY -> {
                repository.switchToFamilyMode()
                _statusBannerMessage.value = "Đã mở Chế độ Gia đình — Toàn quyền kết nối mạng"
            }
            PinAction.VERIFY_GRANT_HOUR -> {
                repository.grantNewKidsOneHourSession()
                _statusBannerMessage.value = "Đã cấp phiên 1 giờ mới cho Chế độ Trẻ em"
            }
            PinAction.VERIFY_WAKE_LOCAL_TV -> {
                repository.wakeTvFromStandby()
                _statusBannerMessage.value = "Đã mở lại màn hình TV"
            }
            else -> Unit
        }
    }

    private fun nextKeypadDigits(): List<Int> {
        return if (guardState.value.shuffledKeypadEnabled) {
            PinCryptoHelper.generateShuffledDigits()
        } else {
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        }
    }

    fun switchToKidsMode() {
        viewModelScope.launch {
            repository.switchToKidsMode()
            _statusBannerMessage.value = "Đã chuyển về Chế độ Trẻ em (Giới hạn 1h & khóa sau 21:00)"
        }
    }

    fun togglePaused() {
        viewModelScope.launch {
            repository.toggleKidsTimerPaused()
        }
    }

    fun setShuffledKeypadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShuffledKeypadEnabled(enabled)
        }
    }

    fun setLockToKidsOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLockToKidsOnBoot(enabled)
        }
    }

    fun setTvServerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setTvServerEnabled(enabled)
        }
    }

    fun lockParentPanel() {
        viewModelScope.launch {
            repository.lockParentPanel()
        }
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        pollingJob?.cancel()
        super.onCleared()
    }
}
