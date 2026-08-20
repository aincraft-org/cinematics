package dev.cinematics.api;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Overlay-only frame for opening/closing transitions. Completes at {@code durationSeconds}. Camera
 * during the fade is the next path's first pose when there is one.
 */
public record FadeFrame(String id, String overlayId, double durationSeconds, Optional<String> next)
    implements ExperienceFrame {

  public FadeFrame {
    String normalizedId = CinematicScene.normalizeName(id);
    if (normalizedId == null) {
      throw new IllegalArgumentException("frame id must be 1–64 [a-z0-9_-] characters");
    }
    Objects.requireNonNull(overlayId, "overlayId");
    String overlay = overlayId.trim().toLowerCase(Locale.ROOT);
    if (overlay.isEmpty()) {
      throw new IllegalArgumentException("overlayId must not be blank");
    }
    if (!Double.isFinite(durationSeconds) || durationSeconds <= 0.0) {
      throw new IllegalArgumentException("fade duration must be finite and > 0");
    }
    id = normalizedId;
    overlayId = overlay;
    next = PathFrame.normalizeNext(next);
  }
}
