package dev.cinematics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cinematics.api.Audience;
import dev.cinematics.api.CameraKeyframe;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.CompletionAction;
import dev.cinematics.api.Experience;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.api.ExperienceSnapshot;
import dev.cinematics.api.OverlayCue;
import dev.cinematics.api.PropCue;
import dev.cinematics.api.TimelineBeat;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One-beat experience playback matches scene sampling; start resolves scenes and shares the
 * exclusive play slot.
 */
class ExperienceServiceTest {

  @TempDir Path tempDir;

  private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000021");

  private DefaultCinematicService newCinematic() {
    return new DefaultCinematicService(new JsonCinematicRepository(tempDir));
  }

  private static CameraPose pose(String world, double x, double y, double z) {
    return new CameraPose(world, x, y, z, 0f, 0f);
  }

  private static CinematicScene twoPointScene() {
    return CinematicScene.load(
        "intro",
        List.of(
            new CameraKeyframe(0, pose("world", 0, 64, 0)),
            new CameraKeyframe(10, pose("world", 10, 64, 20))),
        List.of(new OverlayCue("darkness", 2, 6)),
        List.of(new PropCue("oak_sign", 3, 8, pose("world", 1, 64, 1))));
  }

  private static Experience oneBeatJoin() {
    return Experience.load(
        "join",
        Audience.SUBJECT,
        CompletionAction.RESTORE,
        List.of(new TimelineBeat("flyover", "intro")));
  }

  @Test
  void oneBeatExperienceSamplesLikeTheSceneThenStopRestores() {
    DefaultCinematicService cinematic = newCinematic();
    assertEquals(CinematicResult.SUCCESS, cinematic.save(twoPointScene()));
    ExperienceService experiences = cinematic.experiences();
    assertEquals(CinematicResult.SUCCESS, experiences.save(oneBeatJoin()));

    CameraPose original = pose("world", 100, 70, 100);
    assertEquals(CinematicResult.SUCCESS, experiences.start(playerId, "join", original));

    ExperienceSnapshot mid = experiences.sample(playerId, 4.0).orElseThrow();
    assertTrue(mid.playing());
    assertEquals(0, mid.beatIndex());
    assertEquals("flyover", mid.beatId());
    assertEquals(Audience.SUBJECT, mid.audience());
    assertTrue(mid.shaders().contains("darkness"));
    assertFalse(mid.props().isEmpty());
    assertTrue(mid.pose().x() > 0 && mid.pose().x() < 10);

    ExperienceSnapshot stopped = experiences.stop(playerId).orElseThrow();
    assertFalse(stopped.playing());
    assertEquals(original, stopped.pose());
    assertTrue(stopped.shaders().isEmpty());
    assertTrue(stopped.props().isEmpty());
    assertTrue(experiences.sample(playerId, 4.0).isEmpty());
  }

  @Test
  void sampleAtDurationCompletesAndRestores() {
    DefaultCinematicService cinematic = newCinematic();
    cinematic.save(twoPointScene());
    ExperienceService experiences = cinematic.experiences();
    experiences.save(oneBeatJoin());

    CameraPose original = pose("world", 50, 80, 50);
    assertEquals(CinematicResult.SUCCESS, experiences.start(playerId, "join", original));

    ExperienceSnapshot done = experiences.sample(playerId, 10.0).orElseThrow();
    assertFalse(done.playing());
    assertEquals(original, done.pose());
    assertTrue(done.shaders().isEmpty());
    assertTrue(done.props().isEmpty());
    assertTrue(experiences.sample(playerId, 0).isEmpty());
  }

  @Test
  void startRejectsUnknownExperienceAndMissingScene() {
    DefaultCinematicService cinematic = newCinematic();
    ExperienceService experiences = cinematic.experiences();
    CameraPose original = pose("world", 1, 64, 1);

    assertEquals(CinematicResult.UNKNOWN_EXPERIENCE, experiences.start(playerId, "join", original));

    assertEquals(CinematicResult.SUCCESS, experiences.save(oneBeatJoin()));
    assertEquals(CinematicResult.UNKNOWN_SCENE, experiences.start(playerId, "join", original));
  }

  @Test
  void twoBeatExperienceAdvancesAtTheFirstDuration() {
    DefaultCinematicService cinematic = newCinematic();
    cinematic.save(twoPointScene());
    cinematic.save(outroScene());
    ExperienceService experiences = cinematic.experiences();
    experiences.save(twoBeatJoin());

    CameraPose original = pose("world", 50, 80, 50);
    assertEquals(CinematicResult.SUCCESS, experiences.start(playerId, "join", original));

    ExperienceSnapshot first = experiences.sample(playerId, 4.0).orElseThrow();
    assertTrue(first.playing());
    assertEquals(0, first.beatIndex());
    assertEquals("flyover", first.beatId());
    assertTrue(first.pose().x() > 0 && first.pose().x() < 10);

    ExperienceSnapshot secondStart = experiences.sample(playerId, 10.0).orElseThrow();
    assertTrue(secondStart.playing());
    assertEquals(1, secondStart.beatIndex());
    assertEquals("land", secondStart.beatId());
    assertEquals(10.0, secondStart.pose().x(), 1e-9);
    assertEquals(20.0, secondStart.pose().z(), 1e-9);

    ExperienceSnapshot secondMid = experiences.sample(playerId, 12.0).orElseThrow();
    assertTrue(secondMid.playing());
    assertEquals(1, secondMid.beatIndex());
    assertTrue(secondMid.pose().x() > 10 && secondMid.pose().x() < 20);

    ExperienceSnapshot done = experiences.sample(playerId, 15.0).orElseThrow();
    assertFalse(done.playing());
    assertEquals(original, done.pose());
    assertTrue(experiences.sample(playerId, 0).isEmpty());
  }

  @Test
  void cinematicSamplePlaybackReadsExperienceSessions() {
    DefaultCinematicService cinematic = newCinematic();
    cinematic.save(twoPointScene());
    ExperienceService experiences = cinematic.experiences();
    experiences.save(oneBeatJoin());
    CameraPose original = pose("world", 100, 70, 100);
    assertEquals(CinematicResult.SUCCESS, experiences.start(playerId, "join", original));

    var mid = cinematic.samplePlayback(playerId, 4.0).orElseThrow();
    assertTrue(mid.playing());
    assertTrue(mid.shaders().contains("darkness"));
    assertFalse(mid.props().isEmpty());
  }

  @Test
  void addTimelineBeatCreatesOrAppendsAnExperience() {
    DefaultCinematicService cinematic = newCinematic();
    ExperienceService experiences = cinematic.experiences();
    assertEquals(
        CinematicResult.SUCCESS,
        experiences.addTimelineBeat("join", new TimelineBeat("flyover", "intro")));
    Experience created = experiences.experience("join").orElseThrow();
    assertEquals(1, created.beats().size());
    assertEquals(
        CinematicResult.SUCCESS,
        experiences.addTimelineBeat("join", new TimelineBeat("land", "outro")));
    assertEquals(2, experiences.experience("join").orElseThrow().beats().size());
  }

  @Test
  void savedExperienceReloadsFromTheRepository() {
    Path scenesDir = tempDir.resolve("scenes");
    Path experiencesDir = tempDir.resolve("experiences");
    DefaultCinematicService writer =
        new DefaultCinematicService(
            new JsonCinematicRepository(scenesDir), new JsonExperienceRepository(experiencesDir));
    assertEquals(CinematicResult.SUCCESS, writer.save(twoPointScene()));
    Experience join = oneBeatJoin();
    assertEquals(CinematicResult.SUCCESS, writer.experiences().save(join));

    DefaultCinematicService reader =
        new DefaultCinematicService(
            new JsonCinematicRepository(scenesDir), new JsonExperienceRepository(experiencesDir));
    Experience loaded = reader.experiences().experience("join").orElseThrow();
    assertEquals(join, loaded);
    assertEquals(Audience.SUBJECT, loaded.audience());
    assertEquals(CompletionAction.RESTORE, loaded.onComplete());
    assertEquals("flyover", loaded.beats().getFirst().id());
    assertEquals("intro", ((TimelineBeat) loaded.beats().getFirst()).sceneName());
  }

  @Test
  void corruptedExperienceFileDoesNotFailLoad() throws Exception {
    Path experiencesDir = tempDir.resolve("experiences");
    java.nio.file.Files.createDirectories(experiencesDir);
    java.nio.file.Files.writeString(experiencesDir.resolve("join.json"), "{not-json");
    DefaultCinematicService service =
        new DefaultCinematicService(
            new JsonCinematicRepository(tempDir.resolve("scenes")),
            new JsonExperienceRepository(experiencesDir));
    assertTrue(service.experiences().experience("join").isEmpty());
  }

  @Test
  void scenePlayAndExperienceStartShareTheExclusiveSlot() {
    DefaultCinematicService cinematic = newCinematic();
    cinematic.save(twoPointScene());
    ExperienceService experiences = cinematic.experiences();
    experiences.save(oneBeatJoin());
    CameraPose original = pose("world", 1, 64, 1);

    assertEquals(CinematicResult.SUCCESS, cinematic.play(playerId, "intro", original));
    assertEquals(CinematicResult.ALREADY_PLAYING, experiences.start(playerId, "join", original));
    assertTrue(cinematic.stop(playerId).isPresent());

    assertEquals(CinematicResult.SUCCESS, experiences.start(playerId, "join", original));
    assertEquals(CinematicResult.ALREADY_PLAYING, cinematic.play(playerId, "intro", original));
  }

  private static CinematicScene outroScene() {
    return CinematicScene.load(
        "outro",
        List.of(
            new CameraKeyframe(0, pose("world", 10, 64, 20)),
            new CameraKeyframe(5, pose("world", 20, 64, 20))),
        List.of(),
        List.of());
  }

  private static Experience twoBeatJoin() {
    return Experience.load(
        "join",
        Audience.SUBJECT,
        CompletionAction.RESTORE,
        List.of(new TimelineBeat("flyover", "intro"), new TimelineBeat("land", "outro")));
  }
}
