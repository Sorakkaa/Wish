# Wish Mod 🌟

[![Version](https://img.shields.io/badge/version-1.1.4-ff69b4?style=flat-square)](https://github.com/Sorakkaa/Wish/releases)
[![Minecraft](https://img.shields.io/badge/minecraft-26.2-55ff55?style=flat-square)](https://fabricmc.net/)
[![Loader](https://img.shields.io/badge/loader-Fabric%200.19.5-dbd0b4?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/java-25%2B-orange?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-CC0--1.0-blue?style=flat-square)](LICENSE)

A client-side **Fabric** mod for **Minecraft 26.2** bringing Hypixel Skyblock quality-of-life utilities, 3D weather & sky ambiance, synchronized in-world music lyrics, player cosmetics, and interactive chat triggers — all configurable in-game via `/wish`.

---

## ✨ Features

### 🌌 Visual & Atmosphere (`Visual`)

- **Custom Sky** — Replace the vanilla sky with **7 procedural skyboxes** paired with matching horizon fog colors:
  - `Black Sky` • `Lofi Dusk` • `Midnight Breeze` • `Cloudy Pastel` • `Sunset Clouds` • `Ocean Mirage` • `Sakura Twilight`
  - Individual toggles for **Show Stars**, **Show Sun & Moon**, and **Slow Rotation**.
- **3D Weather Simulation** — Client-side `Rain` and `Snow` simulation using custom `64x64` textures across a **60-block** radius:
  - Adjustable **Count** and **Size** sliders.
  - Real `MOTION_BLOCKING` heightmap collisions with ground impact splash particles.
  - Toggleable **Dynamic Wind** with a **Wind Force** slider that shifts wind direction smoothly over time.
- **Time Changer** — Set client world time anywhere along the `0–24000` tick cycle with an optional **Freeze Time** lock.
- **Smol People** — Renders other players at miniature `0.5x` body scale with oversized `1.5x` heads, built with **Essential cosmetics compatibility** and an optional **Include Yourself** toggle.
- **3D In-World Floating Lyrics** — Displays live, beat-synced song lyrics floating across 16 elevated sky & flank sectors (never clipping below the horizon), with animated music notes (`♪ ♫ ✦ ★ ✧ ♩ ♬`) during breaks:
  - Supports **Spotify**, **YouTube Music** (companion API on port `9863` + MPRIS/window detection), and **Deezer** (port `9865` / desktop & browser detection).
  - Multi-source lyrics lookup: local `.lrc` files, YouTube Music timed lyrics, **LRCLIB**, and **NetEase Cloud Music**.
  - Configurable **Word by word** or **Line by line** display, custom hex color, scale, millisecond sync offset, and **Demo Mode**.

---

### 🎨 Cosmetics & Custom Models (`Pseudo`, `Skin`, `Misc Setting`, `/wish model`)

- **Name Spoofer & Formatting** — Replace your name client-side across chat, tab list, scoreboard, and nametags with `Bold`, `Italic`, `Underline`, and `Strikethrough` styles plus custom badges.
- **Color & Animation Studio** — Pick dual hex colors (`Color 1` & `Color 2`) using HSB sliders or hex input with **6 animation modes** (`None`, `Chroma`, `Gradient`, `Wave`, `Breathe`, `Blink`) and adjustable animation speed (`0.1x–3.0x`).
- **Player Transform (`Skin` & `Misc Setting`)** — Independent **Size X / Y / Z** sliders (`-1.0` to `3.0`) and **Spin Speed** (`-3.0` to `3.0`), plus **Cosmetics Visibility** (`Show All`, `Mine Only`, `Hide All`) and one-click **Copy Cosmetics Config** to clipboard in `Misc Setting`.
- **Custom Item Models (`/wish model`)** — Override the visual model and texture of any held Skyblock item (matched by Skyblock ID) with any vanilla item or loaded resource-pack model.

---

### ⚔️ Hypixel Skyblock & Dungeons (`Slayer`, `Mining`, `Dungeon`)

- **Slayer Carry Helper** — Manage carry runs cleanly with `/wish slayer carry <player> <amount>`:
  - Automatic boss spawn detection via armor-stand tags, **Boss Spawn Notification** HUD + audio alert, and party-chat progress messages.
  - **Auto Send Coords** to `/pc` when your own Slayer boss spawns.
  - Custom hex **Slayer Glow** highlighting for active bosses.
- **Automaton Farming & Live Bazaar Tracker** — Real-time Crystal Hollows Automaton overlay tracking kills, kills/hour, drops (7 Precursor parts + 17 tracked Gemstone variants), coins, and profit/hour:
  - Interactive HUD controls for **Session / Total** stats, **Reset**, and live Hypixel Bazaar **Instasell / Sell Offer** pricing.
  - **Crystal Hollows Sack** mode (prevents Bazaar sell offers from counting as drops), configurable **Pause Timeout** (`1–20s`), and customizable hex **Automaton Glow**.
- **Precursor City Locator** — Scans loaded Crystal Hollows chunks and chat callouts to locate the Precursor City and render a 3D waypoint.
- **Dungeon Lag & Time Lost Tracker** — Monitors client tick lag, server lag, and ping spikes during F7/M7 runs in a movable HUD, with optional end-of-run chat summary and **Always in M7/F7 (Simulators)** mode.
- **F7/M7 Custom Blocks** — Client-side custom block & **Glass Pad** overlays for F7/M7 phases.

---

### 💬 Chat & Music Commands (`Command`)

- **Channel Controls** — Independently enable or disable chat command triggers for **All Chat (`/ac`)**, **Guild Chat (`/gc`)**, and **Party Chat (`/pc`)**.
- **`!song`** — Posts your currently playing track (`{title} - {artist}` customizable format) in chat.
- **Fun Triggers** — `!meow`, `!wanted`, `!kiss`, `!feed`, `!poke`, `!pat`, `!hug`, `!sus`, `!rizz`, `!jerry` (temporarily renders nearby players as Villagers), `!soraka` (temporarily applies the Soraka model), `!iq`, `!sleep`, and `!yuri`.

---

## ⌨️ Commands

| Command | Description |
| :--- | :--- |
| `/wish` | Open the main configuration GUI |
| `/wish gui` | Open the interactive HUD editor (drag to reposition and scroll wheel to scale the Lag, Automaton Tracker, and Boss Spawn HUDs) |
| `/wish model` | Open the custom item model & texture picker for the held Skyblock item |
| `/wish slayer carry <player> <amount>` | Start tracking Slayer carry bosses for `<player>` over `<amount>` kills |
| `/wish slayer stopcarry [player]` | Stop Slayer carry tracking for `[player]` or clear all tracked carries |

---

## 📥 Installation

### Requirements
- **Minecraft** `26.2`
- **Fabric Loader** `0.19.5` (or compatible `>= 0.19.3`)
- **Fabric API** (`0.161.0+26.2`)
- **Java** `25` or newer

### Steps
1. Download `wish-1.1.4.jar` from the [Releases](https://github.com/Sorakkaa/Wish/releases) page.
2. Place `wish-1.1.4.jar` and **Fabric API** inside your `.minecraft/mods` folder.
3. Launch Minecraft `26.2` with Fabric and run `/wish` in-game to customize everything.

---

## 🛠️ Building from Source

```bash
git clone https://github.com/Sorakkaa/Wish.git
cd Wish
./gradlew build
```

The compiled mod JAR will be output to `build/libs/wish-1.1.4.jar`.

---

## ⚙️ Configuration

- Main configuration: `config/wish/config.json`
- Custom item model overrides: `config/wish/models.json`
- Optional update checker (`Check for Updates` in `Misc Setting`) queries GitHub Releases for `Sorakkaa/Wish` on startup and can be disabled at any time.

---

## 📄 License

This project is open source under the [CC0 1.0 Universal License](LICENSE).
