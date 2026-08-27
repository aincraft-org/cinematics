package dev.cinematics.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.cinematics.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CinematicSceneDummyTrackTest {
  @Test
  void cameraAndDummySampleIndependentlyViaSceneSample() {
    var cam0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, 0, 0, 15));
    var cam1 = new CameraKeyframe(4.0, new CameraPose("world", 10, 68, 0, 90, 15));
    var dum0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, -2, 0, 0));
    var dum1 = new CameraKeyframe(4.0, new CameraPose("world", 8, 68, -2, 90, 0));
    var scene =
        CinematicScene.load(
            "rel_dual", List.of(cam0, cam1), List.of(dum0, dum1), List.of(), List.of());
    assertEquals(4.0, scene.durationSeconds());
    var sMid = scene.sample(2.0);
    assertEquals(5.0, sMid.camera().x(), 1e-6);
    assertNotNull(sMid.dummyPose());
    assertEquals(4.0, sMid.dummyPose().x(), 1e-6);
    assertEquals(45.0, sMid.dummyPose().yaw(), 1e-6);
    assertNotEquals(sMid.camera().x(), sMid.dummyPose().x(), 1e-6);
  }

  @Test
  void emptyDummyTrackMeansNullDummyPose() {
    var cam0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, 0, 0, 0));
    var cam1 = new CameraKeyframe(1.0, new CameraPose("world", 1, 68, 0, 0, 0));
    var scene =
        CinematicScene.load("no_dummy", List.of(cam0, cam1), List.of(), List.of(), List.of());
    assertTrue(scene.dummyKeyframes().isEmpty());
    assertNull(scene.sample(0.5).dummyPose());
  }

  @Test
  void dummySampleClampedIndependently() {
    var cam0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, 0, 0, 0));
    var cam1 = new CameraKeyframe(10.0, new CameraPose("world", 10, 68, 0, 0, 0));
    var dum0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, 0, 0, 0));
    var dum1 = new CameraKeyframe(2.0, new CameraPose("world", 2, 68, 0, 0, 0));
    var scene =
        CinematicScene.load(
            "clamp", List.of(cam0, cam1), List.of(dum0, dum1), List.of(), List.of());
    assertEquals(1.0, scene.sample(1.0).camera().x(), 1e-6);
    assertEquals(1.0, scene.sample(1.0).dummyPose().x(), 1e-6);
    assertEquals(2.0, scene.sample(5.0).dummyPose().x(), 1e-6);
  }
}
