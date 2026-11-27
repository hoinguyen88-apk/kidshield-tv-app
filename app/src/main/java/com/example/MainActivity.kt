package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.service.TvPowerController
import com.example.service.WatchGuardForegroundService
import com.example.ui.MainViewModel
import com.example.ui.PinAction
import com.example.ui.components.EncryptedPinDialog
import com.example.ui.components.tvFocusHighlight
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PhoneRemoteScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KidShieldTheme

sealed class AppDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : AppDestination(
        route = "dashboard",
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Remote : AppDestination(
        route = "remote",
        labelRes = R.string.nav_remote,
        selectedIcon = Icons.Filled.PhoneAndroid,
        unselectedIcon = Icons.Outlined.PhoneAndroid
    )

    data object Reports : AppDestination(
        route = "reports",
        labelRes = R.string.nav_reports,
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    )

    data object Settings : AppDestination(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

class MainActivity : ComponentActivity() {

    private var activeViewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WatchGuardForegroundService.ensureRunning(this)

        setContent {
            val vm: MainViewModel = viewModel()
            activeViewModel = vm
            KidShieldTheme(darkTheme = true) {
                KidShieldRootApp(viewModel = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WatchGuardForegroundService.ensureRunning(this)
        activeViewModel?.refreshVpnPermission()
        activeViewModel?.refreshDeviceAdmin()
        activeViewModel?.triggerImmediateTick()
    }
}

@Composable
private fun KidShieldRootApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Dashboard.route

    val guardState by viewModel.guardState.collectAsStateWithLifecycle()
    val weeklyReport by viewModel.weeklyReport.collectAsStateWithLifecycle()
    val pinModalState by viewModel.pinModalState.collectAsStateWithLifecycle()
    val selectedDayIndex by viewModel.selectedDayIndex.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val phoneRemoteState by viewModel.phoneRemoteState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshVpnPermission()
        viewModel.triggerImmediateTick()
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDeviceAdmin()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(bannerMessage) {
        bannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    DisposableEffect(guardState.isTvStandbyBlackout) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            val params = window.attributes
            params.screenBrightness = if (guardState.isTvStandbyBlackout) {
                0.01f
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window.attributes = params
        }
        onDispose {
            if (window != null && guardState.isTvStandbyBlackout) {
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
        }
    }

    val requestVpnSetup: () -> Unit = {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            viewModel.refreshVpnPermission()
            viewModel.triggerImmediateTick()
        }
    }

    val requestDeviceAdminSetup: () -> Unit = {
        val intent = TvPowerController.buildEnableDeviceAdminIntent(context)
        if (intent.resolveActivity(context.packageManager) != null) {
            deviceAdminLauncher.launch(intent)
        } else {
            viewModel.showBannerMessage(
                "Thiết bị này không mở được trang Device Admin; lệnh Tắt TV vẫn sẽ ngắt mạng, tắt tiếng và tối màn hình."
            )
        }
    }

    val destinations = listOf(
        AppDestination.Dashboard,
        AppDestination.Remote,
        AppDestination.Reports,
        AppDestination.Settings
    )

    val navigateTo: (AppDestination) -> Unit = { dest ->
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp || guardState.isTvDevice

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier
                                    .tvFocusHighlight(shape = RoundedCornerShape(14.dp))
                                    .testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier
                                    .tvFocusHighlight(shape = RoundedCornerShape(14.dp))
                                    .testTag("rail_${dest.route}")
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AppDestination.Dashboard.route,
                        modifier = Modifier.widthIn(max = 1120.dp)
                    ) {
                        composable(AppDestination.Dashboard.route) {
                            DashboardScreen(
                                guardState = guardState,
                                onRequestVpnPermission = requestVpnSetup,
                                onRequestSetupPin = {
                                    viewModel.requestPinAction(PinAction.SETUP_INITIAL)
                                },
                                onRequestFamilyMode = {
                                    viewModel.requestPinAction(PinAction.VERIFY_TO_FAMILY)
                                },
                                onSwitchToKidsMode = {
                                    viewModel.switchToKidsMode()
                                },
                                onRequestGrantExtraHour = {
                                    viewModel.requestPinAction(PinAction.VERIFY_GRANT_HOUR)
                                },
                                onTogglePause = {
                                    viewModel.togglePaused()
                                },
                                onRegeneratePairingCode = viewModel::regeneratePairingCode,
                                onRequestDeviceAdmin = requestDeviceAdminSetup,
                                onOpenPhoneRemote = {
                                    navigateTo(AppDestination.Remote)
                                }
                            )
                        }

                        composable(AppDestination.Remote.route) {
                            PhoneRemoteScreen(
                                remoteState = phoneRemoteState,
                                onStartDiscovery = { force -> viewModel.startLanTvDiscovery(force) },
                                onStopDiscovery = viewModel::stopLanTvDiscovery,
                                onHostChange = viewModel::updateManualHost,
                                onPortChange = viewModel::updateManualPort,
                                onPairingCodeChange = viewModel::updatePairingCodeInput,
                                onConnectByPairingCode = viewModel::connectWithPairingCode,
                                onQrScanned = viewModel::connectFromQrText,
                                onCameraPermissionDenied = {
                                    viewModel.setRemoteConnectionError(
                                        "Cần cấp quyền Camera để quét mã QR, hoặc bạn có thể nhập mã 6 số trực tiếp ở trên."
                                    )
                                },
                                onConnect = viewModel::connectToTv,
                                onDisconnect = viewModel::disconnectRemoteTv,
                                onRemoteSwitchFamily = {
                                    viewModel.requestPinAction(PinAction.REMOTE_SWITCH_FAMILY)
                                },
                                onRemoteSwitchKids = {
                                    viewModel.requestPinAction(PinAction.REMOTE_SWITCH_KIDS)
                                },
                                onRemoteGrantHour = {
                                    viewModel.requestPinAction(PinAction.REMOTE_GRANT_1H)
                                },
                                onRemoteFetchReport = {
                                    viewModel.requestPinAction(PinAction.REMOTE_FETCH_REPORT)
                                },
                                onRemotePowerOffTv = viewModel::powerOffRemoteTvDirectly,
                                onRemoteWakeTv = {
                                    viewModel.requestPinAction(PinAction.REMOTE_WAKE_TV)
                                }
                            )
                        }

                        composable(AppDestination.Reports.route) {
                            ReportsScreen(
                                guardState = guardState,
                                weeklyReport = weeklyReport,
                                selectedDayIndex = selectedDayIndex,
                                onSelectDay = viewModel::selectReportDay,
                                onUnlockParent = {
                                    viewModel.requestPinAction(PinAction.VERIFY_OPEN_REPORTS)
                                }
                            )
                        }

                        composable(AppDestination.Settings.route) {
                            SettingsScreen(
                                guardState = guardState,
                                onUnlockParent = {
                                    viewModel.requestPinAction(PinAction.VERIFY_OPEN_SETTINGS)
                                },
                                onRequestVpnPermission = requestVpnSetup,
                                onToggleShuffledKeypad = viewModel::setShuffledKeypadEnabled,
                                onToggleLockToKidsOnBoot = viewModel::setLockToKidsOnBoot,
                                onToggleTvServer = viewModel::setTvServerEnabled,
                                onGrantNewKidsHour = {
                                    viewModel.requestPinAction(PinAction.VERIFY_GRANT_HOUR)
                                },
                                onChangePin = {
                                    viewModel.requestPinAction(PinAction.VERIFY_CHANGE_PIN)
                                },
                                onLockParentPanel = viewModel::lockParentPanel,
                                onRequestDeviceAdmin = requestDeviceAdminSetup
                            )
                        }
                    }
                }
            }
        }

        if (guardState.isTvStandbyBlackout) {
            TvStandbyBlackoutOverlay(
                onUnlockWithPin = {
                    viewModel.requestPinAction(PinAction.VERIFY_WAKE_LOCAL_TV)
                }
            )
        }

        EncryptedPinDialog(
            state = pinModalState,
            shuffledKeypadEnabled = guardState.shuffledKeypadEnabled,
            cooldownSeconds = guardState.pinCooldownRemainingSeconds,
            onDigitPress = viewModel::onPinDigitPressed,
            onBackspace = viewModel::onPinBackspace,
            onClear = viewModel::onPinClear,
            onDismiss = viewModel::dismissPinModal
        )
    }
}

@Composable
private fun TvStandbyBlackoutOverlay(
    onUnlockWithPin: () -> Unit
) {
    BackHandler {
        onUnlockWithPin()
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = "TV đã tắt",
                tint = Color(0xFF475569),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = "TV đã được Phụ huynh tắt từ xa",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Mạng TV đã ngắt và âm thanh đã dừng. Mở lại từ điện thoại hoặc nhập mã PIN 8 số trên TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUnlockWithPin,
                modifier = Modifier
                    .height(50.dp)
                    .focusRequester(focusRequester)
                    .tvFocusHighlight(shape = RoundedCornerShape(25.dp), focusedBorderColor = Color(0xFF94A3B8))
                    .testTag("tv_standby_unlock_button")
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mở lại TV (PIN 8 số)")
            }
        }
    }
}
