# Player-Skin Camera Dummy — Design

> Status: under review
> Date: 2026-08-26
> Repo: `/home/jlo/dev/cinematics`
> Proposed path: full packet/NMS fake player + per-tick camera dolly
> Revision: 2 — Slice 1 is a standalone paper `PlayerSkinDummy`/`CameraDolly` with commands; no `Actor` API or scene/experience integration.

## Intent

Add the ability to create a visible clone of a player (the dummy) using their current skin, and to move the subject's camera independently of that clone. Slice 1 proves the renderer as standalone `paper` classes and commands. Later slices introduce the `Actor` value type, persistence, and scene/experience integration.

## Repository boundary

One Gradle project. Packages stay `dev.cinematics.api`, `core`, `paper`. Slice 1 is entirely in `paper`; no `api` or `core` changes. Future slices will move the value contract into `api` and add persistence.

## Slice 1 — in scope

- `paper.PlayerSkinDummy` — captures a `PlayerProfile` and spawns/destroys a fake player entity.
- `paper.CameraDolly` — teleports the real player to a `CameraPose` and hides their body from the audience.
- Operator commands to create, show, hide, destroy a dummy and to dolly the camera.
- In-memory dummy map in `PlayerSkinDummy`; no persistence.

## Slice 1 — out of scope

- `api.Actor`, `api.PlayerSkin`, `api.ActorKind`, `api.ActorService`.
- `core.DefaultActorService`, `core.ActorRepository`, `core.JsonActorRepository`.
- `dummies/` JSON persistence.
- `Actor` carried by `CinematicScene`, `Experience`, `PlaybackSnapshot`, or `ExperienceSnapshot`.
- Actor sampling inside `CinematicScene.sample` / `Experience.sample`.
- Modifications to `CinematicDraft`, `JsonCinematicRepository`, `JsonExperienceRepository`, or scene/experience JSON codecs.
- Live skin refresh at playback. Re-capture is an explicit operator action.
- General NPC AI or pathfinding.
- Client-side shader/effect on the dummy.
- Third-party NPC plugins as a hard runtime dependency (Citizens, etc.). NMS or a shaded packet library is acceptable.
- Bukkit-free fake player. No such Bukkit API exists.

## Slice 2 — future (Actor value and persistence)

- `api.PlayerSkin` captured skin snapshot (name, UUID, texture value, signature, capturedAt).
- `api.Actor` Bukkit-free actor with pose, kind, display name, and `PlayerSkin`.
- `api.ActorKind` at least `PLAYER_SKIN`.
- `api.ActorService` create/destroy/find/list.
- `core.DefaultActorService` + `core.JsonActorRepository` persist dummies under `dummies/`.
- `paper.PlayerSkinDummy` and `paper.CameraDolly` consume `api.Actor` instead of `Player` directly.

## Slice 3 — future (scene/experience integration)

- Add `List<Actor> actors` to `CinematicDraft`, `CinematicScene`, `PlaybackSnapshot`, `ExperienceSnapshot`.
- Extend `JsonCinematicRepository` and `JsonExperienceRepository` to serialize actors.
- Update `CinematicScene.sample`, `Experience.sample`, and `PaperCinematicController.apply` to spawn/hide actors per audience.

## Architecture (Slice 1)

```text
Operator / plugin
  -> /cinematic dummy create <name> [player]
       -> PlayerSkinDummy.create(dummyId, player) captures PlayerProfile, stores in map
  -> /cinematic dummy show <name>
       -> PlayerSkinDummy.show(dummyId) spawns fake player for audience
  -> /cinematic dolly <player> <x> <y> <z> <yaw> <pitch>
       -> CameraDolly.apply(player, pose) teleports real player
  -> /cinematic dummy hide <name>
       -> PlayerSkinDummy.hide(dummyId)
  -> /cinematic dummy destroy <name>
       -> PlayerSkinDummy.destroy(dummyId)
```

`PlayerSkinDummy` sends the minimal protocol packets to make a player entity appear to viewers: player info (profile and skin), spawn entity, and entity data (pose, name). It is a client-side entity; there is no server-side mob.

`CameraDolly` hides the real player from the audience and teleports the real player to the requested `CameraPose`. The real player does not see themselves; the dummy is the visible body for others.

`PlayerSkinDummy` owns an in-memory `Map<String, Dummy>`. `Dummy` is a `paper`-internal record holding the source `PlayerProfile`, the captured pose, and the optional spawned entity id.

## Types (Slice 1)

### paper.Dummy (package-private)

```java
record Dummy(
    String dummyId,
    PlayerProfile profile,
    CameraPose pose,
    @Nullable Integer entityId,
    boolean shown) {}
```

`PlayerProfile` is the Paper type; this record is not in `api` because Slice 1 does not define the Bukkit-free actor contract yet.

### paper.PlayerSkinDummy

```java
public final class PlayerSkinDummy {
  public CinematicResult create(String dummyId, Player player);
  public CinematicResult show(String dummyId);
  public CinematicResult hide(String dummyId);
  public CinematicResult destroy(String dummyId);
  public CinematicResult move(String dummyId, CameraPose pose);
}
```

Validation and result reuse `CinematicResult` (`SUCCESS`, `INVALID_NAME`, `ALREADY_EXISTS`). `UNKNOWN_SCENE`/`UNKNOWN_EXPERIENCE` can be reused as `UNKNOWN_DUMMY` if needed, or a new `UNKNOWN_DUMMY` added.

### paper.CameraDolly

```java
public final class CameraDolly {
  public void apply(Player player, CameraPose pose, PlayerSkinDummy dummy);
  public void restore(Player player, CameraPose pose);
}
```

`apply` teleports the player and tells `PlayerSkinDummy` to hide the real body from viewers. `restore` returns the player to the pre-dolly pose and makes them visible again.

## Data flow (Slice 1)

1. Operator runs `/cinematic dummy create <name> [player]`. `PlayerSkinDummy.create` reads `Player.getPlayerProfile()`, extracts the `textures` property, and stores a `Dummy` in an in-memory map.
2. Operator runs `/cinematic dummy show <name>`. `PlayerSkinDummy.show` spawns the fake player at the dummy's pose.
3. Operator runs `/cinematic dolly <player> <x> <y> <z> <yaw> <pitch>`. `CameraDolly.apply` teleports the real player to the pose and hides them from the audience.
4. Operator runs `/cinematic dummy hide <name>`. `PlayerSkinDummy.hide` removes the client-side entity.
5. Operator runs `/cinematic dummy destroy <name>`. `PlayerSkinDummy.destroy` removes the entity and the map entry.

## Persistence

Slice 1 is in-memory. Dummies are lost on reload. Slice 2 adds `dummies/` JSON persistence.

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
- `show` spawns the fake player for the audience.
- `hide` removes the fake player.
- `destroy` removes the entry and the entity.
- `dolly` teleports the real player to the given pose to move their camera; it does not move the dummy.

## Testing

- `paper.PlayerSkinDummy` is tested by running the plugin on a Paper 1.21.11 server and verifying the entity is visible to other players. This is not unit-testable under JUnit without a connected client.
- `paper.CameraDolly` pose application is verified by integration test.
- `CinematicCommand` parsing tests for the new sub-commands.

## Paper notes

- The real player is teleported each tick when used inside a path; the Slice 1 `dolly` command is a one-shot teleport. This is the only vanilla-client way to move the camera independent of the body.
- The dummy is not a `LivingEntity`. It is a client-side player entity tracked per viewer.
- Player body visibility is controlled by `player.hidePlayer` / `player.showPlayer` and/or entity packets.
- Spectator mode is not used because it gives noclip and the player can detach from the target.
- `PlayerProfile` is captured at creation. Skin changes after creation require re-creating the dummy.

## Decisions

| Decision | Why |
|---|---|
| Slice 1 is standalone in `paper` with no `api.Actor` | Avoids touching `CinematicDraft`, `JsonCinematicRepository`, `PlaybackSnapshot`, or `ExperienceSnapshot` before the renderer is proven. |
| In-memory dummy map in Slice 1 | Persistence is a Slice 2 concern; the first slice is a renderer and command proof. |
| Capture skin at dummy creation | Deterministic replay; works for offline players and skin changes. |
| NMS/packet in `paper` only | Bukkit API cannot spawn player entities. |
| Per-tick teleport for camera | Spectator mode is not robust for authored paths. |
| `dolly` command does not move the dummy | Separates camera control from the visible clone. |

## Open questions

- Exact NMS classes and packet sequence, or whether to shade PacketEvents/ProtocolLib.
- Whether to show the dummy to the subject (third-person) or only to spectators/public.
- How `PlayerSkinDummy` and `CameraDolly` are wired into `CinematicsPlugin` and `CinematicCommand`.
