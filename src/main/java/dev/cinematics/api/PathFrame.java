package dev.cinematics.api;

import java.util.Optional;

/**
 * Plays a named {@link CinematicScene} (keyframe polyline through world points) then follows {@code
 * next}.
 */
public record PathFrame(String id, String sceneName, Optional<String> next)
    implements ExperienceFrame {

  public PathFrame {
    String normalizedId = CinematicScene.normalizeName(id);
    if (normalizedId == null) {
      throw new IllegalArgumentException("frame id must be 1–64 [a-z0-9_-] characters");
    }
    String normalizedScene = CinematicScene.normalizeName(sceneName);
    if (normalizedScene == null) {
      throw new IllegalArgumentException("scene name must be 1–64 [a-z0-9_-] characters");
    }
    id = normalizedId;
    sceneName = normalizedScene;
    next = normalizeNext(next);
  }

  static Optional<String> normalizeNext(Optional<String> next) {
    if (next == null || next.isEmpty()) {
      return Optional.empty();
    }
    String normalized = CinematicScene.normalizeName(next.get());
    if (normalized == null) {
      throw new IllegalArgumentException("next frame id must be 1–64 [a-z0-9_-] characters");
    }
    return Optional.of(normalized);
  }
}
