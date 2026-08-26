# Player-Skin Camera Dummy — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add standalone `paper` `PlayerSkinDummy` and `CameraDolly` classes plus `/cinematic dummy` and `/cinematic dolly` commands. Slice 1 proves the renderer; it does not add `api.Actor`, persistence, or scene/experience integration.

**Architecture:** `paper.PlayerSkinDummy` owns an in-memory map of dummies, captures a source `PlayerProfile`, and uses PacketEvents 2.13.0 for the 1.21.11 client-only player spawn sequence. `paper.CameraDolly` teleports the real player and hides their body. `CinematicCommand` parses the new sub-commands. `CinematicsPlugin` wires the new classes.

**Tech Stack:** Java 25, Gradle, Paper 1.21.11, PacketEvents 2.13.0 (shaded), JUnit 5.

## Global Constraints

- No `api` or `core` changes in Slice 1.
- No persistence; dummies live in memory only.
- No `Actor`, `PlayerSkin`, `ActorService`, `JsonActorRepository`, `dummies/`.
- No changes to `CinematicDraft`, `CinematicScene`, `PlaybackSnapshot`, `ExperienceSnapshot`, `JsonCinematicRepository`, or `JsonExperienceRepository`.
- `CinematicResult` reused for validation.

---

### Task 1: Add PacketEvents and fake-player packet spike

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `src/main/java/dev/cinematics/paper/FakePlayerPackets.java`

- [ ] **Step 1: Add PacketEvents dependency**

`gradle/libs.versions.toml`:
```toml
[versions]
packetevents = "2.13.0"

[libraries]
packetevents-spigot = { module = "com.github.retrooper:packetevents-spigot", version.ref = "packetevents" }
```

`build.gradle.kts`:
```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.packetevents.spigot)
}

tasks.shadowJar {
    relocate("com.github.retrooper.packetevents", "dev.cinematics.libs.packetevents")
    relocate("io.github.retrooper.packetevents", "dev.cinematics.libs.packetevents")
    relocate("net.kyori", "dev.cinematics.libs.kyori")
}
```

- [ ] **Step 2: Create `FakePlayerPackets` skeleton**

```java
package dev.cinematics.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.cinematics.api.CameraPose;
import io.papermc.paper.entity.PlayerProfile;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class FakePlayerPackets {

  UserProfile fromPlayerProfile(PlayerProfile profile) {
    Objects.requireNonNull(profile, "profile");
    return new UserProfile(
        profile.getId(),
        profile.getName(),
        profile.getProperties().stream()
            .filter(p -> "textures".equals(p.getName()))
            .map(p -> new TextureProperty(p.getName(), p.getValue(), p.getSignature()))
            .toList());
  }

  int spawn(UserProfile profile, int entityId, CameraPose pose, Collection<Player> viewers) {
    // Spike: verify exact PacketEvents 2.13.0 constructors on Paper 1.21.11.
    // Sequence: PlayerInfoUpdate ADD_PLAYER, SpawnEntity PLAYER, EntityMetadata skin parts.
    throw new UnsupportedOperationException("PacketEvents spawn sequence to be validated in spike");
  }

  void destroy(UUID profileId, int entityId, Collection<Player> viewers) {
    throw new UnsupportedOperationException("PacketEvents destroy sequence to be validated in spike");
  }
}
```

- [ ] **Step 3: Validate packet sequence in-game**

Run: `./gradlew runServer`. Join with two clients. Trigger a temporary operator command or smoke test calling `FakePlayerPackets.spawn`.

Acceptance:
- Second client sees a player entity with the source player's name.
- Entity displays the source player's current skin.
- Entity appears at the expected `CameraPose`.
- `destroy` removes the entity and the tab-list entry.

- [ ] **Step 4: Lock verified packet calls**

Replace the `UnsupportedOperationException`s with the exact constructor calls found in Step 3. Document the verified sequence in comments.

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts gradle/libs.versions.toml src/main/java/dev/cinematics/paper/FakePlayerPackets.java
git commit -m "Add PacketEvents and fake-player packet spike"
```

---

### Task 2: Implement `PlayerSkinDummy` lifecycle

**Files:**
- Create: `src/main/java/dev/cinematics/paper/PlayerSkinDummy.java`
- Create: `src/test/java/dev/cinematics/paper/PlayerSkinDummyTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.cinematics.api.CinematicResult;
import org.junit.jupiter.api.Test;

class PlayerSkinDummyTest {

  @Test
  void rejectsInvalidName() {
    PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
    assertEquals(CinematicResult.INVALID_NAME, dummies.create("", null));
  }
}
```

- [ ] **Step 2: Run test**

`./gradlew test --tests dev.cinematics.paper.PlayerSkinDummyTest`

Expected: compile failure.

- [ ] **Step 3: Implement `PlayerSkinDummy`**

```java
package dev.cinematics.paper;

import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import io.papermc.paper.entity.PlayerProfile;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlayerSkinDummy {

  private final Map<String, Dummy> dummies = new ConcurrentHashMap<>();
  private final FakePlayerPackets packets;

  public PlayerSkinDummy(FakePlayerPackets packets) {
    this.packets = Objects.requireNonNull(packets, "packets");
  }

  public CinematicResult create(String dummyId, Player player) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    if (dummies.containsKey(normalized)) {
      return CinematicResult.ALREADY_EXISTS;
    }
    PlayerProfile profile = player.getPlayerProfile();
    CameraPose pose = PaperCinematicController.poseOf(player.getLocation());
    dummies.put(normalized, new Dummy(normalized, player.getUniqueId(), profile, pose, null, false));
    return CinematicResult.SUCCESS;
  }

  public CinematicResult show(String dummyId) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.get(normalized);
    if (dummy == null) {
      return CinematicResult.UNKNOWN_SCENE;
    }
    if (dummy.shown()) {
      return CinematicResult.SUCCESS;
    }
    int entityId = allocateEntityId();
    Collection<Player> viewers = List.copyOf(Bukkit.getServer().getOnlinePlayers());
    packets.spawn(packets.fromPlayerProfile(dummy.profile()), entityId, dummy.pose(), viewers);
    dummies.put(
        normalized,
        new Dummy(dummy.dummyId(), dummy.playerId(), dummy.profile(), dummy.pose(), entityId, true));
    return CinematicResult.SUCCESS;
  }

  public CinematicResult hide(String dummyId) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.get(normalized);
    if (dummy == null || !dummy.shown()) {
      return CinematicResult.SUCCESS;
    }
    Collection<Player> viewers = List.copyOf(Bukkit.getServer().getOnlinePlayers());
    packets.destroy(dummy.playerId(), dummy.entityId(), viewers);
    dummies.put(
        normalized,
        new Dummy(dummy.dummyId(), dummy.playerId(), dummy.profile(), dummy.pose(), null, false));
    return CinematicResult.SUCCESS;
  }

  public CinematicResult destroy(String dummyId) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.remove(normalized);
    if (dummy == null) {
      return CinematicResult.UNKNOWN_SCENE;
    }
    if (dummy.shown()) {
      packets.destroy(
          dummy.playerId(),
          dummy.entityId(),
          List.copyOf(Bukkit.getServer().getOnlinePlayers()));
    }
    return CinematicResult.SUCCESS;
  }

  public CinematicResult move(String dummyId, CameraPose pose) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.get(normalized);
    if (dummy == null) {
      return CinematicResult.UNKNOWN_SCENE;
    }
    dummies.put(
        normalized,
        new Dummy(dummy.dummyId(), dummy.playerId(), dummy.profile(), pose, dummy.entityId(), dummy.shown()));
    if (dummy.shown()) {
      hide(normalized);
      show(normalized);
    }
    return CinematicResult.SUCCESS;
  }

  public Collection<String> list() {
    return List.copyOf(dummies.keySet());
  }

  private int allocateEntityId() {
    return -1 * (dummies.size() + 1);
  }

  private record Dummy(
      String dummyId,
      UUID playerId,
      PlayerProfile profile,
      CameraPose pose,
      Integer entityId,
      boolean shown) {}
}
```

- [ ] **Step 4: Run tests**

`./gradlew test --tests dev.cinematics.paper.PlayerSkinDummyTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/cinematics/paper/PlayerSkinDummy.java src/test/java/dev/cinematics/paper/PlayerSkinDummyTest.java
git commit -m "Add PlayerSkinDummy lifecycle"
```

---

### Task 3: Implement `CameraDolly`

**Files:**
- Create: `src/main/java/dev/cinematics/paper/CameraDolly.java`
- Create: `src/test/java/dev/cinematics/paper/CameraDollyTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CameraDollyTest {

  @Test
  void rejectsNullPlugin() {
    assertThrows(NullPointerException.class, () -> new CameraDolly(null));
  }
}
```

- [ ] **Step 2: Run test**

Expected: compile failure.

- [ ] **Step 3: Implement `CameraDolly`**

```java
package dev.cinematics.paper;

import dev.cinematics.api.CameraPose;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CameraDolly {

  private final Plugin plugin;

  public CameraDolly(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public void apply(Player player, CameraPose pose) {
    Location location = PaperCinematicController.locationOf(pose);
    if (location == null) {
      return;
    }
    for (Player viewer : player.getServer().getOnlinePlayers()) {
      if (!viewer.equals(player)) {
        viewer.hidePlayer(plugin, player);
      }
    }
    player.teleport(location);
  }

  public void restore(Player player, CameraPose pose) {
    Location location = PaperCinematicController.locationOf(pose);
    if (location != null) {
      player.teleport(location);
    }
    for (Player viewer : player.getServer().getOnlinePlayers()) {
      if (!viewer.equals(player)) {
        viewer.showPlayer(plugin, player);
      }
    }
  }
}
```

- [ ] **Step 4: Run tests**

`./gradlew test --tests dev.cinematics.paper.CameraDollyTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/cinematics/paper/CameraDolly.java src/test/java/dev/cinematics/paper/CameraDollyTest.java
git commit -m "Add CameraDolly apply/restore"
```

---

### Task 4: Wire classes and add commands

**Files:**
- Modify: `src/main/java/dev/cinematics/CinematicsPlugin.java`
- Modify: `src/main/java/dev/cinematics/paper/PaperCinematicController.java`
- Modify: `src/main/java/dev/cinematics/paper/CinematicCommand.java`
- Modify: `src/test/java/dev/cinematics/paper/CinematicCommandTest.java`

- [ ] **Step 1: Wire in `CinematicsPlugin`**

```java
FakePlayerPackets packets = new FakePlayerPackets();
PlayerSkinDummy dummies = new PlayerSkinDummy(packets);
CameraDolly dolly = new CameraDolly(this);
CinematicCommand command =
    new CinematicCommand(cinematicService, experienceService, cinematicController, dummies, dolly);
```

- [ ] **Step 2: Update `PaperCinematicController` close/quit**

Hide all shown dummies and restore player visibility in `close()` and `onQuit`.

- [ ] **Step 3: Extend `CinematicCommand`**

Add actions `DUMMY_CREATE`, `DUMMY_SHOW`, `DUMMY_HIDE`, `DUMMY_DESTROY`, `DUMMY_LIST`, `DOLLY`.

Dispatch:
- `dummy create <name> [player]` -> `dummies.create(name, targetPlayer)`
- `dummy show <name>` -> `dummies.show(name)`
- `dummy hide <name>` -> `dummies.hide(name)`
- `dummy destroy <name>` -> `dummies.destroy(name)`
- `dummy list` -> send `dummies.list()`
- `dolly <player> <x> <y> <z> <yaw> <pitch>` -> `dolly.apply(targetPlayer, new CameraPose(world, x, y, z, yaw, pitch))`

- [ ] **Step 4: Add parse tests**

Add the following assertions to `src/test/java/dev/cinematics/paper/CinematicCommandTest`:

```java
@Test
void parsesDummyAndDollyActions() {
  assertEquals(
      CinematicCommand.Action.DUMMY_CREATE,
      CinematicCommand.parseAction(new String[] {"dummy", "create", "hero"}));
  assertEquals(
      CinematicCommand.Action.DUMMY_SHOW,
      CinematicCommand.parseAction(new String[] {"dummy", "show", "hero"}));
  assertEquals(
      CinematicCommand.Action.DUMMY_HIDE,
      CinematicCommand.parseAction(new String[] {"dummy", "hide", "hero"}));
  assertEquals(
      CinematicCommand.Action.DUMMY_DESTROY,
      CinematicCommand.parseAction(new String[] {"dummy", "destroy", "hero"}));
  assertEquals(
      CinematicCommand.Action.DUMMY_LIST,
      CinematicCommand.parseAction(new String[] {"dummy", "list"}));
  assertEquals(
      CinematicCommand.Action.DOLLY,
      CinematicCommand.parseAction(new String[] {"dolly", "jlo", "0", "80", "0", "90", "0"}));
  assertEquals(
      CinematicCommand.Action.UNKNOWN, CinematicCommand.parseAction(new String[] {"dummy"}));
  assertEquals(
      CinematicCommand.Action.UNKNOWN, CinematicCommand.parseAction(new String[] {"dolly"}));
}

@Test
void suggestionsIncludeDummyAndDolly() {
  List<String> all = CinematicCommand.suggestions("");
  assertTrue(all.containsAll(List.of("dummy", "dolly")));
  assertEquals(List.of("dummy"), CinematicCommand.suggestions("du"));
  assertEquals(List.of("dolly"), CinematicCommand.suggestions("do"));
}
```

- [ ] **Step 5: Run tests**

`./gradlew test --tests dev.cinematics.paper.CinematicCommandTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git commit -m "Wire dummy/dolly classes and add commands"
```

---

### Task 5: Integration test and final build

- [ ] **Step 1: Run local server**

`./gradlew runServer`

- [ ] **Step 2: Manual integration test**

Join with two clients and run:
```text
/cinematic dummy create hero
/cinematic dummy show hero
/cinematic dolly <yourname> 0 80 0 90 0
/cinematic dummy hide hero
/cinematic dummy destroy hero
```

Acceptance: second client sees `hero` with your skin while your camera teleports to (0, 80, 0, 90, 0); `hide` removes entity; `destroy` removes entry.

- [ ] **Step 3: Full build**

`./gradlew test check`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git commit -m "Complete Slice 1: player-skin dummy and camera dolly"
```

---

## Self-review

1. **Spec coverage:** Slice 1 standalone renderer and commands are covered; Slice 2/3 intentionally excluded.
2. **Placeholder scan:** Task 1 contains an intentional bounded spike; exact PacketEvents calls are verified in-game before implementation proceeds.
3. **Type consistency:** `PlayerSkinDummy` receives `FakePlayerPackets`; `CameraDolly` receives `Plugin`; `CinematicCommand` receives the new services.
