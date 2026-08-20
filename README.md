# Cinematics

Standalone Paper 1.21 plugin for named cinematic scenes and experiences: a
camera path, timed shader overlays, timed props, and ordered timeline flows.

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
/cinematic experience create <name>
/cinematic experience beat add <name> timeline <beatId> <scene>
/cinematic experience play <name> [player]
/cinematic experience list
```

Shader overlay ids: `darkness`, `nausea`, `blindness`, `night_vision`, `poison`,
`wither`. Prop ids are material names (`oak_sign`, `lantern`, …).

Scenes persist under `plugins/Cinematics/scenes/`. Experiences persist under
`plugins/Cinematics/experiences/`.

## Build

```text
./gradlew test
./gradlew shadowJar
```

The shaded plugin jar is `build/libs/cinematics-0.0.0-local.jar`.
