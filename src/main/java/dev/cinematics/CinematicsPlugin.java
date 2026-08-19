package dev.cinematics;

import dev.cinematics.api.CinematicService;
import dev.cinematics.core.DefaultCinematicService;
import dev.cinematics.core.JsonCinematicRepository;
import dev.cinematics.paper.CinematicCommand;
import dev.cinematics.paper.PaperCinematicController;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Cinematics entrypoint: scene store, Paper playback adapter, and operator command. */
public final class CinematicsPlugin extends JavaPlugin {

  private DefaultCinematicService cinematicService;
  private PaperCinematicController cinematicController;

  @Override
  public void onEnable() {
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
      getLogger().severe("Failed to create data folder; disabling.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Path dataDir = getDataFolder().toPath();
    cinematicService =
        new DefaultCinematicService(new JsonCinematicRepository(dataDir.resolve("scenes")));
    Bukkit.getServicesManager()
        .register(CinematicService.class, cinematicService, this, ServicePriority.Normal);
    cinematicController = new PaperCinematicController(this, cinematicService);
    Bukkit.getPluginManager().registerEvents(cinematicController, this);
    getLifecycleManager()
        .registerEventHandler(
            LifecycleEvents.COMMANDS,
            event ->
                event
                    .registrar()
                    .register(
                        "cinematic",
                        "Create cinematic camera scenes with shaders and props.",
                        List.of("cinematics", "cine"),
                        new CinematicCommand(cinematicService, cinematicController)));
    getLogger().info("Cinematics enabled (scenes at " + dataDir.resolve("scenes") + ").");
  }

  @Override
  public void onDisable() {
    if (cinematicController != null) {
      HandlerList.unregisterAll(cinematicController);
      cinematicController.close();
      cinematicController = null;
    }
    if (cinematicService != null) {
      cinematicService.close();
      cinematicService = null;
    }
    Bukkit.getServicesManager().unregister(this);
    getLogger().info("Cinematics disabled.");
  }

  public CinematicService cinematicService() {
    return cinematicService;
  }
}
