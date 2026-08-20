package dev.cinematics.core;

import dev.cinematics.api.Audience;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.CinematicService;
import dev.cinematics.api.Experience;
import dev.cinematics.api.ExperienceBeat;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.api.ExperienceSnapshot;
import dev.cinematics.api.TimelineBeat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory experiences sampled against resolved timeline scenes. Shares {@link PlayerSessions}
 * with scene play.
 */
final class DefaultExperienceService implements ExperienceService {

  private final CinematicService cinematic;
  private final PlayerSessions sessions;
  private final ExperienceRepository repository;
  private final ConcurrentMap<String, Experience> experiences = new ConcurrentHashMap<>();
  private final Object mutationLock = new Object();

  DefaultExperienceService(
      CinematicService cinematic, PlayerSessions sessions, ExperienceRepository repository) {
    this.cinematic = Objects.requireNonNull(cinematic, "cinematic");
    this.sessions = Objects.requireNonNull(sessions, "sessions");
    this.repository = Objects.requireNonNull(repository, "repository");
    for (Experience experience : repository.loadAll()) {
      experiences.put(experience.name(), experience);
    }
  }

  @Override
  public CinematicResult save(Experience experience) {
    Objects.requireNonNull(experience, "experience");
    synchronized (mutationLock) {
      experiences.put(experience.name(), experience);
      repository.save(experience);
      return CinematicResult.SUCCESS;
    }
  }

  @Override
  public Optional<Experience> experience(String name) {
    String normalized = CinematicScene.normalizeName(name);
    if (normalized == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(experiences.get(normalized));
  }

  @Override
  public Collection<Experience> experiences() {
    List<Experience> list = new ArrayList<>(experiences.values());
    list.sort(Comparator.comparing(Experience::name));
    return List.copyOf(list);
  }

  @Override
  public CinematicResult start(UUID playerId, String experienceName, CameraPose currentPose) {
    Objects.requireNonNull(playerId, "playerId");
    Objects.requireNonNull(currentPose, "currentPose");
    String normalized = CinematicScene.normalizeName(experienceName);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Experience experience = experiences.get(normalized);
    if (experience == null) {
      return CinematicResult.UNKNOWN_EXPERIENCE;
    }
    List<CinematicScene> resolved = new ArrayList<>();
    for (ExperienceBeat beat : experience.beats()) {
      if (!(beat instanceof TimelineBeat timeline)) {
        return CinematicResult.UNKNOWN_SCENE;
      }
      Optional<CinematicScene> scene = cinematic.scene(timeline.sceneName());
      if (scene.isEmpty()) {
        return CinematicResult.UNKNOWN_SCENE;
      }
      resolved.add(scene.get());
    }
    return sessions.occupy(
        playerId, new ExperiencePlayback(experience, List.copyOf(resolved), currentPose));
  }

  @Override
  public Optional<ExperienceSnapshot> stop(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    Optional<Playback> stopped = sessions.remove(playerId);
    if (stopped.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(restored(stopped.get()));
  }

  @Override
  public Optional<ExperienceSnapshot> sample(UUID playerId, double elapsedSeconds) {
    Objects.requireNonNull(playerId, "playerId");
    Optional<Playback> current = sessions.get(playerId);
    if (current.isEmpty() || !(current.get() instanceof ExperiencePlayback playback)) {
      return Optional.empty();
    }
    double elapsed = Double.isFinite(elapsedSeconds) ? elapsedSeconds : 0.0;
    double remaining = elapsed;
    List<CinematicScene> scenes = playback.scenes();
    for (int i = 0; i < scenes.size(); i++) {
      CinematicScene scene = scenes.get(i);
      double duration = scene.durationSeconds();
      boolean last = i == scenes.size() - 1;
      if (!last && remaining >= duration) {
        remaining -= duration;
        continue;
      }
      if (last && remaining >= duration) {
        sessions.remove(playerId, playback);
        return Optional.of(restored(playback));
      }
      String beatId = playback.experience().beats().get(i).id();
      return Optional.of(
          ExperienceSnapshot.playing(
              scene.sample(remaining), playback.experience().audience(), i, beatId));
    }
    sessions.remove(playerId, playback);
    return Optional.of(restored(playback));
  }

  void close() {
    experiences.clear();
    repository.close();
  }

  private static ExperienceSnapshot restored(Playback playback) {
    Audience audience =
        playback instanceof ExperiencePlayback experience
            ? experience.experience().audience()
            : Audience.SUBJECT;
    return ExperienceSnapshot.restored(playback.restorePose(), audience);
  }
}
