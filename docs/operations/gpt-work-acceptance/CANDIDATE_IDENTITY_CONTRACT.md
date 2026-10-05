# Candidate Identity Contract

Campaign 只允许使用 `seminararc-64b5321-56d99be5-api36`：

```text
GIT_COMMIT = 64b5321893a8d79399c38cf85b0cf4256ffb0d63
APK_PATH = private/gpt-work-acceptance/candidates/seminararc-64b5321-56d99be5-api36/SeminarArc-0.5.0-alpha.2-internal.apk
APK_SHA256 = 56D99BE575766823E92BB84E86E2AD6A09F4F4124614423953C5BD9F2E1CE8E3
APPLICATION_ID = com.yuukias.seminararc.internal
VERSION_NAME = 0.5.0-alpha.2
VERSION_CODE = 50002
SIGNER_FINGERPRINT = 7555BF92FB796C6209F353F66C156C6F65E0312F18EE918557B572014B2DB28A
BUILD_TIME = 2026-10-05T01:55:41.1861407Z
ROOM_SCHEMA_VERSION = 7
AVD_PROFILE = SeminarArc_GPT_Work_API36
ANDROID_API = 36
```

Parent 在每个 role 前必须重新核对 candidate id、package、version 和 APK hash handoff。八个 role 必须使用同一 hash。任何 production code 修改或不同 APK 会立即使 campaign invalid；必须冻结新 candidate，并由 consolidation 决定 targeted rerun 或 full rerun。不能用“最新 APK”或 build output 目录代替上述固定路径。
