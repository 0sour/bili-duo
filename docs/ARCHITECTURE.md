# bili-duo 架构设计 v0.1

> 目标：上屏只渲染视频，下屏 `Presentation` 承载全部 UI；播放器、导航、输入、
> 焦点各只有一份所有权。本文是实现基线，当前版本与测试状态见
> [`README.md`](../README.md)。

---

## 1. 总览

```
┌─────────────────────────────────────────────────────────────┐
│ MainActivity（上屏，1920×1080）                              │
│  · TopScreen：PlayerView(useController=false)               │
│  · DuoSessionViewModel（Activity-scoped）                   │
│  · PlayerController（唯一 ExoPlayer）                       │
│  · InputRouter（dispatchKeyEvent → 模式路由）                │
│  · SecondScreenController（查找/显示/销毁 Presentation）      │
└───────────────┬─────────────────────────────────────────────┘
                │ 共享 ViewModel / StateFlow / 命令
┌───────────────▼─────────────────────────────────────────────┐
│ SecondScreenPresentation（下屏，1280×960）                   │
│  · LifecycleOwner + ViewModelStoreOwner + SavedStateRegistry │
│  · ComposeView → DuoApp                                     │
│  · NavHost / 页面 / 焦点 / Overlay / ModeBar                 │
└─────────────────────────────────────────────────────────────┘
```

数据流：

```
UI 事件 ──▶ PlayerController ──▶ ExoPlayer ──▶ 上屏 Surface
   ▲                │
   └──── StateFlow ─┘（播放状态、进度、清晰度、音量、弹幕）
```

- 上屏只读 `PlayerController.player` 和 `isPlaying`（用于屏幕常亮），**不订阅进度**。
- 下屏订阅 `PlaybackState`，进度采样 250ms。
- 所有播放命令从下屏或 InputRouter 进入 `PlayerController`，不直接操作 ExoPlayer。

---

## 2. 包结构

```
cn.xfangfang.biliduo/
├── MainActivity.kt                 // 上屏宿主 + 生命周期
├── BilibiliApp.kt
├── duo/
│   ├── DuoSessionViewModel.kt      // 模式、导航栈、overlay、焦点、视频会话
│   ├── SecondScreenController.kt   // DisplayManager 监听、show/dismiss
│   ├── SecondScreenPresentation.kt // Presentation + 五个 owner + ComposeView
│   ├── InputRouter.kt              // 键位映射、模式路由、去重、长按
│   ├── KeyDeduplicator.kt
│   └── BottomUiBridge.kt           // decorView 转发 / UI 命令
├── player/
│   ├── PlayerController.kt         // 唯一 ExoPlayer + 命令 + 状态
│   ├── PlaybackState.kt
│   ├── QualityController.kt
│   └── danmaku/                    // Phase 3
├── ui/
│   ├── DuoApp.kt                   // 下屏 Compose 根：NavHost + ModeBar
│   ├── navigation/DuoNav.kt
│   ├── shell/                      // Rail、AppBar、ModeBar
│   ├── home/ search/ detail/ player/ comments/ history/ favorites/
│   ├── downloads/ profile/ settings/ login/ channel/
│   ├── components/                 // VideoCard、Chip、FocusRing、Sheet…
│   └── theme/                      // DESIGN_SYSTEM 落地
├── data/                           // 复用基座：api/repository/local/download
└── util/
```

原则：

- `duo/` 只做窗口、输入、会话；不写业务 UI。
- `player/` 只做播放器与播放状态；不引用 Compose。
- `ui/` 只做展示与交互；通过 `PlayerController` 和 repository 拿数据。
- 现有 `ui/player/PlayerScreen.kt` 只在**无下屏的单屏回退**保留；双屏路径不再使用。

> **2026-11-03 实现状态**：Phase 0 的 `SecondScreenController`/`SecondScreenPresentation`
> 已落地；Phase 1 的 `PlayerController`、`TopPlayerSurface`、`PlayerConsoleScreen`
> 放在 `ui/player/`（实际代码位置，避免和现有 `ui/player` 包重复）；Phase 2 的
> `DuoSessionViewModel`/`InputRouter`/`BottomUiBridge` 在 `duo/`，焦点/模式条在
> `ui/duo/`；Phase 3 首页在 `ui/home/`，`DuoRail` 提供 64dp 一级导航，Home 是启动页。
> 下屏 `Presentation` 当前固定深色主题；番剧/影视/排行 tab、弹幕和完整焦点恢复
> 尚未实现。

---

## 3. 所有权

| 对象 | 所有者 | 生命周期 | 说明 |
|---|---|---|---|
| `ExoPlayer` | `PlayerController` | Activity | 全应用唯一；Presentation 重建不重建 |
| `PlayerView` | 上屏 Activity | Activity | 只 attach/detach Surface |
| 当前 bvid/cid/分P | `PlayerViewModel` | Activity | 当前实现；暂不迁移 |
| 播放状态/进度 | `PlayerController` | Activity | StateFlow |
| 当前模式 | `DuoSessionViewModel` | Activity | BROWSE / PLAYER / OVERLAY（PLAYER 下单一焦点树） |
| 下屏导航栈 | `NavController`（Presentation 内） | Presentation | 后台只 hide、不 dismiss，所以能跨 Home 保留 |
| Overlay | `DuoSessionViewModel` | Activity | 单层 quality/speed；栈式 overlay 后续 |
| 焦点 key | `DuoSessionViewModel` | Activity | 每页面一个 key（未实现，当前用 focusRequester） |
| 页面级 ViewModel | Presentation 的 `ViewModelStore` | Presentation | 只放临时 UI 状态 |
| 下屏 Compose 树 | `SecondScreenPresentation` | Presentation | 仅 Activity destroy 时 dismiss |

> 关键：Presentation 的 `ViewModelStore` 会随 dismiss 销毁；任何需要跨重建的状态
> 必须放 Activity-scoped `PlayerViewModel`/后续 `DuoSessionViewModel`，不能依赖
> Presentation 的 `viewModel()`。Phase 1 的 `onStop` 只 `hide()`，不 `dismiss()`，
> 这样 Home/返回不会销毁下屏导航和播放器。

---

## 4. 显示与 Presentation

### 4.1 查找下屏

```kotlin
fun findSecondaryDisplay(ctx: Context): Display? {
    val dm = ctx.getSystemService(DisplayManager::class.java)
    return dm.displays.firstOrNull { d ->
        d.displayId != Display.DEFAULT_DISPLAY &&
        d.state != Display.STATE_OFF &&
        (d.flags and Display.FLAG_PRESENTATION) != 0
    }
}
```

- Duo 实测：`displayId=2`，1280×960，`FLAG_PRESENTATION`，`canHostTasks=false`。
- 不要用 `displayId == 2` 写死；用 flags + 尺寸兜底（1280×960）。
- 不要尝试 `ActivityOptions.setLaunchDisplayId`；下屏不能放 Activity。

### 4.2 控制器

```kotlin
class SecondScreenController(
    private val activity: Activity,
    private val session: DuoSessionViewModel,
) {
    private var presentation: SecondScreenPresentation? = null
    private var sessionId = 0

    fun start() { /* 注册 DisplayListener，showIfPossible() */ }
    fun stop()  { /* dismiss，清空 bridge 引用 */ }

    private fun showIfPossible() {
        val display = findSecondaryDisplay(activity) ?: return
        if (presentation?.isShowing == true) return
        val id = ++sessionId
        presentation = SecondScreenPresentation(activity, display, session) { ... }
            .also { p ->
                runCatching { p.show() }.onFailure {
                    presentation = null
                    session.onSecondScreenUnavailable(it)
                }
            }
    }
}
```

- `DisplayListener` 监听 `DISPLAY_ADDED/REMOVED/CHANGED`；300ms debounce，避免热插拔抖动。
- `show()` 可能抛 `WindowManager.InvalidDisplayException`；捕获后回退单屏并提示。
- `stop()` 先 `dismiss()`，再清 `BottomUiBridge` 的 decorView 引用，防止向已销毁窗口转发按键。
- 旧实例回调必须校验 `sessionId` / `isShowing`，不允许更新新 UI。

### 4.3 Presentation

```kotlin
class SecondScreenPresentation(
    private val activity: ComponentActivity,
    display: Display,
    private val content: @Composable () -> Unit,
) : Presentation(activity, display),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    ActivityResultRegistryOwner, OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle get() = lifecycleRegistry
    override val viewModelStore get() = store
    override val savedStateRegistry get() = saved.savedStateRegistry
    override val activityResultRegistry get() = activity.activityResultRegistry
    override val onBackPressedDispatcher get() = activity.onBackPressedDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saved.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val decor = window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeOnBackPressedDispatcherOwner(this)

        val owner = this
        setContentView(ComposeView(context).apply {
            setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides owner,
                    LocalOnBackPressedDispatcherOwner provides owner,
                ) {
                    DuoTheme { content() }
                }
            }
        })
    }

    override fun onStart() { super.onStart(); lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    override fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onStop()
    }
}
```

注意事项：

- **不要用 Presentation 的 Context 当 Activity**：主题、`LocalContext.current as Activity`、
  文件选择器、`AlertDialog` 都会崩。需要 Activity 的操作通过 `RemoteActions` 回调上屏。
- `setContentView` 前设置 owner；否则 `viewModel()`、Coil、Navigation 会异常。
- 还要提供 `ActivityResultRegistryOwner` + `OnBackPressedDispatcherOwner`：
  activity 1.8.2 没有 `setViewTreeActivityResultRegistryOwner`，用
  `CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner)` 提供；
  BackDispatcher 用 `setViewTreeOnBackPressedDispatcherOwner`。否则
  `rememberLauncherForActivityResult` / `BackHandler` 会崩。
- 主题必须是不依赖 Activity 的 `MaterialTheme`；不要用基座里 cast Activity 的主题。
- Presentation 没有 `onDestroy`；`onStop` 里置 DESTROYED + `store.clear()`，
  控制器负责 dismiss 幂等。**注意**：这个 `onStop` 是在 `dismiss()` 时触发的；
  Activity 普通后台切换只调用 `Presentation.hide()`，不会销毁 Compose/ViewModel。
- 无下屏时不要显示黑屏：`MainActivity` 用 `hasSecondaryDisplay()` / 可用性回调
  把 `MainScreen()` 回退到上屏（MuMu、合盖时可用）。

### 4.4 重建策略

| 事件 | 行为 |
|---|---|
| Activity onStart | 显示 Presentation（已有实例则 `show()`，保留状态） |
| Activity onStop | `player.onHostStop()` 暂停；`Presentation.hide()`，不 dismiss |
| 显示热插拔 | 移除时 dismiss + 单屏回退；恢复时重建 Presentation |
| 配置变化 | Activity 处理 `orientation|screenSize|keyboardHidden`，不重建 |
| 进程死亡 | `SavedStateHandle` 恢复 bvid/cid/position；重新加载 playurl（Phase 2） |

---

## 5. PlayerController

实际实现（2026-11-03，`ui/player/PlayerController.kt`）：

```kotlin
class PlayerController(
    context: Context,
    private val viewModel: PlayerViewModel,   // Activity-scoped 数据层
    private val scope: CoroutineScope,        // lifecycleScope
) {
    val player: ExoPlayer
    val state: StateFlow<State>

    fun setEnabled(enabled: Boolean)          // 仅双屏路径启用
    fun togglePlay() / play() / pause()
    fun seekTo(ms: Long) / seekBy(deltaMs: Long)
    fun setSpeed(speed: Float) / setVolume(volume: Float)
    fun retry()
    fun reportNow()
    fun onHostStop()                          // 后台暂停
    fun stop()                                // 退出播放：上报 + clearMediaItems
    fun release()
}
```

### 5.1 状态

```kotlin
data class State(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val error: String? = null,
    val ended: Boolean = false,
)
```

- `positionMs` 由 250ms 轮询 + `Player.Listener` 更新；下屏 Slider 读取该值。
- 观察 `PlayerViewModel.uiState`：`videoUrl` 变化时构建
  `ProgressiveMediaSource`（DASH 时 `MergingMediaSource` 合并视频/音频）。
- 切清晰度：同一 cid，target 用当前 `currentPosition`；切分P：target 用新页的
  `resumePositionMs`；首次准备也用 `resumePositionMs`。
- 播完：先上报，再 `PlayerViewModel.selectPage(next)`；最后一页置 `ended`。
- 错误：`PlaybackException` 写到 `State.error`，控制台显示重试。
- 退出：`reportNow()` → `stop()` → `clearMediaItems()`；不 release，除非 Activity
  destroy。后台：`onHostStop()` 里 `pause()`，不 clear。

### 5.2 上屏绑定

- `TopPlayerSurface` 只在 `state.isActive` 时创建 `PlayerView`，
  `useController=false`、`setShowNextButton(false)`、`setShowPreviousButton(false)`、
  `resizeMode=FIT`、`keepScreenOn=true`；
- 监听 Activity lifecycle：`ON_STOP` 时 `view.player=null`，`ON_START` 重新绑定；
- 不订阅 `positionMs` 做 UI；上屏没有任何 Compose 控件/手势。

---

## 6. 输入

详见 [`ui/INPUT_AND_FOCUS.md`](ui/INPUT_AND_FOCUS.md)。实现要点：

```kotlin
class InputRouter(
    private val session: DuoSessionViewModel,
    private val bridge: BottomUiBridge,
) {
    fun dispatch(event: KeyEvent): Boolean {
        // ACTION_DOWN only; 40ms same-key dedupe; ACTION_UP is consumed for
        // keys whose DOWN was handled/forwarded.
        return when {
            session.overlay.value != null ->
                if (isBack(event)) closeOverlay() else forwardToBottom(event)
            else -> forwardToBottom(event)
        }
    }
}
```

- `MainActivity.dispatchKeyEvent(event)` 只在 `secondScreenAvailable` 时先给
  `InputRouter`；单屏回退不拦截。
- 播放中没有 `TOP_PLAYER` / `BOTTOM_UI` 双模式：`DuoMode.PLAYER` 下 D-pad、A、
  X、START、LB/RB 全部转发给下屏同一棵 Compose 焦点树（这里 A/B/X 是 canonical
  Xbox keycode；实体丝印对应 B/A/Y）。A 翻译为合成的 `DPAD_CENTER`；B 走
  `requestBack()`。Retro 固件在 InputRouter 入口做 A↔B、X↔Y 归一化。
- `BottomUiBridge.dispatch` 调
  `presentation.window.decorView.dispatchKeyEvent(event)`；失败/未显示时丢弃。
  MuMu/Duo 实测返回 `handled=true`，Compose 焦点为 `AndroidComposeView`。
- 全局键由控制台根 `onPreviewKeyEvent` 处理：canonical X（实体 Y）弹幕开关、
  START 弹幕/字幕设置、LB/RB 右栏 tab；canonical Y 不绑定。
- 去重：同 keyCode 40ms 窗口；只处理 ACTION_DOWN；A/B 和方向键 repeat 被吞掉，
  保证长按只移动一格。
- 触摸：`duoClickable` 直接聚焦并点击，没有“第一次触摸只切模式”的中间态。
- 返回：`DuoSessionViewModel.requestBack()` 先走屏幕级 `setBackInterceptor`
  （如直播输入框收键盘），再走播放器的 `setFocusBackInterceptor`
  （右栏 → 播放键），最后才通过 SharedFlow 让下屏 `navController.popBackStack()`。

---

## 7. 导航与状态恢复

- 下屏使用 `NavHost`；路由：

```kotlin
sealed interface DuoRoute {
    data object Home : DuoRoute
    data object Search : DuoRoute
    data object PlayerConsole : DuoRoute
    data object History : DuoRoute
    data object Favorites : DuoRoute
    data object Downloads : DuoRoute
    data object Profile : DuoRoute
    data object Settings : DuoRoute
    data object Login : DuoRoute
    data class Channel(val mid: Long) : DuoRoute
}
```

- 一级页面（Home/Search/History/Favorites/Profile）各自保留返回栈；切 rail 时恢复。
- `PlayerConsole` 是路由，但进入/退出由 `PlayerController` 状态驱动：
  - `play()` 成功 → `navigate(PlayerConsole)`；
  - 退出播放 → `popBackStack()` 回来源列表（首页/搜索/历史/收藏）。
  - 不再有 `Detail` 路由：列表点击视频直接进入 `PlayerConsole`，简介与 UP
    主信息在播放控制台的「简介」tab。
- 保存：
  ```kotlin
  // onStop
  session.navState = navController.saveState()
  session.currentRoute = navController.currentBackStackEntry?.destination?.route
  ```
  恢复：
  ```kotlin
  val navController = rememberNavController()
  LaunchedEffect(Unit) {
      session.navState?.let { navController.restoreState(it) }
  }
  ```
- 如果 `restoreState` 失败，回退到 Home，不崩溃。
- 焦点 key 与 tab 选择存入 session；页面重建后按 key 恢复。

---

## 8. 数据层

- 复用基座：`BilibiliApi`（Retrofit + WBI + WAF 头）、`BilibiliRepository`、
  `PassportApi`、`AppSettings`、`DownloadManager`、`PlaybackProgressManager`。
- 新增：
  - `HomeRepository/HomeViewModel`：推荐流、热门、番剧、影视。
  - `RelatedRepository`：相关推荐（`x/web-interface/archive/related`）。
  - `DanmakuRepository`（Phase 3）：XML/DASH 弹幕。
  - `LiveRepository`、`BangumiRepository`（Phase 4）。
- 所有网络调用返回 `Result<T>`；错误在 ViewModel 映射为中文 `UiError`。
- 不在 UI 层拼 URL、不打印 cookie/query；日志过滤 token。

### 8.1 API 风险

- `bilibili-API-collect` 已于 2026-01-28 因律师函永久关停，不能再作为文档来源。
- 新接口以**仓库现有实现**和**运行验证**为准；bilimiao2 仅作只读参考（GPL-3.0，
  移植代码需评估整体许可证）。
- 关键接口：推荐流、相关推荐、番剧、直播、弹幕、关注动态。每个接口加降级/空态，
  不阻塞主流程。
- 直播接口（2026-11-04 验证）：`api.live.bilibili.com` 需要 `Referer/Origin:
  https://live.bilibili.com/` + `buvid3/buvid4` + 登录 Cookie；列表用
  `room/v1/area/getRoomList`，分区用 `room/v1/Area/getList`，房间信息用
  `room/v1/Room/get_info` + `live_user/v1/UserInfo/get_anchor_in_room`，播放用
  `xlive/web-room/v2/index/getRoomPlayInfo`。`second/getList` 与 `getInfoByRoom`
  会返回 `-352`，不要作为主路径。
- 直播弹幕：`room/v1/Danmu/getConf` 取 token/host，连 `wss://<host>/sub`；
  16 字节头（长度/头长/version/op/seq），auth op=7、心跳 op=2/30s、
  消息 op=5；请求 `protover=2` 的 zlib 帧，避免引入 brotli。只解析
  `DANMU_MSG`、`SUPER_CHAT_MESSAGE`、`SUPER_CHAT_MESSAGE_DELETE`，
  其余 entry/gift/rank 消息丢弃。发送走 `msg/send`（csrf/csrf_token）。
- 直播 HLS 的窗口非常短；线路选择优先 `ts` 再 `fmp4`（实测 ts 窗口更稳，
  清晰度切换不再触发 BehindLiveWindow）。`PlayerController` 仍对
  `ERROR_CODE_BEHIND_LIVE_WINDOW` 自动清 URL 并重新取 playurl 作为兜底，
  不要把它当成终端错误展示给用户；`PlayerView.keepContentOnPlayerReset`
  用于切换线路时保留旧帧。
- 关注动态（2026-11-04 验证）：`x/polymer/web-dynamic/v1/feed/all`，需要登录
  Cookie；`type=all/video/draw` 对应筛选，`offset` + `has_more` 分页，`page`
  固定 1，`features` 用 `itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote`。
  响应重点在 `items[].modules.module_author/module_dynamic/major` 和
  `items[].orig`；opus 图片在 `major.opus.pics`，视频在 `major.archive`，
  转发原动态在 `orig`。`DynamicModels.kt` 做降级映射，字段缺失不崩溃；
  只读浏览，不调用发布/转发/点赞写接口。
- 动态 UP 筛选（2026-11-04）：`x/polymer/web-dynamic/v1/feed/space`，参数
  `host_mid` + `offset`，返回结构与 `feed/all` 相同；作者 chips 从当前已加载的
  动态里抽取（`authorMid`/`authorName`/`authorFace`，最多 24 个），选择作者后
  清空分类筛选，回到「全部 UP」重新加载关注流。`feed/space` 在未登录且没有
  buvid Cookie 时会返回 412，UI 显示错误 + 重试；登录态路径待验收。
- 关注主播行（2026-11-04）：`x/relation/followings` 取第一页关注，再用
  `room/v1/Room/get_status_info_by_uids?uids[]=...` 批量查直播状态，过滤
  `live_status == 1`、按 `online` 排序；每页最多 50 个 uid。该接口同样依赖
  登录 Cookie/buvid，失败时静默隐藏头像行，不影响直播列表。
- 动态阅读层：下屏 `DynamicReaderLayer` 是内容区内的模态层（rail 仍可见但
  D-pad 被根 `onPreviewKeyEvent` 独占），上屏由 `TopScreenMode.DYNAMIC` 渲染
  DETAIL/GALLERY/TEXT 三种只读场景。返回拦截器以 `overlay.kind + imageIndex`
  为 key，保证画册按 B 回到详情时保留当前图片。
- R8/release（2026-11-04）：Retrofit 在运行时反射读取 suspend 方法的
  `Continuation<? super ApiResponse<T>>` 泛型；`proguard-rules.pro` 必须保留
  `data.api.**`、`kotlin.coroutines.Continuation`、`retrofit2.Call/Response`
  和 `Signature` 属性，否则 release 包在首次请求时报
  `Class cannot be cast to ParameterizedType`，而 debug 包完全正常。

---

## 9. 线程与性能

| 任务 | 线程 |
|---|---|
| ExoPlayer 创建/命令 | Main |
| Retrofit 请求 | OkHttp 线程池（suspend） |
| JSON/DB | IO |
| 弹幕解析 | Default/IO |
| 图片 | Coil（OkHttp） |

- 下屏进度更新 4Hz；`state.positionMs` 用 `sample(250)`。
- 上屏不订阅 Compose 状态；`PlayerView` 只绑 player。
- 列表使用 `key` + immutable model；避免整页重组。
- Coil：内存 16MB / 磁盘 50MB；快速滚动暂停请求。
- 目标：冷启动 < 2.5s 到下屏首页；播放首帧 < 1.5s；下屏焦点移动 < 16ms/帧。

---

## 10. 错误与降级

| 场景 | 处理 |
|---|---|
| 下屏不存在 | 单屏模式：`MainScreen()` 回退到上屏（MuMu/合盖可用），不阻断使用 |
| Presentation show 失败 | 捕获异常；回退单屏 UI；记录日志（不含敏感信息） |
| 按键转发失败 | 丢弃该键；ModeBar 提示「下屏未就绪」；不崩溃 |
| playurl 失败 | 下屏错误面板；重试 / 换清晰度 / 返回 |
| 登录过期（-101） | 清登录态；提示重新登录；不清历史/下载 |
| WBI -403 / WAF 412 | 刷新 WBI key / 重试一次；仍失败给「稍后再试」 |
| 网络断开 | 顶部黄条；本地历史/下载可用；恢复后自动重试当前请求 |
| 播放器解码错误 | 尝试软解/换清晰度；仍失败提示设备不支持 |
| 进程死亡恢复 | 重新加载 playurl；从 SavedState 的 position seek |

---

## 11. 测试策略

### 11.1 单元测试

- `InputRouter`：模式 × 按键矩阵；去重；长按；overlay 吞键。
- `KeyDeduplicator`：40ms 窗口、deviceId、A/Enter 合并。
- `OverlayStack`：push/pop、B 只关栈顶、焦点恢复。
- `NavState`：save/restore、失败回退。
- `QualityController`：清晰度映射、切换后位置保持。

### 11.2 MuMu（单屏）

- Phase 0 单屏回退：无下屏时 `MainActivity` 直接显示 `MainScreen()`，
  可在 MuMu 验证页面布局、焦点顺序、overlay、空态、导航、鼠标触摸。
- 不能验证：真 Presentation、双屏焦点转发、热插拔、双屏生命周期。
- 下屏预览可用 `wm size 1280x960; wm density 320` 单独跑一个 Activity 做像素级检查。

### 11.3 Duo（真机）

- Presentation 显示/销毁/重建；
- 手柄全键位、长按、去重；
- 触摸与手柄混用；
- 后台恢复、锁屏、拔耳机；
- 双屏截图（`screencap -p -a`）；
- 120Hz/HDR 播放。

### 11.4 验收门

- Phase 0：下屏 UI 可触摸、上屏黑屏、重建不叠加。
- Phase 1：上屏播放、下屏控制、退出/恢复。
- Phase 2：键位矩阵全过、无双触发、overlay 独占。
- Phase 3：首页/评论/相关/弹幕可用；MuMu + Duo 各一轮。

---

## 12. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| Presentation Context 不是 Activity | 主题/对话框/文件选择器崩溃 | 主题不 cast；需要 Activity 的操作走 RemoteActions |
| Compose 焦点在非焦点窗口 | 方向键无效或焦点错乱 | decorView 转发 + 真机验证；语义焦点作为降级 |
| IME 不出现在下屏 | 搜索无法输入 | 触摸 IME 优先；应用内键盘兜底 |
| `focusRestorer` 在 Compose 1.6 不可用 | 列表焦点丢失 | 自建 FocusRequester + BringIntoView；必要时升级 BOM |
| raw/nav 双事件 | 焦点跳两格 | 40ms 去重 + 只用原始 KeyEvent |
| Surface detach 黑帧 | 切换闪黑 | 复用 player；`setKeepContentOnPlayerReset(false)`；attach 后恢复 |
| HDR/120Hz 兼容 | 花屏/掉帧 | 自动回退 SDR；设置可关 HDR；真机验证 |
| API 变更/法律风险 | 功能失效 | 接口降级、空态；不内置官方商标/资源 |
| GPL 污染 | 许可证冲突 | bilimiao2 只读参考；移植前确认许可证策略 |
| 下屏热插拔 | 窗口泄漏/崩溃 | DisplayListener + sessionId + dismiss 幂等 |

---

## 13. 实施顺序

1. `DuoSessionViewModel` + `SecondScreenController` + `SecondScreenPresentation`（Phase 0）。
2. `PlayerController` + 上屏 `TopScreen` + 下屏 `PlayerConsole`（Phase 1）。
3. `InputRouter` + `BottomUiBridge` + `duoClickable` + `ModeBar`（Phase 2）。
4. `DuoApp` 导航骨架 + 设计系统 token + 通用组件（Phase 2/3）。
5. 首页/搜索/简介/评论/相关（Phase 3）。
6. 弹幕/直播/番剧/动态（Phase 3/4）。
7. 性能、无障碍、真机验收（Phase 4/5）。
