# SpawnerFinder

A client-side Minecraft mod that displays nearby mob spawners in your HUD with color-coded information showing spawner type, coordinates, and distance to help you locate dungeons efficiently.

## 📸 Screenshots

![SpawnerFinder Sample](sampleimage.png)
*SpawnerFinder HUD displaying nearby mob spawners with coordinates and distances*

## 🎯 Features

### Core Functionality
- **Real-time Spawner Detection** - Automatically scans for mob spawners within 128 blocks of your position
- **Color-Coded Display** - Each mob type has a unique color for instant recognition
- **Distance Tracking** - Shows exact distance to each spawner in meters
- **Coordinate Display** - Provides precise X, Y, Z coordinates for easy navigation
- **HUD Integration** - Clean, non-intrusive overlay that shows the 5 closest spawners

### Supported Mob Types
| Mob Type | Color | Hex Code |
|----------|-------|----------|
| Skeleton | White | `#FFFFFF` |
| Zombie | Green | `#00FF00` |
| Spider | Red | `#FF0000` |
| Cave Spider | Blue | `#0000FF` |
| Magma Cube | Orange | `#FF8000` |
| Blaze | Yellow | `#FFFF00` |
| Silverfish | Gray | `#808080` |
| Unknown Types | Magenta | `#FF00FF` |

### Client-Side Only & Anti-Cheat Safe
- ✅ **100% Client-Side** - No server modifications required
- ✅ **Server Safe** - Cannot be detected by anti-cheat systems
- ✅ **No Network Traffic** - Uses only locally available data
- ✅ **Works on Any Server** - Compatible with vanilla and modded servers
- ✅ **HUD Only Display** - No visual overlays or ESP to avoid behavior detection

## 📋 Requirements

- **Minecraft**: 1.21.8
- **Fabric Loader**: 0.17.3 or higher
- **Fabric API**: 0.136.1+1.21.8 or higher
- **Java**: 21 or higher

## 🚀 Installation

1. **Install Fabric Loader** - Download from [FabricMC](https://fabricmc.net/use/)
2. **Download Fabric API** - Get it from [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
3. **Download SpawnerFinder** - Get the latest release from the releases page
4. **Install the Mod** - Place `spawnerfinder-1.0.0.jar` in your `.minecraft/mods/` folder
5. **Launch Minecraft** - Start the game with the Fabric profile

## 🎮 Usage

### In-Game Display
Once installed, the mod automatically starts working:

1. **HUD Location** - Spawner information appears in the top-left corner of your screen
2. **Automatic Updates** - The list refreshes every 2 seconds as you move around
3. **Distance Sorting** - Spawners are automatically sorted by distance (closest first)
4. **Format** - Each entry shows: `Mob Type: X, Y, Z (Distance)`

### Example Display
```
Closest Spawners:
Skeleton: 245, 23, -156 (12.3m)
Zombie: 267, 18, -145 (28.7m)
Spider: 223, 31, -178 (35.1m)
Cave Spider: 289, 15, -134 (45.6m)
Blaze: 201, 22, -189 (52.4m)
```

## ⚠️ Important Behavioral Warnings

### Why HUD-Only Design?
This mod **intentionally uses HUD display only** instead of visual overlays (ESP/wallhacks) for important safety reasons:

- **Behavior Tracking Protection** - Many servers use behavior analysis that can detect players looking directly at blocks through walls
- **Anti-Cheat Evasion** - Visual ESP mods can trigger anti-cheat systems when players exhibit "impossible" knowledge
- **Natural Gameplay** - HUD display encourages normal exploration rather than direct navigation to hidden spawners

### Recommended Usage
- ✅ **Use coordinates for general direction** - Navigate naturally toward the area
- ✅ **Explore normally** - Don't beeline directly to spawners through walls  
- ✅ **Mine/dig naturally** - Follow normal cave systems and mining patterns
- ❌ **Don't stare at walls** - Avoid looking directly at spawner locations through blocks
- ❌ **Don't tunnel straight** - Avoid digging direct paths to spawners

### Server Safety Notes
While this mod is technically undetectable:
- **Behavior matters more than the mod itself** - How you act is what gets you caught
- **Play naturally** - Use the information as a guide, not a GPS
- **Respect server rules** - Some servers prohibit any form of spawner detection
- **When in doubt, don't use** - Better safe than banned

## 🔧 Technical Details

### Performance
- **Efficient Scanning** - Only scans every 2 seconds to minimize performance impact
- **Optimized Range** - 128-block radius provides good coverage without excessive computation
- **Memory Friendly** - Lightweight implementation with minimal memory usage

### Compatibility
- **Fabric Ecosystem** - Built using standard Fabric APIs
- **Mod Compatibility** - Should work alongside most other client-side mods
- **Resource Pack Safe** - Doesn't interfere with texture packs or shaders

### Security & Design Philosophy
- **No Server Detection** - Uses only client-side world data
- **Anti-Cheat Safe** - Behaves identically to vanilla client from server perspective
- **No Visual ESP** - Deliberately avoids rendering overlays to prevent behavior tracking
- **HUD-Only Information** - Provides data without encouraging suspicious movement patterns
- **No Unfair Advantage** - Only shows information about loaded chunks

## 🛠️ Building from Source

### Prerequisites
- Java 21 or higher
- Git

### Build Steps
```bash
# Clone the repository
git clone <repository-url>
cd spawnerfinder

# Build the mod
./gradlew build

# Find the built JAR
ls build/libs/spawnerfinder-1.0.0.jar
```

## 📝 License

This project is licensed under the CC0-1.0 License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### 📋 TODO List for Contributors

#### High Priority Features
- [ ] **Toggle Button with Keybind** - Add configurable hotkey to enable/disable the HUD display
- [ ] **Settings GUI** - Create in-game configuration menu accessible via keybind or mod menu
- [ ] **Scrollable Spawner List** - Expand beyond 5 spawners with scrollable interface

#### Configuration Options
- [ ] **HUD Customization**
  - [ ] Position adjustment (drag & drop or coordinate input)
  - [ ] Size scaling (small, medium, large)
  - [ ] Color customization for each mob type
  - [ ] Opacity/transparency settings
- [ ] **Display Options**
  - [ ] Show/hide coordinates
  - [ ] Show/hide distance
  - [ ] Show/hide mob type names
  - [ ] Custom distance units (blocks/meters)

#### Advanced Features
- [ ] **Multi-Mod Spawner Detection** - Support for modded spawners and custom mob types
- [ ] **Spawner Groups** - Categorize spawners by biome, dimension, or custom groups
- [ ] **Enhanced Filtering**
  - [ ] Filter by mob type
  - [ ] Filter by distance range
  - [ ] Blacklist specific spawner types
- [ ] **Export/Import** - Save and share spawner locations

#### Technical Improvements
- [ ] **Performance Optimization** - Reduce scanning overhead for large areas
- [ ] **Memory Management** - Optimize spawner data storage and cleanup
- [ ] **Compatibility Layer** - Better integration with other client-side mods

#### UI/UX Enhancements
- [ ] **Modern GUI Design** - Clean, intuitive configuration interface
- [ ] **Tooltips and Help** - In-game help system for new users
- [ ] **Preset Configurations** - Quick setup options for different playstyles

### Development Setup
1. Clone the repository
2. Import into your IDE (IntelliJ IDEA recommended)
3. Run `./gradlew genEclipseRuns` or `./gradlew genIntellijRuns`
4. Use the generated run configurations for testing

### Contributing Guidelines
- Follow existing code style and patterns
- Test thoroughly before submitting PRs
- Update documentation for new features
- Consider backward compatibility for configuration changes

## 📞 Support

- **Issues** - Report bugs or request features via GitHub Issues
- **Compatibility** - Check the requirements section for supported versions
- **Performance** - If you experience lag, try reducing render distance

## 🔄 Version History

### v1.0.0
- Initial release
- Basic spawner detection and HUD display
- Support for common mob types
- Client-side only implementation

---

## 🚨 Disclaimer

**This mod is designed for exploration and convenience, not cheating.** 

### Key Points:
- **Information Only** - Shows data about spawners in loaded chunks, doesn't provide ESP/wallhacks
- **Behavior Responsibility** - How you use this information determines if you get caught, not the mod itself
- **Server Rules** - Always check and respect individual server policies regarding client modifications
- **Natural Play Encouraged** - Use coordinates as guidance for exploration, not direct navigation

### Legal Notice:
- This mod only accesses data already available to your Minecraft client
- No server-side detection is possible through technical means
- Player behavior and movement patterns are your responsibility
- The developers are not responsible for any bans resulting from suspicious gameplay behavior

**Remember: The goal is enhanced exploration, not circumventing game mechanics. Play responsibly!**