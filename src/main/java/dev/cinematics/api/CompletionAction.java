package dev.cinematics.api;

/** What happens to the player when an experience ends (not on quit — quit always restores). */
public enum CompletionAction {
  /** Return to the pre-play pose. */
  RESTORE,
  /** Leave the player at the last sampled camera pose. */
  TELEPORT
}
