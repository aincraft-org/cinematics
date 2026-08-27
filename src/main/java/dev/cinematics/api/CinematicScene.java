package dev.cinematics.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Named cinematic scene: a camera keyframe path plus timed shader overlay and prop cues.
 *
 * <p>Load rejects fewer than two keyframes. Duration is the last keyframe's time. {@link #sample}
 * interpolates the camera along the path and returns cues whose windows contain {@code t}.
 */
public final class CinematicScene {

  private static final int MAX_NAME_LENGTH = 64;
  private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

  private final String name;
  private final List<CameraKeyframe> keyframes;
  private final List<CameraKeyframe> dummyKeyframes;
  private final List<OverlayCue> shaders;
  private final List<PropCue> props;
  private final double durationSeconds;

  private CinematicScene(
      String name,
      List<CameraKeyframe> keyframes,
      List<CameraKeyframe> dummyKeyframes,
      List<OverlayCue> shaders,
      List<PropCue> props,
      double durationSeconds) {
    this.name = name;
    this.keyframes = keyframes;
    this.dummyKeyframes = dummyKeyframes;
    this.shaders = shaders;
    this.props = props;
    this.durationSeconds = durationSeconds;
  }

  /**
   * Builds a complete scene. Keyframe times must be strictly increasing. Names are trimmed and
   * lowercased; only {@code [a-z0-9_-]} of length 1–64 are accepted.
   */
  public static CinematicScene load(
      String name, List<CameraKeyframe> keyframes, List<OverlayCue> shaders, List<PropCue> props) {
    return load(name, keyframes, List.of(), shaders, props);
  }

  /**
   * Builds a complete scene with an optional independent dummy keyframe path. Camera keyframe times
   * must be strictly increasing; dummy keyframe times follow the same rule when two or more are
   * present.
   */
  public static CinematicScene load(
      String name,
      List<CameraKeyframe> keyframes,
      List<CameraKeyframe> dummyKeyframes,
      List<OverlayCue> shaders,
      List<PropCue> props) {
    String normalized = normalizeName(name);
    if (normalized == null) {
      throw new IllegalArgumentException("scene name must be 1–64 [a-z0-9_-] characters");
    }
    Objects.requireNonNull(keyframes, "keyframes");
    Objects.requireNonNull(dummyKeyframes, "dummyKeyframes");
    Objects.requireNonNull(shaders, "shaders");
    Objects.requireNonNull(props, "props");
    if (keyframes.size() < 2) {
      throw new IllegalArgumentException("cinematic scene requires at least two camera keyframes");
    }
    List<CameraKeyframe> sortedCamera = sortKeyframes(keyframes, "camera");
    List<CameraKeyframe> sortedDummy = sortDummyKeyframes(dummyKeyframes);
    return new CinematicScene(
        normalized,
        List.copyOf(sortedCamera),
        List.copyOf(sortedDummy),
        List.copyOf(shaders),
        List.copyOf(props),
        sortedCamera.getLast().timeSeconds());
  }

  /**
   * Normalizes a scene name: trim, lowercase, reject blank / oversized / characters outside {@code
   * [a-z0-9_-]}. Returns {@code null} when the name is unusable as a key.
   */
  public static String normalizeName(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()
        || trimmed.length() > MAX_NAME_LENGTH
        || !NAME.matcher(trimmed).matches()) {
      return null;
    }
    return trimmed;
  }

  public String name() {
    return name;
  }

  public List<CameraKeyframe> keyframes() {
    return keyframes;
  }

  public List<CameraKeyframe> dummyKeyframes() {
    return dummyKeyframes;
  }

  public List<OverlayCue> shaders() {
    return shaders;
  }

  public List<PropCue> props() {
    return props;
  }

  /** Last keyframe time, in seconds. */
  public double durationSeconds() {
    return durationSeconds;
  }

  /**
   * Samples the scene at elapsed seconds {@code t}, clamped to {@code [0, duration]}. The camera
   * pose is interpolated along consecutive keyframes; shader and prop cues whose windows contain
   * the clamped time are included.
   */
  public SceneSample sample(double t) {
    double clamped = clamp(t, durationSeconds);
    CameraPose camera = interpolate(keyframes, clamped);
    CameraPose dummyPose = sampleDummy(t);
    List<String> activeShaders = new ArrayList<>();
    for (OverlayCue cue : shaders) {
      if (cue.contains(clamped)) {
        activeShaders.add(cue.overlayId());
      }
    }
    List<PropCue> activeProps = new ArrayList<>();
    for (PropCue cue : props) {
      if (cue.contains(clamped)) {
        activeProps.add(cue);
      }
    }
    return new SceneSample(camera, dummyPose, activeShaders, activeProps);
  }

  private CameraPose sampleDummy(double t) {
    if (dummyKeyframes.isEmpty()) {
      return null;
    }
    if (dummyKeyframes.size() == 1) {
      return dummyKeyframes.getFirst().pose();
    }
    double dummyDuration = dummyKeyframes.getLast().timeSeconds();
    return interpolate(dummyKeyframes, clamp(t, dummyDuration));
  }

  private static List<CameraKeyframe> sortKeyframes(
      List<CameraKeyframe> keyframes, String trackName) {
    List<CameraKeyframe> sorted = new ArrayList<>(keyframes);
    sorted.sort(Comparator.comparingDouble(CameraKeyframe::timeSeconds));
    for (int i = 1; i < sorted.size(); i++) {
      if (sorted.get(i).timeSeconds() <= sorted.get(i - 1).timeSeconds()) {
        throw new IllegalArgumentException(
            trackName + " keyframe times must be strictly increasing");
      }
    }
    return sorted;
  }

  private static List<CameraKeyframe> sortDummyKeyframes(List<CameraKeyframe> keyframes) {
    if (keyframes.isEmpty() || keyframes.size() == 1) {
      return new ArrayList<>(keyframes);
    }
    return sortKeyframes(keyframes, "dummy");
  }

  private static double clamp(double t, double durationSeconds) {
    if (!Double.isFinite(t) || t <= 0.0) {
      return 0.0;
    }
    if (t >= durationSeconds) {
      return durationSeconds;
    }
    return t;
  }

  private static CameraPose interpolate(List<CameraKeyframe> track, double t) {
    if (t <= track.getFirst().timeSeconds()) {
      return track.getFirst().pose();
    }
    if (t >= track.getLast().timeSeconds()) {
      return track.getLast().pose();
    }
    CameraKeyframe previous = track.getFirst();
    for (int i = 1; i < track.size(); i++) {
      CameraKeyframe next = track.get(i);
      if (t <= next.timeSeconds()) {
        double span = next.timeSeconds() - previous.timeSeconds();
        double alpha = span == 0.0 ? 1.0 : (t - previous.timeSeconds()) / span;
        return lerp(previous.pose(), next.pose(), alpha);
      }
      previous = next;
    }
    return track.getLast().pose();
  }

  static CameraPose lerp(CameraPose from, CameraPose to, double alpha) {
    String world = alpha < 1.0 ? from.worldIdentity() : to.worldIdentity();
    return new CameraPose(
        world,
        from.x() + (to.x() - from.x()) * alpha,
        from.y() + (to.y() - from.y()) * alpha,
        from.z() + (to.z() - from.z()) * alpha,
        lerpYaw(from.yaw(), to.yaw(), alpha),
        (float) (from.pitch() + (to.pitch() - from.pitch()) * alpha));
  }

  private static float lerpYaw(float from, float to, double alpha) {
    return wrapDegrees(from + (float) (wrapDegrees(to - from) * alpha));
  }

  private static float wrapDegrees(float degrees) {
    float wrapped = degrees % 360.0f;
    if (wrapped >= 180.0f) {
      wrapped -= 360.0f;
    }
    if (wrapped < -180.0f) {
      wrapped += 360.0f;
    }
    return wrapped;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CinematicScene that)) {
      return false;
    }
    return Double.compare(that.durationSeconds, durationSeconds) == 0
        && name.equals(that.name)
        && keyframes.equals(that.keyframes)
        && dummyKeyframes.equals(that.dummyKeyframes)
        && shaders.equals(that.shaders)
        && props.equals(that.props);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, keyframes, dummyKeyframes, shaders, props, durationSeconds);
  }

  @Override
  public String toString() {
    return "CinematicScene{name="
        + name
        + ", keyframes="
        + keyframes.size()
        + ", dummyKeyframes="
        + dummyKeyframes.size()
        + ", duration="
        + durationSeconds
        + '}';
  }
}
