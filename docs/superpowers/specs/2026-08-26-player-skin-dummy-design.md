# Player-Skin Camera Dummy — Design

> Status: approved
> Date: 2026-08-26
> Repo: `/home/jlo/dev/cinematics`
> Approved path: full packet/NMS fake player + per-tick camera dolly

## Intent

Add the ability to create a visible clone of a player (the dummy) using their current skin, and to move the subject's camera independently of that clone. The clone is shown to the configured audience while the subject's view follows a sampled `CameraPose` path.

## Repository boundary

One Gradle project. Packages stay `dev.cinematics.api`, `core`, `paper`. The Bukkit-free `api` owns the actor contract and skin snapshot; `paper` owns packet/NMS fake-player rendering and per-tick camera application.

## In scope

- `api.PlayerSkin` — captured skin snapshot (name, UUID, texture value, signature, capturedAt).
- `api.Actor` — Bukkit-free actor with pose, kind, display name, and `PlayerSkin`.
- `api.ActorKind` — at least `PLAYER_SKIN`.
- `PlaybackSnapshot` extended with `List<Actor> actors`.
- `paper.CameraDolly` — applies sampled `CameraPose` to the real player each tick.
- `paper.PlayerSkinDummy` — spawns/destroys a fake player entity using the captured `PlayerSkin`.
- Audience visibility for the dummy (subject, spectators, public).
- Player profile capture at actor creation time.
- Operator command to create a dummy from a player.
- JSON persistence of the skin snapshot alongside scenes/experiences.

## Out of scope

- Real-time live skin refresh at playback. Re-capture is an explicit operator action.
- General NPC AI or pathfinding.
- Client-side shader/effect on the dummy.
- Third-party NPC plugins as a hard runtime dependency (Citizens, etc.). NMS or a shaded packet library is acceptable.
- Bukkit-free fake player. No such Bukkit API exists.

## Architecture

```text
Scene/Experience sample
  -> PlaybackSnapshot (pose + shaders + props + actors)
  -> PaperCinematicController
       -> CameraDolly.apply(player, pose)
       -> PlayerSkinDummy.show/hide per actor and audience
```

`PlayerSkinDummy` sends the minimal protocol packets to make a player entity appear to viewers: player info (profile and skin), spawn entity, and entity data (pose, name). It is a client-side entity; there is no server-side mob.

`CameraDolly` hides or moves the real player's visible body and teleports the real player to the sampled `CameraPose` each tick. The real player does not see themselves; the dummy is the visible body for others.

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

## Data flow

1. Operator or another plugin creates an `Actor` from a `Player`. `paper` reads `Player.getPlayerProfile()`, extracts the `textures` property, and stores a `PlayerSkin` snapshot.
2. The `Actor` is saved in a scene/experience JSON.
3. On playback, `CinematicService.sample` returns a `PlaybackSnapshot` that includes the `Actor`.
4. `PaperCinematicController.apply` calls `CameraDolly.apply(player, pose)` and `PlayerSkinDummy.show(player, actor, audience)`.
5. Each tick, the real player is teleported to the snapshot pose; the dummy is updated if it moves.
6. On stop, the dummy is destroyed and the player is restored to the pre-play pose.

## Persistence

The skin snapshot is stored with the actor JSON.

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

## Commands

Add to `/cinematic`:

```text
/cinematic dummy create <name> [player]
/cinematic dummy destroy <name>
/cinematic dummy list
```

`create` captures the profile of `player` (or the sender) and creates a named, standalone dummy at the current pose. This is the first delivery surface before integrating actors into the scene/experience graph.

## Testing

- Unit tests for `PlayerSkin` and `Actor` validation in `dev.cinematics.core` (Bukkit-free).
- `paper.PlayerSkinDummy` is tested by running the plugin on a Paper 1.21.11 server and verifying the entity is visible to other players. This is not unit-testable under JUnit without a connected client.
- `paper.CameraDolly` pose application is verified by integration test; `api` pose calculation is already covered.

## Paper notes

- The real player is teleported each tick. This is the only vanilla-client way to move the camera independent of the body.
- The dummy is not a `LivingEntity`. It is a client-side player entity tracked per viewer.
- Player body visibility is controlled by `player.hidePlayer` / `player.showPlayer` and/or entity packets.
- Spectator mode is not used because it gives noclip and the player can detach from the target.

## Decisions

| Decision | Why |
|---|---|
| Capture skin at actor creation | Deterministic replay; works for offline players and skin changes. |
| NMS/packet in `paper` only | Bukkit API cannot spawn player entities; `api` stays server-free. |
| Per-tick teleport for camera | Spectator mode is not robust for authored paths. |
| `PlayerSkin` is part of the actor | Lifetime is tied to the actor; simpler lifecycle. |
| Start with standalone `/cinematic dummy` commands | Prove the NMS/packet renderer before integrating actors into scene/experience graphs. |

## Open questions

- Exact NMS classes and packet sequence, or whether to shade PacketEvents/ProtocolLib.
- Whether to support live skin re-capture as a command or only re-create the actor.
- Whether to show the dummy to the subject (third-person) or only to spectators/public.
