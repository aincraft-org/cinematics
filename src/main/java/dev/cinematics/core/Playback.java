package dev.cinematics.core;

import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.Experience;
import java.util.List;

/** Exclusive per-player playback: either a single scene or a resolved experience. */
sealed interface Playback permits ScenePlayback, ExperiencePlayback {

  CameraPose restorePose();
}

record ScenePlayback(CinematicScene scene, CameraPose restorePose) implements Playback {}

record ExperiencePlayback(
    Experience experience, List<CinematicScene> scenes, CameraPose restorePose)
    implements Playback {}
