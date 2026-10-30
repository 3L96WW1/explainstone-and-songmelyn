## Explainstone and Songmelyn (Fabric) 1.0.0-beta+1.20.1

Stone speakers for **Minecraft 1.20.1 (Fabric)** that play **Ogg Vorbis** music — imported by you
(stored in the world) or broadcast from a transmitter on a channel.

**Requires:** Minecraft 1.20.1, Fabric Loader ≥ 0.15, Fabric API, Java 17.

### Blocks
- **Explainstone** — 6 variants: rock / stone / cobblestone / granite / diorite / andesite
- **Songmelyn** — wall-mounted classroom speaker with a thin collision box
- **Broadcast Transmitter** — sends music on a channel without making sound itself

### Features
- Right-click GUI: playlist with **track duration + progress bar**, play / pause / stop
- Play modes: **sequence / repeat one / shuffle**
- **Local mode** vs **Receive mode** with channel `350.000`–`350.999`
- Broadcast signal range **32–512 blocks**; speaker audible range **8–48 blocks**
- Per-block volume plus the vanilla **Records** sound slider
- Timer (off / 1 / 3 / 10 min) and **redstone** control (receive mode ignores redstone)
- Import **.ogg** files (≤6 MB, ≤5 min), stored in `<world>/explainstone/audio`
- Note particles above each speaker while playing; synced playback between speakers

### Notes
- Ogg Vorbis only (`ffmpeg -i in.mp3 -c:a libvorbis -q:a 5 -ac 1 out.ogg`)
- This is a **beta** — please report issues with `latest.log` snippets

### License
Code: **MPL-2.0**. Assets: © GINKA_3L96WW1, redistribution only as part of this mod.
