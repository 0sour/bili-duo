# dist — 构建产物

本目录只保留构建说明和校验用哈希。**APK 不提交到 Git**（`.gitignore` 已排除
`*.apk`），需要时本地 `./gradlew assembleDebug` / `assembleRelease` 生成，或从
GitHub Releases 下载。

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
