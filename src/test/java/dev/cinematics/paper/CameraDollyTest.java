package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CameraDollyTest {

  @Test
  void rejectsNullPlugin() {
    assertThrows(NullPointerException.class, () -> new CameraDolly(null));
  }
}
