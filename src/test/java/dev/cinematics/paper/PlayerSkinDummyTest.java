package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.cinematics.api.CinematicResult;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlayerSkinDummyTest {

  @Test
  void rejectsInvalidName() {
    PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
    assertEquals(CinematicResult.INVALID_NAME, dummies.create("", null));
  }

  @Test
  void allocateEntityIdIsMonotonicAndUnique() {
    PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < 64; i++) {
      int a = dummies.allocateEntityId();
      int b = dummies.allocateEntityId();
      assertNotEquals(a, b, "allocator returned duplicate id " + a);
      assertTrue(seen.add(a), "id reused: " + a);
      assertTrue(seen.add(b), "id reused: " + b);
    }
  }

  @Test
  void allocateEntityIdIsStableAcrossDestroy() {
    PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
    int first = dummies.allocateEntityId();
    Set<Integer> seen = new HashSet<>();
    seen.add(first);
    for (int i = 0; i < 32; i++) {
      int id = dummies.allocateEntityId();
      assertNotEquals(first, id, "allocator returned prior id " + id);
      assertTrue(seen.add(id), "id reused: " + id);
    }
  }
}
