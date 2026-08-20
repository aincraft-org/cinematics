package dev.cinematics.api;

import java.util.Optional;

/**
 * A node in an experience scene graph. Playback sits in one frame, then follows {@link #next()}.
 */
public sealed interface ExperienceFrame permits PathFrame, FadeFrame {

  /** Unique inside the parent experience. Normalized {@code [a-z0-9_-]}. */
  String id();

  /** Successor frame id, or empty at the end of the walk. */
  Optional<String> next();
}
