package dev.cinematics.api;

/** One step in a named experience. Slice 1 ships {@link TimelineBeat} only. */
public sealed interface ExperienceBeat permits TimelineBeat {

  /** Unique inside the parent experience. Normalized {@code [a-z0-9_-]}. */
  String id();
}
