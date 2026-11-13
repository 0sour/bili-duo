# Retroid Pocket Duo / AYN Thor 双屏应用适配指南

> 来源：`bili-duo` 双屏 B 站客户端的实战经验（2026-11）。
> 面向“上屏显示内容、下屏承载操作 UI”的 Android 应用。文中代码是通用骨架，
> 不依赖具体业务，可直接拷到其他项目。
>
> 适用设备：Retroid Pocket Duo、AYN Thor 等上下双屏 Android 掌机。
> 单屏手机/平板可走“无下屏回退”，但不是本文重点。

---

## 0. 十条结论先行

1. **下屏不是 Activity，是 `Presentation`**。Duo 下屏 `canHostTasks=false`，
   普通 Activity 放不上去。
2. **上屏只放内容 Surface**，不要放可聚焦控件、手势、OSD。
3. **手柄事件先到上屏 Activity**，必须单向转发给下屏；同一按键只能有一个消费者。
4. **下屏 `Presentation` 获得焦点后，Activity 收不到系统返回键**，要自己接。
5. **下屏的 IME 状态不要信 `WindowInsets.isImeVisible`**，要自己记账。
6. **`Presentation` 里不要用 `Dialog` / `AlertDialog`**，会 window type mismatch 崩溃。
7. **整个应用只维护一棵 Compose 焦点树**；所有按键提示、焦点环都围绕它设计。
8. **焦点环画在组件边界外**，滚动容器/列表里必须预留内边距，否则会被裁。
9. **播放器和会话状态放 Activity 作用域**，`Presentation` 只做 UI。
10. **尺寸按 dp 设计**：下屏约 640×480dp，上屏约 832×468dp，不要按像素写死。

### 0.1 新项目接入顺序

1. Activity 锁横屏；上屏只放内容 Surface。
2. `DisplayManager` 按 `FLAG_PRESENTATION` 找下屏，找不到走单屏回退。
3. 写 `Presentation` 子类，补齐 5 个 owner，塞 `ComposeView`。
4. Activity `onStart/onStop/onDestroy` 控制 show/hide/dismiss。
5. 加 `BottomUiBridge`，把 Activity 收到的按键转发给 Presentation 的 decorView。
6. 加 `InputRouter`：A→`DPAD_CENTER`、B→返回、长按吞 repeat、40ms 去重。
7. Presentation 里接 BACK/ESC + `OnBackInvokedCallback`，做 250ms 去抖。
8. 建立全局 overlay/返回拦截器 + 自维护 IME 状态。
9. 设计一棵焦点树：明确首焦点、边界 `Cancel`、焦点环留白、overlay 焦点恢复。
10. 播放器/会话放 Activity 作用域；下屏只读状态、发命令。
11. 按第 10 节的方法在真机上逐项验收，尤其是返回、IME、D-pad、截图。

---

## 1. 硬件与显示模型

以 Retroid Pocket Duo 实测为准：

| | 上屏 | 下屏 |
|---|---|---|
| 逻辑 displayId | `0`（不要写死） | `2`（不要写死） |
| 原生分辨率 | 1080×1920，横屏后 1920×1080 | 1280×960 |
| density | 约 369dpi（≈2.31x） | 320dpi（2x） |
| dp 尺寸 | 约 832×468dp | 640×480dp |
| `canHostTasks` | `true` | **`false`** |
| Display flags | 普通内屏 | **`FLAG_PRESENTATION`** |
| 触摸 | `INTERNAL` | **`EXTERNAL`** |
| 方向 | 横屏 | 横屏 |

关键结论：

- 下屏**只能**用 `Presentation`（或系统级 `VirtualDisplay` + 特殊权限，不推荐）。
  `startActivity` + `ActivityOptions.setLaunchDisplayId()` 在下屏会失败或落错屏。
- **不要硬编码 displayId=2**。不同固件、不同机型、外接屏场景都可能变化。
  正确做法是按 `FLAG_PRESENTATION` 查找：

```kotlin
fun findBottomDisplay(dm: DisplayManager): Display? =
    dm.displays.firstOrNull { d ->
        d.displayId != Display.DEFAULT_DISPLAY &&
            d.state != Display.STATE_OFF &&
            (d.flags and Display.FLAG_PRESENTATION) != 0
    }
```

- 下屏可以休眠/暂时消失（合盖、系统切换）。要监听 `DisplayManager` 的
  add/remove/changed，随时准备重新 `show()`。

---

## 2. 双屏架构

推荐结构：

```
┌─────────────────────────────────────────────┐
│ Activity（Display 0，上屏）                   │
│  - PlayerView / 内容 Surface（useController=false）│
│  - 接收所有物理按键（手柄/键盘/系统返回）        │
│  - Activity-scoped：PlayerController、Session   │
│  - 首帧前可叠加只读 Idle/Preview 场景           │
└───────────────┬─────────────────────────────┘
                │ BottomUiBridge：单向转发 KeyEvent
                ▼
┌─────────────────────────────────────────────┐
│ Presentation（Display 2，下屏）               │
│  - ComposeView = 完整应用 UI                 │
│  - 唯一的一棵焦点树                          │
│  - 消费按键 → 调用共享 Controller/ViewModel   │
└─────────────────────────────────────────────┘
```

设计原则：

- **一个播放器实例**：`PlayerController` 由 Activity 创建，视频渲染与下屏控制
  共享同一个 `StateFlow`。不要在 Composable 里 `new ExoPlayer`。
- **一个返回栈**：Navigation 放在**当前承载 UI 的 host** 里（默认下屏
  Presentation；交换布局后是 Activity）；另一个 host 只做展示。
- **一个输入消费者**：按键从 Activity 进来，要么 Activity 自己处理，要么整包
  转发给下屏，不能两边同时响应。
- **无下屏回退**：检测不到 `FLAG_PRESENTATION` 时，走普通单屏 UI（或提示
  “需要下屏”）。不要把双屏 UI 硬塞进上屏。

### 2.1 交换上下屏（可选功能）

有些用户希望“操作 UI 在上、视频在下”。Android 不允许把正在运行的 Activity
搬到 Display 2，也不允许在 Display 0 上创建 `Presentation`，所以只能**交换两个
host 的内容角色**：

```kotlin
// Activity 的 setContent：
if (secondDisplayAvailable) {
    if (AppSettings.displaySwapped) MainScreen(...)   // UI 在上
    else TopScreenHost(...)                           // 视频在上
}

// Presentation 的 content：
if (AppSettings.displaySwapped) TopScreenHost(...)    // 视频在下
else MainScreen(...)                                  // UI 在下
```

交换布局的三个配套点：

1. **输入目标要跟着 UI 走**。UI 在下屏时，InputRouter 把按键转发给
   Presentation 的 decorView；UI 在上屏时，转发给 Activity 自己的 ComposeView。
   可给 bridge 增加 `attachLocal(view)` + `setUseLocal(flag)`，不要写两套 A/B/
   repeat 逻辑。
2. **视频窗口设为 `FLAG_NOT_FOCUSABLE`**。否则下屏视频窗口可能抢走 Activity
   的按键焦点，D-pad 会“失灵”。
3. **导航状态会丢**。两个 host 各自 `rememberNavController()`，交换后新 host
   从 startDestination 重新开始。若不希望回到首页，可在 Activity 里存一个
   递增 token + 目标 route，新 host 首次组合时导航回去（只适用于简单页面；
   带参数的播放页建议直接回首页）。

不要把交换做成“Activity 搬到下屏”或“Display 0 上开 Presentation”，这两条路在
未 root 的 Duo 上走不通。

### 2.2 启动器快捷方式（按布局直接启动）

交换状态虽然持久化，但每次都要进个人页按按钮太绕。可以给 launcher 加两个
静态快捷方式「双屏启动（下屏控制）/（上屏控制）」，直接以指定布局启动：

```xml
<!-- res/xml/shortcuts.xml -->
<shortcut android:shortcutId="display_top_control" ...>
    <intent android:action="android.intent.action.VIEW"
            android:targetPackage="cn.xfangfang.biliduo"
            android:targetClass="cn.xfangfang.biliduo.MainActivity">
        <extra android:name="display_control" android:value="top" />
    </intent>
</shortcut>
```

配套点：

1. Manifest 给 Activity 挂 `android.app.shortcuts` meta-data；Activity 用
   `launchMode="singleTop"`，这样快捷方式点击落在已有实例上时走
   `onNewIntent`，不会新建第二个 Activity。**不要用 `singleTask`**：副屏
   launcher 的 re-home 需要新建任务，singleTask 会把 re-home intent 发回原实例
   （详见 2.3）。
2. `onCreate` 只在 `savedInstanceState == null` 时读 extra（避免配置变更/
   进程恢复时重复应用）；`onNewIntent` 读同一个函数。读完 `removeExtra`，
   避免后续重建又按旧值覆盖。
3. extra 只写 `AppSettings.displaySwapped` 并同步 `setVideoOnly()` /
   `BottomUiBridge.setUseLocal()`。因为写的是持久化设置，**下一次普通启动
   会保持上次选择的布局**。
4. 快捷方式只是入口，布局本身仍由 2.1 的 host 角色交换实现，不要另写一套
   双屏逻辑。

### 2.3 从副屏 launcher 启动：re-home 到默认屏

RP Duo 的下屏跑的是 AOSP `SecondaryDisplayLauncher`。它点图标/长按快捷方式时
会**以 Display 2 作为 launch display** 启动 Activity（`dumpsys` 里 Task 的
`displayId=2`）。这时 `getDisplays(DISPLAY_CATEGORY_PRESENTATION)` 只会返回
Display 2 自己，如果直接拿它建 Presentation，就会在 Activity 同一块屏上再盖
一层窗口，上屏则停在桌面。

正确做法是**把 Activity 送回默认屏**，而不是尝试在本屏双屏化：

```kotlin
// MainActivity.onCreate / onNewIntent 最前面
if (currentDisplayId() != Display.DEFAULT_DISPLAY) {
    val options = ActivityOptions.makeBasic()
        .setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
    startActivity(
        Intent(intent).apply {
            // 必须是新任务；否则 singleTask/singleTop 可能复用当前任务
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra(EXTRA_REHOMED, true)      // 防循环
        },
        options,
    )
    finish()
    return
}
```

配套点：

1. **`launchMode` 用 `singleTop`**。`singleTask` 会把上面的 startActivity 当作
   `LAUNCH_SINGLE_TASK` 发回同一个实例（logcat 里是 `result code=3`），
   Display 0 不会出现新任务；`singleTop` 既允许 `MULTIPLE_TASK` 建新任务，
   又保留运行中/后台点快捷方式走 `onNewIntent`。
2. **复制原 intent**，这样 `display_control`、`open_downloads` 等 extra 会带到
   新实例；`EXTRA_REHOMED` 是内部幂等标记，设备忽略 launch display 时停在
   单屏回退，不要无限重启。
3. **提前 return 后要保护 lateinit**：`onStart/onStop/onDestroy` 用
   `if (::secondScreen.isInitialized)` 包住，否则第一版会在 `onDestroy`
   崩 `lateinit property secondScreen has not been initialized`。
4. `SecondScreenController.findSecondaryDisplay()` 再加一条
   `display.displayId != activityDisplayId()`，双保险，避免同屏 Presentation。
5. 这个 re-home 只在 Activity 不在默认屏时发生；正常上屏启动、交换布局、
   运行中从下屏点图标（系统只投递 `onNewIntent`，不搬任务）都不受影响。

---

## 3. `Presentation` 宿主

### 3.1 必须补齐的 owner

`Presentation` 只是一个 `Dialog` 子类，**没有 Lifecycle/ViewModel/SavedState/
ActivityResult/BackDispatcher**。Compose、`viewModel()`、`BackHandler` 都依赖这些
owner，必须在 window decorView 上挂好：

```kotlin
class BottomPresentation(
    activity: ComponentActivity,
    display: Display,
    content: @Composable () -> Unit,
) : Presentation(activity, display),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    ActivityResultRegistryOwner,
    OnBackPressedDispatcherOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle get() = lifecycleRegistry
    override val viewModelStore get() = store
    override val savedStateRegistry get() = savedState.savedStateRegistry
    override val activityResultRegistry get() = activity.activityResultRegistry
    override val onBackPressedDispatcher get() = activity.onBackPressedDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val decor = window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeOnBackPressedDispatcherOwner(this)

        val view = ComposeView(context).apply {
            isFocusableInTouchMode = true   // 让硬件按键有焦点目标
            setContent { AppTheme { content() } }
        }
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStop() {
        // 用 CREATED 而不是 DESTROYED：Presentation 只是 hide()，之后还会
        // show() 复用同一棵 Compose 树。活动结束后由 Activity 负责 dismiss。
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onStop()
    }
}
```

> `bili-duo` 现有实现在 `Presentation.onStop` 里置 DESTROYED 并
> `store.clear()`。无论是否照抄，**持久状态都不要放在 Presentation 自己的
> ViewModelStore**，统一放 Activity 作用域或 Repository，这样 hide/show 甚至
> Presentation 重建都不会丢。

### 3.2 show / hide 的时机

- `Activity.onStart()` → `presentation.show()`
- `Activity.onStop()` → `presentation.hide()`（**不要 dismiss**，保留 Compose
  树、导航状态、播放会话）
- `Activity.onDestroy()` → `dismiss()` + 注销 DisplayListener
- `DisplayManager.onDisplayAdded/onDisplayChanged` → 下屏回来时重新 show

`show()` 可能过早（窗口 token 未就绪）而抛 `BadTokenException`，要 catch 并
在下次时机重试。

显示后补一次焦点：

```kotlin
presentation.window?.decorView?.post {
    presentation.window?.decorView?.requestFocus()
}
```

Presentation 的 `setCancelable(false)` 只影响返回键的默认处理，**不能阻止
BACK 被窗口吞掉**，所以仍然要自己接返回（见 4.4）。

### 3.3 `Presentation` 里能用什么、不能用什么

| 能力 | 结论 |
|---|---|
| Compose UI | ✅ 通过 `ComposeView`，记得包一层主题 |
| `Toast` | ✅ 一般可用 |
| `Dialog` / `AlertDialog` | ❌ window type mismatch 崩溃；改成布局内 overlay |
| `PopupWindow` | ⚠️ 容易跨窗口层级出问题，优先用 Compose overlay |
| `startActivity` | ⚠️ 必须 `FLAG_ACTIVITY_NEW_TASK`（Presentation 的 Context 不是 Activity） |
| `ActivityResult`（SAF、权限） | ✅ 但要用 Activity 的 registry；Compose 里共享 `LocalActivityResultRegistryOwner` |
| 系统分享 chooser | ❌ 在 Duo 上会弹出透明/不可操作的 `ChooserActivityLauncher`；改用剪贴板 + Toast 或自绘分享层 |
| 系统 IME | ✅ 可以弹，但状态判断有坑（见 4.5） |

结论：**所有模态都做成下屏 Compose 树内的 overlay**，配合一个统一的
`BackInterceptor`，不要用系统 Dialog。

---

## 4. 输入系统

### 4.1 事件路径

上屏 Activity 是输入的第一站：

```
物理手柄/键盘
   → Activity.dispatchKeyEvent
      → InputRouter
         ├─ 自己消费（导航/返回/确认）
         └─ BottomUiBridge.dispatch(event)
               → Presentation.window.decorView.dispatchKeyEvent
                  → Compose 焦点树
```

- **单向**：只从 Activity 往下屏转发，绝不反向。
- **单消费者**：一个按键在任一瞬间只能由一边处理。
- 转发目标是 **decorView**，不是 ComposeView；decorView 会把事件交给当前
  焦点节点。
- 转发时要记住 DOWN 是否被转发过，UP 才能成对转发；否则长按/按键状态会乱。

### 4.2 按键语义（以 canonical Xbox 为准）

下表以 Duo 的实体丝印为例；其他机型以实际丝印为准，但**逻辑语义应保持一致**。

| 物理键 | keycode | 语义 |
|---|---|---|
| D-pad | `DPAD_*` | 移动焦点 |
| B（实体下方） | `BUTTON_B` | **确认**（不是返回） |
| A（实体右侧） | `BUTTON_A` | **返回/关闭** |
| X（实体上方） | `BUTTON_X` | 应用自定义（本项目未绑定） |
| Y（实体左侧） | `BUTTON_Y` | 应用自定义（刷新/点赞等） |
| START / MENU | `BUTTON_START` / `MENU` | 打开设置/菜单 |
| L1/R1 | `BUTTON_L1/R1` | 一级 tab 切换 |
| L2/R2 | `BUTTON_L2/R2` | 二级筛选/缩放 |

注意两个易混点：

- 物理手柄的 X/Y 是 `KEYCODE_BUTTON_X`（99）/`BUTTON_Y`（100），**不是**
  `KEYCODE_X`/`KEYCODE_Y`。键盘 X/Y 才是后者。代码里两个都要接受。
- Compose 的“点击”键是 `DPAD_CENTER`/`ENTER`，**`BUTTON_A/B` 不会自动触发
  Compose 的 click**。需要自己合成：

```kotlin
fun sendCenterTap(bridge: BottomUiBridge) {
    val now = SystemClock.uptimeMillis()
    bridge.dispatch(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
    bridge.dispatch(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
}
```

### 4.3 确认键与返回键不要照搬 Android 习惯

Duo 的实体丝印是 **B 确认、A 返回**。如果照搬 Android “A=确认、B=返回”，
用户拿在手里会一直按错。所有 UI 提示也要按**实体丝印**写。

### 4.4 系统返回键（最容易漏）

两个场景：

1. **上屏 Activity 持有焦点**：`Activity.dispatchKeyEvent` 能收到 BACK，
   交给 InputRouter 即可。
2. **下屏 Presentation 持有焦点**（用户在下屏操作时的常态）：
   Activity **完全收不到** BACK，必须在 Presentation 里接：

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_BACK ||
        event.keyCode == KeyEvent.KEYCODE_ESCAPE
    ) {
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dispatchBack()
        return true
    }
    return super.dispatchKeyEvent(event)
}

override fun onBackPressed() = dispatchBack()

// API 33+ 还要注册：
window?.onBackInvokedDispatcher?.registerOnBackInvokedCallback(
    OnBackInvokedDispatcher.PRIORITY_DEFAULT
) { dispatchBack() }
```

`dispatchBack()` 建议做 **250ms 去抖**，避免系统同时走 `onBackPressed` 和
`OnBackInvokedCallback` 导致双 pop。

返回优先级要和手柄 B 完全一致：

```
收 IME → 关 overlay / 页面内拦截器 → NavHost pop
```

**不要给 `Presentation` 用 `setCancelable(false)` 就以为返回被处理了**；
它只会让默认 dismiss 失效，按键仍然不会到 Activity。

### 4.5 IME：不要信 WindowInsets

在 `Presentation` 里，`WindowInsets.isImeVisible` 可能在冷启动/空闲时**误报
true**，导致第一次返回被当成“收键盘”吞掉。

正确做法：自己维护一个全局输入状态：

```kotlin
object ImeState {
    var shown = false          // 字段真正 show() 成功时置 true
    fun onShown() { shown = true }
    fun onHiddenByBack() { shown = false }
    fun onWindowHidden() { shown = false }
}
```

- 输入框调用 `keyboard.show()` 成功后 `onShown()`。
- 返回/B 第一次：如果 `shown`，只 `hide()` 并保留焦点，**不退出页面**。
- 第二次：正常返回。
- 首次 `show()` 可能被系统拒绝（Presentation 冷启动），可以延迟 300/600ms
  重试，但重试要有 generation/取消机制，避免 B 收键盘后又被旧重试弹出来。

### 4.6 长按与去重

掌机固件可能：

- 按住方向键持续发 repeat；
- 同一个 keycode 在几毫秒内重复发 DOWN（尤其无线/蓝牙）。

处理：

- 对 **方向、返回、肩键、X/Y** 吞掉 `repeatCount > 0`，长按只走一格/一次。
- 对同一 keycode 的 DOWN 做 **40ms 去重**。
- X/Y 的写操作（点赞/刷新）必须去重，避免长按刷请求。

### 4.7 固件“手柄风格”会换 keycode

Retroid Pocket Duo 的系统设置里可以切 Xbox/Retro 风格，ABXY 的 keycode 会互换。
**不要在业务层判断风格**；在输入入口做一次归一化，把所有页面统一成 canonical
Xbox 布局：

```kotlin
fun normalize(code: Int): Int = when (style) {
    Xbox -> code
    Retro -> when (code) {
        KEYCODE_BUTTON_A -> KEYCODE_BUTTON_B
        KEYCODE_BUTTON_B -> KEYCODE_BUTTON_A
        KEYCODE_BUTTON_X -> KEYCODE_BUTTON_Y
        KEYCODE_BUTTON_Y -> KEYCODE_BUTTON_X
        else -> code
    }
}
```

UI 提示始终写**实体位置**（B 确认、A 返回），不要写“逻辑键”。

### 4.8 摇杆/体感：不要全局拦截 generic motion

曾经在 `Activity.dispatchGenericMotionEvent` 里拦截游戏手柄 MotionEvent 来做
“右摇杆切 tab”，结果 **D-pad 的 HAT 事件也被吞，真机左侧方向键直接失灵**。

规则：

- **不要**在 Activity 层无差别消费 `dispatchGenericMotionEvent`。
- 只有当前有明确的摇杆消费者（例如图片查看器缩放/平移）时，才消费
  `AXIS_X/AXIS_Y`；`AXIS_HAT_X/AXIS_HAT_Y`（D-pad）必须放行。
- 消费条件要精确到 `SOURCE_JOYSTICK`/`SOURCE_GAMEPAD` + 当前 UI 状态。

### 4.9 触摸

- 下屏触摸是 `EXTERNAL`，Compose 正常处理，不需要特殊适配。
- **一次点击就直接生效**，不要设计“第一次点击只切模式、第二次才操作”。
- 手柄和触摸共用同一棵焦点树；触摸某一行时如果希望焦点跟随，主动
  `requestFocus()` 到该行，避免焦点环停留在上一项。

---

## 5. Compose 焦点系统

双屏应用的手感，80% 取决于焦点。

### 5.1 单棵树 + 明确边界

- 整个下屏只有一棵焦点树；overlay 打开时要把焦点限制在 overlay 内。
- Compose 的二维空间搜索经常“猜错”（例如从右栏 tab 按 Left 跳到左栏某个
  按钮）。**关键路径用 `focusProperties` 显式指定**，不要全靠自动搜索。
- 边界用 `FocusRequester.Cancel`：

```kotlin
Modifier.focusProperties {
    up = if (isFirst) FocusRequester.Cancel else previous
    down = if (isLast) FocusRequester.Cancel else next
    left = FocusRequester.Cancel
}
```

否则焦点会漏到全局侧栏/另一个区域。

### 5.2 初次焦点与“抢焦点”

常见 bug：页面内容一组合就自动 `requestFocus()`，结果用户还在左侧导航栏，
焦点已经被拽进内容，无法继续上下选择分类。

规则：

- **只在用户明确进入时聚焦内容**（Right/A/触摸），不要因为“内容被预览/组合”
  就自动聚焦。
- 页面导航后的首次聚焦延迟约 **120ms**（等布局/焦点树稳定）。
- 页面入口统一走一个 `requestPageFocus()`，在“侧栏锁定”期间直接跳过，
  避免内容先抢焦点、再被侧栏收回的双闪。

### 5.3 焦点环：画在边界外

外扩焦点环好看，但会带来裁切问题：

```
gap 4dp + ring 1.5dp + dark edge 3.5dp ≈ 外扩 5.75dp
```

规则：

- **环必须画在 `clip` 之前**，否则会被圆角裁掉。
- 滚动容器（`verticalScroll`/`horizontalScroll`）会按自身矩形裁切四个方向：
  内容内侧至少留 **6–8dp** 横向/纵向余量。
- Lazy 列表的 `contentPadding` **保护不了**环：bring-into-view 会把 item 顶到
  viewport 边缘，滚掉 padding。做法是 item 外层加 **7dp 透明余量**，环画在内层。
- 最后一行聚焦后延迟 ~80ms `animateScrollTo(maxValue)`，否则环底边被裁。
- 超视口长卡片（动态图文）聚焦时显式 `scrollToItem(index)` 对齐顶部。

### 5.4 焦点恢复

- overlay 关闭后，把焦点还给**触发它的控件**，延迟 120–300ms。
- 所有关闭路径走同一个 `closeXxx()`，否则某个路径会绕过 `onCancel` 的焦点恢复。
- 输入框：B 收键盘时**保留焦点**，再按 B 才返回/关层；不要收完键盘就把焦点
  扔回页面。

### 5.5 肩键与全局侧栏

本项目的布局是“左侧全局导航栏 + 内容区”。焦点在侧栏时，页面根节点不在焦点树
里，页面的 `onPreviewKeyEvent` 收不到 L1/R1。

解决模式：**全局肩键分发器**。

```kotlin
object ShoulderDispatcher {
    private val stack = ArrayDeque<(KeyEvent) -> Boolean>()
    fun push(handler: (KeyEvent) -> Boolean) { stack.addLast(handler) }
    fun remove(handler: (KeyEvent) -> Boolean) { stack.remove(handler) }
    fun dispatch(e: KeyEvent): Boolean = stack.lastOrNull()?.invoke(e) == true
}
```

页面用 `duoTabCycling` 注册自己的 L1/R1/L2/R2 动作；侧栏聚焦时由根节点转发并
吞掉按键。这样侧栏聚焦也能切页面 tab。

### 5.6 切 tab 后的焦点约定

必须全应用统一，否则用户记不住：

- 本项目：**L1/R1 切一级 tab 后聚焦内容**；**L2/R2 切二级筛选后焦点停在新
  chip**；直接 A/点击 tab 则保留 tab 焦点。
- 播放设置里的“弹幕/字幕”标签页：L1/R1 切换并聚焦新标签。
- 设置页分类栏：仅聚焦分类只预览内容，**不抢内容焦点**；→/A 才进入内容。

### 5.7 空态/页头焦点

- 空态动作按钮的 Up 显式指回**页面返回键**。
- 页头图标（搜索/暂停/清除）的 Down 显式进入内容首项或空态按钮。
- 否则 2D 搜索会落到全局侧栏，用户“按上/下跑到别的页面”。

---

## 6. 上屏显示

### 6.1 播放时：纯 Surface

- `PlayerView(useController = false)`，无 OSD、无手势、无焦点。
- 背景纯黑，视频按比例 letterbox；不要给视频区域加主题色/圆角/遮罩。
- 上屏不要放任何可聚焦控件；所有交互只在下屏。

### 6.2 首帧前：只读场景

播放器还没出画时，上屏可以显示：

- Idle（品牌/待机）
- Preview（当前焦点卡片的封面/标题）
- Person（用户资料）
- Panel（设置分类说明）
- Ambient（动态/文字阅读）

这些场景**只读、无焦点**。用播放器的 `onRenderedFirstFrame` 作为门闸：

```kotlin
player.addListener(object : Player.Listener {
    override fun onRenderedFirstFrame() { hasRenderedFrame = true }
})
```

首帧后隐藏预览层，只留 Surface。退出播放时清空场景，避免残留上一部视频的封面。

### 6.3 主题

上屏的视频区域、弹幕、字幕始终保持黑色/原色，不跟随亮暗主题；主题只影响
待机/预览场景的背景、文字、图标。

---

## 7. 播放器

### 7.1 唯一实例

```kotlin
class PlayerController(context: Context) {
    val player = ExoPlayer.Builder(context).build()
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
}
```

- 由 Activity 创建/持有，下屏 ViewModel 和上屏 `PlayerView` 共用。
- **不要在 Composable 里创建/释放 ExoPlayer**；配置变化、Presentation 重建都会
  导致重建/黑屏/声音重叠。
- 后台时 `pause()`，不要 `release()`；回前台恢复。

### 7.2 HLS 直播

- 用 `media3-exoplayer-hls`；优先 ts/fmp4、AVC。
- B 站直播 m3u8 窗口很短，prepare 后容易 `BehindLiveWindowException` /
  `ERROR_CODE_BEHIND_LIVE_WINDOW`。**不要当终端错误展示**：清空缓存的
  url/cid，限频（如 5s 一次）重新拉 playurl 并 seek 到直播边缘。
- `player.currentLiveOffset` 可以用来做延迟提示；以“播放中的最小 offset”为
  基线，增长 ≥5s 才提示，避免正常 HLS 延迟常驻。
- 暂停后继续会从暂停点走，天然落后实时；提示里区分「直播延迟」和
  「已暂停 · 延迟」。

### 7.3 Surface 生命周期

- 下屏消失不等于播放器销毁；上屏 Activity 仍在。
- Presentation 重建时不要重建 player，只重新 attach `PlayerView`。
- 播放器状态（url/cid/进度/清晰度/弹幕）放 Controller，不放下屏 Compose。
- 播放时给上屏的 `PlayerView` 设 `keepScreenOn = isPlaying`，暂停后释放；
  不要全局给 Activity 加 `FLAG_KEEP_SCREEN_ON`，否则下屏待机也常亮。

---

## 8. 布局与视觉

- 下屏是 **640×480dp** 的密集操作区：字号 10–13sp，行高 44–56dp，控件别小于
  36dp；焦点环外扩后还要留视觉空隙。
- 上屏是 **832×468dp** 的展示区：标题 20–28sp，信息卡不要贴边。
- 所有尺寸用 dp/sp，不要按 1280×960 像素写死；不同固件 density 可能不同。
- 下屏没有系统栏，但边缘仍要留 8–12dp，避免焦点环贴边。
- 亮暗主题都要检查焦点环对比度：亮色用深色环 + 白色分隔，深色用白环 + 黑边。
- 播放控制台左侧常用 272dp 固定栏 + 右侧内容；按钮建议做光学网格（圆钮与
  chip 按列心对齐），不要靠目测。

---

## 9. 生命周期与状态

| 事件 | 处理 |
|---|---|
| Activity `onStart` | `presentation.show()` |
| Activity `onStop` | `presentation.hide()`（保留状态） |
| Activity `onDestroy` | `dismiss()` + 注销 DisplayListener |
| 下屏 `onDisplayRemoved` | `dismiss()` Presentation；保留 Activity/播放器状态，display 回来时重建 |
| 下屏 `onDisplayAdded/Changed` | 重新 `show()` + `requestFocus()` |
| 合盖/唤醒 | 按 display 事件处理，不重建 Activity |
| 进程被杀 | 用 SavedState/Repository 恢复；不要依赖 Presentation 自己的 ViewModelStore |

Manifest 建议：

```xml
<activity
    android:name=".MainActivity"
    android:screenOrientation="landscape"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize" />
```

横屏锁定可以避免上下屏旋转不同步；如果必须支持旋转，自己处理两屏的方向。

---

## 10. 调试与测试

### 10.1 连接

```bash
adb devices -l
adb mdns services
adb connect <ip>:<port>          # 无线调试端口每次可能变化
```

> 如果同时有别的会话在用 adb，不要随手 `adb kill-server`。

### 10.2 查看显示

```bash
adb shell dumpsys display | grep -E "mDisplayId|FLAG_PRESENTATION|canHostTasks"
adb shell dumpsys SurfaceFlinger --display-id
```

### 10.3 截图（重点坑）

Duo 上 `screencap -d 2` 会报 **“Display Id '2' is not valid”**：它要的是
**SurfaceFlinger 的 unique display id**，不是逻辑 displayId。

```bash
# 上屏（SurfaceFlinger id）
adb exec-out screencap -d <sf-id-0> -p > top.png
# 下屏（SurfaceFlinger id）
adb exec-out screencap -d <sf-id-2> -p > bottom.png
```

MuMu 模拟器则通常可以直接 `screencap -d 2`。

> Windows/Git Bash 下如果 `exec-out` 出来的文件头不是 PNG（被换行符污染），
> 先截到设备文件再 pull：
>
> ```bash
> adb shell screencap -d <sf-id> -p /sdcard/shot.png
> adb pull /sdcard/shot.png .
> ```

### 10.4 注入输入

```bash
adb shell input -d 2 tap 640 480          # 逻辑 displayId
adb shell input keyevent DPAD_DOWN
adb shell input keyevent BUTTON_B         # 注意固件风格可能映射成 A
adb shell input keyevent BUTTON_L1
```

- `input -d` 用的是**逻辑 displayId**，和 `screencap -d` 不是一套编号。
- 注入手柄键时先确认设备当前是 Xbox 还是 Retro 风格；必要时用实体位置反推
  keycode，并通过 UI 行为验证。
- 触摸注入用逻辑 displayId；下屏坐标按 1280×960 像素。

### 10.5 MuMu 模拟下屏

```bash
adb shell wm size 1280x960
adb shell wm density 320
```

即可得到 640×480dp 的虚拟下屏。注意：

- MuMu 的“叠加视图”预览窗口会挡住上屏截图，不要误判为应用 OSD；可以拖走。
- `screencap -p -a` 在 MuMu 上不可用。
- MuMu 只能验证布局/焦点/页面，`Presentation` 生命周期、输入转发、IME 仍要
  真机验证。

### 10.6 安装与签名

```bash
adb install -r app-release.apk
adb install -r -d app-debug.apk   # 仅 debuggable 包允许降 versionCode
```

- **debug 包和 release 包签名不同，不能互相覆盖**；从 debug 切 release 必须先
  卸载（会清本地登录/设置）。
- release 包**不能降级安装**（`INSTALL_FAILED_VERSION_DOWNGRADE`）。回滚旧代码
  要把 versionCode 往上加，或卸载重装。
- 覆盖安装同 key 的 release 包会保留数据。

### 10.7 验收清单

- [ ] 冷启动：上屏待机/预览，下屏首页；无闪烁、无空白 Presentation。
- [ ] 上屏首帧后只留视频；无 OSD/焦点。
- [ ] D-pad 全页面可遍历；首/末项不跳全局侧栏。
- [ ] B 确认、A 返回；长按只走一格/一层。
- [ ] 系统 Return 在普通页面、overlay、IME 三种状态下都正确。
- [ ] 侧栏聚焦时 L1/R1 仍能切当前页 tab。
- [ ] overlay 打开时焦点不泄漏到背后页面。
- [ ] 所有滚动列表首/末项的焦点环完整。
- [ ] 亮/暗主题下焦点环、文字、开关对比度正常。
- [ ] 后台/回前台：播放器不重建，进度保留；Presentation 能重新显示。
- [ ] 下屏拔插/合盖后能恢复。
- [ ] 触摸与手柄混用不出现“双焦点/双响应”。

---

## 11. 常见坑速查

| 现象 | 原因 | 处理 |
|---|---|---|
| 下屏全黑/没有 UI | 没找到 `FLAG_PRESENTATION` 显示；`show()` 过早；display off | 用 DisplayManager 查找、onStart 后 show、监听 display 事件 |
| 系统返回键没反应 | 下屏 Presentation 持有焦点，Activity 收不到 | Presentation 里接 BACK/ESC + `OnBackInvokedCallback` |
| 第一次返回被吞 | `WindowInsets.isImeVisible` 在 Presentation 误报 | 自己维护 IME 状态，只在真正 show 后置 true |
| 返回连续退两层 | 系统同时触发 `onBackPressed` 和 back callback | 250ms 去抖 |
| `Presentation` 里弹 Dialog 崩溃 | window type mismatch | 改成 Compose overlay |
| SAF/文件选择器打不开 | Presentation Context 不是 Activity | 用 Activity 的 ActivityResultRegistry |
| 系统分享面板透明/不可操作 | `ChooserActivityLauncher` 在 Presentation 上的问题 | 剪贴板 + Toast 或自绘分享层 |
| 加了摇杆支持后 D-pad 失灵 | Activity 层 generic motion 吞了 HAT 事件 | 只消费 AXIS_X/Y，放行 AXIS_HAT_X/Y |
| ABXY 行为错乱 | 系统“手柄风格”换了 keycode | 输入入口归一化，UI 按实体丝印提示 |
| 长按连跳/连发请求 | repeat 未吞、DOWN 未去重 | 吞 `repeatCount>0`；40ms 去重 |
| 焦点环被裁 | 环画在 clip 之后 / 滚动容器无余量 | 环放 clip 前；容器内留 6–8dp；Lazy item 加 7dp 外层余量 |
| 进页面焦点被抢走 | 内容组合时自动 `requestFocus()` | 只在明确进入时聚焦；延迟 120ms |
| 侧栏聚焦时肩键失效 | 页面根不在焦点树 | 全局 ShoulderDispatcher 转发 |
| 切页先闪到旧焦点 | 焦点环淡出 120ms 残留 | 失焦立即 snap 到 0；点击侧栏先聚焦目标项 |
| 播放器被重建/黑屏 | 在 Composable 里创建 ExoPlayer | Activity-scoped PlayerController |
| 直播暂停后恢复失败 | HLS 窗口过期 `BehindLiveWindow` | 清缓存、限频重新拉 playurl |
| 截图报 display id 无效 | `screencap -d` 要 SurfaceFlinger id | 用 `dumpsys SurfaceFlinger --display-id` |
| 截图带上 MuMu 叠加窗口 | 模拟器预览窗口遮挡 | 拖走/按代码结构判断，别当 OSD |

---

## 12. 最小骨架

### 12.1 MainActivity

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var session: DuoSession
    private lateinit var router: InputRouter
    private lateinit var bridge: BottomUiBridge
    private lateinit var secondScreen: SecondScreenController
    private lateinit var playerController: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerController = PlayerController(this)
        session = ViewModelProvider(this)[DuoSession::class.java]
        bridge = BottomUiBridge()
        router = InputRouter(session, bridge)

        secondScreen = SecondScreenController(
            activity = this,
            content = { AppUi(session, playerController) },
            onBack = { session.handleBackKey() },
            onDecorViewChanged = { view ->
                bridge.attach(view)
                if (view != null) session.requestFocus()
            },
        )

        setContent {
            AppTheme {
                if (secondScreen.hasSecondaryDisplay()) {
                    TopSurface(playerController)   // 上屏：只读 Surface
                } else {
                    SingleScreenFallback()         // 单屏回退
                }
            }
        }
    }

    override fun onStart() { super.onStart(); secondScreen.show() }
    override fun onStop() { secondScreen.hide(); super.onStop() }
    override fun onDestroy() { secondScreen.release(); super.onDestroy() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (secondScreen.hasSecondaryDisplay() && router.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
```

### 12.2 转发桥

```kotlin
class BottomUiBridge {
    private var decor: View? = null
    fun attach(v: View?) { decor = v }
    fun dispatch(e: KeyEvent): Boolean =
        runCatching { decor?.dispatchKeyEvent(e) }.getOrDefault(false)
}
```

### 12.3 返回会话

```kotlin
class DuoSession : ViewModel() {
    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay
    private var hideIme: (() -> Unit)? = null
    private var imeHiddenByBack = false

    fun setImeBackHandler(hide: (() -> Unit)?) { hideIme = hide }

    fun handleBackKey() {
        if (_overlay.value != null) {
            if (handleImeBack()) return
            if (!handleOverlayBack()) closeOverlay()
            return
        }
        requestBack()
    }

    fun handleImeBack(): Boolean {
        if (!ImeState.shown) return false
        if (imeHiddenByBack) { imeHiddenByBack = false }
        hideIme?.invoke()
        ImeState.onHiddenByBack()
        imeHiddenByBack = true
        return true
    }
}
```

---

## 13. 一页速记

- 上屏 Surface，下屏 Presentation。
- 事件进 Activity，单向往下屏转发。
- B 确认、A 返回；A 要翻译成 DPAD_CENTER。
- 系统 Return 在 Presentation 里自己接，并去抖。
- IME 状态自己记，别信 WindowInsets。
- 不用 Dialog；所有模态做成 Compose overlay。
- 一棵焦点树；关键路径显式 `focusProperties`；边界用 Cancel。
- 焦点环画在 clip 前，滚动容器留 6–8dp，Lazy item 留 7dp。
- 只在明确进入时抢焦点；关闭后 120ms 还给触发者。
- 播放器只建一次；直播 HLS 要处理 BehindLiveWindow。
- 截图用 SurfaceFlinger unique id，注入输入用逻辑 displayId。
