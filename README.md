# 路远 · 安卓 App（Luyuan for Android）

去中心化、离线优先的本地语音记事 App。手机端独立录音 + **本地语音识别**（Vosk，不依赖电脑），
记事按 [`SYNC_FORMAT`](https://github.com/RealScroggins/1) 存 JSON，靠系统 **Syncthing** 与电脑端双向同步。
**电脑开不开都不影响手机端使用。**

> 电脑端「路远」项目位于 `D:\Luyuan`，本仓库仅含手机端 App 源码与自动编译配置。

---

## 功能（v0.1）

- 📝 手动打字记一笔（列表页右下「+」）
- 🎙 录音 → **本地转写** → 存文本 + 音频文件（`audio/<id>.wav`）
- 📋 清单式浏览：正文 / 来源徽章（语音·手动）/ 设备（手机）/ 时间
- 🔍 列表内即时搜索（正文 + 标签）
- ✏️ 详情页行内编辑、软删（不物理删文件）
- 🔄 离线优先：无网照用，联网后 Syncthing 自动同步
- ⚙️ 设置：共享目录名、权限引导

## 架构

```
┌──────────────────── Android App（路远）────────────────────┐
│ UI(Compose) │ 领域层(Note) │ 数据层(JSON/录音/STT) │ 平台层(前台服务/权限) │
└───────────────────────────┬───────────────────────────────┘
                              │ 读写 notes/*.json + audio/*.wav
                              ▼
                 /storage/emulated/0/Luyuan/
                              │  Syncthing P2P（端到端加密，仅搬文件）
                              ▼
                 D:\Luyuan\data\notes\（电脑端，同结构）
```

两端说同一种数据契约，详见 `D:\Luyuan\SYNC_FORMAT.md`。

## 构建（无需本地装 Android SDK）

推送 `main` 分支后，GitHub Actions 自动编译并产出 `app-debug.apk`：

1. Fork / 直接用本仓库 `RealScroggins/1`
2. `git push` 到 `main`
3. 仓库 **Actions** 页 → 最新运行 → **Artifacts** → 下载 `app-debug`
4. 解压得到 `app-debug.apk`

（如需 release 签名包，后续再加 signing 步骤。）

## 安装与使用

1. 手机「设置 → 关于手机 → 连续点击版本号」开启开发者模式，允许「未知来源应用」安装
2. 装好 `app-debug.apk`
3. 首次打开授予 **麦克风** 权限
4. 设置页点「去开启所有文件访问」→ 授予（Android 11+ 必须，才能读写 Syncthing 共享目录）
5. 安装并配置 **Syncthing**（手机端 App），将手机上的 `Luyuan/` 目录与电脑端 `D:\Luyuan\data` 配对共享
6. 首次录音会联网下载中文模型（约 40MB），之后完全离线

## 注意

- **本地识别模型**：Vosk 中文小模型首次需联网下载一次，存于应用私有目录，之后离线可用。
  若下载失败，App 仍可用，只是录音只存音频、文本留空（后续可在电脑端转写）。
- **所有文件访问权限**（`MANAGE_EXTERNAL_STORAGE`）：因侧载应用需直接读写 Syncthing 共享目录，
  本 App 申请该权限；如不想授予，可在 Syncthing 中将共享目录改到应用专属目录（需改 `StorageLocator`）。
- 软删：删除不物理删文件，仅标记 `deleted:true`，彻底清除请手动删除 JSON 文件。

## 技术栈

Kotlin · Jetpack Compose (Material3) · Vosk（本地 STT）· 原生 AudioRecord/WAV · ForegroundService
