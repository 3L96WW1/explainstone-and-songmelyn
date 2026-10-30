# Changelog

## 1.1.0-beta+1.20.1 — Minecraft 1.20.1 / Fabric + Forge

### 重构
- 项目拆分为 `common` / `fabric` / `forge` 三个模块，共享逻辑不再依赖单一加载器
- 新增 **Forge 47.4.28+** 支持；Fabric 支持 Loader ≥ 0.16.9
- 音频改为 Minecraft SoundEngine 流式 `SoundInstance` 播放：
  - 不再创建第二套 OpenAL device/context
  - Ogg 在后台流式读取，不再整首解码成 PCM
  - 1.20.1 使用 `OggAudioStream` / `FabricSoundInstance`
- 网络层按 1.20.1 API 重写：
  - Fabric 使用 `PacketByteBuf` + `ServerPlayNetworking`
  - Forge 使用 `SimpleChannel` + `FriendlyByteBuf`
  - 使用 1.20.1 的旧网络 API，不使用 `StreamCodec` / `CustomPacketPayload`

### 同步与播放
- 本地多台讲解石播放同一首曲目时，保持公共时间轴
- 广播包包含 `session` 与 `elapsedSeconds`，新加入的收信讲解石从当前广播进度开始
- 收信进度、本地进度与暂停/恢复后的时间轴统一

### 性能
- 渲染线程不再执行 OpenAL 调用和整首音频解码
- 降低音频线程阻塞和 OpenAL 设备竞争导致的卡顿
- 播放更流畅，CPU/内存占用明显下降

### 兼容性
- 方块 id、存档中的 block entity 数据保持兼容
- 支持 Minecraft **1.20.1**（Fabric + Forge）
- Fabric 与 Forge 是两个独立 jar，不能放进同一个实例的 `mods/`
