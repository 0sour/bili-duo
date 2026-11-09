# dist — 构建产物

本目录只保留构建说明和校验用哈希。**APK 不提交到 Git**（`.gitignore` 已排除
`*.apk`），需要时本地 `./gradlew assembleDebug` / `assembleRelease` 生成，或从
GitHub Releases 下载。

## 0.2.0-rc52（2026-11-09）

- `bili-duo-0.2.0-rc52-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc51。
  - APK SHA-256：`cead2e4a4a10a56f063941487183dde0fd2d3c73f9d0d476cc1fe6a368bb064c`
- `bili-duo-0.2.0-rc52-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`b9d8d54f276169c19547f071a020bc74cea9e40f1d0b21e23b2d765b0a931f7b`

rc52 变更：
- 设置新增独立「手柄」分类，手柄风格与实体按键映射从「播放」迁入。
- 焦点环失焦立即消失，rail 点击先聚焦目标项；页面入口焦点在 rail 锁定期间被
  `DuoPageFocusGate` 拦截，消除切页时指示器闪旧项/内容再回来的残影。
- 起播优化：API 限流改为按请求起点 120ms；VOD 元数据等待 1.6s→0.6s、本地有进度
  跳过；弹幕延后 900ms；VIP 状态缓存 5 分钟；直播 playurl 与房间详情并行。
- Duo 实测同一直播间首帧约 2.0s→0.8s；VOD 约 0.7–1.5s。232 单测通过。

## 0.2.0-rc51（2026-11-09）

- `bili-duo-0.2.0-rc51-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc50。
  - APK SHA-256：`e067bdfbfa28e46b9c45c000d974f5257fc1efaf9e17ffaad6bb2e7f32f59ea3`
- `bili-duo-0.2.0-rc51-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`f21ffc0a61f42db5be5fc96a76d0b321a8bd2cf82a1153e6bfd2bc5b51bee587`

rc51 变更：
- RP Duo 手柄风格：设置 → 播放 → 手柄可选 Xbox / Retro；首次启动显示选择页，
  可跳过并默认 Xbox。Retro 在 `InputRouter` 入口做 A↔B、X↔Y 归一化。
- 实体位置功能固定：B 确认、A 返回、Y 刷新/弹幕/点赞/复制；屏幕提示统一改为
  实体丝印，X（物理上方）不绑定。
- Duo 实测：Xbox 96/97/99 = 确认/返回/刷新；Retro 97/96/100 =
  确认/返回/刷新；切换设置立即生效。232 单测通过。

## 0.2.0-rc50（2026-11-08）

已发布：https://github.com/0sour/bili-duo/releases/tag/v0.2.0-rc50

- `bili-duo-0.2.0-rc50-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc49。
  - APK SHA-256：`4e7bbce73f5a694b46e232e5d56c0367edf16cba43613ba96950ef3e1fb49e11`
- `bili-duo-0.2.0-rc50-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`5c2a5329f1afe470afd06d656583c0829a83a048155e38fdc8a9169503a20bf9`

rc50 变更：
- 左侧 app rail 导航后焦点留在新选中的栏位，不自动跳进页面内容；方向键或触摸
  才释放。
- L1/R1 切标签后直接聚焦内容：首页 top tabs、动态类型、搜索视频/用户、下载筛选、
  UP 空间视频/合集、设置分类、播放设置弹幕/字幕。直接 A/点击标签则保留标签焦点；
  L2/R2 筛选仍停在新 chip。
- rail 聚焦时页面不在焦点树里，新增 `DuoShoulderDispatcher` 转发 L1/R1/L2/R2；
  搜索页肩键改用 `duoTabCycling` 接入。228 单测通过。
- Duo 已验证：rail 焦点、Right 进首卡、rail 聚焦 R1 切首页 tab/动态筛选并落首卡、
  首页 tab 点击保焦、搜索 R1 切用户落首行、播放设置 L1 落首行。用户已完成本轮
  真机复测（含物理 X、摇杆/双指、长按/断连），反馈无明显 bug；设置“关于”状态
  文案改为“测试版 RC”。

## 0.2.0-rc49（2026-11-08）

- `bili-duo-0.2.0-rc49-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc48。
  - APK SHA-256：`dd21af5c82dd5d55397c24bb4181b6e76744fe9dfe487f06d968910d49b33684`
- `bili-duo-0.2.0-rc49-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`c0282144a27b6a87bd3dcc1890922a0b479d97c3b99718c9c3ee1a583ffc0c3e`

rc49 变更：
- 设置 → 播放新增「视频设置 · 每次打开视频时应用」：默认清晰度、默认倍速、
  默认字幕开关与字号/背景/位置；旧 rc48 字幕全局样式自动迁移为默认值。
- 播放控制台的清晰度/倍速/字幕只影响当前视频，不回写设置；弹幕设置仍与
  设置「弹幕」分类双向同步。
- 修复在线/PGC 加载时 `PlayerUiState(isLoading=true)` 覆盖默认字幕开关的问题
  （新状态统一 `withVideoDefaults()`）。228 单测通过。
- Duo 已验证：默认 720P/1.25x/字幕开启在连续两个视频生效，AI 字幕上屏显示；
  播放页改 1080P/1.5x/特大/无背景/靠上后设置默认值不变。测试后已恢复
  自动/1x/字幕关/标准。

## 0.2.0-rc48（2026-11-08）

- `bili-duo-0.2.0-rc48-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc47。
  - APK SHA-256：`640eb4f332b05052f293074d97cb23a33ea6143ab6624e0e5273e74aebb66cca`
- `bili-duo-0.2.0-rc48-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`eca8ec3907b431bfab6c1cc769791073d5fd4a60d6d2c8774826dd189708139e`

rc48 变更：
- 播放设置「弹幕 / 字幕」改为标签页（文字 + 下划线），L1/R1 切换并聚焦新标签；
  D-pad Left/Right 仍可切换；屏蔽词编辑态不响应 L1/R1。
- 标签下方增加 L1/R1 提示；225 单测通过。尚未在真机播放视频复测。

## 0.2.0-rc47（2026-11-08）

- `bili-duo-0.2.0-rc47-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc46。
  - APK SHA-256：`2cb4e523e298d2d6ad862abaddd632190c11ea0b7d63a99ee7d0498bc4279f94`
- `bili-duo-0.2.0-rc47-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`24dce0958b11c34018d4b554de6844c9c8a1a714981e864de07c91926411ebbf`

rc47 变更：
- 播放页 START/MENU 打开 `DuoOverlay.SETTINGS`（弹幕/字幕分栏，默认弹幕），
  不再打开清晰度菜单；清晰度仍从 chips 行进入。
- 播放器页脚、路由底栏提示改为“START 弹幕/字幕”；225 单测通过。
  尚未在真机播放视频复测。

## 0.2.0-rc46（2026-11-08）

- `bili-duo-0.2.0-rc46-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc45。
  - APK SHA-256：`c67726f725338aa00980db92ed142a371dd309ff2a1f649a4789ce64c21eb6ed`
- `bili-duo-0.2.0-rc46-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`722333acb628aea995391c27dc1196793f6658c4a1722665a9068c6915345958`

rc46 变更：
- 底栏合为单行：`● 浏览/播放控制 + 页面按键提示 + B + A`，按路由显示
  X/LB/RB/L2/R2/START 等；标签缩短，给提示留空间。
- 动态阅读器/画册通过 `hintOverride` 显示专属提示；全局菜单打开时隐藏页面提示。
- 无手柄/单屏只显示状态文案；225 单测通过；Duo 已验证首页/搜索/动态底栏。

## 0.2.0-rc45（2026-11-08）

- `bili-duo-0.2.0-rc45-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc44。
  - APK SHA-256：`0898140078775980ebb2900a54b80d6ea364d759a3cbc777b1e9b419eb57e4fa`
- `bili-duo-0.2.0-rc45-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`d7ed9a4b3cf06fcbcb249b472c9bb23401218641fa22b1172a762d2f008b3250`

rc45 变更（修正 rc44 的 X 无反应）：
- 物理手柄 X 是 `KEYCODE_BUTTON_X`，不是 `KEYCODE_X`；Home/Dynamic 同时接受
  `Key.X` / `Key.ButtonX`，长按 repeat 由 InputRouter 吞掉。
- Home 按 X 刷新当前 tab 后焦点落到右上角刷新按钮（旧卡被替换，避免焦点丢失），
  Down 回第一张卡；刷新按钮本身仍保持焦点。
- 225 单测通过；Duo 用 `input gamepad keyevent 99`（BUTTON_X）验证刷新与焦点。

## 0.2.0-rc44（2026-11-08）

- `bili-duo-0.2.0-rc44-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc43。
  - APK SHA-256：`f637c1bf4bc6abb3d3022d13d6464f435c6f8d51f29dddd881d620b45a0908db`
- `bili-duo-0.2.0-rc44-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`20083d2b54f8094624c2c38a54b5055794e8e115fe8559b98d7747d45983b20a`

rc44 变更：
- 主页推荐/热门/排行/直播/番剧/影视统一支持 X 刷新当前标签页，与右上角刷新按钮
  共用 `HomeViewModel.refresh()`；长按 X 由 InputRouter 吞 repeat，只刷新一次。
- 225 单测通过；Duo 实测 X 后推荐内容更新。

## 0.2.0-rc43（2026-11-08）

- `bili-duo-0.2.0-rc43-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc42。
  - APK SHA-256：`c1b549ae8938fe4424d4579a2d762e88edbea2586623cf083e03ef51eebc2110`
- `bili-duo-0.2.0-rc43-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`12e6ba67445847bda2895416cc45808057c78c2d4a17afb38cb46025d6d15b44`

rc43 变更：
- 图片查看器左摇杆平移方向取反：推右/上时画面向左/下移动（滚动式）；触摸拖动
  方向不变。R2/L2 缩放、双指缩放不变。
- 225 单测通过；已装 Duo，真实手柄方向待用户复测。

## 0.2.0-rc42（2026-11-08）

- `bili-duo-0.2.0-rc42-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc41。
  - APK SHA-256：`53943ad17d1c698d2a36493f826a0379754f5f894e2f15cf560461ccac0a78c9`
- `bili-duo-0.2.0-rc42-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`057b3230930a1ad93143409a0286c482a658426134e0aad7a48ecdc71cf0f43f`

rc42 变更：
- 评论图片与动态画册支持 R2/L2 1–4x 缩放、左摇杆平移、双指缩放/单指拖动；
  切换图片重置缩放。左摇杆仅在查看器打开时生效，HAT/DPAD 不拦截。
- 主页推荐接入 `fresh_idx/fresh_idx_1h` 无限滚动；Header 新增刷新按钮（可聚焦，
  加载中显示 spinner，Down 到首卡）；刷新保留旧列表，失败不清空。
- 225 单测通过；Duo 已验证动态画册缩放/拖动/复位、首页刷新与分页。
  真实左摇杆与双指捏合待用户用手柄/触摸复测。

## 0.2.0-rc41（2026-11-08）

- `bili-duo-0.2.0-rc41-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc40。
  - APK SHA-256：`823b85b4790edafac2151c709eb9f3aa335982aa31a2650a35ca8fea00b4ead7`
- `bili-duo-0.2.0-rc41-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`74975c9ec5fa307fa3b7c55095b4d28a5fb954c8eaef37faed542b16586beab4`

rc41 变更（用户逐项选择）：
- 输入框统一为 `DuoTextFieldGate`：非编辑态禁用字段 + 透明焦点 overlay；A/Enter/
  触摸后切到真实字段并弹 IME，B 收键盘但保留焦点。
- C 不抢焦点 11 处：主搜索、下载/历史/收藏/关注/UP 主空间搜索、短信验证码、
  密码登录密码、直播评论、动态评论、收藏夹简介。
- A 选中待命 2 处：短信登录手机号、密码登录账号。
- B 进入即弹 4 处：弹幕发送、视频评论发送、播放设置屏蔽词、收藏夹标题。
- 搜索按钮去重：列表搜索模式隐藏页头搜索图标；主搜索页去掉右侧提交放大镜。
- `showDuoIme()` 增加 300/600ms 重试：修复冷启动第一次 IME 在 Presentation
  更新可见性时被系统拒绝（`PHASE_SERVER_UPDATE_CLIENT_VISIBILITY`）。
- Duo 已验证收藏夹标题 B、主搜索 C（含冷启动 IME、触屏重开 IME）、直播评论 C；
  221 单测通过。登录字段与播放器弹幕/评论浮层待账号/视频样本复测。

## 0.2.0-rc40（2026-11-08）

- `bili-duo-0.2.0-rc40-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc39。
  - APK SHA-256：`082b16c05e7d918444d14c6ed2b7dcaa0057cff89c9939f622dbac63dffd710c`
- `bili-duo-0.2.0-rc40-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`57a45165d99bd3cb7e92ea738a051eff10f3b37755a8f5011174e552f17a4ab8`

rc40 变更（用户反馈）：
- 所有输入框：聚焦只显示选中/焦点环，按 A 才弹出输入法。
- B 先收起输入法，焦点保留在输入框；再按 B 才返回或关闭浮层。
- 搜索、登录、评论/弹幕发送等自动聚焦入口不再自动弹键盘。
- 新增 `Modifier.duoTextInput()`；补全登录、收藏夹简介、屏蔽词、评论等输入框。
- Duo 已验证搜索框完整流程（聚焦→A→B 收键盘保留焦点→B 返回）；221 单测通过。

## 0.2.0-rc39（2026-11-08）

- `bili-duo-0.2.0-rc39-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc38。
  - APK SHA-256：`6b746f99cdf9a1b6e87b03fb250b4145d1fb6b2d3802e1d5a4b495a3b17b2634`
- `bili-duo-0.2.0-rc39-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`904fc3d3e1492460337045f883479fc98c9fde1ce3c6132e46f4ec6ba6bc84ea`

rc39 变更（用户反馈）：
- 动态评论发送后 700ms 补拉一次，减少手动刷新需要。
- Y 暂不映射：动态列表回顶、搜索页切排序移除。
- 动态评论「加载更多」保持焦点，加载中 spinner 不替换焦点节点。
- 上屏直播 SC 卡片移除，SC 只在下屏显示；直播左栏新增「弹幕 开/关」chip。
- 下载自选分P：对勾改为居中图标；分P Up→全选，全选 Down→P1。
- Duo 已验证下载弹窗焦点/对勾与直播弹幕 chip；221 单测通过。

## 0.2.0-rc38（2026-11-08）

- `bili-duo-0.2.0-rc38-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc37。
  - APK SHA-256：`ec929cd647c867243edda3442a806a438c08164e6d09e43f5f4840c96b1f8833`
- `bili-duo-0.2.0-rc38-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`d61a033c3db4f4c5f29f91cfc12e176176444237f8941cdc9322d0375c5b0c9a`

rc38 变更：
- 设置→存储的下载目录可打开 SAF 系统文件夹选择器，持久化 tree URI。
- 新下载通过 DocumentsContract 在所选目录创建文档，filePath 为 content://；
  写入支持 "wa" 追加，删除走 DocumentsContract。
- 下载页可直接播放 SAF 文件（Media3 content://），删除任务同时删除文档。
- 字幕改存应用私有目录，`DownloadInfo.subtitleUris` 记录；兼容旧同目录 JSON。
- 可「恢复默认下载目录」回到应用私有 downloads。
- Duo 已验证选目录、下载、上屏播放、暂停/续传（15.1MB→32.4MB）、删除与恢复默认。
- 221 单测通过。

## 0.2.0-rc37（2026-11-08）

- `bili-duo-0.2.0-rc37-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc36。
  - APK SHA-256：`f4ca449467ea2f0d3f85745987338f910ed15689eea782d69583d95bc579e90a`
- `bili-duo-0.2.0-rc37-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`85207f6aa47b311bc90fcc46f89c603be41edcd33612201fb7e319b5629fde5a`

rc37 变更：
- 下载范围新增「自选分P」：列出全部分P，默认勾选当前 P，逐项勾选 + 全选/清空。
- 「开始下载」固定在浮层底部，分P 很多时不需要滚到列表末尾。
- 批量入队只包含勾选的分P；按钮显示「下载已选 N 个」。
- Duo 已验证：50 分P 视频自选 2 个 → 下载页生成 2 条任务 → 删除还原；221 单测。

## 0.2.0-rc36（2026-11-08）

- `bili-duo-0.2.0-rc36-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc35。
  - APK SHA-256：`cb20559f8dbaff5c5025fc55766c1f9fb19968c0a3162e0e64a83d60ec87c33f`
- `bili-duo-0.2.0-rc36-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`7d5028855a3ebd3fb537af1264a3aa6e4712198b2e35360ae3c130ab23ebd790`

rc36 变更：
- 直播弹幕实时叠加到上屏：`LiveChatStore` 镜像下屏 WebSocket 消息，
  `LiveDanmakuView` 按空闲泳道滚动，支持透明度/速度/字号/区域/屏蔽词/同屏上限。
- 直播间 X 键开关弹幕，与 VOD 共用 `DanmakuPreference`；左栏增加提示。
- SC 不进滚动层，改为上屏左下角卡片，取最新未过期 SC；真实 SC 样本待补测。
- 离开直播间清理消息，不会带到下一个房间。
- Duo 已验证：上屏弹幕出现、X 关/开生效；221 单测通过。

## 0.2.0-rc35（2026-11-08）

- `bili-duo-0.2.0-rc35-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc34。
  - APK SHA-256：`e26605a2a5da50699d27d935102264f59a017101df86b019ce4cc7b2728d1449`
- `bili-duo-0.2.0-rc35-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`9f94db848a1e11935e15f036734447212fde8285ddabb7eb15b42a2def3c9582`

rc35 变更：
- 动态评论 tab 增加内联输入框（发送/1000 字校验/登录校验），发送后刷新列表。
- 发送成功显示服务端 `success_toast`；若提示「被up主精选后对所有人可见」，评论
  暂不出现在公开列表，这是 B 站审核机制，不是发送失败。
- 发送键右侧出现「撤回」，仅对本次会话刚发布的评论生效；自己的评论行按 A 可删除（未实测）。
- B 在输入框/发送/撤回聚焦时先关 IME 并回到「评论」tab，再按 B 才回正文。
- 修复「图文」筛选仍显示视频卡：feed API 的 type 仅作提示，客户端统一再过滤。
- 221 单测通过；Duo 已验证评论发送/提示/撤回与两段 B 返回。

## 0.2.0-rc34（2026-11-08）

- `bili-duo-0.2.0-rc34-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc33。
  - APK SHA-256：`8a88f90c5e51e75602e01fa91186ed7a1c0f82a925e6c19725c08e88d0971712`
- `bili-duo-0.2.0-rc34-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`be3c1064894eb65aa7b84626da7857574f2c98d0c4eeccb1297851594e440ccf`

rc34 变更：
- 动态详情新增「正文 / 评论」标签页，LB/RB 切换，评论内嵌加载/分页/X 点赞。
- B 在评论页先回正文，再按 B 退出详情。
- 移除 Y 评论入口；Y 在动态列表回到顶部。
- 221 单测通过。Duo 已验证：正文/评论标签页、LB/RB、B 从评论回正文、Y 回顶。

## 0.2.0-rc33（2026-11-06）

- `bili-duo-0.2.0-rc33-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc32。
  - APK SHA-256：`REPLACE_HASH`
- `bili-duo-0.2.0-rc33-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`2c0521e8d97811708c292d845d5af9424cf6b65f4759760dd211c73a72a526e1`

rc33 变更：
- 动态详情/纯文字阅读 **LB/RB**（或点评论数）打开动态评论（oid/type 取 basic 字段，分页/重试）；焦点在评论上按 X 点赞/取消。
- 评论浮层关闭后恢复阅读层 B interceptor，不会直接退出动态。
- 直播「回到直播」改为文字按钮，移除 SkipNext 图标歧义。
- 221 单测通过；Duo 已安装；动态评论已验证可打开（opus 卡 type=11、oid 取 item.basic.comment_id_str）。

## 0.2.0-rc32（2026-11-06）

- `bili-duo-0.2.0-rc32-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc31。
  - APK SHA-256：`7b8c73a7a5003cdb4b397b5d07375933011f99526a912b1d4aad56f94e2df7ac`
- `bili-duo-0.2.0-rc32-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`931185cb8148fd8953e96a523bb744661ec7ac53062ef20509a667508d0e7f2a`

rc32 变更（P2 首批）：
- 个人中心显示「大会员」徽标，上屏个人卡同步。
- 动态详情/画册/纯文字阅读支持 X 复制链接（不使用系统 chooser，避免 Presentation
  启动透明且不可操作的 ChooserActivityLauncher）。
- 直播 SC 从弹幕列表移到评论 tab 顶部横幅，按 `end_time` 过期，删除后回退。
- 221 单测通过。Duo 验证徽标、动态阅读层入口与直播布局；真实 SC 样本待补测。

## 0.2.0-rc31（2026-11-06）

- `bili-duo-0.2.0-rc31-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc30。
  - APK SHA-256：`fad357098a46db6f607a2ed43ebd097bb0ef26b9aa9ccc3d0f945870bc83ad0b`
- `bili-duo-0.2.0-rc31-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`2c282013686b6439c9e6343b6ccb9844b230b4956df3486d0fc22a7f76db60e5`

rc31 变更：
- 读取 `nav` 大会员状态；非会员时 1080P+ / PGC 1080P 显示「大会员」并阻止切换。
- PGC `is_preview` 显示「试看」徽标与结束提示；试看结束不自动跳下一集。
- 视频 `is_upower_exclusive` 显示充电专属说明，播放失败时补充提示。
- durl 线路没有 DASH 时清晰度弹窗提示「当前线路不支持切换清晰度」。
- 221 单测通过。Duo 当前账号为大会员，验证清晰度列表无锁、PGC 正常播放；
  非会员锁标/试看/充电专属待补测。

## 0.2.0-rc30（2026-11-06）

- `bili-duo-0.2.0-rc30-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc29。
  - APK SHA-256：`7cc493c96e954ea29f82d51aa46e3fc72e0ed02cfd3c3fb1e35637d3232715c2`
- `bili-duo-0.2.0-rc30-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`34b18dddc6b34d2fb0a78982781347bf3f3d0924a4073ce18749156b728803b3`

rc30 变更：
- 排行 tab 增加「全站/动画/音乐/游戏/知识/生活/影视」分区 chips。
- L2/R2 循环切换分区并聚焦新 chip；切区保留旧列表到新数据返回。
- `ranking/v2` 按 `rid` 请求对应分区；chip Down 进首张卡片。
- 221 单测通过。Duo 验证全站↔动画切换、焦点保持与 Down 进卡。

## 0.2.0-rc29（2026-11-06）

- `bili-duo-0.2.0-rc29-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc28。
  - APK SHA-256：`f10108eac76cf03182b79a5752b97a212cf2aab88f6bb27e53748470eb05de86`
- `bili-duo-0.2.0-rc29-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`7aa34b506fb3bc5909e54a1f560a5674c4a50a6f04727c97cdaad1f29620a781`

rc29 变更：
- 按用户要求移除「一键三连」入口、`performTriple`/`tripleBusy` 和默认收藏夹逻辑。
- 投币弹窗只保留 1 枚/2 枚选择与确认；收藏弹窗与关注 Up 焦点修复不变。
- 221 单测通过。Duo 截图验证投币弹窗底部无三连行。

## 0.2.0-rc28（2026-11-06）

- `bili-duo-0.2.0-rc28-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc27。
  - APK SHA-256：`a8fbde23f13836be0d32f50e7fd8fbf196b6e4cb69e53d9c24d8c2d13371a1bf`
- `bili-duo-0.2.0-rc28-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`167134ebed158f641cb919fc0112300bceff3cd9f6b7cb5ed8d761645f199fdd`

rc28 变更：
- 投币弹窗：选择 1 枚或 2 枚（按已投数量限制），确认后才提交；底部「一键三连」。
- 收藏弹窗：列出收藏夹，已收藏的显示勾选；选择即加入或移出该收藏夹。
- 一键三连：点赞 → 收藏到默认收藏夹（仅未收藏时）→ 补足 2 枚硬币，任一步失败停止。
- 简介页关注按钮：Up 回右栏 tab，Left 回 UP 卡；UP 卡 Right 到关注，不再跳左栏。
- 221 单测通过。Duo 验证弹窗布局/焦点链、关注 Up；投币/收藏/三连确认动作未执行。

## 0.2.0-rc27（2026-11-06）

- `bili-duo-0.2.0-rc27-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc26。
  - APK SHA-256：`ea4dd2e84dc14cde8d9acf12153cb8f1c97c1a366ab77f45bf1dd55c0cdb5e30`
- `bili-duo-0.2.0-rc27-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`52b231fbdbcffe322c2da77121a6b045a3c2ef8004cde34ac82b58cc35d4dd02`

rc27 变更（P1 首批）：
- 播放页右栏 tab 改为「简介 / 评论 / 分P(选集) / 推荐」，默认简介；评论/推荐懒加载
  与 L2/R2 评论排序只在对应 tab 生效。
- 简介页操作行改为「点赞 / 投币 / 收藏 / 下载」，统计行增加硬币数；点赞、投币、
  收藏计数即时更新并显示选中态。
- 点赞 `x/web-interface/archive/like`：点赞 `like=1`，**取消点赞 `like=2`**；传
  `like=0` 会返回 -400「请求错误」。
- 221 单测通过。Duo 验证：默认简介、R1 切评论/分P/推荐、点赞 389→390→389 并
  恢复未点赞。投币不可撤回，未由助手实测。

## 0.2.0-rc26（2026-11-06）

- `bili-duo-0.2.0-rc26-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc25。
  - APK SHA-256：`b1fe7e297e4df27ea173ae7beadde1e3e6b10f3f9ec0bdcfb9ad7724bd7a2314`
- `bili-duo-0.2.0-rc26-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`b18b56e65392830f4f3541ae2da128644a8edb67a856ad204a34eaf8f4dc152c`

rc26 变更：
- 下载页返回键与页头所有图标（暂停/继续/清除/搜索）的 Down 显式进入内容：
  搜索态进搜索框，有任务进当前筛选 chip，空态进「去首页找视频」。
- 修复搜索图标按 Down 落到左侧 rail 的问题。
- 221 单测通过。Duo 截图验证：搜索图标聚焦 → Down → 空态按钮白环。

## 0.2.0-rc25（2026-11-06）

- `bili-duo-0.2.0-rc25-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc24。
  - APK SHA-256：`1734a0fb564965e887587667ee950d0a0530af7dd830d349d862c47f53a52064`
- `bili-duo-0.2.0-rc25-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`6164cb847dcbfa0e67a3dae2daece5dddd42d88e294ab913720d52c5aeab467e`

rc25 变更：
- 下载页空态（无下载 / 筛选为空）的动作按钮「去首页找视频」「查看全部」按上时
  显式回到页头返回键，不再跳到左侧 rail 的「搜索」。
- `DuoEmptyState` 增加可选 `upFocus`；不影响未传该参数的页面。
- 221 单测通过。Duo 截图验证：按钮聚焦后 Up → 返回键白环。

## 0.2.0-rc24（2026-11-06）

- `bili-duo-0.2.0-rc24-release.apk`
  - R8 minify，正式 release keystore 签名；可覆盖安装同 key 的 rc23。
  - APK SHA-256：`91217a4a4b59dc843cd425a6306562e18f3ecd1ca1ba9f1b9d16c7f2dfa0b132`
- `bili-duo-0.2.0-rc24-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`160f490f3162c2a418bdba0973527b49f74fe89322bc6f4f494a7646b53507cc`

rc24 变更（焦点保持与返回修复）：
- 评论 L2/R2 提到控制台根处理：焦点在左栏任意位置也能切热门/最新，切换后指示器
  停在新选中的 chip 上。
- 搜索类型/排序、首页直播父分区、动态类型/UP、下载筛选、UP 主空间视频/合集在
  L1/R1、L2/R2 切换后焦点留在新选中项；列表首项自动聚焦增加 600ms 抑制窗口。
- 搜索排序行在结果加载/空态期间保持挂载，切排序不再因 requester 无目标丢焦点。
- 二级页 `DuoPageHeader` 返回键与标题间距 2→8dp，焦点环不再压字。
- 弹幕发送弹窗「位置」改为滚动/顶部/底部横向单选，`DanmakuOptionRow` 统一
  label 宽度与内缩，和颜色行对齐。
- `MainActivity` B 返回 220ms 节流 + 只在根页以上 pop，pop 过渡改淡出；连续按 B
  不再出现只有左侧栏、右侧空白的页面。
- 221 单测通过。Duo 实测：评论 L2/R2（含左栏）、搜索 R2、动态 R1/L2、首页直播
  L2、历史返回环、快速连按 B。

## 0.2.0-rc23（2026-11-06）

- `bili-duo-0.2.0-rc23-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`9007c83ad8143713bded17b16508971932e29224e474551d66794ab77f5eeab3`
- `bili-duo-0.2.0-rc23-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`db6648e07d2e84bd0eb14cc2d0492951f8b6b24951b1f3279afd9d54731cb476`

rc23 变更（P0 可用性收口）：
- 二级页 `DuoPageHeader` 44→56dp；设置页返回键接入焦点链（首行/分类栏 Up 到返回）。
- 历史/收藏/关注/下载卡片与删除按钮、搜索排序、UP 主 tabs、评论排序、控制台
  右栏 tab、下载浮层的焦点环余量统一修正。
- 评论头像可聚焦（A 进 UP 主页），首条评论 Up 回排序 chip；评论 L2/R2 切
  热门/最新，排序请求加 requestId 防旧响应覆盖。
- 左栏「发一条弹幕」与 chips 左对齐；弹幕颜色改 6 色块；直播输入框 38dp 且占位
  居中。
- 直播父/子分区切换保留旧列表 + inline loading；动态 UP/图文筛选可同时生效。
- 上屏旧卡片清除加 250ms 防抖、卡片焦点改 `hasFocus`；Detail 分享补 `NEW_TASK`。
- 221 单测通过。MuMu 验证：tab/评论排序环、颜色色块、直播输入框、直播页布局；
  Duo 真机回归待用户带机测试。

## 0.2.0-rc22（2026-11-05）

- `bili-duo-0.2.0-rc22-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`722b1e45b2a8f93ec215713963e5f13a8f9868c4f0ee769644f895913042e0f3`
- `bili-duo-0.2.0-rc22-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`737d0ddc10a6712a69ff4540404d16b31074f97c44854c7c19d377c6c1f72151`

rc22 变更：评论区首条「展开全文」按上跳到左栏。
- 首条长评论的展开链接在 LazyColumn 内上方无焦点项，Compose 按几何距离选中左栏
  播放控制。`CommentsTab` 把「热门」chip 的 requester 传给 `CommentRow`，只给
  `index == 0` 的展开链接设 `up`；中间评论仍走默认上一条评论操作行。
Duo 验证：上 → 热门 chip、下 → 点赞，焦点环留在右栏。

## 0.2.0-rc21（2026-11-05）

- `bili-duo-0.2.0-rc21-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`ef0cb5b1b4c4d93db5337a8a6ebbb506097f95a4b6090cbf826b4c8b1db24734`
- `bili-duo-0.2.0-rc21-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`b96966bf3171eedb8c0543ea2b8e357d94854ed2a9c27b07f9df98be859ece6c`

rc21 变更：回退 rc20 的右摇杆切 tab。
- rc20 在 `MainActivity.dispatchGenericMotionEvent` 拦截手柄 MotionEvent，真机上
  导致 Duo 左侧方向键失灵（D-pad HAT 事件被吞）。
- 功能整体移除：`RightStickTabStepper`/单测、`InputRouter.dispatchMotion`、
  `DuoSessionViewModel.tabScroll`、`PlayerConsoleScreen` 的收集逻辑全部删除；
  右栏 tab 仍用 LB/RB 切换。单测回到 221。
Duo 验证：安装 rc21 后 adb 注入方向键右/下/左/上均能正常移动焦点；物理 D-pad 待用户确认。

## 0.2.0-rc19（2026-11-05）

- `bili-duo-0.2.0-rc19-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`533942b467039d758f29232ff162a75448333b00d55cf9fe269b702cf11dda0e`
- `bili-duo-0.2.0-rc19-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`8e31071a33f06310d5abf231893270fe30d457537c50d46abfdb6f2d1b38bf1f`

rc19 变更：进度条焦点环与暂停/播放按钮顶部重叠约 1.75dp。
- 进度条外层 30dp Box 的焦点环外伸 5.75dp，后面只有 4dp 间距。
- 时间行与进度条之间加 4dp，进度条与 transport 行之间由 4dp 改为 10dp。
Duo 真机验证进度条选中时环底部与播放键有明显空隙。

## 0.2.0-rc18（2026-11-05）

- `bili-duo-0.2.0-rc18-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`ee419e880745c46486f187d867350137fe3cdcc77fdd5bbd14fe085eb9dbd4e4`
- `bili-duo-0.2.0-rc18-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`97635f861643996a24a78ea1eb2a3026e7b833e3a45337b8c9b46cfb1d985a02`

rc18 变更：评论区图片缩略图可聚焦但没有焦点指示器。
- `CommentPicturesRow` 只加了 `duoClickable`，漏了 `duoFocusRing`；补上
  4dp 圆角白环（放在 `clip` 前）。评论与回复共用该组件。
Duo 真机验证评论内截图选中时四边焦点环完整；测试观看历史已删除。

## 0.2.0-rc17（2026-11-05）

- `bili-duo-0.2.0-rc17-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`e812aeee6904a762eaeb6b1aeedf9cc5c9d927a2c9a5f2984fcd6a6e92ef0a5a`
- `bili-duo-0.2.0-rc17-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`913d4ab165fe7a47a021991d3e98b3dd79b9404366317a06519e2f7953cb1409`

rc17 变更：控制台底部 chips 行第一个 chip 的焦点环左边被裁。
- `horizontalScroll` 按自身矩形裁切；清晰度 chip 顶满滚动视口，选中时焦点环
  左侧超出视口被裁。
- 把横向 padding 从 `horizontalScroll` 之前移到滚动内容内侧，并增加到 8dp。
Duo 真机验证 1080P60 chip 选中时焦点环左侧完整；测试观看历史已删除。

## 0.2.0-rc16（2026-11-05）

- `bili-duo-0.2.0-rc16-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`0daf66678a1868860a46af281f5f35218e708165a85841a70dfa1781ff17d83f`
- `bili-duo-0.2.0-rc16-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`9a8f0cdf061fe119e15bdf426ca89f96f759dc4713796d6ec5fe92a7d016a8a6`

rc16 变更：清晰度/下载浮层的选中焦点环被滚动容器左右裁切。
- `verticalScroll` 按自身矩形裁切四个方向；行 `fillMaxWidth()` 顶满 viewport 时，
  `duoFocusRing` 外伸的左侧/右侧被裁。
- 清晰度滚动内容加 `padding(horizontal = 8.dp, vertical = 7.dp)`；
  下载浮层滚动内容加横向 8dp。速度浮层不滚动，不受影响。
Duo 真机验证清晰度 1080P+ 选中态四边环完整；下载浮层在 MuMu 验证。

## 0.2.0-rc15（2026-11-05）

- `bili-duo-0.2.0-rc15-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`4cce9674b15f419928df62e296211691f90d8521eabd3f4c879453735a984c80`
- `bili-duo-0.2.0-rc15-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`7393948a8794a970334ef33523ee813cf7b9f7ee6a3cb13b6a8772e68767d179`

rc15 变更：设置页布局与手柄焦点重做。
- 内容行改为 6dp 间距 + 上下 10dp 内边距，卡片不再贴在一起，焦点环不会被
  相邻卡片或滚动视口底边裁切。
- 左侧分类栏可聚焦：↑/↓ 选择分类，→ 或 A 进入内容首行；内容内 ← 回分类栏。
- 首行 Up / 末行 Down 显式停住，不再跳到全局 app rail；所有信息行均可聚焦。
- L1/R1 切分类并聚焦首行；分类切换时滚动回顶部。
MuMu 验证布局、分类栏/内容焦点、外观页底部止步与关于页末项滚动；Duo 真机逐项
按键验证分类栏移动/进入、外观页止步、关于页末项环完整、L1/R1 与触摸分类。

## 0.2.0-rc14（2026-11-05）

- `bili-duo-0.2.0-rc14-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`69536d3a6e01cbfa738c1e2e3787d9db79ac878da1531957f0e5fbe4a9c1ec09`
- `bili-duo-0.2.0-rc14-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`d360b84a2a219c2761c1fb70ce8d0313a4b84b48da6103f9f6e4a4027fafaae9`

rc14 变更：字幕开关与设置移入齿轮菜单。
- 齿轮 overlay 改为「播放设置」，顶部「弹幕 / 字幕」分栏；字幕分栏含
  「显示字幕 / 字幕轨道 / 字号 / 背景 / 位置」。
- 字号（小/标准/大/特大）、背景（无/淡/标准/深）、位置（靠下/标准/靠上）
  为全局样式，持久化在 `bili_prefs`。
- 移除 chips 行「字幕 开/关」，清晰度/倍速/弹幕/音量/齿轮不再被裁切；
  浮层高度上限 360→400dp，弹幕分栏 7 行完整显示。
- 分栏在 tab 上按 Left/Right 切换；菜单内 Left/Right 不落到背后控制台。
单测 221；Duo 实测字幕开关、AI 字幕轨、字号/背景/位置实时生效（测试后恢复
默认并关闭字幕）。

## 0.2.0-rc13（2026-11-05）

- `bili-duo-0.2.0-rc13-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`bef481a33971d3caec0cae136a88ee765171b4c2f2df7c86f379f946ddee7309`
- `bili-duo-0.2.0-rc13-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`31f61e019eecfebdf365d1cfdf9e90ec7c50052af9116f7e187e3e2c490bc149`

rc13 变更：播放页手柄焦点改为按真实空间位置移动。
- 播放键 Up → 进度条 → 返回键；返回键再 Up 无反应。
- 左侧 chips Down 只到「接下来播放」，没有下一P 时停住，不再跳右侧评论栏。
- 右栏顶部 tab Up 无反应；分P/相关列表最后一项 Down 停住；评论底部
  「加载更多 / 没有更多评论了」也作为边界停住。
- 左右栏仍通过 Left/Right 按空间位置切换。
单测 221；Duo 实测上述焦点链路（Up/Down 截图确认）。

## 0.2.0-rc12（2026-11-05）

- `bili-duo-0.2.0-rc12-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`ea36dfc6d8d4f92ddafe100975c15c6a3e86921f7db801ba33fbf0aadad8290d`
- `bili-duo-0.2.0-rc12-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`31a3c4c448af5bba898013edaa22c1da0e7ad41535ad20330c177e693e2b81ef`

rc12 修复：UGC 视频按 R1 切到「分P」标签页崩溃。原因是 rc11 给 `VideoPage`
新增了非空 `badge: String = ""`，但 Gson 反序列化 UGC 分P（JSON 没有 badge 字段）
时不会应用 Kotlin 默认值，运行时 `badge` 为 null，`PartsTab` 里 `badge.isNotBlank()`
抛 NPE。已把 `VideoPage.badge` 和 `PgcEpisode.badge` 改为可空并用 `orEmpty()`；
新增 2 个 Gson 单测。Duo 实测 UGC「分P」标签页正常显示、无崩溃。

## 0.2.0-rc11（2026-11-05）

- `bili-duo-0.2.0-rc11-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`905c97fd62f4592d91832f6a959a3f342df06f74db79ace722a297affefb16ff`
- `bili-duo-0.2.0-rc11-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`29e826dc42e8f340c1ffae5143e8bd61b025979d7356ccc214879520d20f6202`

rc11 变更：动态卡片支持点赞/取消点赞（头部显示状态与计数，手柄 X 直点，
乐观更新+失败回滚）；PGC 选集显示 `badge`（会员/抢先）并在会员专享播放失败时补充
提示；非视频收藏按资源 type 提交取消请求。手柄/UI 审查修复：清晰度/倍速/下载浮层
焦点不再漏到背后、设置开关不再抢焦点、搜索/合集/评论错误重试可聚焦、
清晰度浮层与列表焦点环补足、X/Y 长按去重、分页不再把焦点拉回顶部。
单测 219，Duo 验证动态点赞 131→132→131（账号已还原），MuMu 验证浮层焦点约束和
设置开关焦点保持。

## 0.2.0-rc10（2026-11-05）

- `bili-duo-0.2.0-rc10-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`a359ca3a51d89c606e2bfd409e34991d8ec0417a1dc60dc7467420b677083603`
- `bili-duo-0.2.0-rc10-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`1e0daf219b77fd994316841d22f51e684cbfc038a9b7ce9ca210b74986b8fb56`

rc10 变更：新增云端进度续播。打开视频时优先使用本机分P进度；本机没有记录时从
`x/player/wbi/v2` 读取账号的 `last_play_time`（与字幕共用一次请求，播放地址与
metadata 并行，最多等 1600ms），历史/收藏点击可直接续播。不足 5 秒或已到 95%
时长的进度会从头播放。单测 218，已在 Duo 用历史条目验证从 01:45/04:45 续播；
PGC 云端续播已接代码但未在真实剧集验证。

## 0.2.0-rc9（2026-11-05）

- `bili-duo-0.2.0-rc9-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`e7a921f04e779d4dd72e8531e21d0c2e9882f2264c75f6a9d979d6bc2c5d1c55`
- `bili-duo-0.2.0-rc9-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`6ae185ee1d41a3270a381f67e6f03b1752c6ba72bfeaff2d8465041867fea730`

rc9 变更：播放器下载层（清晰度、当前/全部分P）、下载页状态筛选与全部暂停/继续、
下载卡片速度/剩余时间/进度、分P 整组展开；设置新增下载目录/占用空间、清除全部
下载、清除搜索历史、清除图片缓存。修复 R8 下 `TypeToken<List<DownloadInfo>>`
泛型被剥离导致下载列表解析失败（`Missing type parameter`）。单测 212，已在 Duo
验证单条/批量下载、暂停/继续、筛选与存储清理。

## 0.2.0-rc8（2026-11-05）

- `bili-duo-0.2.0-rc8-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`e4279cd28312ff5d5516662709f3418090ad82b9ea061530df6baa919c82dab9`
- `bili-duo-0.2.0-rc8-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`dc02852a919522709e5b83fc670628bfdcc06f5d8a29680644d6354fdffbc10c`

rc8 变更：聚焦关注/搜索用户列表的 UP 卡时，上屏显示资料、粉丝/视频数、
最近动态与最近视频（220ms 防抖、按 mid 缓存）；UP 主空间改用深色资料卡、
胶囊式视频/合集切换和计数；`TopPersonUi` 增加 mid/stats/近期内容，修正转发动态
只显示“转发动态”占位。单测 210，已在 Duo 验证关注列表与搜索用户列表的上屏预览。

## 0.2.0-rc7（2026-11-05）

- `bili-duo-0.2.0-rc7-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - APK SHA-256：`3d47ec437a7372a6a1c9ecb3bd4cab179a563f6d47c1f8df35833c1ebd879eab`
- `bili-duo-0.2.0-rc7-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`b06ce424aec39b2de1a9ebaa1e42cd000b7de0822cd0d066f52f826612dd8566`

rc7 变更：搜索页新增「视频 / 用户」类型切换（chip / L1R1），用户结果支持进入
UP 主空间、上屏人物卡；修复 `is_upuser` 返回数字导致的解析失败。单测 208。
已在 Duo 验证用户搜索、类型切换、上屏人物卡和进入 UP 主空间。

## 0.2.0-rc6（2026-11-05）

- `bili-duo-0.2.0-rc6-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - 签名证书 SHA-256：
    `ad0cf2f6e3d39ea5e2351f7275ad57fe2f99cd782e75931925687c2034c54adf`
  - APK SHA-256：`a6ee2f1c06f2931a3cb4c18d37c6ea791209acc308eb18e7f0ca3f844467420a`
- `bili-duo-0.2.0-rc6-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`b0bfc21443553a62be64c6aabd8de8099a49f50845d7d2521050415be50e903c`

rc6 变更：评论 tab 新增「写评论」入口、评论/回复发布、删除自己的评论和删除确认；
`x/v2/reply/add` / `reply/del`，需要登录。Duo 真机已验证根评论发布、楼中楼回复与
删除；测试评论已全部撤回，账号状态还原。

## 签名配置

release keystore 和 `keystore.properties` 均不进入仓库（见根目录
[`.gitignore`](../.gitignore) 和 [`keystore.properties.example`](../keystore.properties.example)）。
丢失 keystore 后无法覆盖升级已安装的 release 包，请自行备份。
