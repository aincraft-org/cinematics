# Wynncraft Cinematic Research — What They Actually Do

> Status: research note
> Date: 2026-08-27
> Repo: `/home/jlo/dev/cinematics`

This doc is the result of the question: "how does Wynncraft create their cinematic work, login screen, selection screen, etc." It pulls together public forum threads, wiki pages, community reverse-engineering, and Reddit discussion, then maps those findings onto the `cinematics` plugin architecture.

Key takeaway up front: Wynncraft's cinematic feel is not one trick. It is the combination of (1) a server-side **Actor System** that records and replays NPC performances, (2) **camera rigging** via invisible ridden/spectated entities, (3) **per-player phasing and instancing** to isolate cutscenes, (4) a **mandatory encrypted resource pack** that supplies custom models, fonts, and UI, and (5) a **proprietary scripting layer** (WynnScript/YAML) that lets the Content Team author quests and cutscenes without touching Java.

## 1. The Big Picture: Vanilla-Compatible but Server-Heavy

Wynncraft is a vanilla-compatible Minecraft server. Players do not need a mod to join [1][2]. The cinematic work is done on the server and delivered through packets, resource packs, and vanilla effects. Client-side mods like Wynntils, WynnIris, or shader packs can enhance the experience, but they are not required [3].

That is the same constraint the `cinematics` plugin has chosen: "Vanilla clients must work" [4]. So the Wynncraft approach is directly applicable.

## 2. The Actor System — Recorded NPC Performances

The most distinctive Wynncraft cinematic technology is its **Actor System**.

- An **actor** is an NPC controlled by a recorded player performance.
- During authoring, a Content Team member is assigned an `actorId` and their movements are captured in real time: walking, looking, arm swings, item switching, and pathing [5].
- The capture is transcribed into **Actor Frames**.
- At playback, those frames are sent to the client through NPC entities. Because the recording is tick-accurate, the playback is smooth and mirrors the original performance [5].
- A **Scene Editor** manages the overall timeline: triggering dialogue, running commands, opening doors, explosions, and camera paths.
- An **Actor Editor** gives per-actor control for fine-tuning behavior and synchronization [6].

This is conceptually what `PlayerSkinDummy` / `FakePlayerPackets` is building toward: a client-side player entity that can be moved and posed. The difference is that Wynncraft's actors are recorded from a live performance, not hand-authored pose by pose.

### Implication for this project

The `cinematics` plugin currently has an in-memory dummy (`PlayerSkinDummy`) and a `CameraDolly` that teleports the real player [7]. To get Wynncraft-style actors, the next slices should add:

- `Actor` value type with `PlayerSkin` snapshot and a timeline of poses/actions.
- Recording mode: an operator runs a command, performs the movement, then stops recording; the plugin stores the pose/action frames.
- Playback from frames inside `CinematicScene.sample` / `Experience.sample`.
- Per-viewer spawn/hide of actor entities (already the plan for `SUBJECT` audience) [4].

## 3. Camera Work — Ride/Spectate a Moving Rig

Wynncraft's smooth camera motion is historically done by **spawning an invisible entity and having the player ride or spectate it** [8]. The entity is moved at a constant velocity between pre-defined points. Because the player is mounted or spectating, the client handles the interpolation instead of the server teleporting the player every tick.

Evolution of the trick:

- Legacy: invisible item drops or armor stands.
- Modern: display entities (`item_display`, `text_display`) with `teleport_duration` for native client interpolation [8].
- Alternative: `/spectate` the rig entity for a jitter-free locked camera [8].

### Implication for this project

The `cinematics` plugin currently does **per-tick teleport of the real player** via `CameraDolly.apply` [7]. This works for vanilla clients but can be jittery and leaves the player body visible unless hidden. To match Wynncraft:

- Add an **optional camera rig mode**: spawn an invisible `item_display` with `teleport_duration`, spectate it, and move the rig along the interpolated path.
- Keep the current teleport mode as the fallback for old clients or spectator-unfriendly scenes.
- Hide the real player from audience (already partly done by `CameraDolly` hiding the player [7]).

## 4. Phasing and Instancing — Isolating Cutscenes

A huge part of Wynncraft's immersion is **phasing**.

- **Instancing**: the player is teleported to a private copy of an area, e.g., a dungeon or a quest sequence. Progress (mobs killed, doors opened, chests looted) does not affect other players [9].
- **Phasing**: the same physical location is shown differently to different players based on quest progress. For example, one player sees a village rebuilt, another sees it destroyed. This is done by **packet interception**: the server modifies the data sent to each client [9].
- During cutscenes, other players may be made invisible or appear as "ghosts" so they do not block the narrative [9].

### Implication for this project

The `cinematics` plugin explicitly lists "Instanced void worlds / schematic paste" as Future and "Audience is a session field, not world instancing" as a decision [4]. That is the right call for slice order. To move toward Wynncraft-style phasing later, the project should eventually:

- Build a per-player block/entity packet filter (like the `FakePlayerPackets` layer already in place [7]).
- Support cloning a build to a private world or a void studio for a single player.
- Hide other players from the subject during `SUBJECT` audience mode (already in the `CameraDolly` hide logic, but it needs to generalize to audience `SPECTATORS` / `PUBLIC` [4]).

## 5. The Login / Class Selection Screen

Wynncraft's class/character selection is a **server-side, resource-pack-driven GUI**.

### How it works

1. On join, the server forces the client to download and apply the Wynncraft resource pack [10].
2. The player is placed in a protected area where they cannot take damage, chat, or interact normally [11].
3. The class selection menu is displayed. It lists character slots; the green plus creates a new character. Clicking a slot loads that character [11].
4. The menu visuals (and much of the rest of the UI) are rendered using **font textures** rather than standard `textures/gui/*.png` files. This lets Wynncraft draw complex UI with text characters and Unicode private-use mappings [12].
5. The official resource pack is **encrypted**, so third-party tools like MCRPX are needed to inspect it [12].
6. If the pack fails to load, the player sees a black screen, falls into the void, or sees default items instead of the custom menu [10].

### Commands

- `/class` — re-opens the selection screen while in-game [11].
- `/toggle autojoin` — skips the selection screen on future logins [11].

### Implication for this project

The `cinematics` plugin's success scenario is literally "`first-join`: fade, fly a camera path, park in a class-select studio until the player picks a mannequin, fade, fire `ClassPicked`" [4]. That is the same shape as Wynncraft's flow. The differences are:

- Wynncraft uses a **resource pack + font texture GUI** for the menu; this project currently has no resource-pack integration.
- Wynncraft puts the player in a **protected studio area**; this project will use `HoldFrame` + freeze + actor mannequins.
- Wynncraft's class selection is **persistent character slots**; this plugin intentionally only fires `ClassPicked` and leaves kits/stats to downstream plugins [4].

For this project to get the Wynncraft look, the studio beat would need either:

- A partner resource pack that maps custom font/texture glyphs to actor selection buttons, or
- A simpler vanilla approach: clickable armor-stand / item-display actors with custom names and `ClassPickedEvent` [13].

The second is more aligned with the current "vanilla clients only" boundary [4].

## 6. The Scripting Layer — WynnScript and YAML

Wynncraft is not just a pile of Java plugins. The Content Team uses:

- **WynnScript**: a proprietary, JavaScript-like scripting language for quests, dungeons, mobs, and events [14].
- **YAML**: data files for NPC dialogue, quest stages, and mob configurations [14].
- A separation of concerns: a small core-dev team builds the engine, while a larger volunteer Content Team writes content in WynnScript/YAML [15].

### Implication for this project

The `cinematics` plugin already uses **JSON persistence** for scenes and experiences [4]. It does not have a scripting language. The equivalent progression would be:

- Keep JSON as the data layer for now.
- Add a small expression/evaluator for `allowlisted timestamped commands` (already in Future [4]) so beats can run arbitrary server commands without writing Java.
- Consider a lightweight DSL later if the experience graph becomes complex.

## 7. Resource Pack and Visual Layer

Wynncraft's resource pack is mandatory and does a lot of the heavy lifting:

- Custom item models, weapon textures, armor, and UI [10].
- Font-based GUI elements for menus [12].
- Custom skyboxes, region fog, and lighting effects. Standard shaders can conflict, so WynnIris (an Iris fork) exists to support these custom skyboxes [3].

### Implication for this project

The `cinematics` plugin explicitly excludes "Iris / OptiFine / Vibrant Visuals client shader packs" [4]. That means the project cannot rely on client shader packs for fog/skybox. To mimic Wynncraft's atmosphere with vanilla clients, the plugin can use:

- Biome/weather packet tricks (not currently in scope).
- Display-entity props for localized atmosphere (already partially supported [4]).
- Vanilla shader overlay ids (`darkness`, `blindness`, `night_vision`, etc.) [16].

## 8. Mapping Wynncraft to the `cinematics` Roadmap

| Wynncraft technique | Already in `cinematics` | Next / future slice |
|---|---|---|
| Recorded actor performances | `PlayerSkinDummy` in-memory [7] | `api.Actor`, recording, frame playback |
| Smooth camera rig | `CameraDolly` per-tick teleport [7] | Optional spectate/rig entity mode |
| Camera keyframe paths | `CinematicScene` [17] | Ease functions, Catmull-Rom/Bezier |
| Shader overlays | `VanillaShaderOverlays` [4] | More vanilla effects, timed sound, titles |
| Per-player phasing | Audience `SUBJECT` [4] | Packet-level block/entity filtering |
| Private instances | Out of scope [4] | Void-world / schematic studio copies |
| Class-select studio | `StudioBeat` planned [13] | `HoldFrame`, freeze, actor placements |
| Resource-pack UI | Not integrated | Optional partner pack or font-based GUI |
| WynnScript content authoring | JSON experiences [4] | YAML/JSON command lists, maybe a DSL |

## 9. Recommended Next Steps

1. **Prove the actor renderer** — finish the `PlayerSkinDummy` / `CameraDolly` Slice 1 plan, then add `Actor` value types and persistence.
2. **Add a camera rig mode** — spawn an invisible `item_display` and spectate it for smooth, Wynncraft-style camera motion, with the current teleport mode as fallback.
3. **Build the studio beat** — `HoldFrame` with parked camera, actor mannequins, freeze, and `ClassPickedEvent`. This directly matches the Wynncraft login/selection experience.
4. **Defer phasing/instancing** — keep it in Future as decided [4], but design the packet layer so it can later isolate a single player's world view.
5. **Keep resource-pack integration optional** — do not make a custom resource pack mandatory; support vanilla-first, with an optional pack for the font/GUI look.

## 10. Sources

1. Wynncraft Forums — "The Actor System [ Includes Video & Gif Demonstrations ]" — https://forums.wynncraft.com/threads/70-supporters-98-6-the-actor-system-includes-video-gif-demonstrations.255656/
2. Wynncraft Fandom — Newcomer's Guide — https://wynncraft.fandom.com/wiki/Newcomer%27s_Guide
3. Reddit /r/WynnCraft — shader recommendations and WynnIris notes — https://www.reddit.com/r/WynnCraft/comments/1spibrg/shader_recommendations/
4. `cinematics` repo — `docs/living-specs/cinematics.md`
5. Wynncraft Forums — "How Are Wynncraft Quests Made?" — https://forums.wynncraft.com/threads/how-are-wynncraft-quests-made.251446/ (summarized via search)
6. Wynncraft Forums — "How Is Wynncraft Made?" — https://forums.wynncraft.com/threads/how-is-wynncraft-made.308302/ (summarized via search)
7. `cinematics` repo — `docs/superpowers/specs/2026-08-26-player-skin-dummy-design.md`
8. SpigotMC — "How to make a smooth camera animation" — https://www.spigotmc.org/threads/how-to-make-a-smooth-camera-animation.612081/
9. Wynncraft Forums — "How Are Wynncraft Quests Made?" (phasing/instancing discussion) — https://forums.wynncraft.com/threads/how-are-wynncraft-quests-made.251446/
10. Wynncraft Forums — resource pack / class selection issues — https://forums.wynncraft.com/threads/allowing-players-to-join-without-downloading-the-pack.99174/ and https://forums.wynncraft.com/threads/problem-loading-into-character-select.320110/
11. Wynncraft Fandom — Newcomer's Guide (class selection mechanics) — https://wynncraft.fandom.com/wiki/Newcomer%27s_Guide
12. Wynncraft Forums — "How do I recreate the ability tree GUI texture?" — https://forums.wynncraft.com/threads/how-do-i-recreate-the-ability-tree-gui-texture.319669/
13. `cinematics` repo — `docs/superpowers/specs/2026-08-19-experience-director-design.md`
14. Wynncraft Fandom — Content Team (WynnScript) — https://wynncraft.fandom.com/wiki/Content_Team
15. Reddit /r/WynnCraft — "How does this server have only 4 devs?" — https://www.reddit.com/r/WynnCraft/comments/14szj31/how_does_this_server_have_only_4_devs/
16. `cinematics` repo — `src/main/java/dev/cinematics/paper/VanillaShaderOverlays.java`
17. `cinematics` repo — `src/main/java/dev/cinematics/api/CinematicScene.java`
