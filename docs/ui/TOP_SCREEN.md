# bili-duo 上屏规范 v0.2

> 上屏：1920×1080 @369dpi = **832×468dp**，120Hz，HDR10。
> 播放时只显示视频 Surface；浏览时显示只读的待机提示或视频预览。
> 没有 OSD、按钮、可聚焦控件和手势；所有控制都在下屏，见
> [`SCREEN_SPEC.md`](SCREEN_SPEC.md)。
>
> **设计稿（2026-11-03）**：[`mockups/top-idle.png`](mockups/top-idle.png)（待机）/
> [`mockups/top-preview.png`](mockups/top-preview.png)（选中预览）。
> **实现状态（2026-11-04）**：上屏从“待机/视频预览”扩展为六类只读场景：
> 待机、视频预览、人物（关注/个人）、设置面板、页面引导、动态阅读
> （DETAIL/GALLERY/TEXT）。首页/搜索/历史/收藏/下载聚焦内容时显示预览；
> 关注聚焦 UP 主、个人中心显示账号卡；设置随分类切换；登录页显示登录状态
> 引导；空列表和未登录显示页面引导；动态图文打开下屏详情/画册时，上屏同步
> 显示只读详情/画册/纯文字海报。
> `PlayerController` 的 `hasRenderedFrame` 保证播放首帧前不黑屏，首帧后所有
> 场景层隐藏，只保留视频与弹幕。
> **MuMu 注意**：MuMu 的“叠加视图”系统窗口会盖住 display 0，可用
> `adb shell input -d 0 swipe` 从它的标题栏拖到角落再截图；最终视觉验收仍
> 以 Duo 真机为准。

---

## 1. 原则

1. **上屏只负责显示。** 播放中除视频/弹幕外不出现 UI；浏览时允许显示只读的
   品牌待机页和视频预览信息，但不能出现按钮、可聚焦控件或手势。
2. **不抢焦点。** 上屏 Activity 里的内容不可聚焦、不可点击出控件、不参与导航。
3. **播放状态不显示在上屏。** 缓冲、暂停、音量、倍速、清晰度、错误操作全部由
   下屏表达；上屏最多保留最后一帧或回退到预览。
4. **触摸不产生 UI。** 上屏是触摸屏，但触摸只被消费，不弹任何东西。
5. **Surface 与控制器分离。** 上屏只 attach/detach Surface；播放器生命周期由
   `PlayerController` 持有（见 [`../ARCHITECTURE.md`](../ARCHITECTURE.md)）。
6. **永远不黑屏。** 无选择显示待机提示；有选择显示封面/简介预览；加载 playurl
   时保持预览，首帧到达后再切视频。

---

## 1.5 场景系统（2026-11-04）

| 下屏页面 | 上屏场景 | 数据来源 |
|---|---|---|
| 首页 | 5 个 tab 各自引导 / 空态 / 视频预览 | tab + 卡片焦点 |
| 搜索 | 搜索引导 / 搜索中 / 无结果 / 结果预览 | 查询状态 + 卡片焦点 |
| 观看历史 | 登录引导 / 空态 / 视频预览 | 焦点 + 登录态 |
| 我的收藏 | 登录引导 / 空态 / 收藏夹预览 / 视频预览 | 焦点 + 登录态 |
| 我的关注 | 登录引导 / 空态 / UP 主人物卡 | 焦点 → `TopPersonUi` |
| 我的下载 | 空态 / 本地下载预览 | 行焦点 → `TopPreviewUi` |
| 设置 | 分类面板（图标/分类列表/当前项/当前值） | `category` + 设置值 → `TopPanelUi` |
| 个人中心 | 未登录引导 / 账号人物卡 | 登录态 → `TopPersonUi` |
| 登录 | 登录状态引导（扫码/已扫/成功/失效/错误） | `LoginUiState` |
| 播放控制台 | 视频（首帧后） | `PlayerController` |

规则：

- 所有场景只读：不包含可聚焦控件，不响应触摸。
- 场景切换由页面在进入/焦点变化时上报，`TopScreenViewModel` 持有唯一状态；
  播放首帧到达后 `TopScreenHost` 优先显示视频，场景层不参与合成。
- 人物卡使用头像/签名；预览卡使用封面/简介/UP；设置面板使用分类图标、
  当前分类和当前值（如“仅 WiFi 下载：开 · 当前 WiFi”）；引导卡用于空列表、
  未登录、搜索状态和登录流程。
- 首页 5 个 tab 各有自己的标题/说明/图标：推荐、热门、排行、番剧、影视。

---

## 2. 状态表

| 状态 | 上屏 | 下屏 |
|---|---|---|
| 无视频、无选中 | 品牌待机页 + 「下屏选择视频」提示 | 浏览 UI |
| 视频卡片聚焦/选中 | **预览**：左 2/3 封面 + 右 1/3 标题/简介/UP/统计 | 来源列表，焦点在卡片 |
| 正在加载 playurl | 保持预览，不黑屏 | 来源列表 loading / 播放按钮 loading |
| 播放中 | 视频画面 | 播放控制台（TOP_PLAYER 压暗） |
| 暂停 | 冻结当前帧 | 控制台显示「已暂停」 |
| 缓冲 | 保留最后一帧 | 控制台显示「缓冲中…」+ 小进度 |
| 播放结束 | 冻结最后一帧 | 「已播完 · A 重播 · B 返回」 |
| 播放错误 | 回退到预览（无预览则待机） | 错误面板 + 重试 / 换清晰度 |
| 纯音频 | 封面 + 标题预览 | 控制台显示音频标识与标题 |
| 后台 | 窗口不可见；暂停 | — |
| 息屏 | 暂停 | — |

规则：

- 播放中不在上屏放图标、文字、spinner、进度条；预览态是内容展示，不是 OSD。
- 缓冲不在上屏显示 spinner；用户看下屏就知道。
- 错误码和操作不在上屏显示；下屏负责解释和操作。
- 暂停不显示大播放按钮；下屏有。
- 预览信息随下屏焦点变化更新；不做点击、悬停或手势交互。

---

## 3. 待机画面

> 设计稿：[`mockups/top-idle.png`](mockups/top-idle.png) / HTML
> [`mockups/top-idle.html`](mockups/top-idle.html)

```
1920×1080
┌──────────────────────────────────────────────────────────────┐
│ bili-duo                                  ● 待机 · 下屏浏览  │
│                                                              │
│   DUAL-SCREEN BILIBILI CLIENT              ┌──────────────┐  │
│   下屏选视频，                              │ TOP · VIDEO  │  │
│   上屏先预览。                              │   [ 播放 ]   │  │
│                                            └──────────────┘  │
│   移动焦点或触摸视频卡片，上屏会展示封面、   ┌──────────┐      │
│   简介和 UP 主信息；开始播放后自动切换。     │BOTTOM·UI │      │
│                                            │ ▤ ▦ ▦ ▦  │      │
│   [ A ] 开始播放 · 移动焦点即可预览         └──────────┘      │
│                                                              │
│ 上屏只负责显示 ── 不响应触摸或手柄      下屏触摸与手柄共用焦点 │
└──────────────────────────────────────────────────────────────┘
```

- 背景 `#0B0B0E` + 低透明度粉/蓝环境光；不使用大面积纯黑。
- 左侧：品牌字标、主标题、说明、按键提示；右侧：双屏设备示意图。
- 所有元素只读；没有焦点环、没有可点击区域。
- 文案随状态可替换为「正在加载首页…」「没有可预览的视频」。

---

## 3.1 视频预览画面

> 设计稿：[`mockups/top-preview.png`](mockups/top-preview.png) / HTML
> [`mockups/top-preview.html`](mockups/top-preview.html)

```
1920×1080
┌───────────────────────────────────┬──────────────────────────┐
│                                   │ ● 已选择 · 即将播放       │
│  ┌─────────────────────────────┐  │ 双屏掌机上的 B 站客户端， │
│  │                             │  │ 到底该怎么设计？          │
│  │        封面 1152×648        │  │ 80.7万播放 · 4287弹幕     │
│  │        （左 2/3）           │  │ ───────────────────────  │
│  │                             │  │ (头像) 数码研究所  1080P │
│  └─────────────────────────────┘  │        UP 主 · 数码科技   │
│                                   │ 简介：把视频留给上屏……    │
│                                   │ [双屏] [Android] [设计]   │
│                                   │                          │
│                                   │ [ A ] 开始播放 · B 返回   │
└───────────────────────────────────┴──────────────────────────┘
```

尺寸：

| 区域 | 尺寸/位置 | 内容 |
|---|---|---|
| 封面 | 左侧 1152×648（2/3 宽，16:9），左边距 64、垂直居中 | 视频封面、时长角标 |
| 右侧信息栏 | 右侧 640×1080，左内边距 56、右 64 | 标题、播放/弹幕、UP、简介、标签 |
| 标题 | 42/56sp，最多 3 行 | 视频标题 |
| 元信息 | 22sp | 播放量、弹幕数、发布日期 |
| UP | 头像 66 + 名称 27 + 角色 18 | UP 主信息；清晰度 badge 在右 |
| 简介 | 23/38sp，最多 5 行 | 视频简介 |
| 标签 | 17sp chip | 分区/关键词（可选） |
| 底部提示 | 21sp | `A 开始播放 · B 返回`，只读提示 |

- 封面使用 16:9 `ContentScale.Crop`，圆角 28，描边 1dp `#2E2E38`。
- 右侧栏背景 `#141418`，与封面之间用 1px 分隔线/渐变过渡。
- 预览态不显示播放器控制、进度、弹幕或 loading spinner。
- 封面缺失时用 `surface3` 占位 + 视频标题，不回到纯黑。

---

## 4. PlayerView 配置

```kotlin
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setKeepContentOnPlayerReset(false)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            setOnTouchListener { _, _ -> true }   // 消费触摸，防止系统手势/误触
            player = controller.player
        }
    },
    modifier = Modifier.fillMaxSize().background(Color.Black),
)
```

- 必须 `useController = false`；禁止 Media3 默认控制条。
- 必须关闭 buffering spinner；禁止任何 overlay。
- `RESIZE_MODE_FIT`：16:9 填满 832×468dp；4:3 左右黑边；21:9 上下黑边；不裁剪。
- `setKeepContentOnPlayerReset(false)`：切换视频/重置时黑屏，不残留上一帧。
- 不注册 `GestureDetector`、不监听双击、不做亮度/音量手势。
- 不在 PlayerView 上叠 Compose 控件。

### 4.1 窗口

- Activity 全屏 immersive：隐藏 status/navigation bars，`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`。
- 屏幕常亮：播放中 `FLAG_KEEP_SCREEN_ON`；暂停/退出清除。
- 方向：`landscape`；`configChanges` 处理 `orientation|screenSize|keyboardHidden`，避免旋转重建。
- 上屏窗口背景纯黑，避免启动/退出闪白。

---

## 5. 弹幕层（Phase 3，已接入）

> 2026-11-03 实现：`ui/player/DanmakuView.kt` + `PlayerViewModel.loadDanmaku`；
> MuMu 已验证显示/开关/暂停冻结/seek 重算，Duo 真机待复测。

- 渲染方式：在 `PlayerView` 之上叠一个自定义 `View`（Canvas 绘制，硬件层），
  不用 Compose，避免每帧重组。
- 数据：`x/v1/dm/list.so?oid=<cid>`；响应 `Content-Encoding: deflate` 是 raw
  deflate（无 zlib 头），先 `Inflater(nowrap = true)`，失败/为空再按带 zlib 头解；
  XML `<d p="time,mode,size,color,...">text</d>` 解析时间/模式/字号/颜色，按时间排序。
- 拉取时机：进入播放与切分P时按 cid 重载；切 P 先清空旧弹幕；本地播放不拉取。
- 样式（当前实现）：
  - 字号按视图高度约 2.6% 缩放，16sp 为基准（范围 12–24sp 可调）。
  - 滚动弹幕持续 8s，从右到左；顶部/底部弹幕停留 4s。
  - 白色 + 黑色描边；默认不透明度 90%。
  - 泳道按时间顺序分配，滚动弹幕逐条计算前一条尾部离开屏幕的时间，避免重叠；
    顶部/底部各保留 1–3 行。
- 交互：上屏不接收弹幕点击/悬停；下屏控制台「弹幕 开/关」chip 切换，
  `TOP_PLAYER` 下 X 键也可切换；开关状态在退出播放重置时保留。
- 暂停：播放/缓冲时停止动画帧刷新，画面与弹幕一起冻结；seek 后按新位置重绘。
- 性能：只在播放中通过 `postInvalidate()` 刷新（Choreographer 回调里直接
  `invalidate()` 不触发硬件层重绘）；绘制窗口为当前位置前 12s。
- 设置：下屏控制台 chip 行右侧齿轮打开「弹幕设置」overlay，可调显示开关/
  不透明度（30–100%）/速度（0.7–1.8×）/字号（小–特大）/显示区域（1/4–全屏）；
  设置实时生效并持久化到 `bili_prefs`。
- 未做：同屏上限、弹幕发送、屏蔽词、点击查看。

---

## 6. 生命周期与 Surface

```
Activity.onCreate
  └─ 创建 PlayerController（Activity-scoped ViewModel）
       └─ ExoPlayer 实例（唯一）

Activity.onStart
  └─ PlayerView.attach(player)

Activity.onStop
  └─ PlayerView.player = null（detach Surface，不释放 player）
       └─ 按设置暂停

Activity.onDestroy
  └─ PlayerController.release()
```

- `PlayerView.player = null` 只解绑 Surface，不 `release()` 播放器；播放器由
  `PlayerController` 管理，下屏重建不受影响。
- Presentation 重建不影响上屏 Surface；两者独立。
- 切换视频：复用同一 ExoPlayer，`setMediaItem` + `prepare` + `play`；不重建 PlayerView。
- 退出播放：`stop()` + `clearMediaItems()`；上屏黑屏；播放器保留待复用。
- 从后台恢复：重新 attach；若 `Surface` 丢失，ExoPlayer 自动重建输出。

---

## 7. 性能

| 指标 | 目标 |
|---|---|
| 播放帧率 | 120Hz 屏幕下视频按片源帧率；UI 不掉帧 |
| 上屏 UI 重组 | 播放中 0 次/秒；预览态只在焦点切换时重组 |
| Surface 切换 | < 100ms，无白闪 |
| 首帧 | 点击播放后 < 1.5s（缓存命中 < 500ms） |
| 内存 | 播放 1080p 时 < 300MB |
| 下屏进度更新 | 4Hz（250ms），不随视频帧刷新 |

- 上屏不订阅 `PlayerController.positionFlow`；进度只在下屏订阅。
- 下屏进度用 `sample(250)` 或 `distinctUntilChanged`，避免每帧重组。
- 视频播放时下屏禁止持续动画（shimmer 只在加载态）。
- HDR：若设备/解码器不支持，自动回退 SDR，不弹错误；下屏设置里可手动关闭 HDR。
- 预览态图片用 Coil 缓存 + 固定 1152×648 目标尺寸；焦点快速移动时 debounce
  约 200ms，避免为每个卡片请求详情。

---

## 8. 实现建议（已接入首页/搜索）

1. **状态模型**：Activity-scoped `TopScreenViewModel` 已经落地：
   ```kotlin
   enum class TopScreenMode { IDLE, PREVIEW, PERSON, PANEL, AMBIENT, DYNAMIC }
   data class TopScreenUiState(mode, preview, person, panel, ambient, dynamic, descriptionLoading)
   data class TopPreviewUi(bvid, title, cover, owner, stat, duration, description, category)
   data class TopDynamicReaderUi(kind = DETAIL/GALLERY/TEXT, title, text, images, imageIndex, ...)
   ```
2. **数据来源**：首页/搜索卡片 `onFocusChanged` 上报 `bvid + title + pic + owner +
   stat + pubdate + duration`；Activity 侧 debounce 250ms 调 `getVideoInfo` 补简介，
   并做 8 条 LRU 缓存。焦点离开卡片时 180ms debounce 后回 `IDLE`。
3. **渲染位置**：`TopScreenHost` 保持 `TopPlayerSurface` 常驻；`hasRenderedFrame`
   为 false 时叠加 `TopReadOnlyLayer`，首帧后隐藏，不销毁 `PlayerView`。
4. **触摸**：预览/待机层消费触摸事件，不响应点击、双击、滑动；不注册手势。
5. **加载/错误**：播放器 `hasRenderedFrame=false` 时显示最近一次预览；没有预览
   则显示待机页。首帧到达后再隐藏预览层。
6. **纯音频**：没有视频帧时保持封面 + 标题预览，不回到黑屏。
7. **接入范围**：首页、搜索、历史、收藏列表均已上报焦点；历史/收藏因当前设备
   未登录，空态下只验证到待机页，待登录后做真实卡片预览验收。详情页的
   `TopPreviewUi` 映射也可复用。

---

## 9. 验收清单

> 2026-11-04：本清单是初始验收参考，未随每次迭代同步；当前完成状态以
> [`README.md`](../../README.md) 为准。

- [x] 播放中上屏无任何 OSD、按钮、文字、spinner（弹幕除外）。
- [x] 弹幕按 cid 拉取、解析并在视频上渲染；暂停冻结，seek 后重建。
- [x] 下屏 chip 与 TOP_PLAYER 的 X 键切换弹幕开关。
- [x] 弹幕设置：开关/透明度/速度/字号/显示区域，实时生效并持久化。
- [x] 无选中视频时显示待机品牌 + 提示，不黑屏。
- [x] 首页/搜索聚焦视频卡片时，上屏左 2/3 显示封面、右 1/3 显示标题/简介/UP/统计。
- [x] 焦点移动时预览更新；不重建播放器。
- [x] 开始播放后预览层消失，首帧前不黑屏。
- [ ] 播放错误回退到预览；无预览则回退到待机（代码路径已留，未做真机断网矩阵）。
- [ ] 触摸上屏不出现任何 UI，不暂停/不弹菜单。
- [ ] 16:9 片源填满 832×468dp；4:3 左右黑边；21:9 上下黑边；不裁剪。
- [ ] 暂停/缓冲/结束/错误时上屏没有多余 OSD。
- [ ] 后台恢复后上屏能继续播放，Surface 不黑、不闪白。
- [x] 120Hz 下播放流畅；下屏操作不引起上屏掉帧。
- [x] 上屏永远不是焦点窗口的 UI 来源；焦点环只在下屏。
- [x] 历史/收藏列表接入预览（代码已接，设备未登录时显示引导）。
- [x] 关注 UP 主显示人物卡；个人中心显示账号卡；未登录显示引导。
- [x] 设置随分类切换上屏面板（图标、分类列表、当前项）。
- [x] 登录页随扫码状态切换上屏引导。
- [x] 下载列表显示本地缓存预览（清晰度/大小/状态）。
- [ ] Duo 真机上确认人物卡、设置面板、登录引导的实际观感（MuMu 叠加窗遮挡）。
