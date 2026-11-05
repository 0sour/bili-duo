# dist — 构建产物

本目录只保留构建说明和校验用哈希。**APK 不提交到 Git**（`.gitignore` 已排除
`*.apk`），需要时本地 `./gradlew assembleDebug` / `assembleRelease` 生成，或从
GitHub Releases 下载。

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
