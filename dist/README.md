# dist — 构建产物

本目录只保留构建说明和校验用哈希。**APK 不提交到 Git**（`.gitignore` 已排除
`*.apk`），需要时本地 `./gradlew assembleDebug` / `assembleRelease` 生成，或从
GitHub Releases 下载。

## 0.2.0-rc4（2026-11-04）

- `bili-duo-0.2.0-rc4-release.apk`
  - R8 minify + resource shrink，正式 release keystore 签名。
  - 签名证书 SHA-256：
    `ad0cf2f6e3d39ea5e2351f7275ad57fe2f99cd782e75931925687c2034c54adf`
  - APK SHA-256：`de8d60d13d0f8fdb2712b1075b1505c26de9b6db753c91acae1ca13362c9116f`
- `bili-duo-0.2.0-rc4-debug.apk`
  - 调试签名，用于抓日志/开发测试；不能覆盖安装 release 版。
  - SHA-256：`940f4c5fd978948e8a800487cabf66537a3f4dc62f2592b42ec37b374e31c22a`

rc4 变更：直播页向下滚动时自动收起「子分区」和「关注的主播」两行，回到顶部再展开。

## 签名配置

release keystore 和 `keystore.properties` 均不进入仓库（见根目录
[`.gitignore`](../.gitignore) 和 [`keystore.properties.example`](../keystore.properties.example)）。
丢失 keystore 后无法覆盖升级已安装的 release 包，请自行备份。
