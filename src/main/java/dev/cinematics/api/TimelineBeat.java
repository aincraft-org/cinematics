package dev.cinematics.api;

/**
 * Plays a named {@link CinematicScene} to completion (or skip).
 *
 * <p>{@code id} and {@code sceneName} use the same name rules as scenes.
 */
public record TimelineBeat(String id, String sceneName) implements ExperienceBeat {

  public TimelineBeat {
    String normalizedId = CinematicScene.normalizeName(id);
    if (normalizedId == null) {
      throw new IllegalArgumentException("beat id must be 1–64 [a-z0-9_-] characters");
    }
    String normalizedScene = CinematicScene.normalizeName(sceneName);
    if (normalizedScene == null) {
      throw new IllegalArgumentException("scene name must be 1–64 [a-z0-9_-] characters");
    }
    id = normalizedId;
    sceneName = normalizedScene;
  }
}
