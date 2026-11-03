# Wish Mod 🌟

A modern, high-performance Fabric client mod designed for **Hypixel Skyblock** utilities, dungeon QoL, Automaton farming tracking, 3D music lyrics, and cosmetic customizations.

---

## ✨ Features

### 🎵 3D In-World Floating Lyrics
- **Live 3D Floating Lyrics**: Displays real-time lyrics floating seamlessly in the Minecraft 3D world around the player.
- **Display Modes**:
  - **Word by Word**: Dynamic, synchronized floating words with fluid animations and automatic despawn limits.
  - **Line by Line**: Clean, centered karaoke-style floating lines.
- **Provider Support**:
  - **YouTube Music Desktop App** (via Companion API pairing).
  - **Spotify** (Live web token & local status).
  - **Deezer** (Desktop player integration).
- **Custom Font Engine**: 9 built-in typography fonts rendered directly in 3D:
  - *Lavender*, *Dreamy*, *Fight*, *Comic*, *Script*, *Fire*, *Ice*, *Royal*, and *Clean*.
- **Sync & Customization**: Customizable sync offset (ms), height offset, floating radius, and rotation.

### 👤 Name Spoofer & Player Cosmetics
- **Name Spoofer**: Custom nickname spoofer (up to 32 characters) that updates your in-game nametag, Tab list, and chat messages locally.
- **Color & Animation Styles**:
  - Animated styles: **Chroma**, **Wave**, **Gradient**, **Breathe**, and **Blink**.
  - Dual custom Hex color stops with configurable cycle speed.
- **Text Formatting**: Toggle **Bold**, **Italic**, **Underline**, and **Strikethrough** styles.
- **Player Scaling & Spin**: Adjust individual X, Y, and Z model scale and enable continuous rotation.
- **Custom Item Models (`/wish model`)**: Replace Skyblock item models, override textures, and toggle enchantment glints.

### 🤖 Automaton Farming & Live Bazaar Tracker
- **Real-Time HUD**: Live tracking of Automaton kills, rare drops (FTX 3070, Synthetic Heart, Robotron Reflector, Electron Transmitter, Control Switch, Superlite Motor, Bob-omb), and gemstones.
- **Live Bazaar Valuation**: Asynchronously fetches Hypixel Bazaar prices to compute live earnings and coins per hour.
- **Interactive On-Screen Controls**: One-click toggles between **[Session]** and **[Total]** tracking, and between **[Instasell]** and **[Sell Offer]** valuations.
- **Automaton Mob Glow**: Highlights Automatons in the Precursor Remnants with a bright, customizable glow outline.

### 🏛️ Precursor City Locator
- **Intelligent Structure Recognition**: Scans for the 6-altar Precursor City formation and recognizable structure signature blocks.
- **Chat & Scoreboard Coordinate Parsing**: Automatically extracts coordinates shared in party/all chat (e.g., `city 650 82 720`).
- **3D Waypoint & Directional Pointer**: In-world beacon beam and distance tracker pointing straight to the city altar.

### ⏱️ Dungeon Lag & Time Lost Tracker
- **Precise Tick Lag Detection**: Compares real client time elapsed against Hypixel server tick progression during Floor 7 and Master Mode Floor 7 runs.
- **Time Lost Calculation**: Tracks time lost to server lag and gate opening delays in real time.
- **Configurable HUD**: Position and style the lag indicator using `/wish gui`.

### 🏰 Dungeon Custom Blocks (F7 / M7)
- **Glass Overlays**: Local visual block highlights for dungeon terminal phases and parkour pathways (e.g. green, red, yellow, purple routes).

### 💬 Social & Music Chat Commands
- `!song`: Share your currently playing track directly to Party (`/pc`), Guild (`/gc`), or All Chat (`/ac`).
- **Interactive Social Commands**: Fun triggers including `!kiss`, `!hug`, `!pat`, `!poke`, `!feed`, `!wanted`, `!sus`, `!rizz`, `!jerry`, `!soraka`, `!meow`, and more.

---

## ⌨️ Commands

| Command | Description |
| :--- | :--- |
| `/wish` | Opens the main configuration screen |
| `/wish gui` | Opens the interactive HUD layout editor |
| `/wish model` | Opens the item model customizer for the held item |

---

## 📥 Installation

1. Install **Fabric Loader** (version `0.19.5` or newer).
2. Ensure you are using **Minecraft 26.2** and **Java 25+**.
3. Download the latest `wish-1.1.1.jar` from the [Releases](https://github.com/Sorakkaa/Wish/releases) page.
4. Place the jar into your `.minecraft/mods` folder along with **Fabric API**.
5. Launch your game and type `/wish` to configure your options!

---

## 🛠️ Building from Source

To compile Wish yourself:

```bash
# Clone the repository
git clone https://github.com/Sorakkaa/Wish.git
cd Wish

# Build with Gradle
./gradlew build
```

The compiled mod jar will be in `build/libs/wish-1.1.1.jar`.

---

## 📄 License

This project is licensed under the [CC0 1.0 Universal License](LICENSE).
