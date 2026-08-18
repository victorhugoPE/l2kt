# L2kt - Lineage 2 Interlude Server (Kotlin Edition)

A modern, high-performance Lineage 2 Interlude private server emulator written entirely in **Kotlin** and built with **Gradle** and **Kotlin Coroutines**. This project represents a complete port of the original L2J engine to Kotlin, leveraging modern JVM tooling and concurrency patterns.

> **Status**: Work in progress (WIP)  
> **Version**: 1.0.0  
> **License**: GPL v3

## 🎯 Project Overview

**L2kt** is a learning and development project aimed at modernizing Lineage 2 server emulation. Rather than continuing with legacy Java patterns, this codebase demonstrates:

- **Kotlin idioms**: Null safety, extension functions, sealed classes, data classes
- **Modern concurrency**: Coroutines instead of raw thread pools
- **Contemporary tooling**: Gradle Kotlin DSL, HikariCP connection pooling, structured logging
- **Clean architecture**: Separated concerns, handler patterns, manager systems
- **Comprehensive coverage**: 46 commits spanning all core systems (auth, networking, NPCs, skills, spawns, items, quests)

## 📊 Project Scope

| Metric | Value |
|--------|-------|
| **Kotlin Files** | 1,660 |
| **Java (legacy stubs)** | 22 |
| **Client Packets** | 215 |
| **Server Packets** | 280 |
| **Quest Scripts** | 329+ |
| **AI/Custom Scripts** | 52 |
| **SQL Schema Files** | 63 |
| **Commits** | 46 |

## ✅ Core Systems Implemented

| System | Status | Description |
|--------|--------|-------------|
| **LoginServer** | ✅ | Authentication, game server registration, account management |
| **GameServer** | ✅ | World state, client connections, packet routing |
| **Database** | ✅ | HikariCP pooling, MySQL 8.0+ connector |
| **Networking** | ✅ | Full L2 Interlude protocol (packets, encryption) |
| **NPCs & AI** | ✅ | Spawning, AI decision trees, aggression, drops |
| **Players** | ✅ | Character creation, inventory, skills, stats, buffs |
| **Items** | ✅ | Equipment, consumables, enchantment, augmentation |
| **Skills** | ✅ | Casting, effect application, hit calculations |
| **Combat** | ✅ | PvE/PvP, damage formulas, resistances, aggro |
| **Quests** | ✅ | 329+ quest scripts, saga system |
| **Crafting** | ✅ | Recipe system, buy/sell lists, manor |
| **Siege** | ✅ | Clan halls, castle operations, war mechanics |
| **Olympiad** | ✅ | Tournament system, rewards, rankings |
| **GeoEngine** | ✅ | Pathfinding, collision detection, movement validation |
| **Boats** | ✅ | Route system, schedules, boarding |
| **Community BBS** | ✅ | Forums, announcements, boards |

## 🏗️ Architecture

```
src/com/l2kt/
├── loginserver/              # Authentication server
│   ├── LoginServer.kt        # Entry point
│   ├── LoginController.kt    # Account/session management
│   ├── GameServerManager.kt  # Connected game servers
│   ├── crypt/                # Blowfish + RSA encryption
│   └── network/              # Login protocol packets
│
├── gameserver/               # World simulation
│   ├── GameServer.kt         # Entry point
│   ├── network/              # Client/Server packets (215+280)
│   │   ├── clientpackets/    # Incoming from player client
│   │   └── serverpackets/    # Outgoing to player client
│   ├── model/                # Game entities
│   │   ├── actor/            # Creatures (Player, NPC, Monster)
│   │   │   ├── ai/           # AI decision engine
│   │   │   ├── instance/     # NPC types (merchant, guard, etc)
│   │   │   ├── stat/         # Character statistics
│   │   │   ├── status/       # HP/MP/CP management
│   │   │   └── template/     # Base data templates
│   │   ├── item/             # Item system
│   │   ├── zone/             # PvP/safe/siege zones
│   │   ├── entity/           # World objects (doors, boats)
│   │   ├── olympiad/         # Tournament logic
│   │   ├── pledge/           # Clan system
│   │   └── boat/             # Boat routes
│   ├── handler/              # Command routing
│   │   ├── admincommandhandlers/  # //admin commands
│   │   ├── skillhandlers/         # Skill execution
│   │   ├── itemhandlers/          # Item use
│   │   ├── chathandlers/          # Chat commands
│   │   └── usercommandhandlers/   # .commands
│   ├── data/                 # Data loading & caching
│   │   ├── xml/              # XML parsers (items, NPCs, skills)
│   │   ├── sql/              # SQL queries (characters, clans)
│   │   ├── manager/          # In-memory data managers
│   │   └── cache/            # HTM cache, crest cache
│   ├── geoengine/            # Pathfinding & collision
│   │   ├── geodata/          # Geodata format readers
│   │   └── pathfinding/      # A* pathfinding
│   ├── scripting/            # Quest & AI scripts
│   │   ├── quests/           # 329 quest implementations
│   │   ├── scripts/          # AI, teleports, customs
│   │   └── tasks/            # Scheduled game events
│   ├── skills/               # Skill engine
│   │   ├── effects/          # Buff/debuff/damage effects
│   │   ├── conditions/       # Cast conditions
│   │   ├── funcs/            # Stat modifiers
│   │   └── l2skills/         # Specialized skill types
│   ├── instancemanager/      # Raids, sieges, events
│   ├── taskmanager/          # Background schedulers
│   └── extensions/           # Kotlin extension functions
│
├── commons/                  # Shared utilities
│   ├── concurrent/           # ThreadPool + coroutines
│   ├── mmocore/              # NIO-based network I/O
│   ├── logging/              # Custom logging
│   ├── geometry/             # 2D/3D math
│   ├── math/                 # Game calculations
│   └── random/               # RNG utilities
│
├── L2DatabaseFactory.kt      # HikariCP connection pooling
├── Config.kt                 # Global configuration loader
└── util/                     # DeadLockDetector, IPv4Filter
```

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (required for Kotlin 2.1)
- **Gradle 8.7+** (wrapper included)
- **MySQL 8.0+** for production (or SQLite for dev)

### Build

```bash
git clone https://github.com/<user>/l2kt.git
cd l2kt

# Build
./gradlew clean build

# Create distribution (LoginServer + GameServer)
./gradlew dist
```

### Database Setup

```bash
# MySQL
mysql -u root -p < sql/full_install.sql

# Or use the installer scripts
cd tools && ./database_installer.sh
```

### Run

```bash
# Terminal 1: Start LoginServer (port 2106)
./start_loginserver.command

# Terminal 2: Start GameServer (port 7777)
./start_gameserver.command
```

The GameServer allocates **2 GB** of heap memory by default (G1GC with 100ms pause target).  
The LoginServer uses **512 MB** (lightweight auth only).

### Configuration

All config files are in `config/`:

| File | Purpose |
|------|---------|
| `server.properties` | Ports, max players, XP/drop rates |
| `loginserver.properties` | Auth server port, database credentials |
| `geoengine.properties` | Pathfinding & collision settings |
| `players.properties` | Player limits, class restrictions |
| `npcs.properties` | NPC behavior, spawn settings |
| `siege.properties` | Castle siege schedules |
| `events.properties` | Event timers |

## 🔧 Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 2.1.21 |
| **Build** | Gradle 8.7 (Kotlin DSL) |
| **JVM** | Java 17+ |
| **Database** | MySQL 8.3 / HikariCP 5.1 |
| **Concurrency** | Kotlin Coroutines 1.8.1 |
| **Logging** | SLF4J 2.0 + JDK14 |
| **Network** | Custom NIO (MMOCore) |
| **Geodata** | L2J binary format |

## 🎮 Features

### Gameplay
- Full L2 Interlude protocol compatibility
- Character creation with all 31 base classes
- Complete skill system with 2000+ skills
- PvE & PvP combat with accurate damage formulas
- 329+ quest implementations
- Olympiad tournament system
- Clan system with wars, halls, and sieges
- Boat travel routes
- Enchanting, augmentation, soulshots

### Technical
- Kotlin coroutines for async operations
- HikariCP connection pooling (replacing legacy C3P0)
- GeoEngine pathfinding with A*
- NIO-based networking (non-blocking I/O)
- Extensible handler/scripting system
- Auto-restart on scheduled reboots (exit code 2)
- DeadLock detector thread
- IPv4 flood protection

## 📁 Data Files (Not Included)

The following directories are excluded from this repository due to size:

- **`data/html/`** (~54 MB) — NPC dialog HTML files (15,754 files)
- **`data/geodata/`** (~1.1 GB) — L2 geodata binary files

These must be obtained separately and placed in the `data/` directory.

The following IS included:
- **`data/xml/`** (~22 MB) — Game data (items, NPCs, skills, spawns, drops, etc.)

## 📝 Commit History

The migration from Java to Kotlin was done in a concentrated effort (Feb 9–22, 2019), covering:

1. **LoginServer** — Full migration (auth, packets, encryption)
2. **GameServer Packets** — 215 client + 280 server packet handlers
3. **Data Loaders** — XML factories, SQL managers, caches
4. **Handlers** — Admin, skill, item, chat, user commands
5. **Scripting** — Quests, AI scripts, teleports, customs
6. **Models** — Actors, items, zones, entities
7. **AI System** — Decision trees, aggro, targeting
8. **Siege & Olympiad** — Full tournament and war systems
9. **GeoEngine** — Pathfinding and collision
10. **Coroutines** — Thread pool operations migrated to structured concurrency

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Write tests before implementation (TDD)
4. Commit with conventional messages (`feat:`, `fix:`, `refactor:`)
5. Push and create a Pull Request

## 📄 License

GPL v3 — See [LICENSE](LICENSE) for details.

> **Disclaimer**: This project is for educational and private server purposes only. Lineage 2 is a trademark of NCSOFT Corporation.

---

**Built with ❤️ in Kotlin** | Last updated: August 2026
