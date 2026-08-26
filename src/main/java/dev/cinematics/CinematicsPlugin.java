package dev.cinematics;

import com.github.retrooper.packetevents.PacketEvents;
import dev.cinematics.api.CinematicService;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.core.DefaultCinematicService;
import dev.cinematics.core.JsonCinematicRepository;
import dev.cinematics.core.JsonExperienceRepository;
import dev.cinematics.paper.CameraDolly;
import dev.cinematics.paper.CinematicCommand;
import dev.cinematics.paper.FakePlayerPackets;
import dev.cinematics.paper.PaperCinematicController;
import dev.cinematics.paper.PlayerSkinDummy;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
  public void onLoad() {
    PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
    PacketEvents.getAPI().load();
  }

  @Override
  public void onEnable() {
    PacketEvents.getAPI().init();
    if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
      getLogger().severe("Failed to create data folder; disabling.");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    Path dataDir = getDataFolder().toPath();
    cinematicService =
        new DefaultCinematicService(
            new JsonCinematicRepository(dataDir.resolve("scenes")),
            new JsonExperienceRepository(dataDir.resolve("experiences")));
    Bukkit.getServicesManager()
        .register(CinematicService.class, cinematicService, this, ServicePriority.Normal);
    ExperienceService experienceService = cinematicService.experiences();
    Bukkit.getServicesManager()
        .register(ExperienceService.class, experienceService, this, ServicePriority.Normal);
    cinematicController = new PaperCinematicController(this, cinematicService, experienceService);
    Bukkit.getPluginManager().registerEvents(cinematicController, this);
    FakePlayerPackets packets = new FakePlayerPackets();
    PlayerSkinDummy dummies = new PlayerSkinDummy(packets);
    CameraDolly dolly = new CameraDolly(this);
    cinematicController.attachDummies(dummies);
    cinematicController.attachDolly(dolly);
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
                        new CinematicCommand(
                            cinematicService,
                            experienceService,
                            cinematicController,
                            dummies,
                            dolly)));
    getLogger()
        .info(
            "Cinematics enabled (scenes at "
                + dataDir.resolve("scenes")
                + ", experiences at "
                + dataDir.resolve("experiences")
                + ").");
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
    PacketEvents.getAPI().terminate();
    Bukkit.getServicesManager().unregister(this);
    getLogger().info("Cinematics disabled.");
  }

  public CinematicService cinematicService() {
    return cinematicService;
  }
}
