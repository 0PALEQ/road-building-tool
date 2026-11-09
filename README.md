# Road Builder

Road Builder is a Fabric mod for designing reusable road presets and building
straight or curved roads from a city-builder-style interface. Roads remain
editable graph objects and can be removed with conflict-aware terrain
restoration.

See [Planned Features](docs/PLANNED_FEATURES.md) for the product scope and
[Development Plan](docs/DEVELOPMENT_PLAN.md) for implementation decisions and
acceptance criteria.

New roads record stable terrain for demolition: non-full collision blocks without
block entities (including crops and surface decorations) restore as air, or as
their contained fluid when waterlogged. Farmland and dirt paths restore as dirt.
Full blocks and previously built road layers retain their states. Transaction
rollback still uses the original states, and blocks with block entities remain
protected because their contents are not stored by the terrain ledger. Existing
roads keep the restoration snapshots saved when they were built.

## Development setup

Road Builder currently targets:

- Minecraft 1.20.1
- Fabric Loader and Fabric API
- Java 17 bytecode for the mod
- Java 21 to run the current Gradle/Loom build (newer JDKs may work, but 21 is recommended for Minecraft 1.20.1 tooling)

Import the repository as a Gradle project in IntelliJ IDEA and set the project's **Gradle JVM** to Java 21. For command-line builds, make sure the shell's `JAVA_HOME` points to JDK 21 or newer; Loom is loaded before Gradle can select the Java 17 compilation toolchain.

For example, in a PowerShell session:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
& "$env:JAVA_HOME\bin\java.exe" -version
```

Then run:

```powershell
.\gradlew.bat build
```

Useful development tasks:

```powershell
.\gradlew.bat test
.\gradlew.bat runGameTest
.\gradlew.bat runRoadBuilderLiveTwoClientMatrix
.\gradlew.bat verifyMutationProcessKills
.\gradlew.bat measureDenseCurvedSync
.\gradlew.bat generateRoadBuilderReleaseMatrix
.\gradlew.bat measureRoadBuilderServerBaseline
.\gradlew.bat runClient
.\gradlew.bat runServer
.\gradlew.bat runDatagen
```

On Linux or macOS, use `./gradlew` instead of `.\gradlew.bat`. The `build`/`check`
lifecycle also runs the headless GameTest server; use `test` when you only need
the JUnit suite. Fresh-process recovery, process-kill recovery, performance,
and live-client checks remain explicit verification tasks and are combined by
`generateRoadBuilderReleaseMatrix`.

## Source layout

- `src/main/java` contains shared and server-authoritative code.
- `src/client/java` contains input, preview rendering, screens, and other client-only code.
- `src/main/resources` and `src/client/resources` contain Fabric metadata, mixin configuration, and game assets.
- Generated data belongs in `src/main/generated`; it should be produced through `RoadBuilderDataGenerator` once providers are added.

Persistent road data and server APIs do not depend on client classes. The
architecture section of the feature plan documents the package boundaries.

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
and a dedicated server with two real clients. Reports are written under
`build/reports/`.

## License

Copyright (c) 2026 opaleq. All rights reserved. See [LICENSE.txt](LICENSE.txt).
