package cn.xfangfang.biliduo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import cn.xfangfang.biliduo.ui.duo.DuoModeBar
import cn.xfangfang.biliduo.ui.duo.DuoRail
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
import cn.xfangfang.biliduo.ui.theme.BilibiliPureTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val openDownloadsRequest = MutableStateFlow(0)

    private val playerViewModel: PlayerViewModel by viewModels()
    private val duoSession: DuoSessionViewModel by viewModels()
    private val topScreenViewModel: TopScreenViewModel by viewModels()
    private val bottomUiBridge = BottomUiBridge()
    private lateinit var playerController: PlayerController
    private lateinit var inputRouter: InputRouter

    private lateinit var secondScreen: SecondScreenController
    private var secondScreenAvailable by mutableStateOf(false)

    private lateinit var inputManager: InputManager
    private val gamepadListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshGamepadConnection()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshGamepadConnection()
        override fun onInputDeviceChanged(deviceId: Int) = refreshGamepadConnection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        secondScreen = SecondScreenController(
            activity = this,
            content = {
                MainScreen(
                    openDownloadsRequest = openDownloadsRequest,
                    dualScreen = true,
                    playerViewModel = playerViewModel,
                    playerController = playerController,
                    duoSession = duoSession,
                    onTopVideoFocused = topScreenViewModel::onVideoFocused,
                    onTopFocusCleared = topScreenViewModel::onFocusCleared,
                    onTopPerson = topScreenViewModel::showPerson,
                    onTopPanel = topScreenViewModel::showPanel,
                    onTopAmbient = topScreenViewModel::showAmbient,
                    onTopDynamicReader = topScreenViewModel::showDynamicReader,
                    onTopIdle = topScreenViewModel::showIdle,
                )
            },
            onAvailabilityChanged = { secondScreenAvailable = it },
            onDecorViewChanged = { view ->
                bottomUiBridge.attach(view)
                if (view != null) duoSession.requestFocus()
            },
        )
        secondScreenAvailable = secondScreen.hasSecondaryDisplay()
        requestNotificationPermissionIfNeeded()
        handleOpenDownloadsIntent(intent)
        lifecycleScope.launch {
            val pending = PendingReportManager(getSharedPreferences("bili_prefs", MODE_PRIVATE))
            BilibiliRepository().flushPendingReports(pending)
        }
        setContent {
            BilibiliPureTheme(darkTheme = true) {
                LaunchedEffect(secondScreenAvailable) {
                    playerController.setEnabled(secondScreenAvailable)
                }
                if (secondScreenAvailable) {
                    // Top screen shows the read-only preview layer until the
                    // first video frame is rendered, then becomes surface-only.
                    TopScreenHost(
                        controller = playerController,
                        viewModel = topScreenViewModel,
                        playerViewModel = playerViewModel,
                    )
                } else {
                    MainScreen(
                        openDownloadsRequest = openDownloadsRequest,
                        dualScreen = false,
                        playerViewModel = playerViewModel,
                        playerController = null,
                        duoSession = duoSession,
                    )
                }
            }
        }
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
        setIntent(intent)
        handleOpenDownloadsIntent(intent)
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

    private fun handleOpenDownloadsIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            openDownloadsRequest.value++
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    openDownloadsRequest: StateFlow<Int> = MutableStateFlow(0),
    dualScreen: Boolean = false,
    playerViewModel: PlayerViewModel = viewModel(),
    playerController: PlayerController? = null,
    duoSession: DuoSessionViewModel = viewModel(),
    onTopVideoFocused: (TopPreviewUi) -> Unit = {},
    onTopFocusCleared: () -> Unit = {},
    onTopPerson: (TopPersonUi) -> Unit = {},
    onTopPanel: (TopPanelUi) -> Unit = {},
    onTopAmbient: (TopAmbientUi) -> Unit = {},
    onTopDynamicReader: (TopDynamicReaderUi) -> Unit = {},
    onTopIdle: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination


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
        duoSession.backRequests.collect {
            navController.popBackStack()
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

    Scaffold(
        bottomBar = {
            DuoModeBar(session = duoSession, enabled = dualScreen)
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        onNavigate = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
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
                popExitTransition = { slideOutHorizontally(tween(180)) { it } },
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
                    onFocusCleared = onTopFocusCleared,
                    onTopAmbient = onTopAmbient,
                    session = duoSession,
                )
            }
        }
    }
}
}
