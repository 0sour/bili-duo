# bili-duo 设计系统 v0.1

> 适用范围：下屏 `Presentation` 内的全部 Compose UI，以及上屏视频宿主的视觉约束。
> 目标设备：Retroid Pocket Duo（上屏 1920×1080 @369dpi = 832×468dp，下屏
> 1280×960 @320dpi = **640×480dp**，双 120Hz）。
> 本文是 UI 实现与验收的唯一视觉基准；数值改动需同步更新本文和 `SCREEN_SPEC.md`。

---

## 1. 设计原则

1. **视频是主角。** 上屏永远只有画面；下屏的所有装饰都不能让人误以为视频控制在上屏。
2. **一套焦点。** 任意时刻屏幕上最多一个高亮焦点；触摸和手柄操作的是同一棵树。
3. **模式可见。** 当前是「手柄控播放」还是「手柄控下屏」，必须常驻可见，不靠用户猜。
4. **4:3 优先。** 640×480dp 是横向 4:3、纵向很矮的屏幕，布局按「宽度换列数、高度省着用」设计。
5. **近场阅读。** 下屏是 4.2 英寸掌机屏，观看距离约 25–35cm，不是 10-foot TV；字号可以比 TV 小，但触控目标不能小于 44dp。
6. **少即是快。** 一屏只回答一个问题；二级信息进 tab / sheet，不堆在主界面上。

---

## 2. 屏幕与安全区

| 区域 | 物理分辨率 | dp | 用途 |
|---|---|---|---|
| 上屏 | 1920×1080 | 832×468dp | 仅视频 Surface；无 OSD、无按钮、无焦点 |
| 下屏 | 1280×960 | 640×480dp | 全部 UI、导航、焦点、overlay |

下屏安全区：

```
640dp
┌──────────────────────────────────────────────┐
│ 16dp 内边距（内容不贴边）                      │
│  ┌──────┬─────────────────────────────────┐  │
│  │ Rail │ Content                         │  │
│  │ 64dp │ 576dp                           │  │
│  └──────┴─────────────────────────────────┘  │
│  StatusBar 28dp（全宽，常驻）                  │
└──────────────────────────────────────────────┘
480dp
```

- 外侧安全边距 **16dp**；列表内容左右再留 12dp。
- 左侧导航 rail **64dp**；底部模式条 **28dp**。
- 内容区基准 **576×452dp**（640−64，480−28）。
- 触控目标最小 **44×44dp**；相邻可点元素间距 ≥ 8dp。
- 不使用系统状态栏/导航栏；Presentation 全屏，edge-to-edge。

---

## 3. 色彩

### 3.1 基础策略

- **亮/暗双主题。** 上下双屏一起换色；上屏播放留边、视频画面、弹幕和字幕保持黑色。默认深色，设置里可选日出日落 / 跟随系统 / 手动。
- 背景用「表面层级」区分，不靠阴影；掌机 UI 阴影在低亮度下几乎不可见，还会糊。
- 品牌粉只用于**焦点、选中、关键动作**；大面积背景保持中性，避免粉色疲劳。
- 所有文字/背景组合对比度 ≥ 4.5:1（大字号 ≥ 3:1）。

### 3.2 色板

| Token | Hex | 用途 |
|---|---|---|
| `bg` | `#0B0B0E` | 页面底色、上屏黑边 |
| `surface1` | `#141418` | 卡片、列表行 |
| `surface2` | `#1C1C22` | 悬浮面板、sheet |
| `surface3` | `#26262E` | 输入框、次级按钮 |
| `surface4` | `#32323C` | 按下态、进度条底 |
| `outline` | `#2E2E38` | 分隔线、卡片描边 |
| `outlineStrong` | `#42424E` | 输入框描边、可交互边界 |
| `textPrimary` | `#F5F5F7` | 标题、正文 |
| `textSecondary` | `#A9A9B4` | 元信息、UP 主、说明 |
| `textTertiary` | `#73737F` | 时间、占位、弱提示 |
| `textDisabled` | `#4A4A55` | 禁用 |
| `pink` | `#FB7299` | 焦点环、选中态、品牌 |
| `pinkPressed` | `#E45F86` | 粉色按下 |
| `onPink` | `#1A0710` | 粉底上的文字/图标（深色，保证对比） |
| `blue` | `#00A1D6` | 链接、直播、信息强调 |
| `success` | `#4CC38A` | 完成、已下载 |
| `warning` | `#F5A524` | 仅 WiFi、弱网提示 |
| `error` | `#F2555A` | 错误、删除、退出确认 |
| `scrim` | `#000000` 60% | 模态遮罩 |
| `focusGlow` | `#FB7299` 30% | 焦点外发光，8dp |

### 3.2b 亮色色板

| Token | Hex | 用途 |
|---|---|---|
| `bg` | `#ECEDF1` | 页面底色 |
| `surface1` | `#FFFFFF` | 卡片、列表行 |
| `surface2` | `#F8F8FB` | sheet、rail、次级面板 |
| `surface3` | `#E4E5EB` | 输入框、chip、次级按钮 |
| `surface4` | `#D7D8E0` | 按下态、进度条底 |
| `outline` | `#D9DAE2` | 分隔线、卡片描边 |
| `outlineStrong` | `#B9BAC5` | 输入框描边 |
| `textPrimary` | `#121216` | 标题、正文 |
| `textSecondary` | `#3F3F4A` | 元信息、说明 |
| `textTertiary` | `#5C5C68` | 时间、占位、弱提示 |
| `textDisabled` | `#92939E` | 禁用 |
| `pink` | `#FB7299` | 品牌、下划线、进度 |
| `pinkInk` | `#B02A5B` | 亮色上的粉色文字/图标、选中 |
| `onPink` | `#FFFFFF` | 深粉填充上的文字/图标 |
| `blue` | `#006E9C` | 链接、直播 |
| `success` | `#237A57` | 完成 |
| `warning` | `#8A5A00` | 弱网、仅 WiFi |
| `error` | `#C0353B` | 错误、删除 |
| `focusRing` | `#1B1B20` | 亮色焦点环 |
| `focusHalo` | `#FFFFFF` | 环与内容的分隔 |
| `scrim` | `#000000` 45% | 模态遮罩 |
| `stageBg` | `#000000` | 上屏播放留边（两主题都黑） |

### 3.3 使用规则

- **焦点**：深色为白环 1.5dp + 黑分隔 3.5dp；亮色为墨环 `#1B1B20` + 白分隔。环在组件外 4dp 空隙，不要只用颜色变化。
- **选中但未聚焦**：`pink` 12% 背景 + `pink` 文字；焦点进入时再加描边。
- **主按钮**：深色 `pink` 填充 + `onPink` 深字；亮色 `pinkInk` 填充 + `onPink` 白字。次要按钮 `surface3` + `textPrimary`；危险按钮 `error` 文字 + `error` 12% 背景。
- **进度/弹幕开关等状态色**：开启用 `pink`，关闭用 `textTertiary`，不要用绿色表示「开」。
- **禁用**：整体 38% 透明度，不使用纯灰文字替代。
- 上屏播放时视频区之外（留边）永远是纯黑 `#000000`；待机/预览/人物/设置/动态等非视频场景跟随亮暗主题。

---

## 4. 字体

### 4.1 字族

- 中文/日文：系统 `Noto Sans CJK SC`（Android 默认 fallback）。
- 拉丁/数字：`Roboto`（系统默认）。
- 时间、进度、码率等数字启用 **tabular figures**（`FontFeature("tnum")`），避免跳动。
- 不引入自定义字体：中文字体包体积大，且系统字体在 320dpi 下已经足够清晰。

### 4.2 字阶（sp / 行高）

| Token | 字号/行高 | 字重 | 用途 |
|---|---|---|---|
| `display` | 28 / 34 | Bold | 空态大标题、登录页品牌字（少用） |
| `titleL` | 20 / 26 | SemiBold | 页面标题（「首页」「搜索」） |
| `titleM` | 16 / 22 | SemiBold | 区块标题、对话框标题、视频标题（播放控制台） |
| `bodyL` | 14 / 20 | Regular | 正文、简介、评论 |
| `bodyM` | 13 / 18 | Regular | 卡片标题（最多 2 行）、列表副标题 |
| `label` | 12 / 16 | Medium | tab、chip、元信息、按钮文字 |
| `caption` | 11 / 14 | Regular | 时间戳、角标、键位提示 |

规则：

- 卡片标题固定 **2 行**，超出省略；不要 3 行，否则 3 列网格高度不可控。
- 评论正文最多 6 行，超出「展开」；展开后整行可滚动。
- 数字/时间用 `caption` 或 `label`，右对齐或等宽。
- 最小可读字号 11sp；不要为了塞内容降到 10sp。
- 系统字体缩放上限 1.15×；超过时布局改为单列（见 §7 响应式）。

---

## 5. 间距与栅格

- 基准单位 **4dp**；常用梯度 4 / 8 / 12 / 16 / 20 / 24 / 32。
- 页面左右内边距 **12dp**（内容区），区块间距 **16dp**，卡片间距 **8dp**。
- 列表行高：
  - 单行元信息行 44dp
  - 双行视频行 84dp（缩略图 128×72dp）
  - 设置项 48dp
  - 评论行最小 72dp（自适应）
- 3 列视频网格（内容宽 552dp，gap 8dp）：单卡 **178.7dp**，缩略图 **178.7×100.5dp**（16:9），标题区 54dp，总高约 **158dp**。
- 2 列网格（收藏夹、相关推荐）：单卡 **272dp**，缩略图 153dp，总高约 **215dp**。
- 不要用百分比宽度写死列数；用 `LazyVerticalGrid(GridCells.Fixed(3))` + `contentPadding`。

---

## 6. 形状、描边与层级

| Token | 值 | 用途 |
|---|---|---|
| `rXs` | 6dp | chip、小标签 |
| `rSm` | 8dp | 按钮、输入框、缩略图 |
| `rMd` | 10dp | 视频卡片 |
| `rLg` | 12dp | 面板、列表容器 |
| `rXl` | 16dp | bottom sheet、dialog |
| `rFull` | 50% | 头像、圆形按钮 |

- 卡片默认 **无描边**，靠 `surface1` 与 `bg` 的明度差分层；焦点时加 2dp 粉边。
- 分隔线 1dp `outline`；列表最后一项不画线。
- 不使用 elevation 阴影作为主要层级手段；sheet 可以用 8dp 阴影 + scrim。
- overlay 面板：`surface2` + 1dp `outline` + `rXl`。

---

## 7. 响应式与异常尺寸

虽然目标机固定 640×480dp，仍要防：

- 下屏被系统缩放/分屏时，宽度 < 560dp：3 列改 2 列；rail 保留。
- 字体缩放 > 1.15×：卡片标题降为 1 行，网格保持 3 列；必要时列表化。
- 上屏比例变化（外接显示器）：视频 `RESIZE_MODE_FIT`，黑边，不裁剪。
- 横竖屏：Activity 锁定 landscape；Presentation 跟随 display，不单独旋转。

---

## 8. 图标

- 基础库：**Material Symbols Rounded**，weight 400，grade 0，optical size 24。
- 尺寸：rail 22dp、行内 18dp、播放控制 24dp、空态 48dp。
- 描边视觉粗细统一 2dp；不要混用 Filled / Outlined 两套风格（选中态可用 Filled）。
- 必须自绘的 B 站语义图标（用 `ImageVector`，24dp 网格）：
  - 弹幕（横向短划线 + 小点）
  - 投币（圆形 + 竖线）
  - 收藏（星形/书签，二选一，全应用统一）
  - 三连（点赞+币+藏的组合图标，仅播放控制台简介 tab）
  - 分P（列表 + 数字）
  - 清晰度（HD 方块字）
  - 倍速（1.5× 文本图标）
  - 直播（圆点 + 波纹）
- 图标按钮必须有 `contentDescription`；纯图标操作在底部模式条或 tooltip 中给出文字。

---

## 9. 组件规范

### 9.1 左侧导航 Rail

- 宽 64dp，背景 `surface1`，右侧 1dp `outline`。
- 顶部 8dp 品牌标记（20dp 高），下面 5 个 item：首页、搜索、历史、收藏、我的。
- 每个 item 56dp 高：图标 22dp + 文字 10sp **常驻**（不隐藏），焦点时图标与文字变 `pink`。
  文字常驻比「焦点才浮出」更可预测，也避免用户记图标含义。
- 选中态：左侧 3dp 粉色竖条 + 图标 `pink`；焦点态再加 2dp 圆角描边。
- 底部（rail 最下）放设置齿轮，和「我的」分开。

### 9.2 顶部栏（内容区）

- 高 44dp；左侧页面标题 `titleL`，右侧动作图标（搜索、刷新、更多）。
- 下边框 1dp `outline`；滚动时标题不吸顶变形，保持稳定。
- 不做大标题折叠（高度不够）。

### 9.3 Tab 行

- 高 36dp；文字 `label`，选中 `textPrimary` + 2dp 粉色下划线；未选中 `textSecondary`。
- 横向可滚动，但首屏最多 4 个 tab；超出用「更多」。
- 焦点在 tab 上时，左右键切换 tab 并立即加载；不要「先聚焦再按 A」。

### 9.4 视频卡片 `VideoCard`

```
┌─────────────────┐
│ 16:9 封面        │  ← rMd，右下角时长角标
│            12:34│
├─────────────────┤
│ 标题两行，最多…  │  ← bodyM，2 行
│ UP · 12.3万播放  │  ← caption，1 行
└─────────────────┘
```
- 封面加载前用 `surface3` 占位 +  shimmer（仅首次，不循环）。
- 时长角标：右下 4dp，`#000` 70% 圆角 4dp，白字 10sp。
- 焦点：整体 scale 1.03 + 2dp 粉边 + 外发光；标题变 `textPrimary`。
- 按下：scale 0.97，120ms。
- 不要在卡片上放「稍后再看」「三连」等悬浮按钮；这些进简介 tab 或长按菜单。

### 9.5 Chip

- 高 28dp，左右 10dp，`rXs`，`surface3`；选中 `pink` 12% + `pink` 文字 + 1dp 粉边。
- 用于排序、清晰度、倍速、标签；一行放不下时横向滚动，不换行。

### 9.6 播放控制按钮

- 圆形，直径 40dp（次要）/ 56dp（播放暂停）。
- 背景 `surface3`，图标 `textPrimary`；主按钮 `pink` + `onPink`。
- 焦点：2dp 粉边 + 外发光；按下 0.94。
- 图标下方可加 10sp 文字（仅首次引导时显示，之后隐藏）。

### 9.7 进度条 `SeekBar`

- 轨道高 3dp，`surface4`；已播放 `pink`；缓冲 `#FFFFFF` 20%。
- 滑块 12dp 圆形，仅焦点/拖动时显示；非焦点时隐藏，保持干净。
- 左右 10s 的按键操作要在进度条上给 1 帧的预览高亮（seek preview）。
- 时间文本 `caption` + tabular，左当前/右总长，固定宽度防抖。

### 9.8 模式条 `ModeBar`

- 高 28dp，全宽，背景 `surface1`，上边 1dp `outline`。
- 左侧：8dp 圆点 + 12sp 文本。
  - `TOP_PLAYER`：粉点 + 「手柄：播放」
  - `BOTTOM_UI`：蓝点 + 「手柄：下屏」
  - 无视频：灰点 + 「浏览」
- 中部：当前上下文提示（如「B 返回 · Y 切换」），`textTertiary`。
- 右侧：关键键位 2–3 个，`caption`；随模式变化。
- 不允许隐藏；overlay 打开时改为 overlay 的提示（「B 关闭」）。

### 9.9 Sheet / Dialog

- 右侧 sheet：宽 320dp，占满内容高度，`rXl` 仅左侧圆角；用于清晰度、倍速、弹幕设置、更多。
- 底部 sheet：最大高 280dp；用于分P、排序、确认。
- 居中 dialog：宽 320dp，`rXl`，标题 `titleM` + 正文 `bodyL` + 两个按钮（主/次）。
- 打开时 scrim 60%，焦点陷阱，B 只关闭最上层。

### 9.10 空态 / 加载 / 错误

- 空态：48dp 线性图标 + `titleM` 一句 + `bodyM` 说明 + 主按钮（可选）。垂直居中。
- 加载：骨架屏（3 列网格 6 个占位卡），不要全屏 spinner。
- 错误：`error` 图标 + 一句人话 + 「重试」按钮；网络错误附带「检查网络」。
- 所有状态都要有文字，不能只有图标。

### 9.11 Toast / HUD

- 轻提示：底部模式条上方 16dp，`surface2` 圆角 8dp，2 秒自动消失。
- 音量/亮度/倍速：不弹系统音量条；在下屏显示 200×48dp 的 HUD，1.5 秒消失。
- 上屏永远不显示 Toast/HUD。

---

## 10. 动效

| 场景 | 时长 | 曲线 | 说明 |
|---|---|---|---|
| 焦点进入 | 120ms | `FastOutSlowIn` | scale 1.00→1.03，描边淡入 |
| 按下 | 80ms | `LinearOutSlowIn` | scale→0.97 |
| 页面切换 | 180ms | `FastOutSlowIn` | 淡入 + 8dp 上移；返回反向 |
| Sheet | 220ms | `FastOutSlowIn` | 从右侧/底部滑入 |
| Dialog | 160ms | `FastOutSlowIn` | scale 0.96→1 + 淡入 |
| 进度更新 | 250ms | `Linear` | 仅进度条，数字直接跳 |
| 骨架 shimmer | 1000ms | `Linear` | 仅加载态，循环 |

- 视频播放中，除进度条和弹幕开关外**不做持续动画**；避免与 120Hz 视频抢帧。
- 尊重系统「移除动画」设置：`Settings.Global.ANIMATOR_DURATION_SCALE == 0` 时全部改为直接切换。
- 不做焦点「呼吸灯」、不做背景视差、不做 3D 翻转。

---

## 11. 图像与封面

- 封面统一 16:9，`ContentScale.Crop`；不要拉伸。
- 列表缩略图 128×72dp；网格缩略图按列宽自适应。
- 头像圆形，32dp（评论）/ 48dp（个人中心）/ 64dp（简介 tab UP 主）。
- Coil 配置：内存 16MB、磁盘 50MB；列表快速滚动时暂停新请求，停止后恢复。
- 加载失败：`surface3` + 居中小图标，不显示破图。
- 不在封面上叠加半透明渐变（除非为了白字可读，且只加底部 40%）。

---

## 12. 品牌与应用图标

- 名称：`Bili-Duo`；上屏用 24dp `DuoLogo`（黑底 + 上 16:9 屏 + 下 4:3 屏 +
  粉色播放三角）加 wordmark；下屏 rail 顶部用 40dp 同款 `DuoLogo`，不再放
  文字或头像。启动器图标使用同一几何的黑底 adaptive icon。
- 图标概念：深色圆角方形底，两个上下堆叠的圆角矩形（上 16:9、下 4:3）用白色描边；上屏内一个粉色播放三角。
- 自适应图标：前景为双屏 + 播放三角，背景 `#0B0B0E`；安全区 66dp。
- 启动页：纯黑 + 居中 64dp 图标 + 12sp「bili-duo」，不超过 600ms；不要动画。
- 不用 B 站官方 logo/2233 娘，避免商标和版权问题。

---

## 13. Compose 落地

```kotlin
// theme/Color.kt
val DuoBg = Color(0xFF0B0B0E)
val DuoSurface1 = Color(0xFF141418)
val DuoSurface2 = Color(0xFF1C1C22)
val DuoSurface3 = Color(0xFF26262E)
val DuoSurface4 = Color(0xFF32323C)
val DuoOutline = Color(0xFF2E2E38)
val DuoOutlineStrong = Color(0xFF42424E)
val DuoTextPrimary = Color(0xFFF5F5F7)
val DuoTextSecondary = Color(0xFFA9A9B4)
val DuoTextTertiary = Color(0xFF73737F)
val DuoPink = Color(0xFFFB7299)
val DuoPinkPressed = Color(0xFFE45F86)
val DuoOnPink = Color(0xFF1A0710)
val DuoBlue = Color(0xFF00A1D6)

// theme/Theme.kt
private val DuoColorScheme = darkColorScheme(
    primary = DuoPink,
    onPrimary = DuoOnPink,
    primaryContainer = DuoPink.copy(alpha = 0.12f),
    onPrimaryContainer = DuoPink,
    secondary = DuoBlue,
    background = DuoBg,
    onBackground = DuoTextPrimary,
    surface = DuoSurface1,
    onSurface = DuoTextPrimary,
    surfaceVariant = DuoSurface3,
    onSurfaceVariant = DuoTextSecondary,
    outline = DuoOutline,
    outlineVariant = DuoOutlineStrong,
    error = Color(0xFFF2555A),
)
```

- 所有页面只使用 `MaterialTheme.colorScheme` 和本文 token；禁止在页面里写裸 `Color(0x...)`。
- 字体通过 `Typography` 覆盖，统一 `FontFamily.Default`。
- 焦点视觉统一封装为 `Modifier.duoFocusRing(shape, focused)`，不要每个组件各写一套。
  规格：内容外 4dp 空隙 + 1.5dp 白环 + 3.5dp 深色分隔边，环在组件边界外
  绘制；不使用粉色光晕或粉色选中底。`duoFocusRing` 要写在 `clip(...)` 之前，
  否则会被裁掉。
- 触控目标统一 `Modifier.minimumInteractiveComponentSize()` 或自定义 44dp 包装。

---

## 14. 验收清单（视觉）

> 2026-11-04：本清单是初始验收参考，未随每次迭代同步；当前完成状态以
> [`README.md`](../../README.md) 为准。

- [ ] 下屏 640×480dp 下无内容被裁切、无横向滚动条（除刻意横滑的 chip 行）。
- [ ] 任意时刻只有一个焦点环；焦点环在深色背景上清晰可见。
- [ ] 3 列网格在 640dp 宽下卡片不重叠、文字不溢出。
- [ ] 模式条常驻，切换模式时文字/颜色同步变化。
- [ ] 上屏除视频外无非黑像素（待机占位除外）。
- [ ] 所有文字对比度达标；粉色底上不使用白字。
- [ ] 触控目标 ≥ 44dp；焦点态和按下态可区分。
- [ ] 120Hz 下焦点动画不丢帧；视频播放时下屏无持续动画。
