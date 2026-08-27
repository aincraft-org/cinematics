package dev.cinematics.paper;

import dev.cinematics.api.CameraPose;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Per-player camera dolly session: hides the body and teleports the real player each tick. */
final class CameraRig {

  private final CameraDolly dolly;
  private boolean active;

  private CameraRig(CameraDolly dolly) {
    this.dolly = Objects.requireNonNull(dolly, "dolly");
  }

  static CameraRig spawn(CameraDolly dolly, Player player, CameraPose pose) {
    CameraRig rig = new CameraRig(dolly);
    rig.spawn(player, pose);
    return rig;
  }

  void spawn(Player player, CameraPose pose) {
    Objects.requireNonNull(player, "player");
    Objects.requireNonNull(pose, "pose");
    dolly.apply(player, pose);
    active = true;
  }

  void update(Player player, CameraPose pose) {
    if (!active) {
      return;
    }
    dolly.apply(player, pose);
  }

  void destroy(Player player) {
    if (!active) {
      return;
    }
    dolly.restore(player);
    active = false;
  }

  void destroy(Player player, CameraPose restorePose) {
    if (!active) {
      return;
    }
    dolly.restore(player, restorePose);
    active = false;
  }

  boolean active() {
    return active;
  }
}
