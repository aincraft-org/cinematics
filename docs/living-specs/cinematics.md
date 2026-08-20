# Cinematics — Living Spec

> Status: active
> Last updated: 2026-08-19
> Owners: jlo

## Intent

A standalone Paper plugin that directs **out-of-body experiences**: take a player's camera, walk a named **scene graph of frames** (path, fade, later hold), then restore or hand off. Opening transitions and full cinematics are the same graph with different sizes. Vanilla clients must work — overlays are server-triggered post/core effects, not Iris/OptiFine packs.

Success looks like `first-join`: fade, fly a camera path, park in a class-select studio until the player picks a mannequin, fade, fire `ClassPicked` with a class key. Professions/kits own stats and loadouts. This plugin owns presentation and camera.

## Boundaries

### In scope
- Named scenes of camera keyframes (world identity, position, yaw, pitch)
- Timed shader overlay cues and timed prop cues
- Sampling at elapsed time `t` in `[0, duration]`
- Playback sessions (play / stop / natural completion / restore)
- Named **experiences**: a scene graph of frames (`PathFrame`, `FadeFrame`, later hold) linked by `next`
- Ordered timeline beats as sugar that compile into a PathFrame chain
- Class-select **presentation** (held camera, actors, pick event) — not class data
- Audience policy: subject / spectators / public
- Operator command using the terms **camera**, **shaders**, **props**, plus **experience**
- JSON persistence under `<data>/scenes/` and `<data>/experiences/`
- `CinematicService` and `ExperienceService` SPI for downstream consumers
- Standalone Gradle project / plugin JAR at `/home/jlo/dev/cinematics`

### Out of scope / non-goals
- Iris / OptiFine / Vibrant Visuals client shader packs
- Replay recording / exporting the live world camera to MP4 (the Paper server does
  not render a player's view; that needs a client recorder such as Flashback,
  ReplayMod, or OBS)
- Embedding in the Extras plugin (this used to live there; Extras no longer owns it)
- Class/kit/profession stats, abilities, and loadouts
- Instanced void worlds / schematic paste (Future)
- Screen shake, FOV zoom, camera roll, NPC AI, MapGUI-as-framework
- Timestamped arbitrary commands (see Future)

## Invariants

- A scene with fewer than two camera keyframes is rejected for load/play.
- Keyframe times are finite, `>= 0`, and strictly increasing.
- `sample(0)` matches the first keyframe; `sample(duration)` matches the last.
- A mid-duration sample lies on the path between the endpoint positions and is
  not a jump to the last keyframe.
- Shader overlays and props are active only while `t` is inside their window
  (inclusive of both endpoints).
- Play records the player's pre-play pose. Stop or natural completion restores
  that pose (or teleports, if the experience says so) and reports no remaining overlays/props.
- Quit and plugin disable always restore — never leave the player in a studio.
- A second play/start for the same player is rejected (`ALREADY_PLAYING`) until stop
  or completion. Scene play and experience start share that slot.
- An experience has at least one frame; frame ids are unique; `next` is empty or names a frame in the same graph; the walk from `entry` is acyclic.
- Public API types do not mention Bukkit `World` or `Player`.
- Class keys on studio actors are opaque strings; this plugin does not apply kits.

## Implementation guidance

- `dev.cinematics.api` = Bukkit-free SPI + immutable value types.
- `dev.cinematics.core` = `DefaultCinematicService` (drafts, play sessions, JSON
  repository, experience director) callable from JUnit without a server.
- `dev.cinematics.paper` = Folia player scheduler applies sampled poses,
  display-entity props, vanilla shader overlay ids, `/cinematic`, and Bukkit events.
- `dev.cinematics.CinematicsPlugin` wires the service, controller, and command.
- Files: `<data>/scenes/<name>.json`, `<data>/experiences/<name>.json`; missing
  files decode to unknown; corrupted values degrade rather than crash enable.
- Command: `/cinematic` (aliases `cinematics`, `cine`), permission
  `cinematics.use` (default op).
- Tests drive `CinematicScene.sample` / service play-stop / experience sampling —
  not a re-implementation of interpolation.
- Design: `docs/superpowers/specs/2026-08-19-experience-director-design.md`

## Current

- [x] Named scene model with keyframes, shader cues, prop cues, duration
- [x] Load rejects fewer than two keyframes
- [x] Time sampling: interpolated camera + active overlay/prop sets
- [x] Play/stop session restores pre-play pose and clears cues
- [x] Paper adapter + `/cinematic` camera / shaders / props / play / stop
- [x] Standalone plugin JAR (not part of Extras)
- [x] Per-viewer display props (`visibleByDefault=false`, shown only to the watching player)
- [x] Experience director: named flows of timeline beats, shared exclusive session
- [x] Experience JSON under `<data>/experiences/`
- [x] `/cinematic experience` create / beat add / play / list
- [x] Scene graph: frames with `next` (path + fade); beat lists compile to a chain

### Current notes
Authoring for scenes is add-only (create, append keyframe/cue, list, play/stop). There is no remove/replace, preview, skip, freeze, or text/sound track yet. Players can still walk and see their own body during play. Experiences are the active build surface.

## Next

- [ ] Named points catalog (keyframes/holds reference point ids instead of inlining poses)
- [ ] Hold frames (class-select studio) on the graph — was studio beats
- [ ] Studio beats: held camera, actor placements, `pick`, `ClassPicked` event
- [ ] Freeze the watching player during play (cancel move/look, optional hide self)
- [ ] Player skip (sneak or `/cinematic skip`) that still restores/clears or advances
- [ ] Scene authoring edits: info, remove/replace keyframe, delete scene, operator preview

## Future

- [ ] Timed titles / subtitles / action bar (dialogue without NPCs)
- [ ] Timed sound cues (vanilla sound keys)
- [ ] Play for multiple players (`@a`, nearby, or a named list)
- [ ] Spectator list + public body visibility (audience besides `SUBJECT`)
- [ ] Ease-in-out per segment; later Catmull-Rom / Bezier paths
- [ ] Hold-at-keyframe / wait
- [ ] Operator path preview (particles along the camera polyline)
- [ ] Downstream trigger: other plugins / region enter / first join call `start`
- [ ] Mannequin / player-display actors (beyond item/block displays)
- [ ] Instanced studio copies (void world / schematic)
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
| 2026-08-19 | One plugin is the experience director, not kernel-only | Class-select and cutscenes share session/restore/audience |
| 2026-08-19 | Class-select is presentation only; kits stay downstream | This plugin fires `ClassPicked` with an opaque class key |
| 2026-08-19 | Audience is a session field, not world instancing | Instancing is a different product; start with shared locations |
| 2026-08-19 | Flows (beat sequences) live in this plugin | Join = fade → flyover → studio → fade should be one named experience |
| 2026-08-19 | Quit always restores | Never trap a player in a studio on disconnect |
| 2026-08-19 | Experiences are a scene graph of frames with `next` | Openings and full cinematics share one structure; a list of beats is just a chain |

## Open questions

- [x] Inclusive cue windows? Yes: `start <= t <= end`.
- [x] Kernel-only vs full toolkit? Full toolkit, presentation-only class-select.
- [x] Studio instancing vs shared world? Audience field first; instancing is Future.
- [ ] Which Next slice after timeline experiences: transition beats or freeze?
- [ ] Want an in-game video screen (play an MP4 on maps), or only live camera keyframes?
