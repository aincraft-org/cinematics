package dev.cinematics.core;

import static org.junit.jupiter.api.Assertions.*;

import dev.cinematics.api.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonCinematicRepositoryDummyTrackTest {
  @Test
  void roundTripsDummyKeyframes() {
    var cam0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, 0, 0, 15));
    var cam1 = new CameraKeyframe(2.0, new CameraPose("world", 4, 68, 0, 90, 15));
    var dum0 = new CameraKeyframe(0.0, new CameraPose("world", 0, 68, -2, 0, 0));
    var dum1 = new CameraKeyframe(2.0, new CameraPose("world", 2, 68, -2, 45, 0));
    var draft =
        new CinematicDraft(
            "orbit_dual", List.of(cam0, cam1), List.of(dum0, dum1), List.of(), List.of());
    String json = JsonCinematicRepository.encode(draft);
    assertTrue(json.contains("dummyKeyframes"), json);
    CinematicDraft decoded = JsonCinematicRepository.decode(json);
    assertEquals(2, decoded.dummyKeyframes().size());
    assertEquals(2.0, decoded.dummyKeyframes().get(1).timeSeconds());
  }

  @Test
  void decodesLegacyWithoutDummyKeyframes() {
    String legacy =
        "{\"name\":\"peek\",\"keyframes\":[{\"time\":0.0,\"world\":\"world\",\"x\":0,\"y\":68,\"z\":0,\"yaw\":0,\"pitch\":0},{\"time\":2.0,\"world\":\"world\",\"x\":1,\"y\":68,\"z\":0,\"yaw\":10,\"pitch\":0}],\"shaders\":[],\"props\":[]}";
    CinematicDraft d = JsonCinematicRepository.decode(legacy);
    assertNotNull(d);
    assertTrue(d.dummyKeyframes().isEmpty());
  }
}
