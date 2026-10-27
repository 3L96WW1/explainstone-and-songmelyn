## Explainstone and Songmelyn 1.0.0-beta+1.21.1

Stone speakers for **Minecraft 1.21.1 (Fabric)** that play **Ogg Vorbis** music —
imported by you (stored in the world, shared with other players) or broadcast from a transmitter.

**Requires:** Fabric Loader ≥ 0.20.1, Fabric API, Java 21 — drop the jar into `mods/`.

### Blocks / 方块
- **Explainstone** — 6 variants: rock, stone, cobblestone, granite, diorite, andesite (讲解石：6 种外壳)
- **Songmelyn** — wall-mounted classroom speaker (颂美铃：壁挂式教室广播音响，薄板碰撞箱)
- **Broadcast Transmitter** — channel transmitter with `>broadcasting_system` screen (广播发信器)

### Features / 功能
- Right-click GUI: playlist with **track duration + progress bar**, play/stop, play mode (**sequence / repeat one / shuffle**)
- **Local mode** vs **Receive mode** with channel `350.000`–`350.999`
- Broadcast signal range **32–512 blocks** (adjustable); speaker audible range **8–48 blocks**
- Per-block **volume** (0–100%) plus independent control via the vanilla **Records** sound slider
- **Timer** (off / 1 / 3 / 10 min) and **redstone** control (receive mode ignores redstone)
- Import **.ogg** files (stored in the world, auto-downloaded by other players; ≤6 MB, ≤10→5 min)
- Multiple speakers stay **in sync** on a shared timeline, including after looping or switching tracks
- Note particles while playing; requires no resource pack

### Notes / 注意
- Ogg Vorbis only (convert mp3: `ffmpeg -i in.mp3 -c:a libvorbis -q:a 5 -ac 1 out.ogg`)
- Mono audio recommended for 3D positioning
- This is a **beta** — please report issues with `latest.log` snippets

### License
Code: **MPL-2.0**. Assets (textures, icon): © GINKA_3L96WW1, redistribution only as part of this mod.
