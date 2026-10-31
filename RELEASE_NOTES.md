## Explainstone and Songmelyn 1.1.0-beta

**Minecraft 26.1–26.1.2 / 26.2（+ 保留 1.21–1.21.1）· Java 25（26.x）/ Java 21（1.21）**

- **Fabric:** Fabric Loader ≥ 0.20.1 + 对应 Minecraft 版本的 Fabric API（26.1 用 0.155.3+26.1.2，26.2 用 0.161.0+26.2）
- **NeoForge:** 26.1.2.125+（Minecraft 26.1.x）/ 26.2.0.93+（Minecraft 26.2）；1.21 线仍用 21.0.167+ / 21.1.265+

> Fabric 与 NeoForge 使用两个独立 jar，请不要同时安装到同一个实例。

### New

- **Minecraft 26.1.x 与 26.2 支持** — 同一份源码编译出四条 jar（Fabric/NeoForge × 26.1/26.2），功能与 1.21 版一致。
- **NeoForge 版本** — 新增 NeoForge 支持，Fabric 与 NeoForge 功能一致。

### Improved

- **播放更流畅** — 重写音频播放管线，Ogg 改为流式读取，降低播放卡顿和内存占用。
- 音频设备交由 Minecraft 原版声音系统统一管理，不再额外占用独立的 OpenAL 设备。

### Compatibility

- 支持 Minecraft **26.1–26.1.2**、**26.2**，以及 **1.21–1.21.1**（1.21 线保留在 `legacy/` 独立构建）。
- 原有存档、方块 id、播放列表和广播配置保持兼容。
- Fabric 与 NeoForge 版本不能安装在同一个 `mods/` 目录中。

### Known limitations

- 仅支持 Ogg Vorbis，不支持 mp3 / m4a / flac
- 随机模式下各客户端独立随机
- 方块被打掉后播放列表配置会丢失（带 NBT 的搬运工具可保留）
- 文件选择框依赖 AWT，无图形环境时请使用 `config/explainstone/inbox/`

### License

Code: **MPL-2.0**. Assets © GINKA_3L96WW1; redistribution only as part of this mod.
