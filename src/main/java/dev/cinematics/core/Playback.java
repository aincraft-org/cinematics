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

  CameraPose cameraOrigin();

  CameraPose dummyOrigin();
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

  @Override
  public CameraPose cameraOrigin() {
    return scene.keyframes().getFirst().pose();
  }

  @Override
  public CameraPose dummyOrigin() {
    return scene.dummyKeyframes().isEmpty() ? null : scene.dummyKeyframes().getFirst().pose();
  }
}

record ResolvedFade(String id, double durationSeconds, String overlayId, CameraPose pose)
    implements ResolvedFrame {

  @Override
  public SceneSample sample(double localT) {
    return new SceneSample(pose, null, List.of(overlayId), List.of());
  }

  @Override
  public CameraPose cameraOrigin() {
    return null;
  }

  @Override
  public CameraPose dummyOrigin() {
    return null;
  }
}
