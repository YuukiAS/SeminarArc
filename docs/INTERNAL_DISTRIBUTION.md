# SeminarArc Internal APK 分发

Status: active for `0.3.1-internal`

## 下载入口

SeminarArc internal / dogfood APK 从 GitHub `Releases` 页面下载，不从 GitHub Actions artifacts 下载。

推荐流程：

1. 打开 `YuukiAS/SeminarArc` 的 GitHub `Releases` 页面。
2. 选择最新的 internal/prerelease 版本，例如 `v0.5.0-alpha.1`。
3. 下载 `.apk` asset，例如 `SeminarArc-0.5.0-alpha.1.apk`。
4. 可选下载同名 `.apk.sha256`，在本地核对 APK 的 SHA-256。
5. 在 Android 设备上打开 APK，并只给当前浏览器或文件管理器“安装未知应用”权限。
6. 安装 `SeminarArc Internal`。

## 更新

后续 internal APK 只有在满足以下条件时才能原地覆盖升级并保留 app data：

- applicationId 相同：`com.yuukias.seminararc.internal`；
- signing key 相同；
- 新 APK 的 `versionCode` 更高。

如果 applicationId 或 signing key 改变，Android 会把它视为不同应用或拒绝覆盖安装。

## Internal 与 Production 的区别

`SeminarArc Internal` 仅用于 personal dogfood / internal 测试：

- applicationId 为 `com.yuukias.seminararc.internal`；
- 使用 repo 外 Windows local secret store 中的 internal signer；
- 不等同于未来 Google Play production signing；
- 不要求 GitHub Actions 保存 keystore 或 password。

未来 production / Google Play build 将单独规划 signing、AAB、Data safety 和发布流程。

## 卸载影响

卸载 `SeminarArc Internal` 会清除该 internal app 的本地 Room database 和 app-private seminar media。已经通过 Android 文件选择器或 share sheet 导出的 Markdown/ZIP 是外部副本，不会随 app 卸载自动删除。

## 安全边界

不要把以下内容上传到 GitHub Release 或提交到 Git：

- signing key / keystore；
- signing password；
- local signing properties；
- 用户 seminar 数据；
- 测试录音、照片或私有截图；
- API token 或 provider secret。
