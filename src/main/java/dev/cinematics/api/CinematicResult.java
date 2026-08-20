package dev.cinematics.api;

/** Outcome of a cinematic scene or experience mutation or play attempt. */
public enum CinematicResult {
  SUCCESS,
  TOO_FEW_KEYFRAMES,
  UNKNOWN_SCENE,
  UNKNOWN_EXPERIENCE,
  EMPTY_EXPERIENCE,
  NOT_PLAYING,
  INVALID_NAME,
  ALREADY_EXISTS,
  ALREADY_PLAYING,
  INVALID_KEYFRAME,
  INVALID_CUE
}
