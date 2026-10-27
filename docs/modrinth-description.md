# Explainstone and Songmelyn

Stone speakers for **Minecraft 1.21.1 (Fabric)** that play **Ogg Vorbis** music — either imported by you
(stored inside the world and shared with other players) or broadcast from a transmitter on a channel.

No resource pack required — the mod ships its own audio engine (OpenAL) and can play any `.ogg` you import.

## Blocks

| Block | Description |
|---|---|
| **Explainstone** | 6 variants: rock / stone / cobblestone / granite / diorite / andesite. Right-click to open the speaker UI. |
| **Songmelyn** | Wall-mounted classroom speaker with a thin, walk-through collision box. |
| **Broadcast Transmitter** | Sends music on a channel within 32–512 blocks. Dark shell with 12 black edges and a `>broadcasting_system` screen. |

## Features

- **Playlist UI** with track duration, live progress bar, play / stop
- **Play modes**: sequence, repeat one, shuffle
- **Local mode** plays its own playlist; **Receive mode** listens to a channel (`350.000`–`350.999`) and shows only the frequency window
- **Broadcast transmitter**: pick a track, set a frequency, play — receivers within signal range start in sync
- **Timer**: off / 1 / 3 / 10 minutes, plus **redstone** control (receive mode ignores redstone)
- **Range sliders**: broadcast signal 32–512 blocks, speaker audible range 8–48 blocks
- **Volume**: per-block 0–100% and an independent master via the vanilla **Records** sound slider
- **Import any `.ogg`** (≤6 MB, ≤5 min) — stored in the world, auto-downloaded by other players
- Music-note particles while playing; multiple speakers stay **in sync** on a shared timeline

## Requirements

- Minecraft **1.21.1**
- **Fabric Loader** ≥ 0.20.1
- **Fabric API** (required)
- **Java 21**

Works in single-player and on multiplayer servers (install on both sides).

## Audio format

- `.ogg` (**Ogg Vorbis**) only — mp3 is not supported, convert first:
  `ffmpeg -i in.mp3 -c:a libvorbis -q:a 5 -ac 1 out.ogg`
- **Mono** recommended (needed for 3D positioning)
- Put files into `config/explainstone/inbox/` and press "Import ogg…" in the block UI, or pick a file in the dialog

## Crafting

All Explainstone variants and Songmelyn share the same shape, only the shell material changes:

```
 . M .
 M N M
 M O M
```

`N` = Note Block · `O` = Observer · `M` = shell (iron ingots for Explainstone, white wool for Songmelyn,
or stone / cobblestone / granite / diorite / andesite for the variants)

**Broadcast Transmitter**:

```
 T A T
 I O I
 R C I
```

`T` = Redstone Torch · `A` = Amethyst Shard · `I` = Iron Ingot · `O` = Observer · `R` = Redstone Dust · `C` = Comparator

## Known limitations

- Minecraft **1.21.1** only
- `.ogg` only (no mp3 / m4a / flac)
- Cross-player sync is based on each client receiving the broadcast (network latency applies); shuffle is per-client
- Breaking a block loses its playlist (use a tool that copies block entity data if you need to move it)

## License

- **Code**: MPL-2.0
- **Assets** (textures, icon): © GINKA_3L96WW1, redistribution only as part of this mod
- Some variant grille textures are derived from Minecraft's vanilla textures (Minecraft © Mojang / Microsoft)

*Not affiliated with Mojang or Microsoft.*
