# dist — 构建产物

本目录只保留构建说明和校验用哈希。**APK 不提交到 Git**（`.gitignore` 已排除
`*.apk`），需要时本地 `./gradlew assembleDebug` / `assembleRelease` 生成，或从
GitHub Releases 下载。

## 0.2.0-rc6（2026-11-05）

- `bili-duo-0.2.0-rc6-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - 签名证书 SHA-256：
    `ad0cf2f6e3d39ea5e2351f7275ad57fe2f99cd782e75931925687c2034c54adf`
  - APK SHA-256：`f2db1be29d58cd457b51a6410e1a99601fab9f3311ecbf74b6061fdfcadbed2a`
- `bili-duo-0.2.0-rc6-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`057bd53af52d30339589b062bede2a6075016353e0eb6844e84a9d079e2c7685`

rc6 变更：评论 tab 新增「写评论」入口、评论/回复发布、删除自己的评论和删除确认；
`x/v2/reply/add` / `reply/del`，需要登录。评论发布尚未在真机实测（会公开发布，
测试需授权）。

## 签名配置

release keystore 和 `keystore.properties` 均不进入仓库（见根目录
[`.gitignore`](../.gitignore) 和 [`keystore.properties.example`](../keystore.properties.example)）。
丢失 keystore 后无法覆盖升级已安装的 release 包，请自行备份。
