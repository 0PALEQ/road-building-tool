# Road Builder

Road Builder is a Fabric mod for designing reusable road presets and building
straight or curved roads from a city-builder-style interface. Roads remain
editable graph objects and can be removed with conflict-aware terrain
restoration.

New roads record stable terrain for demolition: non-full collision blocks without
block entities (including crops and surface decorations) restore as air, or as
their contained fluid when waterlogged. Farmland and dirt paths restore as dirt.
Full blocks and previously built road layers retain their states. Transaction
rollback still uses the original states, and blocks with block entities remain
protected because their contents are not stored by the terrain ledger. Existing
roads keep the restoration snapshots saved when they were built.

## Development setup

Road Builder uses one Gradle build with separate Fabric projects and Java
toolchains.

| Gradle project | Minecraft | Game and compile JDK | Status |
| --- | --- | --- | --- |
| `:fabric-1.20.1` | 1.20.1 | 17 | Existing target |
| `:fabric-1.21.1` | 1.21.1 | 21 | Ported |
| `:fabric-1.21.11` | 1.21.11 | 21 | Ported |

Install JDKs 17 and 21 to build all targets. Set `JAVA_HOME` to JDK 21 for the
Gradle wrapper and select JDK 21 as the Gradle JVM when importing the root
project into IntelliJ IDEA. Gradle selects the appropriate Java toolchain for
each game's compilation, tests and runs.

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric-1.21.11:build
.\gradlew.bat :fabric-1.20.1:test
.\gradlew.bat :fabric-1.20.1:runGameTest
.\gradlew.bat :fabric-1.20.1:runClient
.\gradlew.bat :fabric-1.20.1:runServer
.\gradlew.bat :fabric-1.20.1:runDatagen
```

On Linux or macOS, use `./gradlew` instead of `.\gradlew.bat`. The root
`build` and `check` tasks verify all supported versions with JUnit and headless
GameTests. Use `test` when you only need JUnit.

Additional verification tasks include `verifyPlacementProcessRestart`,
`verifyMutationProcessKills`, `measureDenseCurvedSync`,
`measureRoadBuilderServerBaseline`, and `runRoadBuilderLiveTwoClientMatrix`.
Use the project prefix to run them for one version.
`generateRoadBuilderReleaseMatrix` combines the checks and writes a release
report. The live matrix uses Windows PowerShell and a graphical session to run
two Minecraft clients with a dedicated server.

## Source layout

- `shared/src/main` contains the road model, planning, persistence and server
  logic together with common mod metadata.
- `shared/src/client` contains common client code, translations and textures.
- `shared/src/test` and `shared/src/gametest` contain the shared test suites.
- `versions/fabric-*` contains the dependencies, Java version, and API adapters
  for each target. A version's `src` directory follows the same source-set
  layout as `shared/src`. Gradle compiles both together against that version of
  Minecraft and Fabric.
- `gradle/roadbuilder.gradle` configures the shared build and verification tasks.

Each version writes jars to `versions/fabric-*/build/libs`, reports to
`versions/fabric-*/build/reports`, and develops in its own `run` directory.
The jar filename includes the Fabric target.

All supported versions export one road-pack ZIP that works on Minecraft 1.20.1,
1.21.1 and 1.21.11. Enable the ZIP in the client's Resource Packs menu for
personal use, or place it in a world's `datapacks` directory to share its
presets through the server. Each preset requires its blocks to be available
in the destination game version.

## Status

Implemented features include:

- Straight, quadratic, multi-control, and freehand road tools.
- A city-builder camera, toolbar, preset editor, snapping, and elevation controls.
- Server-owned validation, placement, topology, persistence, and synchronization.
- Graph-backed demolition with conflict-aware restoration.
- Incremental, cancellable construction jobs with crash recovery.
- Crossing and junction materialization with directed lane connectors.
- Data-pack presets and ZIP road-pack export.

The verification tasks cover unit and component behavior, headless GameTests,
fresh-process persistence, mutation-journal recovery, dense synchronization,
and a dedicated server with two real clients.

## License

Copyright (c) 2026 opaleq. All rights reserved. See [LICENSE.txt](LICENSE.txt).
