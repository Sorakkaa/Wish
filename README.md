# Wish Mod 🌟

A modern, high-performance Fabric client mod designed for **Hypixel Skyblock** utilities, dungeon QoL, Automaton farming tracking, and cosmetic customizations.

---

## ✨ Features

### 🤖 Automaton Farming & Live Bazaar Tracker
- **Real-Time HUD**: Displays live kills, parts looted (FTX 3070, Synthetic Heart, Robotron Reflector, Electron Transmitter, Control Switch, Superlite Motor, Bob-omb), and gemstones (Sapphire, Ruby, Amber, Jade, Amethyst, Topaz, Jasper, Opal).
- **Live Bazaar Integration**: Fetches real-time Hypixel Bazaar prices asynchronously and computes total profit and coins/hour.
- **Interactive HUD**: Clickable controls on screen to toggle between **[Session]** and **[Total]** statistics, and between **[Instasell]** and **[Sell Offer]** valuations.
- **Drop Alerts**: Custom audio and chat drop notifications.
- **Automaton Mob Glow**: Highlights Automatons in the Precursor Remnants with a vibrant, customizable glow outline.

### 🏛️ Precursor City Locator
- **Intelligent Multi-Tier Detection**:
  - Exact 6-Altar configuration scanner.
  - Structure signature block recognition.
  - Scoreboard & chat coordinate detection (e.g., `city 650 82 720`).
- **3D Waypoint & Directional Pointer**: In-world beacon beam and distance tracker guiding you directly to the city altar.

### ⚔️ Dungeon Lag Tracker
- **Dungeon Lag & Time Lost Tracker**: Compares real client time elapsed against official Hypixel server run time to calculate time lost to server lag and gate opening delays.

### 🎨 Custom Player Cosmetics & Models
- **Animated Nametags**: Support for Chroma, Wave, Gradient, Breathe, and Blink animations with custom speed controls.
- **Dual Hex Colors**: Supports custom hex codes and smooth multi-stop color gradients.
- **Player Scaling & Rotation**: Adjust player model scale (X, Y, Z) and enable continuous spin animations.
- **Item Model Customizer (`/wish model`)**: Replace Skyblock item models and textures with custom pack models or toggle enchantment glints.
- **Ghost Blocks**: Tools to place and batch-fill ghost blocks locally.

### 🎵 Music Chat Integration
- Integrates with **YouTube Music Desktop App**, **Spotify**, and **Deezer**.
- Automatically queries your current playing song.
- Respond with `!song` to share your current track directly to Party (`/pc`), Guild (`/gc`), or All Chat (`/ac`).
- Interactive social commands (`!meow`, `!wanted`, `!kiss`, `!feed`, `!poke`, `!pat`, `!hug`, `!sus`, `!rizz`, `!jerry`, `!iq`, `!sleep`, etc.).

---

## ⌨️ Commands

| Command | Description |
| :--- | :--- |
| `/wish` | Opens the main configuration screen |
| `/wish gui` | Opens the interactive HUD layout editor |
| `/wish model` | Opens the item model customizer for the held item |
| `/wish carry <player> <amount>` | Starts tracking a Slayer carry quota for a player |
| `/wish stopcarry [player]` | Stops Slayer carry tracking |
| `/wish automatonresetsession` | Resets current session stats for the Automaton tracker |
| `/wish automatonresettotal` | Resets lifetime total stats for the Automaton tracker |

---

## 📥 Installation

1. Install **Fabric Loader** (version `0.19.3` or newer).
2. Ensure you are using **Minecraft 26.2** (or matching version) and **Java 25+**.
3. Download the latest `Wish-*.jar` from the [Releases](https://github.com/Sorakkaa/Wish/releases) page.
4. Place the jar into your `.minecraft/mods` folder along with **Fabric API**.
5. Launch your game and type `/wish` in-game to configure your options!

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

The compiled mod jar will be in `build/libs/`.

---

## 📄 License

This project is licensed under the [CC0 1.0 Universal License](LICENSE).
