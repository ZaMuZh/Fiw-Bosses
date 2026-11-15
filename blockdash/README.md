# Fiw BlockDash (Fabric 1.21.1)

Standalone server-side Fabric mod project. It creates synchronized moving packed-ice display walls with random gaps. Players enrolled when the round starts are pushed on contact; falling below the arena removes them from the round.

## Build

Run from this directory with Java 21 and Gradle 8.10.2:

```bash
gradle clean build
```

The output is `build/libs/fiw-blockdash-0.1.0.jar`.

GitHub Actions builds this folder on every push that changes `blockdash/**` and uploads the JAR as the artifact `fiw-blockdash-0.1.0`.

## Server setup

Install the JAR on the Fabric 1.21.1 server. It requires Fabric Loader 0.16.10+ and Fabric API. It is marked server-only; clients do not need the mod.

All commands require permission level 2 (operator).

1. Stand at one end of the platform and run `/blockdash create main`.
2. Run `/blockdash setstart main` at the near/player end.
3. Stand at the far end and run `/blockdash setend main`.
4. Configure and start:

```
/blockdash width main 9
/blockdash duration main 90
/blockdash interval main 50
/blockdash speed main 2
/blockdash maxspeed main 5
/blockdash push main 0.9
/blockdash start main
```

Other commands: `/blockdash stop <id>` and `/blockdash list`.

## Current prototype scope

- Multiple independent arena definitions (held in memory until server restart).
- Server-authoritative moving display walls, so connected players see the same obstacles.
- Random 2–3 block gaps, three-block wall height, configurable width.
- Configurable duration, spawn interval, initial/max speed and push strength.
- Players are enrolled if they are inside the lane when the round starts.
- Falling below the arena removes a player from the active round; remaining players win when the timer ends.
- Obstacle walls are display entities, not placed world blocks, so the arena floor is not overwritten.

Arena settings are currently in memory and must be recreated after a server restart. This initial build is a test version; test it on a backup/test server before using it on the live world.
