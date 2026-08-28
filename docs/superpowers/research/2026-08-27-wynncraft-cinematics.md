# Wynncraft Cinematic Research — Reported Techniques and Inferences

> Status: research note
> Date: 2026-08-27
> Repo: `/home/jlo/dev/cinematics`

This doc collects what is publicly reported about Wynncraft's cinematic and UI systems. Because the server is closed-source, many details come from community reverse-engineering, forum summaries, and secondhand sources. Claims are marked as **direct** (quoted or shown by an official source), **reported** (stated in a community thread/wiki with no primary dev confirmation), or **inferred** (reasonable technical extrapolation from behavior and tooling). This is not a dev-sanctioned specification.

## 1. Scope and evidence quality

The main evidence used:

- Wynncraft Fandom wiki (official wiki, player-edited, but often the closest public source to the team) [A].
- Wynncraft official forums (public thread titles and summaries; most full threads require login to read) [B].
- Reddit /r/WynnCraft and SpigotMC community discussions (secondhand, reverse-engineered, or comparative) [C].
- The `cinematics` repo's own design docs and code [D].

Evidence tags in this doc:

- **DIRECT** — from an official wiki page or a publicly readable source.
- **REPORTED** — from a forum summary, Reddit, or community source.
- **INFERRED** — a plausible mechanism derived from behavior, but not directly confirmed.

## 2. The big picture: vanilla clients, server-side tricks

**DIRECT**: Wynncraft's wiki states the official resource pack is sent to the client on join and that it is "possible to play without it, but it is not recommended" [A:Newcomer's Guide]. This matches the server-side, resource-pack-driven model.

**REPORTED**: Multiple community sources describe Wynncraft as vanilla-compatible, meaning players do not need a client mod to join [B,C]. Client-side mods such as Wynntils, WynnIris, or shader packs are optional enhancements [C].

This is the same constraint the `cinematics` plugin has chosen: "Vanilla clients must work" [D:Living Spec]. The Wynncraft approach is therefore a useful reference point, not an exact blueprint.

## 3. The Actor System — reported, not directly observed

The **Actor System** is the most distinctive cinematic technology attributed to Wynncraft, but the primary evidence is a forum thread title and search summaries because the full thread requires login [B:Actor System thread].

What is reported:

- Wynncraft uses an internal **Actor System** to record player movements and replay them through NPCs for cutscenes [B,C].
- An editor assigns a player to an `actorId`, captures walking, looking, arm swings, item switching, and pathing, then transcribes the capture into **Actor Frames** [B,C].
- Playback is tick-accurate, producing smooth, choreographed sequences [B,C].
- A **Scene Editor** is said to manage the timeline (dialogue, commands, camera paths), and an **Actor Editor** is said to give per-actor fine-tuning [B,C].

**INFERRED**: The actors are almost certainly client-side NPC entities spawned with packets, because vanilla Minecraft has no server-side actor concept and the server cannot render a scene itself.

### Mapping to `cinematics`

The repo's `PlayerSkinDummy` / `FakePlayerPackets` is already heading in this direction: it spawns a client-side player entity from a `PlayerProfile` [D:Player Skin Dummy Design]. The gap is the recording/playback pipeline. To get Wynncraft-style actors, future slices would need:

- An `Actor` value type with a `PlayerSkin` snapshot and a timeline of poses/actions.
- A recording mode that captures an operator's movement as frames.
- Playback inside `CinematicScene.sample` / `Experience.sample`.
- Per-viewer spawn/hide (already planned for `SUBJECT` audience) [D:Living Spec].

## 4. Camera work — reported historical and modern techniques

**REPORTED / INFERRED**: Wynncraft's smooth camera motion is described in community sources as using an invisible entity that the player rides or spectates [B,C]. The entity is moved along a path, and because the player is mounted or spectating, the client handles interpolation instead of the server teleporting the player every tick [C:SpigotMC thread summary].

The reported evolution:

- **Legacy** (reported): invisible item drops or armor stands.
- **Modern options** (reported/inferred): display entities (`item_display`, `text_display`) with `teleport_duration`, or `/spectate` targeting a rig entity [C].

**Important**: These are community-reported and comparative descriptions. There is no direct quote from a Wynncraft developer confirming the exact entity type or command in current use.

### Mapping to `cinematics`

The repo's `CameraDolly` currently teleports the real player each tick and hides them from other players [D:Player Skin Dummy Design]. This is a valid vanilla-client approach, but the reported Wynncraft-style alternatives are worth noting as future options:

- Add an **optional camera rig mode**: spawn an invisible `item_display` with `teleport_duration`, spectate it, and move the rig along the interpolated path.
- Keep the current teleport mode as a fallback.

## 5. Phasing and instancing — reported, mechanism inferred

**DIRECT**: The Fandom wiki's `Newcomer's Guide` confirms that blocks cannot be broken in the world, that most blocks are decorative, and that loot chests are a separate per-player system [A]. The `Loot Chests` section describes chests that spawn on fixed locations and refill after being claimed, which is consistent with per-player instancing of containers.

**REPORTED**: Community sources describe two related ideas:

- **Instancing**: players are teleported to private copies of areas for dungeons or quest sequences, so one player's progress does not affect another [B,C].
- **Phasing**: the same physical location is shown differently to different players based on quest progress, achieved by modifying the packets sent to each client [B,C].

**INFERRED**: "Phasing" in a Minecraft server is technically implemented by intercepting and rewriting entity/block/chunk packets per player, or by physically moving players to separate but identical map copies. The community claims packet interception; this is plausible but not directly confirmed.

**REPORTED**: During cutscenes, other players may be made invisible or appear as "ghosts" so they do not block the narrative [B,C].

### Mapping to `cinematics`

The repo explicitly lists "Instanced void worlds / schematic paste" as Future and records the decision "Audience is a session field, not world instancing" [D:Living Spec]. This is consistent with Wynncraft only if the long-term goal is to add phasing/instancing later. The next incremental steps would be:

- Use the existing `FakePlayerPackets` layer to hide or reskin entities per viewer.
- Later, add a per-player packet filter for blocks/entities.
- Even later, support void-world or schematic studio copies.

## 6. The login / class selection screen

This is the part of the user's question with the most direct evidence.

### What is directly known

**DIRECT**: The `Newcomer's Guide` on the Fandom wiki describes the join flow:

- The official resource pack downloads automatically when joining a world [A].
- The class selection screen appears after that [A].
- It has a green plus button to create a character [A].
- The icons in the upper row are the regular classes [A].
- Donor ranks get more character slots: 6 for no rank, 9 for VIP, 11 for VIP+, 14 for HERO [A].
- `/toggle autojoin` skips the selection screen and uses the last-selected class [A].
- Players switch classes with `/kill` [A].

The wiki also shows screenshots of the class selection, character creation, and class info menus [A].

### What is reported/inferred about the implementation

**REPORTED**: The class selection screen is server-side, and the player is held in a protected area where they cannot take damage, chat, or interact normally until a class is chosen [B,C].

**REPORTED**: The custom UI relies heavily on the official resource pack. If the pack fails to load, players report a black screen, falling into the void, or default item textures replacing custom ones [B,C].

**REPORTED**: Wynncraft's modern GUI elements, including advanced menus, are said to use **font textures** (custom font/Unicode mappings) rather than standard `textures/gui/*.png` files [B].

**REPORTED**: The official resource pack is encrypted, making it hard to inspect without tools like MCRPX [B].

**INFERRED**: Because the class selection is a custom UI inside a Minecraft client, the server is almost certainly sending packets to show item/entity/element representations that the resource pack re-textures into buttons. The exact protocol is not public.

### Mapping to `cinematics`

The repo's intended success scenario is: "`first-join`: fade, fly a camera path, park in a class-select studio until the player picks a mannequin, fade, fire `ClassPicked`" [D:Living Spec]. This is the same high-level shape as Wynncraft's join flow, with these differences:

- **Wynncraft**: resource-pack-driven GUI with font textures and persistent character slots.
- **`cinematics`**: planned to use `HoldFrame` + actor mannequins + `ClassPickedEvent`, no character data, no mandatory resource pack.

For the `cinematics` plugin to get the Wynncraft *look*, it would need either:

- An optional partner resource pack that supplies custom font/texture glyphs, or
- A vanilla-first approach with clickable armor-stand / item-display actors and custom names.

The second is more aligned with the current "vanilla clients only" boundary [D:Living Spec].

## 7. Scripting and data-driven content

**DIRECT**: The Wynncraft Fandom `Content Team` page explicitly states that **Game Masters (GMs)** "work with YAML files (.yml) and a proprietary scripting language called Wynnscript" to deliver quests, discoveries, minigames, events, lootruns, mobs, and boss altars [A]. It also states that **Scripters** "work with Wynnscript, a proprietary scripting language made specifically for work on Wynncraft" [A]. The page further notes that the **CMD** (command-blocker) role is being replaced by Scripter [A].

This is a strong, direct source. It does not, however, describe the language's syntax in detail or whether Wynnscript is used for *cinematics* specifically. Cinematics may be handled by the same tooling, a separate tool, or command blocks.

**INFERRED**: Because Wynnscript exists for quest/mob/event content, it is plausible that modern cutscenes are authored in it or in a related tool, but the specific cinematic authoring pipeline is not documented.

### Mapping to `cinematics`

The repo uses JSON persistence for scenes and experiences and has no scripting language [D:Living Spec]. The equivalent progression would be:

- Keep JSON as the data layer for now.
- Add `allowlisted timestamped commands` (already in Future [D:Living Spec]) so beats can trigger server commands without Java changes.
- Consider a lightweight DSL later if the experience graph becomes too complex for pure JSON.

## 8. Resource pack and visual layer

**DIRECT**: The `Newcomer's Guide` confirms that Wynncraft has an official resource pack and that it is required to view different weapon models [A].

**REPORTED**: The pack is said to be encrypted and to contain custom item models, weapon textures, armor, and UI [B]. Modern GUI elements are reported to use font textures [B].

**REPORTED**: Custom skyboxes, region fog, and lighting effects can conflict with standard shaders. WynnIris (a community Iris fork) is reported to support Wynncraft's custom skyboxes [C].

**INFERRED**: Because `cinematics` explicitly excludes client shader packs [D:Living Spec], the only vanilla-compatible ways to create atmosphere are: vanilla potion overlays (already supported), biome/weather packet tricks, and display-entity props.

## 9. Mapping reported Wynncraft techniques to the `cinematics` roadmap

| Wynncraft technique (as reported) | Confidence | Already in `cinematics` | Next / future slice |
|---|---|---|---|
| Recorded actor performances | REPORTED | `PlayerSkinDummy` in-memory [D] | `api.Actor`, recording, frame playback |
| Smooth camera via ridden/spectated rig | REPORTED / INFERRED | `CameraDolly` per-tick teleport [D] | Optional spectate/rig entity mode |
| Camera keyframe paths | DIRECT (behavior observed) | `CinematicScene` [D] | Ease functions, Catmull-Rom/Bezier |
| Vanilla shader overlays | DIRECT (vanilla effects) | `VanillaShaderOverlays` [D] | More vanilla effects, timed sound, titles |
| Per-player phasing | REPORTED / INFERRED | Audience `SUBJECT` [D] | Packet-level block/entity filtering |
| Private instances | REPORTED | Out of scope [D] | Void-world / schematic studio copies |
| Class-select studio | DIRECT (screenshots + wiki) | `StudioBeat` planned [D] | `HoldFrame`, freeze, actor placements |
| Resource-pack UI | REPORTED | Not integrated | Optional partner pack or font-based GUI |
| WynnScript/YAML content authoring | DIRECT (wiki) | JSON experiences [D] | YAML/JSON command lists, maybe a DSL |

## 10. Recommended next steps (inferred from the mapping)

These are the author's suggestions for moving the `cinematics` plugin closer to the *reported* Wynncraft style without claiming it is an exact copy:

1. **Prove the actor renderer** — complete the `PlayerSkinDummy` / `CameraDolly` Slice 1 plan, then add `Actor` value types and persistence.
2. **Add a camera rig mode** (optional) — spawn an invisible `item_display` and spectate it for smoother camera motion, with the current teleport mode as fallback.
3. **Build the studio beat** — `HoldFrame` with parked camera, actor mannequins, freeze, and `ClassPickedEvent`.
4. **Defer phasing/instancing** — keep it in Future as decided, but design the packet layer so it can later isolate a single player's world view.
5. **Keep resource-pack integration optional** — do not make a custom pack mandatory; support vanilla-first, with an optional pack for the font/GUI look.

## 11. Sources

### A. Wynncraft Fandom wiki (public, official wiki)

A1. `Newcomer's Guide` — https://wynncraft.fandom.com/wiki/Newcomer%27s_Guide
- Direct quotes used: resource pack downloads on join, class selection screen, green plus, donor rank slots, `/toggle autojoin`, `/kill` to switch class, weapon models require the pack, blocks cannot be broken, loot chests respawn on fixed locations.

A2. `Content Team` — https://wynncraft.fandom.com/wiki/Content_Team
- Direct quotes used: GMs work with YAML and Wynnscript; Scripters work with Wynnscript; CMD role is being replaced by Scripter.

### B. Wynncraft official forums (titles and search summaries; full threads require login)

B1. "The Actor System [ Includes Video & Gif Demonstrations ]" — https://forums.wynncraft.com/threads/70-supporters-98-6-the-actor-system-includes-video-gif-demonstrations.255656/
- Reported: recording and replaying player movements as Actor Frames, Scene Editor, Actor Editor.

B2. "How Are Wynncraft Quests Made?" — https://forums.wynncraft.com/threads/how-are-wynncraft-quests-made.251446/
- Reported: phasing, instancing, packet interception, ghost players during cutscenes.

B3. "How Is Wynncraft Made?" — https://forums.wynncraft.com/threads/how-is-wynncraft-made.308302/
- Reported: custom Java plugins, Wynnscript, separation of core devs and Content Team.

B4. "Allowing players to join without downloading the pack" — https://forums.wynncraft.com/threads/allowing-players-to-join-without-downloading-the-pack.99174/
- Reported: resource pack is mandatory, failure leads to black screen / void falling.

B5. "Problem loading into character select" — https://forums.wynncraft.com/threads/problem-loading-into-character-select.320110/
- Reported: class selection depends on resource pack loading.

B6. "How do I recreate the ability tree GUI texture?" — https://forums.wynncraft.com/threads/how-do-i-recreate-the-ability-tree-gui-texture.319669/
- Reported: modern GUI uses font textures, resource pack is encrypted.

### C. Community / comparative sources

C1. Reddit /r/WynnCraft — shader recommendations and WynnIris — https://www.reddit.com/r/WynnCraft/comments/1spibrg/shader_recommendations/
- Reported: WynnIris supports custom skyboxes; standard shaders can conflict.

C2. SpigotMC — "How to make a smooth camera animation" — https://www.spigotmc.org/threads/how-to-make-a-smooth-camera-animation.612081/
- Reported/inferred: invisible entity riding/spectating for smooth camera, display entity `teleport_duration`, `/spectate`.

C3. Reddit /r/WynnCraft — "How does this server have only 4 devs?" — https://www.reddit.com/r/WynnCraft/comments/14szj31/how_does_this_server_have_only_4_devs/
- Reported: small core-dev team builds tools, larger volunteer Content Team uses them.

### D. `cinematics` repo design docs and code

D1. `docs/living-specs/cinematics.md`
D2. `docs/superpowers/specs/2026-08-19-experience-director-design.md`
D3. `docs/superpowers/specs/2026-08-26-player-skin-dummy-design.md`
D4. `src/main/java/dev/cinematics/paper/VanillaShaderOverlays.java`
D5. `src/main/java/dev/cinematics/api/CinematicScene.java`
