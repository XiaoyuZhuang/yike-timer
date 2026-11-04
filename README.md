# 一刻

> 只做倒计时，不打扰。

<p align="center">
  <img src="docs/images/poster.png" width="620" alt="一刻 App 宣传海报">
</p>

一刻是一款为 Android 写的极简倒计时应用。没有任务清单、社区、会员和广告，打开、选一个标签、选一段时间，就可以开始。

界面坚持纯黑：OLED 屏幕下黑色像素不发光，长时间显示更省电；数字默认使用双平行细线而不是实心填充，进一步减少亮起的像素。需要给周围的人看时，也可以在设置里一键切成实体数字。

## 功能

- **纯黑省电**：全局纯黑背景，适合 OLED 屏幕长时间显示。
- **双平行线数字**：默认空心双线，减少发光面积；可切换实体数字。
- **结束提醒**：通知、震动或两者同时提醒，支持重复提醒间隔。
- **记录与时间轴**：自动累计每个事项的时长，按天查看；长按记录可修改标签、时间或删除。
- **自由标签与时长**：默认提供「学习、娱乐、日常、休息、其他」和 5–120 分钟常用时长；长按可拖动排序，或拖到底部删除。
- **极简设置**：纯文本批量设置、一键恢复默认模板、横竖屏、屏幕常亮、防烧屏位移、点击震动、数字粗细。
- **本地运行**：没有联网权限，没有广告、登录和统计，所有数据只保存在本机。

## 界面

| 首页 | 记录 | 设置 |
| --- | --- | --- |
| <img src="docs/images/home.png" width="240" alt="首页"> | <img src="docs/images/records.png" width="240" alt="记录"> | <img src="docs/images/settings.png" width="240" alt="设置"> |

<p align="center">
  <img src="docs/images/countdown.png" width="420" alt="倒计时">
  <img src="docs/images/paused.png" width="420" alt="暂停">
</p>

设置页下方还提供了纯文本快捷设置和一键恢复默认模板：

<p align="center">
  <img src="docs/images/settings-bulk.png" width="300" alt="纯文本快捷设置">
</p>

## 使用

1. 在首页选择一个事项标签和一个时长，点击即可开始。
2. 倒计时页默认为横向显示，可在设置里切换为竖向或跟随系统；支持暂停、继续和结束。
3. 在首页长按标签可以拖动排序；拖到页面底部的「拖到这里删除」即可删除。
4. 在记录页长按一条记录，可以修改标签、修改起止时间或删除。
5. 设置页可以调整提醒方式、重复提醒间隔、屏幕常亮、防烧屏位移、数字粗细等。

## 安装

1. 打开本仓库的 **Releases**，下载最新的 `yike.apk`。
2. 在手机上允许安装未知来源应用，然后完成安装。
3. Android 13 及以上首次启动时，请允许通知权限，否则结束提醒无法显示。
4. 后续覆盖安装会保留已有记录和设置。

最低支持 Android 8.0（API 26）。

## 隐私

- 应用没有声明 `INTERNET` 权限，不会联网。
- 没有账号、广告、统计或第三方 SDK。
- 记录、设置和个性签名都保存在应用本地的 SharedPreferences 中，卸载即删除。

## 从源码构建

需要：

- JDK 21（JDK 17 也可以）
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0
- Python 3

```bash
export ANDROID_SDK=/path/to/android-sdk
bash android/build.sh
```

产物为 `android/build/yike.apk`。脚本不依赖 Gradle，直接调用 `aapt2`、`javac`、`d8`、`zipalign` 和 `apksigner`。

本地第一次构建时，如果 `android/yike.keystore` 不存在，脚本会自动生成一个测试签名。请保管好这个文件：后续升级必须使用同一个签名。也可以显式传入自己的签名信息：

```bash
KEYSTORE_PATH=/path/to/release.keystore \
KEYSTORE_PASSWORD=your-store-password \
KEY_ALIAS=your-alias \
KEY_PASSWORD=your-key-password \
bash android/build.sh
```

## 自动发布

推送到 `v*` 标签（例如 `v1.0.0`）会触发 [Release APK](.github/workflows/release.yml) 工作流：GitHub Actions 编译、签名 APK，并自动创建对应的 GitHub Release。也可以在 Actions 页面手动运行，并填写 release 标签。

需要在仓库 Secrets 中配置：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 签名文件 base64，例如 `base64 -w 0 android/yike.keystore` |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key 密码 |

签名文件不会提交到仓库。

## 技术实现

- 原生 Java + Android framework Views，不使用 WebView、Compose、AndroidX 或 Gradle。
- `minSdk 26`，`targetSdk 35`，`compileSdk 35`。
- 倒计时界面使用自定义 `DigitView` 绘制数字，支持双平行线、实体填充、字重调整和防烧屏位移。
- 结束提醒使用 `AlarmManager` + `Notification`，并在应用内提供震动提醒。
- APK 约 70 KB。

## 目录

```
android/
  src/com/yike/timer/      Java 源码
  res/                     资源与图标
  AndroidManifest.xml
  build.sh                 无 Gradle 构建脚本
  add_dex.py               将 classes.dex 写入 APK
docs/images/               README 与海报素材
marketing/                 发布文案
.github/workflows/         自动构建与 Release
```
