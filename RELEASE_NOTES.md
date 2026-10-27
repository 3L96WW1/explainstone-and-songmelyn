## Explainstone and Songmelyn 1.0.1-beta+1.21.1

**Requires:** Minecraft 1.21.1, Fabric Loader ≥ 0.20.1, Fabric API, Java 21.

### Fixed
- **Blocks did not drop when broken in survival** — they now have proper loot tables and drop themselves.
- Hardness 1.05 — about **0.8 s with a wooden pickaxe**, ~0.4 s with stone, ~1.6 s by hand. No tool is required to get the drop.

### Blocks
- **Explainstone** — 6 variants (rock / stone / cobblestone / granite / diorite / andesite)
- **Songmelyn** — wall-mounted classroom speaker (thin collision box)
- **Broadcast Transmitter** — channel broadcast with `>broadcasting_system` screen

### Features
- Right-click GUI: playlist with track duration + progress bar, play/stop, play mode (sequence / repeat one / shuffle)
- Local mode vs Receive mode, channels `350.000`–`350.999`
- Broadcast range 32–512 blocks; speaker audible range 8–48 blocks
- Per-block volume + independent control via the vanilla **Records** slider
- Timer (off / 1 / 3 / 10 min) and redstone control (receive mode ignores redstone)
- Import `.ogg` files (stored in the world, auto-shared; ≤6 MB, ≤5 min), synced playback across speakers

### License
Code: **MPL-2.0**. Assets © GINKA_3L96WW1, redistribution only as part of this mod.
