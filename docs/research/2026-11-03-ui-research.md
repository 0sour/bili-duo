# UI / 双屏交互调研记录（2026-11-03）

> 目的：为 `docs/ui/` 下的设计系统、页面规格、输入规范提供依据。
> 只记录可验证的事实与决策映射；不复制第三方代码。

---

## 1. 设备事实（Duo 实测）

```bash
adb shell dumpsys display | grep -E "DisplayDeviceInfo|mDisplayId="
adb shell wm size; adb shell wm density
```

| 项 | 上屏 | 下屏 |
|---|---|---|
| displayId | 0 | 2 |
| 物理 | 1080×1920（横屏 1920×1080） | 1280×960 |
| dpi | 369 | 320 |
| dp | 832×468 | **640×480** |
| 刷新率 | 120Hz / 60Hz | 120Hz / 60Hz |
| HDR | HDR10 | HDR10 |
| touch | INTERNAL | EXTERNAL |
| canHostTasks | true | **false** |
| flags | — | **FLAG_PRESENTATION** |

结论：下屏是 4:3、640×480dp 的「中等宽度 + 极矮高度」屏幕；Material 手机/平板断点
不适用；必须专门设计 3 列网格与 28dp 模式条。

---

## 2. xVideoPlayer（最接近的双屏实现）

仓库：<https://github.com/MaiconErick/xVideoPlayer>（只读参考）

### 2.1 Presentation 写法

`DualScreen.kt` 的 `SecondScreen`：

- 继承 `Presentation`，同时实现 `LifecycleOwner`、`ViewModelStoreOwner`、
  `SavedStateRegistryOwner`。
- `onCreate` 里先 `saved.performRestore(null)`，再给 `decorView` 设置三个 owner，
  最后 `setContentView(ComposeView(context))`。
- `onStart` → RESUMED；`onStop` → DESTROYED。
- 主题不能用会 `LocalContext.current as Activity` 的主题；它直接用
  `MaterialTheme(darkColorScheme())`。

采用：同样的 owner 顺序；但 Presentation 没有 `onDestroy`，`onStop` 里置
DESTROYED + `store.clear()`（实际实现）。另外补 `ActivityResultRegistryOwner`
和 `OnBackPressedDispatcherOwner`，用 CompositionLocal 提供 Activity 的实例。

### 2.2 下屏布局

`RemotePanel`：

- 宽高比判断：`maxWidth > maxHeight * 1.05` → 横向双栏 46/54；否则上下 45/55。
- 左栏：标题、进度、传输按钮（5 个，按宽度自适应 34–54dp）、音量、倍速 chip、工具 chip。
- 右栏：6 个 tab（字幕/音频/画面/工具/文件/更多）+ 可滚动内容。
- 底部注释：「Thor 的下屏只给这一列留了约 230dp，所以按钮要缩小」。

对我们的意义：Duo 下屏有 480dp 高，不需要 Thor 那种极端压缩；采用 **左 272dp +
右 332dp** 的双栏控制台，右栏放评论/推荐/简介/分P。

### 2.3 输入

- 手柄事件留在上屏 `Activity.dispatchKeyEvent` → `GamepadRouter`，下屏以触摸为主。
- 打开下屏时，上屏控制层隐藏（`visible = showControls && !onSecondScreen`）。
- 没有「手柄控制下屏」的模式，也没有焦点转发。

对我们的意义：xVideoPlayer 证明了下屏 Compose Presentation 可行，但没有解决
「手柄操作下屏」。我们必须在它之上加 `InputRouter` + decorView 转发。

---

## 3. Wayfinder（焦点切换）

仓库：<https://github.com/Thor-Wayfinder/thor-wayfinder>（只读参考）

`DisplayFocus.kt`：

- 通过 `ServiceManager.getService("activity_task")` + `IActivityTaskManager` 反射调用
  `focusTopTask(displayId)`；失败回退 `setFocusedTask(taskId)`。
- 注释明确写明 **ROOT-SIDE ONLY**：运行在 `app_process` 里。
- 合成触摸不可靠：只有落在「能接收按键的窗口」上才会移动 display focus。

结论：未 root 的普通 app 无法把系统手柄焦点切到下屏；「上屏接收、按模式转发」
是唯一可行路径。这正是我们显式 `TOP_PLAYER / BOTTOM_UI` 的依据，也说明为什么
不能做「所有按键全量转发」。

---

## 4. BBLL（B 站 TV 端）

来源：BBLL 截图（1920×1080），只读参考。

观察：

- 左侧固定图标 rail（约 72px），顶部一级 tab（推荐/热门/番剧/影视）。
- 内容区 4 列网格；卡片 16:9 封面 + 2 行标题 + UP/播放量。
- 焦点卡片：紫色/粉色底 + 粉色描边，尺寸略大；非焦点卡片无边框。
- 深色背景（近 `#0F0F0F`），文字白色，元信息灰色。
- 番剧页 6 列海报网格，焦点项粉色描边 + 标题变粉。

映射到 640×480dp：

- 1920px 宽、4 列 → 640dp 宽约 2.7 列；取 **3 列**（178.7dp 卡），与 BBLL 的
  视觉密度一致。
- 左 rail 64dp、顶部 tab 36dp、模式条 28dp；焦点态用粉边 + 轻微放大，不用整卡变色
  （小屏上整卡变色会压过封面）。

---

## 5. Android TV / Compose 焦点规范

来源：

- <https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv>
- <https://developer.android.com/training/tv/get-started/navigation>
- <https://developer.android.com/training/tv/optimizing-navigation-tv>

要点：

1. D-pad 必须能到达所有可见控件；不能有「死角」。
2. 列表获得焦点时，上下键滚动列表；选中项保持可见。
3. 焦点必须清晰可见；不能只靠颜色。
4. 布局按网格组织，方向键移动可预测；避免重叠/浮动控件。
5. 每个屏幕进入时必须有初始焦点；没有初始焦点等于「屏幕坏了」。
6. 横向 carousel 要恢复焦点（`focusRestorer` / 自定义），避免滚动后焦点丢失。

采用：`INPUT_AND_FOCUS.md` 的初始焦点表、焦点恢复、`BringIntoViewRequester`、
40ms 去重。注意 Compose BOM 2024.04.01（Compose 1.6.x）可能没有
`Modifier.focusRestorer()`；实现时先用 `FocusRequester` 自建，必要时升级 BOM。

---

## 6. Wii U / DS 的第二屏经验

来源：

- <https://game-wisdom.com/critical/examining-multi-screen-ui-design>
- <https://www.popmatters.com/178267-living-with-the-second-screen-2495694424.html>
- <https://www.copetti.org/writings/consoles/wiiu>

要点：

1. 第二屏应**支持主画面**，而不是复制主画面；地图、菜单、背包、状态是常见用法。
2. 当重要信息在第二屏时，要提示用户「看下面」，否则会被忽略。
3. 第二屏容易变成「杂物抽屉」：什么都塞，反而降低可预测性。
4. Wii U 的失败教训之一：第二屏没有强制/清晰的使用场景时，开发者会做成事后补充。

采用：

- 下屏不是上屏的遥控器复刻，而是完整 UI；播放时承担「信息 + 控制 + 评论」。
- 模式条常驻，明确告诉用户手柄现在控制谁。
- 每个页面只放一个主任务；二级信息进 tab/sheet，避免杂物抽屉。

---

## 7. Surface Duo 大屏模式

来源：<https://devblogs.microsoft.com/surface-duo/large-screen-design-patterns>

- **Supporting panel**：主视图 + 辅助面板（工具/信息），适合双屏。
- **Feed**：网格内容流，可跨屏或单屏。
- 列表-详情（list-detail）在折叠/双屏上比单屏堆叠更自然。

采用：**不设独立详情页**。列表点击视频直接进入播放控制台；UP 主卡、收藏、下载
放进简介 tab，评论/相关/分P 继续留在播放控制台。播放控制台 = 左控制 +
右 tab（supporting panel）；首页 = 3 列 feed。

---

## 8. API 文档现状与法律风险

- `SocialSisterYi/bilibili-API-collect` 已于 **2026-01-28** 停止维护并删除文档：
  维护者称收到 B 站委托律所的律师函，指控系统性收集/传播非公开 API。
- 影响：
  - 不能再引用该仓库作为接口文档。
  - 新接口以仓库现有实现 + 运行验证为准；不把接口细节写进公开文档。
  - bilimiao2（GPL-3.0）只读参考；移植代码前确认整体许可证策略。
- 产品侧：不内置 B 站官方 logo/2233 娘/受版权保护的资源；应用名为 `bili-duo`。

---

## 9. 决策映射

| 调研结论 | 设计决策 | 文档 |
|---|---|---|
| 下屏 640×480dp、4:3、极矮 | 左 rail 64dp + 3 列网格 + 28dp 模式条 | DESIGN_SYSTEM §2/§5 |
| BBLL 4 列在 1920px | 640dp 取 3 列，卡片 178.7dp | SCREEN_SPEC §3 |
| xVideoPlayer 46/54 双栏 | Duo 用 272/332 双栏控制台 | SCREEN_SPEC §6 |
| Wayfinder 焦点需 root | 上屏接收 + 显式模式转发，不自建系统焦点 | INPUT_AND_FOCUS §3 |
| TV 焦点规范 | 初始焦点、焦点可见、列表带入视野 | INPUT_AND_FOCUS §4 |
| Wii U 第二屏支持主画面 | 下屏完整 UI，不做遥控器复刻 | DESIGN_SYSTEM §1 |
| Surface Duo supporting panel | 详情/控制台双栏 | SCREEN_SPEC §5/§6 |
| API 文档关停 | 接口降级、空态、只读参考 bilimiao2 | ARCHITECTURE §8.1 |

---

## 10. 待真机验证

- [ ] Presentation 在下屏的实际窗口尺寸/insets（是否有系统栏）。
- [ ] `decorView.dispatchKeyEvent` 能否驱动 Compose 焦点（重点）。
- [ ] 系统 IME 是否出现在下屏；应用内键盘兜底。
- [ ] 手柄 A/B/X/Y 的 keyCode 与重复行为（不同手柄）。
- [ ] 120Hz 下双窗口的帧率/功耗。
- [ ] HDR 视频在 PlayerView 的表现。
- [ ] 下屏热插拔/合盖的实际行为。

> 这些项目在 MuMu 上无法验证，必须等 Duo 在线时集中测试。
