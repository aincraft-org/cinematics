package dev.cinematics.api;

import java.util.List;
import java.util.Objects;

/**
 * Pose and active cues to apply for one playback tick, or the restored pre-play pose after stop /
 * completion.
 */
public record PlaybackSnapshot(
    CameraPose pose,
    CameraPose dummyPose,
    CameraPose cameraOrigin,
    CameraPose dummyOrigin,
    List<String> shaders,
    List<PropCue> props,
    boolean playing) {

  public PlaybackSnapshot {
    Objects.requireNonNull(pose, "pose");
    shaders = List.copyOf(Objects.requireNonNull(shaders, "shaders"));
    props = List.copyOf(Objects.requireNonNull(props, "props"));
  }

  /** Frame taken from a scene sample while the session is still running. */
  public static PlaybackSnapshot playing(
      SceneSample sample, CameraPose cameraOrigin, CameraPose dummyOrigin) {
    Objects.requireNonNull(sample, "sample");
    return new PlaybackSnapshot(
        sample.camera(),
        sample.dummyPose(),
        cameraOrigin,
        dummyOrigin,
        sample.shaders(),
        sample.props(),
        true);
  }

  /** Stop or natural completion: restore pose, no overlays or props. */
  public static PlaybackSnapshot restored(CameraPose pose) {
    return new PlaybackSnapshot(pose, null, null, null, List.of(), List.of(), false);
  }
}
