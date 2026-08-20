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
}
