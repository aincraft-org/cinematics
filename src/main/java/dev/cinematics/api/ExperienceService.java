package dev.cinematics.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public surface for named experiences: ordered beats sampled for a per-player playback session.
 *
 * <p>Types are Bukkit-free. Scene play and experience start share the exclusive session slot.
 */
public interface ExperienceService {

  /**
   * Persists a complete experience (one or more beats). Replaces any experience of the same name.
   */
  CinematicResult save(Experience experience);

  /** The complete experience for {@code name}, if present. */
  Optional<Experience> experience(String name);

  /** Experiences in name order. */
  Collection<Experience> experiences();

  /**
   * Starts {@code experienceName} for {@code playerId}, recording {@code currentPose} to restore on
   * stop or completion. Rejects a second start/play while one is active. Timeline beats must
   * resolve to complete scenes at start.
   */
  CinematicResult start(UUID playerId, String experienceName, CameraPose currentPose);

  /**
   * Stops playback for {@code playerId} and returns the restored pre-play pose with no overlays or
   * props. Empty when the player is not playing.
   */
  Optional<ExperienceSnapshot> stop(UUID playerId);

  /**
   * Samples the active experience at elapsed seconds from start. At or after total timeline
   * duration the session completes and the restored pre-play pose is returned. Empty when the
   * player is not in an experience.
   */
  Optional<ExperienceSnapshot> sample(UUID playerId, double elapsedSeconds);
}
