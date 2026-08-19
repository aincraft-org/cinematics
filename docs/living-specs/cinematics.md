# Cinematics — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

## Intent

A standalone Paper plugin for named cinematic scenes: a camera path through
world space plus timed **shader** overlays and timed **props**, played for a
target player. Vanilla clients must work — overlays are server-triggered
post/core effects (status-effect shaders or pack-backed overlay ids), not
Iris/OptiFine packs.

Success looks like a cutscene: the player's camera interpolates along keyframes
for the scene duration, overlays and props appear only inside their time
windows, and stop/completion restores the player's pre-play pose and clears
those overlays and props.

## Boundaries

### In scope
- Named scenes of camera keyframes (world identity, position, yaw, pitch)
- Timed shader overlay cues and timed prop cues
- Sampling at elapsed time `t` in `[0, duration]`
- Playback sessions (play / stop / natural completion / restore)
- Operator command using the terms **camera**, **shaders**, **props**
- JSON persistence under `<data>/scenes/`
- `CinematicService` SPI registration for downstream consumers
- Standalone Gradle project / plugin JAR at `/home/jlo/dev/cinematics`

### Out of scope / non-goals
- Iris / OptiFine / Vibrant Visuals client shader packs
- Replay recording / exporting the live world camera to MP4 (the Paper server does
  not render a player's view; that needs a client recorder such as Flashback,
  ReplayMod, or OBS)
- Embedding in the Extras plugin (this used to live there; Extras no longer owns it)
- Screen shake, FOV zoom, camera roll, NPC actors, dialogue, timestamped
  arbitrary commands (see Future)

## Invariants

- A scene with fewer than two camera keyframes is rejected for load/play.
- Keyframe times are finite, `>= 0`, and strictly increasing.
- `sample(0)` matches the first keyframe; `sample(duration)` matches the last.
- A mid-duration sample lies on the path between the endpoint positions and is
  not a jump to the last keyframe.
- Shader overlays and props are active only while `t` is inside their window
  (inclusive of both endpoints).
- Play records the player's pre-play pose. Stop or natural completion restores
  that pose and reports no remaining overlays/props.
- A second play for the same player is rejected (`ALREADY_PLAYING`) until stop
  or completion.
- Public API types do not mention Bukkit `World` or `Player`.

## Implementation guidance

- `dev.cinematics.api` = Bukkit-free SPI + immutable value types.
- `dev.cinematics.core` = `DefaultCinematicService` (drafts, play sessions, JSON
  repository) callable from JUnit without a server.
- `dev.cinematics.paper` = Folia player scheduler applies sampled poses,
  display-entity props, vanilla shader overlay ids, and `/cinematic`.
- `dev.cinematics.CinematicsPlugin` wires the service, controller, and command.
- Files: `<data>/scenes/<name>.json`; missing files decode to no scene;
  corrupted values degrade rather than crash enable.
- Command: `/cinematic` (aliases `cinematics`, `cine`), permission
  `cinematics.use` (default op).
- Tests drive `CinematicScene.sample` / `CinematicService` play-stop — not a
  re-implementation of interpolation.

## Current

- [x] Named scene model with keyframes, shader cues, prop cues, duration
- [x] Load rejects fewer than two keyframes
- [x] Time sampling: interpolated camera + active overlay/prop sets
- [x] Play/stop session restores pre-play pose and clears cues
- [x] Paper adapter + `/cinematic` camera / shaders / props / play / stop
- [x] Standalone plugin JAR (not part of Extras)
- [x] Per-viewer display props (`visibleByDefault=false`, shown only to the watching player)

### Current notes
Authoring is add-only (create, append keyframe/cue, list, play/stop). There is no remove/replace, preview, skip, freeze, or text/sound track yet. Players can still walk and see their own body during play.

## Next

- [ ] Freeze the watching player during play (cancel move/look, optional hide self)
- [ ] Scene authoring edits: info, remove/replace keyframe, delete scene, operator preview
- [ ] Player skip (sneak or `/cinematic skip`) that still restores pose and clears cues

## Future

- [ ] Timed titles / subtitles / action bar (dialogue without NPCs)
- [ ] Timed sound cues (vanilla sound keys)
- [ ] Fade bookends (darkness/blindness in/out at start and end)
- [ ] Play for multiple players (`@a`, nearby, or a named list)
- [ ] Ease-in-out per segment; later Catmull-Rom / Bezier paths
- [ ] Hold-at-keyframe / wait; optional land at last pose instead of restore
- [ ] Operator path preview (particles along the camera polyline)
- [ ] Downstream trigger: other plugins / region enter / first join call `play`
- [ ] Mannequin / player-display actors
- [ ] Allowlisted timestamped commands
- [ ] Screen shake, FOV zoom, camera roll (poor vanilla fit; keep deferred)
- [ ] In-game MP4/WebM playback on a map or item-frame screen (not live camera export)

## Decisions log

| Date | Decision | Why |
|------|----------|-----|
| 2026-08-19 | Bukkit-free scene sampling and playback sessions; Paper only applies | Tests must run without a connected client |
| 2026-08-19 | Shader overlays are vanilla post/core effect ids, not client packs | Vanilla clients must work |
| 2026-08-19 | Second play for a player is rejected, not stacked | Restores stay unambiguous; operator stops first |
| 2026-08-19 | Duration is the last keyframe time; cues outside that window never play | `sample(duration)` matching the last keyframe stays well-defined |
| 2026-08-19 | Extract from Extras into `/home/jlo/dev/cinematics` | Cinematics is its own plugin; Extras stays social/mail/chat |
| 2026-08-19 | Do not export live scenes to MP4 on the server | Paper never sees the pixels of the world; client recording only |
| 2026-08-19 | Park in-game MP4-on-screen as Future, not Next | Different product from camera-path cutscenes; needs FFmpeg/maps |

## Open questions

- [x] Inclusive cue windows? Yes: `start <= t <= end`.
- [ ] Which Next slice to build first: freeze, authoring edits, or skip?
- [ ] Want an in-game video screen (play an MP4 on maps), or only live camera keyframes?
