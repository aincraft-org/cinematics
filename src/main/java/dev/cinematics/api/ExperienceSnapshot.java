package dev.cinematics.api;

import java.util.List;
import java.util.Objects;

/**
 * Pose and active cues for one experience tick, or the completed/restored pose after stop /
 * completion.
 */
public record ExperienceSnapshot(
    CameraPose pose,
    List<String> shaders,
    List<PropCue> props,
    Audience audience,
    int beatIndex,
    String beatId,
    boolean playing) {

  public ExperienceSnapshot {
    Objects.requireNonNull(pose, "pose");
    shaders = List.copyOf(Objects.requireNonNull(shaders, "shaders"));
    props = List.copyOf(Objects.requireNonNull(props, "props"));
    Objects.requireNonNull(audience, "audience");
    Objects.requireNonNull(beatId, "beatId");
  }

  /** Frame taken from a beat sample while the session is still running. */
  public static ExperienceSnapshot playing(
      SceneSample sample, Audience audience, int beatIndex, String beatId) {
    Objects.requireNonNull(sample, "sample");
    return new ExperienceSnapshot(
        sample.camera(), sample.shaders(), sample.props(), audience, beatIndex, beatId, true);
  }

  /** Stop or natural completion: restore pose, no overlays or props. */
  public static ExperienceSnapshot restored(CameraPose pose, Audience audience) {
    return new ExperienceSnapshot(pose, List.of(), List.of(), audience, 0, "", false);
  }
}
