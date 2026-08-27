package dev.cinematics.core;

import dev.cinematics.api.Audience;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.CinematicService;
import dev.cinematics.api.CompletionAction;
import dev.cinematics.api.Experience;
import dev.cinematics.api.ExperienceFrame;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.api.ExperienceSnapshot;
import dev.cinematics.api.FadeFrame;
import dev.cinematics.api.PathFrame;
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
  public CinematicResult addTimelineBeat(String experienceName, TimelineBeat beat) {
    Objects.requireNonNull(beat, "beat");
    String normalized = CinematicScene.normalizeName(experienceName);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    synchronized (mutationLock) {
      Experience existing = experiences.get(normalized);
      Experience next;
      try {
        if (existing == null) {
          next =
              Experience.load(
                  normalized, Audience.SUBJECT, CompletionAction.RESTORE, List.of(beat));
        } else {
          next = existing.withTimelineBeat(beat);
        }
      } catch (IllegalArgumentException invalid) {
        return CinematicResult.INVALID_CUE;
      }
      experiences.put(normalized, next);
      repository.save(next);
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
    List<ExperienceFrame> walk = experience.walk();
    List<ResolvedFrame> resolved = new ArrayList<>();
    for (int i = 0; i < walk.size(); i++) {
      ExperienceFrame frame = walk.get(i);
      if (frame instanceof PathFrame path) {
        Optional<CinematicScene> scene = cinematic.scene(path.sceneName());
        if (scene.isEmpty()) {
          return CinematicResult.UNKNOWN_SCENE;
        }
        resolved.add(new ResolvedPath(path.id(), scene.get()));
      } else if (frame instanceof FadeFrame fade) {
        CameraPose fadePose = currentPose;
        for (int j = i + 1; j < walk.size(); j++) {
          if (walk.get(j) instanceof PathFrame later) {
            Optional<CinematicScene> laterScene = cinematic.scene(later.sceneName());
            if (laterScene.isPresent()) {
              fadePose = laterScene.get().sample(0).camera();
            }
            break;
          }
        }
        resolved.add(
            new ResolvedFade(fade.id(), fade.durationSeconds(), fade.overlayId(), fadePose));
      } else {
        return CinematicResult.UNKNOWN_SCENE;
      }
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
    List<ResolvedFrame> frames = playback.frames();
    ExperienceSnapshot sampled = null;
    for (int i = 0; i < frames.size(); i++) {
      ResolvedFrame frame = frames.get(i);
      double duration = frame.durationSeconds();
      boolean last = i == frames.size() - 1;
      if (!last && remaining >= duration) {
        remaining -= duration;
      } else if (last && remaining >= duration) {
        sessions.remove(playerId, playback);
        sampled = restored(playback);
        i = frames.size();
      } else {
        sampled =
            ExperienceSnapshot.playing(
                frame.sample(remaining),
                frame.cameraOrigin(),
                frame.dummyOrigin(),
                playback.experience().audience(),
                i,
                frame.id());
        i = frames.size();
      }
    }
    if (sampled == null) {
      sessions.remove(playerId, playback);
      sampled = restored(playback);
    }
    return Optional.of(sampled);
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
