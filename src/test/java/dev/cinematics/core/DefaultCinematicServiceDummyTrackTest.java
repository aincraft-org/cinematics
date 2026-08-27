package dev.cinematics.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.cinematics.api.*;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultCinematicServiceDummyTrackTest {
  @Test
  void samplePlaybackCarriesDummyPoseThroughService() throws Exception {
    var dir = Files.createTempDirectory("cinematics-test");
    var repo = new JsonCinematicRepository(dir);
    var svc = new DefaultCinematicService(repo);
    var cam0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, 0, 0, 0));
    var cam1 = new CameraKeyframe(2.0, new CameraPose("world", 10, 68, 0, 90, 0));
    var dum0 = new CameraKeyframe(0.0, new CameraPose("world", 100, 68, 100, 180, 0));
    var dum1 = new CameraKeyframe(2.0, new CameraPose("world", 108, 68, 100, 270, 0));
    var scene =
        CinematicScene.load(
            "dual_svc", List.of(cam0, cam1), List.of(dum0, dum1), List.of(), List.of());
    svc.save(scene);
    var start = new CameraPose("world", 0, 68, 0, 0, 0);
    var id = java.util.UUID.randomUUID();
    assertEquals(CinematicResult.SUCCESS, svc.play(id, "dual_svc", start));
    var s = svc.samplePlayback(id, 1.0).orElseThrow();
    assertTrue(s.playing());
    assertEquals(5.0, s.pose().x(), 1e-6);
    assertNotNull(s.dummyPose(), "service must propagate dummyPose via PlaybackSnapshot");
    assertEquals(104.0, s.dummyPose().x(), 1e-6);
    assertNotEquals(s.pose().x(), s.dummyPose().x(), 1e-6);
  }
}
