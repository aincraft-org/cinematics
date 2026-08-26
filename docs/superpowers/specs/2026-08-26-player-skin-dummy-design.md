# Player-Skin Camera Dummy — Design

> Status: approved
> Date: 2026-08-26
> Repo: `/home/jlo/dev/cinematics`
> Approved path: full packet/NMS fake player + per-tick camera dolly
> Revision: 1 — Slice 1 is a standalone dummy/actor service; scene/experience integration is Slice 2.

## Intent

Add the ability to create a visible clone of a player (the dummy) using their current skin, and to move the subject's camera independently of that clone. Slice 1 proves the renderer as a standalone actor service. Slice 2 integrates actors into scenes and experiences.

## Repository boundary

One Gradle project. Packages stay `dev.cinematics.api`, `core`, `paper`. The Bukkit-free `api` owns the actor contract and skin snapshot; `paper` owns packet/NMS fake-player rendering and per-tick camera application.

## Slice 1 — in scope

- `api.PlayerSkin` — captured skin snapshot (name, UUID, texture value, signature, capturedAt).
- `api.Actor` — Bukkit-free actor with pose, kind, display name, and `PlayerSkin`.
- `api.ActorKind` — at least `PLAYER_SKIN`.
- `api.ActorService` — create, destroy, find, list named actors/dummies.
- `core.DefaultActorService` + `core.ActorRepository` + `core.JsonActorRepository` — persist dummies under `dummies/`.
- `paper.PlayerSkinDummy` — spawns/destroys a fake player entity using a captured `PlayerSkin`.
- `paper.CameraDolly` — teleports the real player to a `CameraPose` and hides their body from the audience.
- Operator commands to create, list, show, hide, destroy a dummy and to dolly the camera.

## Slice 1 — out of scope

- Actors carried by `CinematicScene`, `Experience`, `PlaybackSnapshot`, or `ExperienceSnapshot`.
- Actor sampling inside `CinematicScene.sample` / `Experience.sample`.
- Modifications to `CinematicDraft`, `JsonCinematicRepository`, `JsonExperienceRepository`, or scene/experience JSON codecs.
- Live skin refresh at playback. Re-capture is an explicit operator action.
- General NPC AI or pathfinding.
- Client-side shader/effect on the dummy.
- Third-party NPC plugins as a hard runtime dependency (Citizens, etc.). NMS or a shaded packet library is acceptable.
- Bukkit-free fake player. No such Bukkit API exists.

## Slice 2 — future

- Add `List<Actor> actors` to `CinematicDraft`, `CinematicScene`, `PlaybackSnapshot`, `ExperienceSnapshot`.
- Extend `JsonCinematicRepository` and `JsonExperienceRepository` to serialize actors.
- Update `CinematicScene.sample`, `Experience.sample`, and `PaperCinematicController.apply` to spawn/hide actors per audience.

## Architecture

```text
Operator / plugin
  -> ActorService.create(...) captures PlayerProfile -> Actor persisted in dummies/<name>.json
  -> /cinematic dummy show <name> -> PaperCinematicController
       -> PlayerSkinDummy.spawn(actor, audience)
  -> /cinematic dolly <player> <x> <y> <z> <yaw> <pitch> -> CameraDolly.apply(player, pose)
  -> /cinematic dummy hide <name> -> PlayerSkinDummy.destroy(actorId)
```

`PlayerSkinDummy` sends the minimal protocol packets to make a player entity appear to viewers: player info (profile and skin), spawn entity, and entity data (pose, name). It is a client-side entity; there is no server-side mob.

`CameraDolly` hides or moves the real player's visible body and teleports the real player to the requested `CameraPose`. The real player does not see themselves; the dummy is the visible body for others.

`ActorService` lives in `core` and is Bukkit-free. It owns `Actor` persistence. `paper.CinematicCommand` and `paper.PaperCinematicController` call the service and the renderers.

## Types

### PlayerSkin

```java
public record PlayerSkin(
    String playerId,       // UUID
    String playerName,
    String skinValue,      // base64 encoded texture payload
    String skinSignature,  // Mojang signature
    Instant capturedAt) {}
```

Validation: `playerId` and `playerName` are non-blank; `skinValue` is present. `skinSignature` may be empty for some profile sources but must be present for Mojang-signed skins.

### Actor

```java
public record Actor(
    String actorId,
    ActorKind kind,
    CameraPose pose,
    PlayerSkin skin,
    Optional<String> displayName) {}
```

`actorId` follows the existing `CinematicScene` name rules.

### ActorKind

```java
public enum ActorKind { PLAYER_SKIN }
```

Future kinds (armor stand, item display) can be added without changing the surface.

### ActorService

```java
public interface ActorService {
  CinematicResult create(String actorId, PlayerSkin skin, CameraPose pose, Optional<String> displayName);
  CinematicResult destroy(String actorId);
  Optional<Actor> actor(String actorId);
  Collection<Actor> actors();
}
```

## Data flow (Slice 1)

1. Operator runs `/cinematic dummy create <name> [player]`. `paper` reads `Player.getPlayerProfile()`, extracts the `textures` property, and stores a `PlayerSkin` snapshot.
2. `ActorService.create` saves the `Actor` to `dummies/<name>.json`.
3. Operator runs `/cinematic dummy show <name>`. `paper` looks up the `Actor` and `PlayerSkinDummy.spawn` sends the spawn packets.
4. Operator runs `/cinematic dolly <player> <x> <y> <z> <yaw> <pitch>`. `CameraDolly.apply` teleports the real player to the pose and sets body visibility.
5. Operator runs `/cinematic dummy hide <name>`. `PlayerSkinDummy.destroy` removes the client-side entity.
6. Operator runs `/cinematic dummy destroy <name>`. `ActorService.destroy` deletes the JSON.

## Persistence

The skin snapshot is stored with the actor JSON under `plugins/Cinematics/dummies/<name>.json`.

```json
{
  "id": "hero-mannequin",
  "kind": "PLAYER_SKIN",
  "pose": { "worldIdentity": "world", "x": 0, "y": 80, "z": 0, "yaw": 90, "pitch": 0 },
  "displayName": "Hero",
  "skin": {
    "playerId": "9c...",
    "playerName": "jlo",
    "skinValue": "eyJ...",
    "skinSignature": "aBc...",
    "capturedAt": "2026-08-26T12:00:00Z"
  }
}
```

`JsonActorRepository` reuses the hand-rolled JSON pattern. Missing files are unknown; corrupted values degrade rather than fail.

## Commands (Slice 1)

Add to `/cinematic`:

```text
/cinematic dummy create <name> [player]
/cinematic dummy destroy <name>
/cinematic dummy list
/cinematic dummy show <name>
/cinematic dummy hide <name>
/cinematic dolly <player> <x> <y> <z> <yaw> <pitch>
```

- `create` captures the profile of `player` (or the sender) and the current pose.
- `show` spawns the fake player for the configured audience (default `subject` in Slice 1; audience set by later command/slice).
- `hide` removes the fake player.
- `dolly` teleports the real player to the given pose to move their camera; it does not move the dummy.

## Testing

- Unit tests for `PlayerSkin` and `Actor` validation in `dev.cinematics.core` (Bukkit-free).
- `dev.cinematics.core.ActorServiceTest` for create/destroy/list and JSON round-trip.
- `paper.PlayerSkinDummy` is tested by running the plugin on a Paper 1.21.11 server and verifying the entity is visible to other players. This is not unit-testable under JUnit without a connected client.
- `paper.CameraDolly` pose application is verified by integration test; `api` pose calculation is already covered.

## Paper notes

- The real player is teleported each tick when used inside a path; the Slice 1 `dolly` command is a one-shot teleport. This is the only vanilla-client way to move the camera independent of the body.
- The dummy is not a `LivingEntity`. It is a client-side player entity tracked per viewer.
- Player body visibility is controlled by `player.hidePlayer` / `player.showPlayer` and/or entity packets.
- Spectator mode is not used because it gives noclip and the player can detach from the target.

## Decisions

| Decision | Why |
|---|---|
| Slice 1 is a standalone actor service | `CinematicDraft`, `JsonCinematicRepository`, `PlaybackSnapshot`, and `ExperienceSnapshot` have no actor support yet. Touching their constructors/codec/sample paths would make the renderer unlandable. |
| Capture skin at actor creation | Deterministic replay; works for offline players and skin changes. |
| NMS/packet in `paper` only | Bukkit API cannot spawn player entities; `api` stays server-free. |
| Per-tick teleport for camera | Spectator mode is not robust for authored paths. |
| `PlayerSkin` is part of the actor | Lifetime is tied to the actor; simpler lifecycle. |
| Separate `dummies/` persistence | Avoids changing scene/experience JSON and repositories until Slice 2. |

## Open questions

- Exact NMS classes and packet sequence, or whether to shade PacketEvents/ProtocolLib.
- Whether to support live skin re-capture as a command or only re-create the actor.
- Whether to show the dummy to the subject (third-person) or only to spectators/public.
- How `ActorService` is wired into `CinematicsPlugin` and exposed to other plugins.
