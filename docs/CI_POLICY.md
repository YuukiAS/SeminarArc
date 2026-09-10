# SeminarArc CI Policy

Status: active

Updated: 2026-09-08

## Principle

GitHub Actions 是版本级远端验收，不是每次 push 的持续构建器，也不是 APK 分发渠道。

普通开发提交、文档更新、task/result/review、README/TODO/设计文档同步都不应自动消耗一次 GitHub Actions run。

## Trigger

`.github/workflows/android.yml` 只在以下情况运行：

1. push 版本 tag：`v*`
2. 用户或维护者手动 `workflow_dispatch`

普通 branch push / docs-only push / PR 不自动触发该 workflow。

## Version gate

每个对用户有意义的可安装版本至少运行一次远端 CI，例如：

- `v0.3.1-internal.1`
- `v0.4.0-internal.1`
- `v0.4.0-internal.2`
- `v0.5.0-alpha.1`
- `v0.5.0-alpha.2`

远端 gate 至少执行：

- `testDebugUnitTest`
- `assembleDebug`
- `lintDebug`

Windows Emulator connected/instrumentation 仍在受控 Windows 本地环境执行，不依赖 GitHub-hosted runner。

## Release flow

Accepted version 的标准顺序：

1. Windows 本地 unit/build/lint；
2. Windows Emulator connected regression；
3. package/version/signature/secret scan；
4. 使用 repo 外稳定 internal signer 生成签名 APK；
5. commit + push `main`；
6. 创建并 push version tag；
7. tag 触发一次 GitHub Actions version gate；
8. 远端 CI PASS 后创建 GitHub prerelease/release；
9. 上传 `.apk`、`.sha256` 和 release notes。

若远端 CI 因 GitHub 基础设施瞬时失败，可人工/任务内重跑一次；不要因为普通文档提交重新触发完整 CI。

## Distribution

用户下载入口固定为 GitHub `Releases`，不是 Actions artifacts。

GitHub Actions 不构建或上传用户下载 APK；签名 key、keystore、password 和私人 API credential 永远不进入仓库或 GitHub Actions。

## Future private-repo policy

如果仓库未来改为 private，应继续保留版本 tag / manual-only 的触发策略，并重新评估 GitHub-hosted Actions 配额与是否进一步降低远端 CI 频率。
