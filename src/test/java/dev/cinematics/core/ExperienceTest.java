package dev.cinematics.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cinematics.api.Audience;
import dev.cinematics.api.CompletionAction;
import dev.cinematics.api.Experience;
import dev.cinematics.api.FadeFrame;
import dev.cinematics.api.PathFrame;
import dev.cinematics.api.TimelineBeat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Experience load rules: names, non-empty unique beats, timeline scene references. */
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
            List.of(
                new TimelineBeat("Fade", "Intro"), new TimelineBeat("flyover", "overworld_path")));
    assertEquals("first-join", experience.name());
    assertEquals(Audience.PUBLIC, experience.audience());
    assertEquals(CompletionAction.TELEPORT, experience.onComplete());
    assertEquals(2, experience.beats().size());
    assertEquals("fade", experience.beats().getFirst().id());
    assertEquals("intro", ((TimelineBeat) experience.beats().getFirst()).sceneName());
  }

  @Test
  void loadRejectsInvalidExperienceName() {
    TimelineBeat beat = new TimelineBeat("flyover", "intro");
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Experience.load(
                    "no spaces allowed",
                    Audience.SUBJECT,
                    CompletionAction.RESTORE,
                    List.of(beat)));
    assertTrue(thrown.getMessage().toLowerCase(Locale.ROOT).contains("name"));
  }

  @Test
  void loadCompilesBeatsIntoAPathFrameChain() {
    Experience experience =
        Experience.load(
            "join",
            Audience.SUBJECT,
            CompletionAction.RESTORE,
            List.of(new TimelineBeat("flyover", "intro"), new TimelineBeat("land", "outro")));
    assertEquals("flyover", experience.entry());
    assertEquals(2, experience.frames().size());
    PathFrame first = (PathFrame) experience.frames().getFirst();
    assertEquals("intro", first.sceneName());
    assertEquals("land", first.next().orElseThrow());
    PathFrame last = (PathFrame) experience.frames().getLast();
    assertTrue(last.next().isEmpty());
  }

  @Test
  void graphRejectsUnknownNextAndCycles() {
    PathFrame loop = new PathFrame("a", "intro", java.util.Optional.of("a"));
    IllegalArgumentException cycle =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Experience.graph(
                    "join", Audience.SUBJECT, CompletionAction.RESTORE, "a", List.of(loop)));
    assertTrue(cycle.getMessage().toLowerCase(Locale.ROOT).contains("cycle"));

    FadeFrame dangling = new FadeFrame("fade", "darkness", 2.0, java.util.Optional.of("missing"));
    IllegalArgumentException next =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                Experience.graph(
                    "join", Audience.SUBJECT, CompletionAction.RESTORE, "fade", List.of(dangling)));
    assertTrue(next.getMessage().toLowerCase(Locale.ROOT).contains("next"));
  }
}
