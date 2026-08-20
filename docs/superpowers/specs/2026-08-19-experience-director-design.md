# Experience Director — Design

> Status: approved direction
> Date: 2026-08-19
> Repo: `/home/jlo/dev/cinematics` (one Paper plugin, one JAR)

## Intent

This plugin directs **out-of-body experiences** for a player: take their camera, run a sequence of beats, then restore or hand off. Cutscenes, class-select studios, and join fades are the same product with different beat types.

Success: an operator authors a named experience `first-join` that fades in, flies a camera path, parks in a class-select studio until the player clicks a mannequin, fades out, and fires `ClassPicked` with a class key. Professions/kits apply the kit. This plugin never owns stats, abilities, or loadouts.

Vanilla clients only. Overlays are server-triggered post/core effects.

## Repository boundary

One Gradle project. Packages stay `dev.cinematics.api` / `core` / `paper`. No second plugin, no Gradle modules until a package is too large to hold in one file set.

### In scope

- Session kernel: exclusive per-player OOBE, restore pose, freeze, hide-self
- Audience policy on the session (who else can see)
- Tracks: camera path, held camera, shader overlays, props, actors, titles, skip/pick input
- Director: named **experiences** (ordered beats) with completion handoff
- Timeline scenes (already shipped) as one beat type
- Interactive studio beats (class-select presentation)
- Transition beats (fade bookends)
- Bukkit-free SPI + Paper adapter + operator commands
- Events for downstream plugins (`ClassPicked`)

### Out of scope

- Class/kit/profession data and combat
- Instanced void worlds / schematic paste (Future)
- Iris/OptiFine packs, MP4 export, MapGUI-as-framework
- General NPC AI
- Screen shake, FOV zoom, camera roll

## Architecture

One player, one session. A director walks beats. Paper only applies snapshots.

```text
ExperienceService.start(player, "first-join", currentPose)
        │
        ▼
   Session (exclusive)
     restorePose, audience, beatIndex
        │
        ▼
   current beat samples at local t
        │
        ▼
   ExperienceSnapshot → Paper applies pose / overlays / props / actors
        │
        ▼
   beat completes → next beat, or onComplete (restore | teleport)
```

`CinematicService` remains the timeline authoring/play API. `/cinematic play <scene>` stays a one-beat experience internally: it occupies the same session slot.

| Layer | Owns |
|---|---|
| Session kernel | enter/exit, restore, exclusive play, audience, freeze/hide flags |
| Tracks | interpolation, cue windows, held camera, actor placements |
| Director | which beat is active, completion, skip/pick, handoff |
| Paper | Folia ticker, display entities, vanilla shaders, commands, Bukkit events |

## Types

Names use the same rules as scenes: trim, lowercase, `[a-z0-9_-]`, length 1–64.

### Audience

| Value | Meaning |
|---|---|
| `SUBJECT` | Props/actors shown only to the watching player (today’s per-viewer displays) |
| `SPECTATORS` | Subject plus an explicit spectator id list |
| `PUBLIC` | The body actually moves; other players can see the flight |

Audience is a session field, not a world clone. Instancing is Future.

### Completion

| Value | Meaning |
|---|---|
| `RESTORE` | Stop returns the pre-play pose, no overlays/props/actors |
| `TELEPORT` | Stop leaves the player at the last sampled camera pose, still cleared of overlays/props/actors |

Quit and plugin disable always restore (never leave the player stranded in the studio).

### Scene graph (frames)

This is not a GPU render graph. It is a **shot graph**: named points in the world, keyframes on a shot, frames as nodes, `next` as the edge.

```text
points (world poses)          gate, tower, warrior-cam
        ▲
        │ referenced by
keyframes on a path           t=0 at gate → t=4 at tower
        ▲
        │ owned by
PATH frame                    "flyover"  --next-->  FADE "black"  --next-->  HOLD "class-select"
```

- **Point** — named `CameraPose` (stand somewhere, save it). Path keyframes and hold frames reuse points. Slice 1 still inlines poses inside `CinematicScene`; named points are the next authoring step.
- **Frame** — a node the director is *in*. Sealed `ExperienceFrame`:
  - **`PathFrame(id, sceneName, next)`** — play that scene’s keyframe polyline. Completes at last keyframe.
  - **`FadeFrame(id, overlayId, durationSeconds, next)`** — overlay for the whole duration (opening/closing). Camera is the next path’s first keyframe when there is one, otherwise the restore pose.
  - **`HoldFrame`** (later) — parked camera + actors until pick/skip.
- **Edge** — `next` (one successor). A linear experience is a chain. Branching (pick warrior vs mage) is extra outgoing edges later — not in this slice.
- **Experience** — `entry` frame id + the frame map. `Experience.load(beats)` still works: it compiles a `TimelineBeat` list into a PathFrame chain.

Duplicate frame ids are rejected. `next` must name a frame in the same graph or be empty (end). Cycles from `entry` are rejected. A PathFrame whose scene is missing is rejected at **start**, not at save.

Authoring sugar: `/cinematic experience beat add` appends a PathFrame onto the current tail (`next` was empty).

### Snapshots

`ExperienceSnapshot` is the director’s tick output: pose, shaders, props, actors, audience, beat index/id, playing flag, optional `pickedClassKey`.

Paper maps this the same way it maps `PlaybackSnapshot` today, plus actors as per-viewer displays (later: mannequins).

## Data flow

1. Operator (or another plugin) `save`s scenes, then `save`s an experience that references them.
2. `start(playerId, experienceName, currentPose)` records restore pose, occupies the session, samples beat 0 at local `t = 0`.
3. Paper ticks `sample(playerId, elapsedSinceStart)`. The director maps global elapsed onto the current beat’s local time.
4. Timeline/transition beats consume their duration and advance. Studio beats consume no duration until `pick` or `skip`.
5. After the last beat, `sample` returns a completed snapshot (`playing=false`) and clears the session — restore or teleport per `onComplete`.
6. `stop` always ends immediately with the completion action (quit uses restore).

Elapsed time is **since experience start**. Studio time is open-ended: while the current beat is a studio, further global elapsed stays on that beat until pick/skip. After pick, the next beat’s local clock starts at 0 on the following `sample` calls; Paper’s ticker treats post-pick elapsed as a new origin (the service stores `beatStartedAtElapsed`).

Practical sampling rule for tests (no studio):

- Experience beats with durations `d0, d1, …`
- Global `t` in `[sum(d0..d{i-1}), sum(d0..di))` samples beat `i` at local `t - prefix`
- Global `t >= sum(durations)` completes

At a beat boundary that is not the end (`t == d0` for a two-beat flow), sample beat 1 at local 0 (first keyframe), still playing.

## Errors

Reuse `CinematicResult` where the meaning matches. Add only:

- `UNKNOWN_EXPERIENCE`
- `EMPTY_EXPERIENCE` (zero beats)

| Result | When |
|---|---|
| `INVALID_NAME` | Experience or beat id fails name rules |
| `ALREADY_EXISTS` | Saving/creating a name that exists |
| `UNKNOWN_EXPERIENCE` | Start/play of a missing experience |
| `UNKNOWN_SCENE` | Timeline beat’s scene missing or incomplete at start |
| `EMPTY_EXPERIENCE` | Save/start of an experience with no beats |
| `ALREADY_PLAYING` | Start or cinematic play while that player has a session |
| `TOO_FEW_KEYFRAMES` | Unchanged: cinematic play of an incomplete scene |

`pick` on a non-studio beat, unknown actor, or no session → no-op result that is not `SUCCESS` (`UNKNOWN_SCENE` is wrong; use a small `INVALID_CUE` reuse or document `UNKNOWN_EXPERIENCE` only for missing experiences). **Decision:** unknown actor / wrong beat kind returns `INVALID_CUE`. No session returns empty optional on `pick`/`skip`? Prefer `CinematicResult` on pick: no session → `UNKNOWN_EXPERIENCE` is misleading. **Decision:** `pick`/`skip` return `CinematicResult`; no session is `UNKNOWN_EXPERIENCE` only if we add `NOT_PLAYING`. Add `NOT_PLAYING`.

Skip during a timeline beat advances to the next beat (or completes). Skip still clears overlays/actors for the abandoned beat.

## Class-select contract

Studio beat lists actors with `classKey` strings (e.g. `warrior`). This plugin does not interpret them.

Paper event (Bukkit, not in `api`):

```text
ClassPickedEvent(player, experienceName, classKey, actorId)
```

Downstream registers a listener and applies the kit. If no listener cares, the flow still continues.

## Persistence

- Scenes: `plugins/Cinematics/scenes/<name>.json` (unchanged)
- Experiences: `plugins/Cinematics/experiences/<name>.json`

Hand-rolled JSON, same degrade-on-corrupt policy. Missing file = unknown.

Experience document:

```json
{
  "name": "first-join",
  "audience": "subject",
  "on_complete": "restore",
  "beats": [
    {"id": "fade-in", "type": "transition", "overlay": "darkness", "duration": 2},
    {"id": "flyover", "type": "timeline", "scene": "intro"},
    {
      "id": "class-select",
      "type": "studio",
      "prompt": "Choose a class",
      "world": "world", "x": 0, "y": 80, "z": 0, "yaw": 90, "pitch": 0,
      "actors": [
        {
          "id": "warrior-mannequin",
          "class": "warrior",
          "name": "Warrior",
          "prop": "iron_sword",
          "world": "world", "x": 2, "y": 80, "z": 4, "yaw": 180, "pitch": 0
        }
      ]
    },
    {"id": "fade-out", "type": "transition", "overlay": "darkness", "duration": 2}
  ]
}
```

Slice 1 persists `timeline` beats only. Unknown `type` values are dropped (degrade), which may yield `EMPTY_EXPERIENCE` on load of a studio-only file until slice 2.

## Commands (additive)

Keep `/cinematic` scene authoring.

Add:

```text
/cinematic experience create <name>
/cinematic experience beat add <name> timeline <beatId> <scene>
/cinematic experience play <name> [player]
/cinematic experience list
```

Studio/transition authoring commands come with those beat types. `play`/`stop` of a raw scene stay as they are.

## Testing

Drive Bukkit-free services from JUnit, same as today:

- Experience load rejects empty beats, bad names, duplicate beat ids
- One-beat timeline experience samples identically to `CinematicScene.sample` / current play
- Two-beat flow: `t` in first duration stays on beat 0; at `d0` starts beat 1; at sum of durations completes and restores
- `ALREADY_PLAYING` is shared: cinematic play blocks experience start and vice versa
- Stop/quit path restores and clears cues
- JSON round-trip for timeline experiences
- `pick` tests wait for studio slice

Do not re-implement interpolation in experience tests — resolve to the existing scene and assert beat identity + restore.

## Paper notes

- One Folia ticker per session, as today
- Slice 1: apply pose/shaders/props from `ExperienceSnapshot` the same way as `PlaybackSnapshot`
- Audience `SUBJECT` matches current per-viewer props
- `SPECTATORS` / `PUBLIC` recorded in the snapshot; Paper visibility matrix is Next
- Freeze (cancel move/look) and hide-self are Next (already on the living spec)
- `ClassPickedEvent` ships with the studio slice

## Phasing

**Slice 1 (this plan):** `Experience` + `TimelineBeat` + `ExperienceService` + JSON + shared exclusive session + `/cinematic experience` create/beat/play/list. Existing scene play unchanged from the player’s point of view.

**Slice 2:** `TransitionBeat` (fade bookends).

**Slice 3:** `StudioBeat`, `pick`, actors on the snapshot, `ClassPickedEvent`.

**Slice 4:** Freeze, hide-self, skip, spectator/public visibility.

**Later:** titles/sound, ease functions, instanced studios, mannequin entities, authoring edits for scenes.

## Key decisions

| Decision | Why |
|---|---|
| One plugin / one JAR | Operators should not install a kernel plus a director |
| Experiences are sequences of beats, not a second plugin | Class-select and cutscenes share restore/audience/session |
| `CinematicService` stays | Scene authoring and one-shot play already work; do not break consumers |
| Shared exclusive session | Restore stays unambiguous; one camera owner |
| Class-select is presentation only | Kits stay in professions; this plugin fires an event |
| Audience is a field, not instancing | World copies are a different product |
| Quit always restores | Never trap a player in a studio on disconnect |
| Slice timeline flows first | Director + session sharing is the risky seam; studio/input can layer on |

## Open questions (resolved in conversation)

- Repo is the full experience toolkit, not kernel-only — **yes**
- Class data lives in professions/kits — **yes**
- Visibility depends on the experience (`audience`) — **yes**
- Multi-step flows live here — **yes**
