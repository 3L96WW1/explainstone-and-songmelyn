## Explainstone and Songmelyn 1.1.0-beta+1.21.1

**Minecraft 1.21.1 · Java 21**

- **Fabric:** Fabric Loader ≥ 0.20.1 + Fabric API
- **NeoForge:** NeoForge 21.1.265+

> Fabric 与 NeoForge 使用两个独立 jar，请不要同时安装到同一个实例。

### New

- **NeoForge 版本** — 新增 NeoForge 支持，Fabric 与 NeoForge 功能一致。

### Improved

- **播放更流畅** — 重写音频播放管线，Ogg 改为流式读取，降低播放卡顿和内存占用。
- 音频设备交由 Minecraft 原版声音系统统一管理，不再额外占用独立的 OpenAL 设备。

### Compatibility

- 原有存档、方块 id、播放列表和广播配置保持兼容。
- Fabric 与 NeoForge 版本不能安装在同一个 `mods/` 目录中。

### Known limitations

- 仅支持 Minecraft **1.21.1**
- 仅支持 Ogg Vorbis，不支持 mp3 / m4a / flac
- 随机模式下各客户端独立随机
- 方块被打掉后播放列表配置会丢失（带 NBT 的搬运工具可保留）
- 文件选择框依赖 AWT，无图形环境时请使用 `config/explainstone/inbox/`

### License

Code: **MPL-2.0**. Assets © GINKA_3L96WW1; redistribution only as part of this mod.
