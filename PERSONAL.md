# 自用手机版

基于 FongMi/TV `c616c0aa3613e87529791587a9f71b78c278c991`（5.6.3）。

- 应用名称：影视·自用；包名：`com.fongmi.android.tv.personal`，与官方版并存。
- 默认配置：饭太硬，由 qist/tvbox 的 `fty.json` 提供。
- 备用配置：qist 综合，由 `jsm.json` 提供。
- 使用 qist README 公布的 Pages 域名 `https://qist.wyfc.qzz.io/`。
- 地址集中在 `app/src/mobile/res/values/builtin_configs.xml`，修改后重新构建即可。
- 首次启动写入预置配置并选中饭太硬；之后启动不覆盖选择、不重新添加已删除的记录。
- 设置 → 点播 → 内置配置，可重新选择或恢复预置配置；原有地址编辑功能保留。
- 首次启动关闭官方自动更新，避免向自用版提示不同签名的官方安装包。

内置的是配置地址。频道、站点和扩展包仍由远端配置加载，能否访问和播放取决于网络及来源服务。

## 当前构建限制

此目录的 5.6.3 需要未公开的新版播放器 AAR（含 libass/双字幕接口）。
已公开的 Media3 分支截至 2026-08-12，不能直接替代这些依赖。
可构建的 5.6.1 自用版在独立 worktree `/ssd/apps/TV-personal`，详见其 PERSONAL.md。

## 构建

需要 JDK 21、Android SDK 37、Python 3.10。`local.properties` 配置 `sdk.dir`。
本机使用 Gradle 的 JetBrains JDK 21 和 uv 安装的 Python 3.10。

播放器 AAR 源码来自 `FongMi/media` 的 `release-1.11.0-fongmi` 分支，
提交 `3c2cbe8ac742c2fe15eff52f03eeb3b1b648848d`，本机目录 `/ssd/apps/TV-media`。
使用该工程的 Gradle 9.1.0（bin 发行包），compileSdk 调整为本机已安装的 37。
在 settings 中仅包含 lib-* 与测试支持项目，避免拉取演示应用及测试媒体；
构建自动安装了 NDK 29.0.14206865。按 `move.txt` 编译所列模块的 `assembleRelease`，
将 AAR 放入本工程 `app/libs/`（这些依赖被上游 .gitignore 排除）。

```bash
bash scripts/build-personal.sh
```

输出：`app/build/outputs/apk/mobile/debug/`，荣耀 BVL-AN00 使用 arm64-v8a 包。
调试包使用本机 Android debug keystore，后续覆盖升级应保留同一密钥。
未配置正式签名时，release 构建也使用本机 debug 签名；本包用于个人安装。

安装命令（指定自己的设备序列号）：

```bash
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/mobile/debug/app-mobile-arm64-v8a-debug.apk
```
