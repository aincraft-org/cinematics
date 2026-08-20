package dev.cinematics.core;

import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.Experience;
import dev.cinematics.api.SceneSample;
import java.util.List;

/** Exclusive per-player playback: either a single scene or a resolved experience walk. */
sealed interface Playback permits ScenePlayback, ExperiencePlayback {

  CameraPose restorePose();
}

record ScenePlayback(CinematicScene scene, CameraPose restorePose) implements Playback {}

record ExperiencePlayback(Experience experience, List<ResolvedFrame> frames, CameraPose restorePose)
    implements Playback {}

sealed interface ResolvedFrame permits ResolvedPath, ResolvedFade {

  String id();

  double durationSeconds();

  SceneSample sample(double localT);
}

record ResolvedPath(String id, CinematicScene scene) implements ResolvedFrame {

  @Override
  public double durationSeconds() {
    return scene.durationSeconds();
  }

  @Override
  public SceneSample sample(double localT) {
    return scene.sample(localT);
  }
}

record ResolvedFade(String id, double durationSeconds, String overlayId, CameraPose pose)
    implements ResolvedFrame {

  @Override
  public SceneSample sample(double localT) {
    return new SceneSample(pose, List.of(overlayId), List.of());
  }
}
