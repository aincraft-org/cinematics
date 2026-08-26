package dev.cinematics.paper;

import dev.cinematics.api.CameraPose;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CameraDolly {

  private final Plugin plugin;
  private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();

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
    hidden.add(player.getUniqueId());
    player.teleport(location);
  }

  public void restore(Player player, CameraPose pose) {
    Location location = PaperCinematicController.locationOf(pose);
    if (location != null) {
      player.teleport(location);
    }
    restore(player);
  }

  public void restore(Player player) {
    Objects.requireNonNull(player, "player");
    hidden.remove(player.getUniqueId());
    for (Player viewer : player.getServer().getOnlinePlayers()) {
      if (!viewer.equals(player)) {
        viewer.showPlayer(plugin, player);
      }
    }
  }

  void restoreAll() {
    for (UUID id : Set.copyOf(hidden)) {
      Player player = plugin.getServer().getPlayer(id);
      if (player != null) {
        restore(player);
      } else {
        hidden.remove(id);
      }
    }
  }

  boolean isHidden(Player player) {
    return hidden.contains(player.getUniqueId());
  }
}
