# dist — 构建产物

本目录只保留构建说明和校验用哈希。**APK 不提交到 Git**（`.gitignore` 已排除
`*.apk`），需要时本地 `./gradlew assembleDebug` / `assembleRelease` 生成，或从
GitHub Releases 下载。

## 0.2.0-rc5（2026-11-04）

- `bili-duo-0.2.0-rc5-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - 签名证书 SHA-256：
    `ad0cf2f6e3d39ea5e2351f7275ad57fe2f99cd782e75931925687c2034c54adf`
  - APK SHA-256：`30e6588c1c97dd4452f1a717e8800d8b608d31314b969a0ff46f051ada061db5`
- `bili-duo-0.2.0-rc5-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`146b23791fc96a823e09213b77266a60349144899d83a18b941922d432f0387c`

rc5 变更：设置「关于」新增版本状态、开源仓库与 GitHub Issues 入口，删除邮件
反馈入口；修复在 Presentation 中 `startActivity` 缺少
`FLAG_ACTIVITY_NEW_TASK` 导致的崩溃。

## 签名配置

release keystore 和 `keystore.properties` 均不进入仓库（见根目录
[`.gitignore`](../.gitignore) 和 [`keystore.properties.example`](../keystore.properties.example)）。
丢失 keystore 后无法覆盖升级已安装的 release 包，请自行备份。
