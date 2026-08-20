package dev.cinematics.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Named out-of-body flow: an audience, a completion action, and one or more beats.
 *
 * <p>Load rejects empty beat lists and duplicate beat ids. Names follow {@link CinematicScene}
 * rules.
 */
public final class Experience {

  private final String name;
  private final Audience audience;
  private final CompletionAction onComplete;
  private final List<ExperienceBeat> beats;

  private Experience(
      String name, Audience audience, CompletionAction onComplete, List<ExperienceBeat> beats) {
    this.name = name;
    this.audience = audience;
    this.onComplete = onComplete;
    this.beats = beats;
  }

  /**
   * Builds a complete experience. Beat ids must be unique. Names are trimmed and lowercased; only
   * {@code [a-z0-9_-]} of length 1–64 are accepted.
   */
  public static Experience load(
      String name, Audience audience, CompletionAction onComplete, List<ExperienceBeat> beats) {
    String normalized = CinematicScene.normalizeName(name);
    if (normalized == null) {
      throw new IllegalArgumentException("experience name must be 1–64 [a-z0-9_-] characters");
    }
    Objects.requireNonNull(audience, "audience");
    Objects.requireNonNull(onComplete, "onComplete");
    Objects.requireNonNull(beats, "beats");
    if (beats.isEmpty()) {
      throw new IllegalArgumentException("experience requires at least one beat");
    }
    List<ExperienceBeat> copy = new ArrayList<>(beats.size());
    Set<String> ids = new HashSet<>();
    for (ExperienceBeat beat : beats) {
      Objects.requireNonNull(beat, "beat");
      if (!ids.add(beat.id())) {
        throw new IllegalArgumentException("duplicate beat id: " + beat.id());
      }
      copy.add(beat);
    }
    return new Experience(normalized, audience, onComplete, List.copyOf(copy));
  }

  public String name() {
    return name;
  }

  public Audience audience() {
    return audience;
  }

  public CompletionAction onComplete() {
    return onComplete;
  }

  public List<ExperienceBeat> beats() {
    return beats;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Experience that)) {
      return false;
    }
    return name.equals(that.name)
        && audience == that.audience
        && onComplete == that.onComplete
        && beats.equals(that.beats);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, audience, onComplete, beats);
  }

  @Override
  public String toString() {
    return "Experience{name=" + name + ", beats=" + beats.size() + '}';
  }
}
