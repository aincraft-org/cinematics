package dev.cinematics.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Named out-of-body flow: an audience, a completion action, and a scene graph of frames.
 *
 * <p>{@link #load} compiles a beat list into a PathFrame chain. {@link #graph} takes frames and
 * {@code next} edges directly. Duplicate ids, unknown {@code next}, and cycles from {@code entry}
 * are rejected.
 */
public final class Experience {

  private final String name;
  private final Audience audience;
  private final CompletionAction onComplete;
  private final String entry;
  private final List<ExperienceFrame> frames;

  private Experience(
      String name,
      Audience audience,
      CompletionAction onComplete,
      String entry,
      List<ExperienceFrame> frames) {
    this.name = name;
    this.audience = audience;
    this.onComplete = onComplete;
    this.entry = entry;
    this.frames = frames;
  }

  /**
   * Builds a chain of PathFrames from timeline beats. Beat ids must be unique. Names follow {@link
   * CinematicScene} rules.
   */
  public static Experience load(
      String name, Audience audience, CompletionAction onComplete, List<ExperienceBeat> beats) {
    Objects.requireNonNull(beats, "beats");
    if (beats.isEmpty()) {
      throw new IllegalArgumentException("experience requires at least one beat");
    }
    List<ExperienceFrame> chain = new ArrayList<>(beats.size());
    for (int i = 0; i < beats.size(); i++) {
      ExperienceBeat beat = Objects.requireNonNull(beats.get(i), "beat");
      if (!(beat instanceof TimelineBeat timeline)) {
        throw new IllegalArgumentException("load() only accepts timeline beats");
      }
      Optional<String> next =
          i + 1 < beats.size() ? Optional.of(beats.get(i + 1).id()) : Optional.empty();
      chain.add(new PathFrame(timeline.id(), timeline.sceneName(), next));
    }
    return graph(name, audience, onComplete, chain.getFirst().id(), chain);
  }

  /**
   * Builds an experience from an explicit frame graph. {@code entry} must name a frame. Each {@code
   * next} must name a frame in this graph or be empty.
   */
  public static Experience graph(
      String name,
      Audience audience,
      CompletionAction onComplete,
      String entry,
      List<ExperienceFrame> frames) {
    String normalized = CinematicScene.normalizeName(name);
    if (normalized == null) {
      throw new IllegalArgumentException("experience name must be 1–64 [a-z0-9_-] characters");
    }
    Objects.requireNonNull(audience, "audience");
    Objects.requireNonNull(onComplete, "onComplete");
    Objects.requireNonNull(frames, "frames");
    if (frames.isEmpty()) {
      throw new IllegalArgumentException("experience requires at least one beat");
    }
    String entryId = CinematicScene.normalizeName(entry);
    if (entryId == null) {
      throw new IllegalArgumentException("entry frame id must be 1–64 [a-z0-9_-] characters");
    }
    List<ExperienceFrame> copy = new ArrayList<>(frames.size());
    Map<String, ExperienceFrame> byId = new HashMap<>();
    for (ExperienceFrame frame : frames) {
      Objects.requireNonNull(frame, "frame");
      if (byId.put(frame.id(), frame) != null) {
        throw new IllegalArgumentException("duplicate beat id: " + frame.id());
      }
      copy.add(frame);
    }
    if (!byId.containsKey(entryId)) {
      throw new IllegalArgumentException("unknown entry frame: " + entryId);
    }
    for (ExperienceFrame frame : copy) {
      frame
          .next()
          .ifPresent(
              nextId -> {
                if (!byId.containsKey(nextId)) {
                  throw new IllegalArgumentException("unknown next frame: " + nextId);
                }
              });
    }
    Set<String> seen = new HashSet<>();
    String current = entryId;
    while (current != null) {
      if (!seen.add(current)) {
        throw new IllegalArgumentException("cycle in experience frame graph");
      }
      current = byId.get(current).next().orElse(null);
    }
    return new Experience(normalized, audience, onComplete, entryId, List.copyOf(copy));
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

  /** Id of the first frame in the walk. */
  public String entry() {
    return entry;
  }

  public List<ExperienceFrame> frames() {
    return frames;
  }

  /** Path frames in walk order from {@link #entry()}, as timeline beats (authoring sugar). */
  public List<ExperienceBeat> beats() {
    List<ExperienceBeat> beats = new ArrayList<>();
    for (ExperienceFrame frame : walk()) {
      if (frame instanceof PathFrame path) {
        beats.add(new TimelineBeat(path.id(), path.sceneName()));
      }
    }
    return List.copyOf(beats);
  }

  /** Frames from {@link #entry()} following {@code next} until the tail. */
  public List<ExperienceFrame> walk() {
    Map<String, ExperienceFrame> byId = new HashMap<>();
    for (ExperienceFrame frame : frames) {
      byId.put(frame.id(), frame);
    }
    List<ExperienceFrame> order = new ArrayList<>();
    String current = entry;
    while (current != null) {
      ExperienceFrame frame = byId.get(current);
      if (frame == null) {
        break;
      }
      order.add(frame);
      current = frame.next().orElse(null);
    }
    return List.copyOf(order);
  }

  /**
   * Appends a path frame onto the current tail (the walked frame with empty {@code next}). Creates
   * a one-frame graph when this experience has no frames.
   */
  public Experience withTimelineBeat(TimelineBeat beat) {
    Objects.requireNonNull(beat, "beat");
    List<ExperienceFrame> nextFrames = new ArrayList<>();
    boolean linked = false;
    for (ExperienceFrame frame : frames) {
      if (frame.next().isEmpty() && !linked) {
        nextFrames.add(withNext(frame, beat.id()));
        linked = true;
      } else {
        nextFrames.add(frame);
      }
    }
    nextFrames.add(new PathFrame(beat.id(), beat.sceneName(), Optional.empty()));
    String entryId = frames.isEmpty() ? beat.id() : entry;
    return graph(name, audience, onComplete, entryId, nextFrames);
  }

  private static ExperienceFrame withNext(ExperienceFrame frame, String nextId) {
    Optional<String> next = Optional.of(nextId);
    return switch (frame) {
      case PathFrame path -> new PathFrame(path.id(), path.sceneName(), next);
      case FadeFrame fade ->
          new FadeFrame(fade.id(), fade.overlayId(), fade.durationSeconds(), next);
    };
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
        && entry.equals(that.entry)
        && frames.equals(that.frames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, audience, onComplete, entry, frames);
  }

  @Override
  public String toString() {
    return "Experience{name=" + name + ", frames=" + frames.size() + '}';
  }
}
