package cn.xfangfang.biliduo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import cn.xfangfang.biliduo.data.download.DownloadLocation
import cn.xfangfang.biliduo.duo.DuoImeState
import cn.xfangfang.biliduo.data.local.PendingReportManager
import cn.xfangfang.biliduo.data.repository.BilibiliRepository
import cn.xfangfang.biliduo.duo.BottomUiBridge
import cn.xfangfang.biliduo.duo.DuoSessionViewModel
import cn.xfangfang.biliduo.duo.InputRouter
import cn.xfangfang.biliduo.duo.SecondScreenController
import cn.xfangfang.biliduo.ui.channel.ChannelScreen
import cn.xfangfang.biliduo.ui.detail.DetailScreen
import cn.xfangfang.biliduo.ui.detail.NoteScreen
import cn.xfangfang.biliduo.ui.downloads.DownloadsScreen
import cn.xfangfang.biliduo.ui.dynamic.DynamicScreen
import cn.xfangfang.biliduo.data.local.GamepadStyle
import cn.xfangfang.biliduo.data.local.GamepadStyleStore
import cn.xfangfang.biliduo.ui.duo.DuoModeBar
import cn.xfangfang.biliduo.ui.duo.DuoRail
import cn.xfangfang.biliduo.ui.duo.DuoShoulderDispatcher
import cn.xfangfang.biliduo.ui.duo.GamepadStyleSetupOverlay
import cn.xfangfang.biliduo.ui.duo.rememberDuoRailFocusState
import cn.xfangfang.biliduo.ui.favorites.FavoritesScreen
import cn.xfangfang.biliduo.ui.following.FollowingListScreen
import cn.xfangfang.biliduo.ui.history.HistoryScreen
import cn.xfangfang.biliduo.ui.home.HomeScreen
import cn.xfangfang.biliduo.ui.live.LiveConsoleScreen
import cn.xfangfang.biliduo.ui.login.LoginScreen
import cn.xfangfang.biliduo.ui.navigation.Routes
import cn.xfangfang.biliduo.ui.navigation.Screen
import cn.xfangfang.biliduo.ui.player.PlayerConsoleScreen
import cn.xfangfang.biliduo.ui.player.PlayerController
import cn.xfangfang.biliduo.BuildConfig
import cn.xfangfang.biliduo.ui.common.DuoEmptyState
import cn.xfangfang.biliduo.ui.common.ImageZoomMotion
import cn.xfangfang.biliduo.ui.player.PlayerScreen
import cn.xfangfang.biliduo.ui.player.PlayerViewModel
import cn.xfangfang.biliduo.ui.player.PlaybackSource
import cn.xfangfang.biliduo.ui.profile.ProfileScreen
import cn.xfangfang.biliduo.ui.search.SearchScreen
import cn.xfangfang.biliduo.ui.top.TopScreenHost
import cn.xfangfang.biliduo.ui.top.TopScreenViewModel
import cn.xfangfang.biliduo.ui.top.TopAmbientUi
import cn.xfangfang.biliduo.ui.top.TopDynamicReaderUi
import cn.xfangfang.biliduo.ui.top.TopPanelUi
import cn.xfangfang.biliduo.ui.top.TopPersonUi
import cn.xfangfang.biliduo.ui.top.TopPreviewUi
import cn.xfangfang.biliduo.ui.settings.SettingsScreen
import cn.xfangfang.biliduo.data.local.AppSettings
import cn.xfangfang.biliduo.ui.theme.BilibiliPureTheme
import cn.xfangfang.biliduo.ui.theme.DuoTheme
import cn.xfangfang.biliduo.ui.theme.THEME_MANUAL
import cn.xfangfang.biliduo.ui.theme.rememberDuoDarkTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val downloadDirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) DownloadLocation.setTreeUri(this, uri)
        }

    private val openDownloadsRequest = MutableStateFlow(0)

    private val playerViewModel: PlayerViewModel by viewModels()
    private val duoSession: DuoSessionViewModel by viewModels()
    private val topScreenViewModel: TopScreenViewModel by viewModels()
    private val bottomUiBridge = BottomUiBridge()
    private lateinit var playerController: PlayerController
    private lateinit var inputRouter: InputRouter

    private lateinit var secondScreen: SecondScreenController
    private var secondScreenAvailable by mutableStateOf(false)

    /**
     * Bumped when the user swaps the panels. Both MainScreen hosts (Activity and
     * Presentation) read it; the newly composed one navigates back to Profile
     * since that is where the swap button lives.
     */
    private val swapRestoreSerial = mutableIntStateOf(-1)

    private lateinit var inputManager: InputManager
    private val gamepadListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshGamepadConnection()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshGamepadConnection()
        override fun onInputDeviceChanged(deviceId: Int) = refreshGamepadConnection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Launcher shortcut ("双屏启动 · 上/下屏控制") forces the layout for
        // this launch and is persisted, so a later normal launch keeps it.
        if (savedInstanceState == null) {
            applyDisplayControlIntent(intent)
            intent?.removeExtra(EXTRA_DISPLAY_CONTROL)
        }
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        playerController = PlayerController(
            context = this,
            viewModel = playerViewModel,
            scope = lifecycleScope,
        )
        lifecycleScope.launch {
            DownloadLocation.pickRequests.collect { downloadDirLauncher.launch(null) }
        }
        if (BuildConfig.DEBUG && intent?.getBooleanExtra("disable_progress_report", false) == true) {
            playerController.setProgressReportingEnabled(false)
        }
        inputRouter = InputRouter(
            session = duoSession,
            bridge = bottomUiBridge,
        )
        inputManager = getSystemService(InputManager::class.java)
        inputManager.registerInputDeviceListener(gamepadListener, null)
        refreshGamepadConnection()

        fun toggleDisplaySwap() {
            val next = !AppSettings.displaySwapped
            AppSettings.displaySwapped = next
            secondScreen.setVideoOnly(next)
            bottomUiBridge.setUseLocal(next)
            swapRestoreSerial.intValue++
            val serial = swapRestoreSerial.intValue
            // The Profile page is where the button lives. Both hosts are
            // recreated, so tell the new one to navigate back there. Clear the
            // token shortly after so a later window recreation starts at Home.
            lifecycleScope.launch {
                delay(2_000)
                if (swapRestoreSerial.intValue == serial) swapRestoreSerial.intValue = -1
            }
        }

        val mainContent: @Composable () -> Unit = {
            MainScreen(
                openDownloadsRequest = openDownloadsRequest,
                dualScreen = true,
                playerViewModel = playerViewModel,
                playerController = playerController,
                duoSession = duoSession,
                displaySwapped = AppSettings.displaySwapped,
                displaySwapAvailable = secondScreenAvailable,
                onToggleDisplaySwap = { toggleDisplaySwap() },
                restoreProfileSerial = swapRestoreSerial.intValue,
                onTopVideoFocused = topScreenViewModel::onVideoFocused,
                onTopFocusCleared = topScreenViewModel::onFocusCleared,
                onTopPerson = topScreenViewModel::showPerson,
                onTopPanel = topScreenViewModel::showPanel,
                onTopAmbient = topScreenViewModel::showAmbient,
                onTopDynamicReader = topScreenViewModel::showDynamicReader,
                onTopIdle = topScreenViewModel::showIdle,
            )
        }
        val topContent: @Composable () -> Unit = {
            TopScreenHost(
                controller = playerController,
                viewModel = topScreenViewModel,
                playerViewModel = playerViewModel,
            )
        }
        secondScreen = SecondScreenController(
            activity = this,
            content = {
                if (AppSettings.displaySwapped) topContent() else mainContent()
            },
            onBack = { duoSession.handleBackKey() },
            onAvailabilityChanged = { secondScreenAvailable = it },
            onDecorViewChanged = { view ->
                bottomUiBridge.attach(view)
                if (view != null && !AppSettings.displaySwapped) duoSession.requestFocus()
            },
        )
        secondScreenAvailable = secondScreen.hasSecondaryDisplay()
        secondScreen.setVideoOnly(AppSettings.displaySwapped)
        bottomUiBridge.setUseLocal(AppSettings.displaySwapped)
        requestNotificationPermissionIfNeeded()
        handleOpenDownloadsIntent(intent)
        lifecycleScope.launch {
            val pending = PendingReportManager(getSharedPreferences("bili_prefs", MODE_PRIVATE))
            BilibiliRepository().flushPendingReports(pending)
        }
        setContent {
            BilibiliPureTheme(darkTheme = rememberDuoDarkTheme()) {
                LaunchedEffect(secondScreenAvailable) {
                    playerController.setEnabled(secondScreenAvailable)
                }
                if (secondScreenAvailable) {
                    // With a second display the two windows swap roles: video
                    // on top + UI below by default, or UI on top + video below.
                    if (AppSettings.displaySwapped) mainContent() else topContent()
                } else {
                    MainScreen(
                        openDownloadsRequest = openDownloadsRequest,
                        dualScreen = false,
                        playerViewModel = playerViewModel,
                        playerController = null,
                        duoSession = duoSession,
                        displaySwapAvailable = false,
                    )
                }
            }
        }
        // When the UI runs in the Activity (swapped layout), key events still
        // pass through InputRouter; point the bridge at the Compose content.
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        bottomUiBridge.attachLocal(contentRoot?.getChildAt(0) ?: contentRoot)
    }

    override fun onStart() {
        super.onStart()
        secondScreen.show()
    }

    override fun onStop() {
        if (::playerController.isInitialized) playerController.onHostStop()
        secondScreen.hide()
        super.onStop()
    }

    override fun onDestroy() {
        if (::inputManager.isInitialized) {
            inputManager.unregisterInputDeviceListener(gamepadListener)
        }
        secondScreen.release()
        if (::playerController.isInitialized) playerController.release()
        super.onDestroy()
    }

    /**
     * The built-in controls and a paired Xbox pad both expose SOURCE_GAMEPAD /
     * SOURCE_JOYSTICK. Only the mode-bar hint depends on this; touch always works.
     */
    private fun refreshGamepadConnection() {
        if (!::inputManager.isInitialized) return
        val hasGamepad = inputManager.inputDeviceIds.any { id ->
            val device = inputManager.getInputDevice(id) ?: return@any false
            val mask = InputDevice.SOURCE_GAMEPAD or
                InputDevice.SOURCE_JOYSTICK or
                InputDevice.SOURCE_DPAD
            device.sources and mask != 0
        }
        duoSession.setGamepadConnected(hasGamepad)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenDownloadsIntent(intent)
        applyDisplayControlIntent(intent)
        intent.removeExtra(EXTRA_DISPLAY_CONTROL)
        setIntent(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (secondScreenAvailable &&
            ::inputRouter.isInitialized &&
            inputRouter.dispatch(event)
        ) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * The active bottom-screen image viewer consumes the left stick here. This
     * is deliberately scoped to an open viewer and ignores HAT axes, so the
     * D-pad is never swallowed (the rc20 regression was Activity-wide generic
     * motion interception).
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ImageZoomMotion.active != null) {
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                ImageZoomMotion.reset()
            } else if (event.actionMasked == MotionEvent.ACTION_MOVE &&
                (event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                    event.isFromSource(InputDevice.SOURCE_GAMEPAD))
            ) {
                val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                if (hatX == 0f && hatY == 0f) {
                    ImageZoomMotion.onStick(
                        event.getAxisValue(MotionEvent.AXIS_X),
                        event.getAxisValue(MotionEvent.AXIS_Y),
                    )
                    return true
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun handleOpenDownloadsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            openDownloadsRequest.value++
        }
    }

    /**
     * Static launcher shortcuts pass "display_control=bottom|top". The value is
     * persisted, so the next normal launch keeps the last chosen layout.
     */
    private fun applyDisplayControlIntent(intent: Intent?) {
        val mode = intent?.getStringExtra(EXTRA_DISPLAY_CONTROL) ?: return
        val swapped = mode == DISPLAY_CONTROL_TOP
        if (AppSettings.displaySwapped == swapped) return
        AppSettings.displaySwapped = swapped
        if (::secondScreen.isInitialized) {
            secondScreen.setVideoOnly(swapped)
            bottomUiBridge.setUseLocal(swapped)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"

        /** Must match app/src/main/res/xml/shortcuts.xml. */
        const val EXTRA_DISPLAY_CONTROL = "display_control"
        const val DISPLAY_CONTROL_BOTTOM = "bottom"
        const val DISPLAY_CONTROL_TOP = "top"
    }
}

private fun duoPageHint(route: String?): String? = when (route) {
    Screen.Home.route ->
        "Y 刷新 · LB/RB 切标签 · L2/R2 直播/排行分区"
    Screen.Search.route ->
        "LB/RB 视频/用户 · L2/R2 排序 · B 输入/播放"
    Screen.Dynamic.route ->
        "LB/RB 全部/视频/图文 · L2/R2 UP 筛选 · Y 点赞/复制"
    Screen.Profile.route -> "B 进入"
    Routes.HISTORY -> "B 播放"
    Routes.FAVORITES -> "B 打开/播放"
    Routes.FOLLOWING -> "B 进入 UP 主空间"
    Routes.DOWNLOADS -> "LB/RB 切筛选 · B 播放/展开"
    Routes.SETTINGS ->
        "↑/↓ 分类 · → 进入 · ← 回分类 · LB/RB 切分类"
    Routes.LOGIN -> "B 输入/刷新二维码 · A 收键盘"
    Routes.CHANNEL -> "LB/RB 视频/合集 · B 播放/进入"
    Routes.PLAYER, Routes.PLAYER_CID, Routes.DOWNLOAD_PLAY, Routes.PGC_PLAYER ->
        "LB/RB 切 tab · L2/R2 评论排序 · Y 弹幕 · START 弹幕/字幕"
    Routes.LIVE_PLAYER -> "方向键/B 切换评论与信息 · X 弹幕 · START 弹幕设置"
    Routes.DETAIL, Routes.NOTE -> "方向键移动 · B 播放/进入"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    openDownloadsRequest: StateFlow<Int> = MutableStateFlow(0),
    dualScreen: Boolean = false,
    playerViewModel: PlayerViewModel = viewModel(),
    playerController: PlayerController? = null,
    duoSession: DuoSessionViewModel = viewModel(),
    displaySwapped: Boolean = false,
    displaySwapAvailable: Boolean = false,
    onToggleDisplaySwap: () -> Unit = {},
    restoreProfileSerial: Int = -1,
    onTopVideoFocused: (TopPreviewUi) -> Unit = {},
    onTopFocusCleared: () -> Unit = {},
    onTopPerson: (TopPersonUi) -> Unit = {},
    onTopPanel: (TopPanelUi) -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onTopDynamicReader: (TopDynamicReaderUi) -> Unit = {},
    onTopIdle: () -> Unit = {},
) {
    val navController = rememberNavController()
    // After a display swap the host is recreated and its fresh NavController
    // starts at Home. Return to Profile so the swap button stays reachable.
    var restoredSwapSerial by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(restoreProfileSerial, navController) {
        if (restoreProfileSerial >= 0 && restoredSwapSerial != restoreProfileSerial) {
            restoredSwapSerial = restoreProfileSerial
            // Let NavHost attach its graph before restoring the page.
            delay(120)
            runCatching {
                navController.navigate(Screen.Profile.route) { launchSingleTop = true }
            }
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val railFocusState = rememberDuoRailFocusState()
    val isDarkTheme = DuoTheme.colors.isDark
    var showGamepadSetup by remember { mutableStateOf(!GamepadStyleStore.hasChosen()) }

    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (!imeVisible) DuoImeState.shown = false
    }
    DisposableEffect(duoSession) {
        duoSession.setImeBackHandler(hide = { keyboard?.hide() })
        onDispose { duoSession.setImeBackHandler(null) }
    }


    LaunchedEffect(currentDestination?.route) {
        when (currentDestination?.route) {
            null, Routes.PLAYER, Routes.LIVE_PLAYER -> Unit
            Screen.Home.route, Screen.Search.route -> onTopIdle()
            else -> onTopFocusCleared()
        }
    }

    val openDownloadsTrigger by openDownloadsRequest.collectAsState()
    var handledOpenDownloads by remember { mutableStateOf(0) }
    LaunchedEffect(openDownloadsTrigger) {
        if (openDownloadsTrigger != handledOpenDownloads) {
            handledOpenDownloads = openDownloadsTrigger
            navController.navigate(Routes.DOWNLOADS) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(navController, duoSession) {
        var lastBackAt = 0L
        duoSession.backRequests.collect {
            // Rapid B presses used to interrupt the pop transition and leave
            // the NavHost on a blank destination. Throttle pops to one per
            // transition and never pop the start destination.
            val now = SystemClock.uptimeMillis()
            if (now - lastBackAt < 220L) return@collect
            lastBackAt = now
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
        }
    }

    // Keep the rail for every browse page, including Profile's secondary
    // screens; only immersive player/login routes hide it. This avoids the
    // layout jump/flash when navigating in and out of a player.
    val showRail = when (currentDestination?.route) {
        null -> true
        Routes.LOGIN,
        Routes.PLAYER,
        Routes.PLAYER_CID,
        Routes.DOWNLOAD_PLAY,
        Routes.PGC_PLAYER,
        Routes.LIVE_PLAYER -> false
        else -> true
    }
    val railWidth by animateDpAsState(
        targetValue = if (showRail) 64.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "duoRailWidth",
    )

    fun finishGamepadSetup(style: GamepadStyle) {
        GamepadStyleStore.save(style)
        showGamepadSetup = false
        // The overlay owned focus; hand it back to the rail so the next
        // direction/confirm press has a predictable target.
        railFocusState.beginNavigation(currentDestination?.route ?: Screen.Home.route)
    }

    Scaffold(
        bottomBar = {
            DuoModeBar(
                    session = duoSession,
                    enabled = dualScreen,
                    pageHint = duoPageHint(currentDestination?.route),
                )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Any deliberate key or touch means the user is done with the
                // rail; release the focus lock before the event is handled.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val railOwnsFocus = railFocusState.railHasFocus
                    railFocusState.release()
                    // While the rail owns focus the page root is outside the
                    // focus tree, so forward shoulder shortcuts to it.
                    railOwnsFocus && DuoShoulderDispatcher.dispatch(event.key)
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                railFocusState.release()
                            }
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
            ) {
                if (railWidth > 0.dp) {
                    DuoRail(
                        currentRoute = currentDestination?.route,
                        focusState = railFocusState,
                        onToggleTheme = {
                            AppSettings.themeMode = THEME_MANUAL
                            AppSettings.manualDark = !isDarkTheme
                        },
                        onNavigate = { screen ->
                            // Navigating back to the start destination with
                            // saveState+restoreState can restore the stack we
                            // just saved (e.g. Profile), so the Home rail item
                            // only focused without changing pages. Pop instead.
                            if (screen == Screen.Home) {
                                val popped = navController.popBackStack(
                                    Screen.Home.route,
                                    inclusive = false,
                                )
                                if (!popped) {
                                    navController.navigate(Screen.Home.route) {
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.weight(1f),
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(160)) },
                // A slide-out pop can be left half-finished by rapid B
                // presses, leaving an empty NavHost. Fade is idempotent.
                popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onVideoClick = { bvid ->
                            navController.navigate(Routes.player(bvid))
                        },
                        onPgcClick = { seasonId ->
                            navController.navigate(Routes.pgc(seasonId))
                        },
                        onLiveClick = { roomId ->
                            navController.navigate(Routes.live(roomId))
                        },
                        onVideoFocused = onTopVideoFocused,
                        onFocusCleared = onTopFocusCleared,
                        onTopAmbient = onTopAmbient,
                    )
                }

                composable(Screen.Dynamic.route) {
                    DynamicScreen(
                        onVideoClick = { bvid ->
                            navController.navigate(Routes.player(bvid))
                        },
                        onLogin = { navController.navigate(Routes.LOGIN) },
                        session = duoSession,
                        onVideoFocused = onTopVideoFocused,
                        onFocusCleared = onTopFocusCleared,
                        onDynamicReader = onTopDynamicReader,
                        onTopAmbient = onTopAmbient,
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        onVideoClick = { bvid ->
                            navController.navigate(Routes.player(bvid))
                        },
                        onUserClick = { mid ->
                            navController.navigate(Routes.channel(mid))
                        },
                        onBack = { navController.popBackStack() },
                        onVideoFocused = onTopVideoFocused,
                        onUserFocused = onTopPerson,
                        onFocusCleared = onTopFocusCleared,
                        onTopAmbient = onTopAmbient,
                    )
                }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLoginClick = { navController.navigate(Routes.LOGIN) },
                    onHistoryClick = { navController.navigate(Routes.HISTORY) },
                    onFavoritesClick = { navController.navigate(Routes.FAVORITES) },
                    onFollowingClick = { navController.navigate(Routes.FOLLOWING) },
                    onDownloadsClick = { navController.navigate(Routes.DOWNLOADS) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onTopPerson = onTopPerson,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                    displaySwapped = displaySwapped,
                    displaySwapAvailable = displaySwapAvailable,
                    onToggleDisplaySwap = onToggleDisplaySwap,
                )
            }

            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("bvid") { type = NavType.StringType })
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                DetailScreen(
                    bvid = bvid,
                    onBack = { navController.popBackStack() },
                    onPlay = { b -> navController.navigate(Routes.player(b)) },
                    onUploaderClick = { mid -> navController.navigate(Routes.channel(mid)) },
                    onUserClick = { mid -> navController.navigate(Routes.channel(mid)) },
                    onCollectionVideoClick = { b -> navController.navigate(Routes.detail(b)) },
                    onOpenNote = { cvid -> navController.navigate(Routes.note(cvid)) }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("bvid") { type = NavType.StringType }),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                if (dualScreen && playerController != null) {
                    PlayerConsoleScreen(
                        bvid = bvid,
                        controller = playerController,
                        vm = playerViewModel,
                        session = duoSession,
                        onBack = { navController.popBackStack() },
                        source = PlaybackSource.ONLINE,
                        onUploaderClick = { navController.navigate(Routes.channel(it)) },
                    )
                } else {
                    PlayerScreen(
                        bvid = bvid,
                        onBack = { navController.popBackStack() },
                        vm = playerViewModel,
                        source = PlaybackSource.ONLINE,
                    )
                }
            }

            composable(
                route = Routes.PLAYER_CID,
                arguments = listOf(
                    navArgument("bvid") { type = NavType.StringType },
                    navArgument("cid") { type = NavType.LongType }
                ),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                val cid = backStackEntry.arguments?.getLong("cid") ?: 0L
                if (dualScreen && playerController != null) {
                    PlayerConsoleScreen(
                        bvid = bvid,
                        controller = playerController,
                        vm = playerViewModel,
                        session = duoSession,
                        onBack = { navController.popBackStack() },
                        startCid = if (cid != 0L) cid else null,
                        source = PlaybackSource.ONLINE,
                        onUploaderClick = { navController.navigate(Routes.channel(it)) },
                    )
                } else {
                    PlayerScreen(
                        bvid = bvid,
                        onBack = { navController.popBackStack() },
                        vm = playerViewModel,
                        startCid = if (cid != 0L) cid else null,
                        source = PlaybackSource.ONLINE,
                    )
                }
            }

            composable(
                route = Routes.PGC_PLAYER,
                arguments = listOf(navArgument("seasonId") { type = NavType.LongType }),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val seasonId = backStackEntry.arguments?.getLong("seasonId") ?: return@composable
                if (dualScreen && playerController != null) {
                    PlayerConsoleScreen(
                        bvid = "",
                        controller = playerController,
                        vm = playerViewModel,
                        session = duoSession,
                        onBack = { navController.popBackStack() },
                        pgcSeasonId = seasonId,
                        onUploaderClick = { navController.navigate(Routes.channel(it)) },
                    )
                } else {
                    // PGC needs the bottom-screen console. Single-screen is a
                    // dev/emulator fallback, not a supported playback target.
                    DuoEmptyState(
                        title = "此内容需要下屏控制台",
                        hint = "当前设备未检测到第二块屏幕。番剧/影视播放只在双屏模式下提供。",
                        actionText = "返回",
                        onAction = { navController.popBackStack() },
                    )
                }
            }

            composable(
                route = Routes.LIVE_PLAYER,
                arguments = listOf(navArgument("roomId") { type = NavType.LongType }),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
                if (dualScreen && playerController != null) {
                    LiveConsoleScreen(
                        roomId = roomId,
                        controller = playerController,
                        vm = playerViewModel,
                        session = duoSession,
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    // Live playback needs the bottom-screen console. Single-screen
                    // is a dev/emulator fallback, not a supported playback target.
                    DuoEmptyState(
                        title = "此内容需要下屏控制台",
                        hint = "当前设备未检测到第二块屏幕。直播播放与实时弹幕只在双屏模式下提供。",
                        actionText = "返回",
                        onAction = { navController.popBackStack() },
                    )
                }
            }

            composable(
                route = Routes.DOWNLOAD_PLAY,
                arguments = listOf(
                    navArgument("bvid") { type = NavType.StringType },
                    navArgument("cid") { type = NavType.LongType }
                ),
                popExitTransition = { slideOutHorizontally { it } }
            ) { backStackEntry ->
                val bvid = backStackEntry.arguments?.getString("bvid") ?: return@composable
                val cid = backStackEntry.arguments?.getLong("cid") ?: 0L
                if (dualScreen && playerController != null) {
                    PlayerConsoleScreen(
                        bvid = bvid,
                        controller = playerController,
                        vm = playerViewModel,
                        session = duoSession,
                        onBack = { navController.popBackStack() },
                        startCid = if (cid != 0L) cid else null,
                        source = PlaybackSource.LOCAL,
                        onUploaderClick = { navController.navigate(Routes.channel(it)) },
                    )
                } else {
                    PlayerScreen(
                        bvid = bvid,
                        onBack = { navController.popBackStack() },
                        vm = playerViewModel,
                        startCid = if (cid != 0L) cid else null,
                        source = PlaybackSource.LOCAL,
                    )
                }
            }

            composable(route = Routes.LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    // Profile stays on the back stack and refreshes on ON_RESUME,
                    // so pushing a duplicate Profile destination is unnecessary.
                    onLoginSuccess = { navController.popBackStack() },
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }

            composable(route = Routes.HISTORY) {
                HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.player(bvid))
                    },
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onVideoFocused = onTopVideoFocused,
                    onFocusCleared = onTopFocusCleared,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }

            composable(route = Routes.FAVORITES) {
                FavoritesScreen(
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.player(bvid))
                    },
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onVideoFocused = onTopVideoFocused,
                    onFocusCleared = onTopFocusCleared,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }

            composable(route = Routes.FOLLOWING) {
                FollowingListScreen(
                    onBack = { navController.popBackStack() },
                    onUploaderClick = { mid ->
                        navController.navigate(Routes.channel(mid))
                    },
                    onLogin = { navController.navigate(Routes.LOGIN) },
                    onTopPerson = onTopPerson,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }

            composable(route = Routes.DOWNLOADS) {
                DownloadsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { bvid, cid ->
                        navController.navigate(Routes.downloadPlay(bvid, cid))
                    },
                    onBrowse = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                    onVideoFocused = onTopVideoFocused,
                    onFocusCleared = onTopFocusCleared,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }

            composable(route = Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onTopPanel = onTopPanel,
                    session = duoSession,
                    playerViewModel = playerViewModel,
                )
            }

            composable(
                route = Routes.NOTE,
                arguments = listOf(navArgument("cvid") { type = NavType.StringType })
            ) { backStackEntry ->
                val cvid = backStackEntry.arguments?.getString("cvid") ?: return@composable
                NoteScreen(
                    cvid = cvid,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.CHANNEL,
                arguments = listOf(navArgument("mid") { type = NavType.LongType })
            ) { backStackEntry ->
                val mid = backStackEntry.arguments?.getLong("mid") ?: return@composable
                ChannelScreen(
                    mid = mid,
                    onBack = { navController.popBackStack() },
                    onVideoClick = { bvid ->
                        navController.navigate(Routes.player(bvid))
                    },
                    onVideoFocused = onTopVideoFocused,
                    onTopPerson = onTopPerson,
                    onFocusCleared = onTopFocusCleared,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }
        }
            if (showGamepadSetup) {
                GamepadStyleSetupOverlay(
                    onChoose = { style -> finishGamepadSetup(style) },
                    onSkip = { finishGamepadSetup(GamepadStyle.XBOX) },
                    session = duoSession,
                )
            }
        }
    }
}
}
