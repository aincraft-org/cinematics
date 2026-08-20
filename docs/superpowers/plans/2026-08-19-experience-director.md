# Experience Director (Slice 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add named experiences that sequence existing timeline scenes, sharing the exclusive per-player playback session, with JSON persistence and `/cinematic experience` commands.

**Architecture:** Keep `CinematicService` for scene authoring/play. Add Bukkit-free `Experience` / `TimelineBeat` / `ExperienceService` in `api`, persist under `experiences/`, and let `DefaultCinematicService` implement both interfaces so one session map rejects a second play. Paper registers the new SPI and additive commands. Studio/transition beats are not in this slice.

**Tech Stack:** Java 25, JUnit 5, Paper 1.21 (compileOnly), existing hand-rolled JSON, Gradle.

**Spec:** `docs/superpowers/specs/2026-08-19-experience-director-design.md`

---

### Task 1: Experience model

**Files:**
- Create: `src/main/java/dev/cinematics/api/Audience.java`
- Create: `src/main/java/dev/cinematics/api/CompletionAction.java`
- Create: `src/main/java/dev/cinematics/api/ExperienceBeat.java`
- Create: `src/main/java/dev/cinematics/api/TimelineBeat.java`
- Create: `src/main/java/dev/cinematics/api/Experience.java`
- Modify: `src/main/java/dev/cinematics/api/CinematicResult.java`
- Test: `src/test/java/dev/cinematics/core/ExperienceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.cinematics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cinematics.api.Audience;
import dev.cinematics.api.CompletionAction;
import dev.cinematics.api.Experience;
import dev.cinematics.api.TimelineBeat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ExperienceTest {

  @Test
  void loadRejectsEmptyBeatsAndDuplicateIds() {
    IllegalArgumentException empty =
        assertThrows(
            IllegalArgumentException.class,
            () -> Experience.load("join", Audience.SUBJECT, CompletionAction.RESTORE, List.of()));
    assertTrue(empty.getMessage().toLowerCase(Locale.ROOT).contains("beat"));

    TimelineBeat flyover = new TimelineBeat("flyover", "intro");
    IllegalArgumentException duplicate =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Experience.load(
                    "join",
                    Audience.SUBJECT,
                    CompletionAction.RESTORE,
                    List.of(flyover, new TimelineBeat("flyover", "outro"))));
    assertTrue(duplicate.getMessage().toLowerCase(Locale.ROOT).contains("duplicate"));
  }

  @Test
  void loadNormalizesNameAndKeepsBeatOrder() {
    Experience experience =
        Experience.load(
            " First-Join ",
            Audience.PUBLIC,
            CompletionAction.TELEPORT,
            List.of(new TimelineBeat("Fade", "Intro"), new TimelineBeat("flyover", "overworld_path")));
    assertEquals("first-join", experience.name());
    assertEquals(Audience.PUBLIC, experience.audience());
    assertEquals(CompletionAction.TELEPORT, experience.onComplete());
    assertEquals(2, experience.beats().size());
    assertEquals("fade", experience.beats().getFirst().id());
    assertEquals("intro", ((TimelineBeat) experience.beats().getFirst()).sceneName());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests dev.cinematics.core.ExperienceTest`

Expected: compile failure (`Experience` / `Audience` not found) or test failure.

- [ ] **Step 3: Write minimal implementation**

Add `UNKNOWN_EXPERIENCE`, `EMPTY_EXPERIENCE`, `NOT_PLAYING` to `CinematicResult`.

`Audience`: `SUBJECT`, `SPECTATORS`, `PUBLIC`.

`CompletionAction`: `RESTORE`, `TELEPORT`.

`ExperienceBeat` sealed interface with `String id()`, permits `TimelineBeat`.

`TimelineBeat` record `(String id, String sceneName)` — normalize both with `CinematicScene.normalizeName`; throw if either is null.

`Experience.load` — normalize name; reject null/empty beats; reject duplicate ids; copy the beat list.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests dev.cinematics.core.ExperienceTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/cinematics/api/Audience.java \
  src/main/java/dev/cinematics/api/CompletionAction.java \
  src/main/java/dev/cinematics/api/ExperienceBeat.java \
  src/main/java/dev/cinematics/api/TimelineBeat.java \
  src/main/java/dev/cinematics/api/Experience.java \
  src/main/java/dev/cinematics/api/CinematicResult.java \
  src/test/java/dev/cinematics/core/ExperienceTest.java
git commit -m "Add experience model with timeline beats"
```

---

### Task 2: One-beat experience playback

**Files:**
- Create: `src/main/java/dev/cinematics/api/ExperienceSnapshot.java`
- Create: `src/main/java/dev/cinematics/api/ExperienceService.java`
- Modify: `src/main/java/dev/cinematics/core/DefaultCinematicService.java`
- Test: `src/test/java/dev/cinematics/core/ExperienceServiceTest.java`

- [ ] **Step 1: Write the failing test**

A saved two-point scene plus a one-beat experience named `join` referencing that scene:

- `start` then `sample(4)` matches scene sample at 4 (playing, beat 0, beat id `flyover`)
- `stop` restores original pose and clears cues
- `sample` at duration restores and clears the session
- `start` of unknown experience → `UNKNOWN_EXPERIENCE`
- `start` when scene missing → `UNKNOWN_SCENE`

Drive through `DefaultCinematicService` as `ExperienceService`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests dev.cinematics.core.ExperienceServiceTest`

Expected: compile failure or assertion failure.

- [ ] **Step 3: Write minimal implementation**

`ExperienceService`: `save`, `experience`, `experiences`, `start`, `stop`, `sample`.

`ExperienceSnapshot` mirrors `PlaybackSnapshot` plus `audience`, `beatIndex`, `beatId`.

`DefaultCinematicService` implements `ExperienceService`. In-memory map of experiences. `start` occupies the existing session map (generalize `Session` to hold either a scene or an experience + resolved scenes). `sample` for a one-beat experience delegates to `CinematicScene.sample` until duration, then restores.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests dev.cinematics.core.ExperienceServiceTest --tests dev.cinematics.core.CinematicServiceTest`

Expected: PASS (existing play/stop still works)

- [ ] **Step 5: Commit**

```bash
git commit -m "Play one-beat timeline experiences through shared sessions"
```

---

### Task 3: Multi-beat director + exclusive slot

**Files:**
- Modify: `src/main/java/dev/cinematics/core/DefaultCinematicService.java`
- Modify: `src/test/java/dev/cinematics/core/ExperienceServiceTest.java`

- [ ] **Step 1: Write the failing tests**

- Two timeline beats durations 10 and 5: `sample(4)` beat 0; `sample(10)` beat 1 at local 0 still playing; `sample(12)` beat 1 local 2; `sample(15)` completed restore
- Cinematic `play` then experience `start` → `ALREADY_PLAYING` and vice versa

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Map global elapsed onto beats; share session map**

- [ ] **Step 4: Run `./gradlew test`**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "Advance timeline beats by elapsed time and share the play slot"
```

---

### Task 4: Experience JSON persistence

**Files:**
- Create: `src/main/java/dev/cinematics/core/ExperienceRepository.java`
- Create: `src/main/java/dev/cinematics/core/JsonExperienceRepository.java`
- Modify: `src/main/java/dev/cinematics/core/DefaultCinematicService.java`
- Modify: `src/main/java/dev/cinematics/CinematicsPlugin.java`
- Modify: `src/test/java/dev/cinematics/core/ExperienceServiceTest.java`

- [ ] **Step 1: Write the failing test**

Save an experience, construct a new `DefaultCinematicService` on the same temp dir (scenes + experiences subdirs), `experience(name)` equals the original beats/audience.

Corrupt JSON degrades to unknown (no throw on load).

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Hand-rolled JSON under `experiences/<name>.json`; plugin passes `dataDir.resolve("experiences")`**

Constructor: keep `DefaultCinematicService(CinematicRepository)` working by using an in-memory/no-op experience repo, **or** add an overload `DefaultCinematicService(CinematicRepository, ExperienceRepository)` and point existing tests at a temp experiences dir. Prefer overload + existing tests use a temp `JsonExperienceRepository`.

- [ ] **Step 4: Run `./gradlew test`**

- [ ] **Step 5: Commit**

```bash
git commit -m "Persist timeline experiences as JSON"
```

---

### Task 5: Paper SPI and commands

**Files:**
- Modify: `src/main/java/dev/cinematics/CinematicsPlugin.java`
- Modify: `src/main/java/dev/cinematics/paper/CinematicCommand.java`
- Modify: `src/main/java/dev/cinematics/paper/PaperCinematicController.java`
- Modify: `src/test/java/dev/cinematics/paper/CinematicCommandTest.java`
- Modify: `README.md`

- [ ] **Step 1: Write the failing command tests**

`parseAction` recognizes `experience`. Suggestions include `experience`. Nested parse: `experience play`, `experience list`, `experience create`, `experience beat`.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Register `ExperienceService` in Bukkit services. Controller `playExperience(Player, name)` uses `start` + same ticker applying pose/shaders/props (ignore actors). Commands: create, beat add timeline, play, list.**

- [ ] **Step 4: Run `./gradlew test check`**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "Add experience commands and register ExperienceService"
```

---
