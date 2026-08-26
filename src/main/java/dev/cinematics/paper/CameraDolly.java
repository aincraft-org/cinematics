package dev.cinematics.paper;

import dev.cinematics.api.CameraPose;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CameraDolly {

  private final Plugin plugin;

  public CameraDolly(Plugin plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public void apply(Player player, CameraPose pose) {
    Location location = PaperCinematicController.locationOf(pose);
    if (location == null) {
      return;
    }
    for (Player viewer : player.getServer().getOnlinePlayers()) {
      if (!viewer.equals(player)) {
        viewer.hidePlayer(plugin, player);
      }
    }
    player.teleport(location);
  }

  public void restore(Player player, CameraPose pose) {
    Location location = PaperCinematicController.locationOf(pose);
    if (location != null) {
      player.teleport(location);
    }
    for (Player viewer : player.getServer().getOnlinePlayers()) {
      if (!viewer.equals(player)) {
        viewer.showPlayer(plugin, player);
      }
    }
  }
}
