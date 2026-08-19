# Cinematics

Standalone Paper 1.21 plugin for named cinematic scenes: a camera path, timed
shader overlays, and timed props.

## Command

Permission `cinematics.use` (default op). Aliases: `/cinematics`, `/cine`.

```text
/cinematic create <name>
/cinematic camera add <name> [time]
/cinematic shaders add <name> <overlay> <start> <end>
/cinematic props add <name> <prop> <start> <end>
/cinematic play <name> [player]
/cinematic stop [player]
/cinematic list
```

Shader overlay ids: `darkness`, `nausea`, `blindness`, `night_vision`, `poison`,
`wither`. Prop ids are material names (`oak_sign`, `lantern`, …).

Scenes persist under `plugins/Cinematics/scenes/`.

## Build

```text
./gradlew test
./gradlew shadowJar
```

The shaded plugin jar is `build/libs/cinematics-0.0.0-local.jar`.
