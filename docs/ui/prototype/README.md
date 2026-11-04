# bili-duo 可点击交互原型

> 目的：在写 Compose 之前，验证四条关键流程的操作逻辑是否直觉。
> 这**不是**产品实现，不包含真实视频、B 站 API、登录或网络请求。
> 视觉尺寸按 1dp = 2px：下屏 1280×960（= 640×480dp），上屏 1280×720（16:9 视频占位）。

---

## 打开方式

在本目录运行：

```bash
python -m http.server 8123 --bind 127.0.0.1
```

然后打开：<http://127.0.0.1:8123/index.html>

也可以直接双击 `index.html`（`file://`），但部分浏览器会限制查询参数，推荐用本地服务。

---

## 键盘 = 手柄

| 键盘 | 手柄 | 行为 |
|---|---|---|
| 方向键 | D-pad | 焦点移动；TOP_PLAYER 下左右 seek、上下音量 |
| Enter / Space | A | 确认 / 播放暂停 |
| Esc / Backspace | B | 关闭 overlay → 返回上一页 → 回 TOP_PLAYER → 退出确认 |
| X | X | TOP_PLAYER 弹幕开关；下屏上下文操作 |
| Y | Y | 在 `TOP_PLAYER` / `BOTTOM_UI` 之间切换 |
| Q / E | LB / RB | 上一 / 下一分P |
| 1 / 2 | LT / RT | 切 tab；TOP_PLAYER 下减/加倍速 |
| Tab | START | TOP_PLAYER：清晰度；BOTTOM_UI：更多菜单 |
| Shift+Tab | BACK | TOP_PLAYER：更多；BOTTOM_UI：跳到简介 |
| 鼠标 | 触摸 | 点击下屏；TOP_PLAYER 下第一次点击只切模式 |

---

## 四条流程

1. **首页 → 直接播放**
   首页方向键选卡片 → Enter 直接在上屏播放 → 下屏进入播放控制台；**没有独立详情页**。
2. **TOP_PLAYER ↔ BOTTOM_UI**
   播放后默认手柄控播放；按 Y 或点击下屏切到手柄控下屏；按 Y 或 B 切回。
   TOP_PLAYER 下控制台压暗、无焦点环；BOTTOM_UI 下控制台恢复并显示焦点。
3. **Overlay**
   TOP_PLAYER 按 Tab 打开清晰度；选 720P 后 chip 更新。B 只关闭 overlay，不退出播放。
   退出确认打开时背景不可聚焦，B 取消后仍在下屏。
4. **简介 tab：UP 主与操作**
   播放控制台右侧选「简介」→ UP 主卡片（头像/粉丝/签名）+ 右侧「关注」；
   下方是「收藏 / 下载」，再往下是 BV 号。评论 tab 才显示排序。

---

## 截图用状态

带查询参数可以直接打开某个状态，便于对照文档或录屏：

```
?state=home            首页
?state=player-top      TOP_PLAYER（控制台压暗、无焦点环）
?state=player-bottom   BOTTOM_UI（控制台完整、焦点在播放）
?state=intro           简介 tab（UP 主卡 + 关注/收藏/下载）
?state=quality         清晰度右侧 sheet
?state=speed           倍速 bottom sheet
?state=more            更多右侧 sheet
?state=exit            退出确认 dialog
&scale=0.45|0.5|0.6|0.7  缩放
```

示例：<http://127.0.0.1:8123/index.html?state=player-bottom>

---

## 已知边界（原型不验证）

- 真实 `Presentation` 窗口、双屏 Surface、窗口生命周期；
- `decorView.dispatchKeyEvent` 是否能驱动 Compose 焦点；
- 下屏系统 IME；
- Media3 播放、HDR、120Hz、真实弹幕渲染；
- 长按重复、40ms 去重、手柄断连等真机行为。

这些必须在 MuMu/Duo 的 Compose 实现里验证，见
[`../../ARCHITECTURE.md`](../../ARCHITECTURE.md) 与
[`../INPUT_AND_FOCUS.md`](../INPUT_AND_FOCUS.md)。

---

## 文件

- `index.html` — 双屏设备框 + 三个页面 + overlay
- `styles.css` — 设计 token 与所有布局
- `app.js` — 模式状态机、空间焦点、键位、overlay 栈、播放状态
- `shot-*.png` — 验收截图（home/player/playertop/intro/quality/exit）
