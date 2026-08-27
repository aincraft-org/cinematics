package dev.cinematics.paper;

import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicService;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.api.PlaybackSnapshot;
import dev.cinematics.api.PropCue;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Folia-safe Paper adapter: applies sampled camera poses, vanilla shader overlays, and display
 * props for an active cinematic session.
 */
public final class PaperCinematicController implements Listener {

  private static final long TICK_PERIOD = 1L;
  private static final int OVERLAY_EFFECT_TICKS = 40;

  private final Plugin plugin;
  private final CinematicService cinematicService;
  private final ExperienceService experienceService;
  private final ConcurrentMap<UUID, ScheduledTask> tickers = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AppliedState> applied = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Location> playStartLocations = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CameraPose> playSceneOrigins = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CameraPose> playDummyOrigins = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CameraRig> rigs = new ConcurrentHashMap<>();
  private PlayerSkinDummy dummies;
  private CameraDolly dolly;

  public PaperCinematicController(
      Plugin plugin, CinematicService cinematicService, ExperienceService experienceService) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.cinematicService = Objects.requireNonNull(cinematicService, "cinematicService");
    this.experienceService = Objects.requireNonNull(experienceService, "experienceService");
  }

  public void attachDummies(PlayerSkinDummy dummies) {
    this.dummies = Objects.requireNonNull(dummies, "dummies");
  }

  public void attachDolly(CameraDolly dolly) {
    this.dolly = Objects.requireNonNull(dolly, "dolly");
  }

  public CinematicResult play(Player player, String sceneName) {
    Objects.requireNonNull(player, "player");
    UUID playerId = player.getUniqueId();
    CinematicResult result =
        cinematicService.play(playerId, sceneName, poseOf(player.getLocation()));
    if (result != CinematicResult.SUCCESS) {
      return result;
    }
    playStartLocations.put(playerId, player.getLocation().clone());
    cinematicService
        .scene(sceneName)
        .ifPresent(
            scene -> {
              playSceneOrigins.put(playerId, scene.keyframes().getFirst().pose());
              if (!scene.dummyKeyframes().isEmpty()) {
                playDummyOrigins.put(playerId, scene.dummyKeyframes().getFirst().pose());
              }
            });
    if (dummies != null) {
      dummies.create("me", player);
      dummies.show("me");
    }
    startTicker(player);
    return CinematicResult.SUCCESS;
  }

  public CinematicResult playExperience(Player player, String experienceName) {
    Objects.requireNonNull(player, "player");
    UUID playerId = player.getUniqueId();
    CinematicResult result =
        experienceService.start(playerId, experienceName, poseOf(player.getLocation()));
    if (result != CinematicResult.SUCCESS) {
      return result;
    }
    playStartLocations.put(playerId, player.getLocation().clone());
    playSceneOrigins.remove(playerId);
    playDummyOrigins.remove(playerId);
    if (dummies != null) {
      dummies.create("me", player);
      dummies.show("me");
    }
    startTicker(player);
    return CinematicResult.SUCCESS;
  }

  public Optional<PlaybackSnapshot> stop(Player player) {
    Objects.requireNonNull(player, "player");
    cancelTicker(player.getUniqueId());
    Optional<PlaybackSnapshot> stopped = cinematicService.stop(player.getUniqueId());
    stopped.ifPresent(snapshot -> apply(player, snapshot));
    return stopped;
  }

  public void close() {
    for (UUID playerId : List.copyOf(tickers.keySet())) {
      Player player = Bukkit.getPlayer(playerId);
      if (player != null) {
        stop(player);
      } else {
        cancelTicker(playerId);
        cinematicService.stop(playerId);
        AppliedState state = applied.remove(playerId);
        if (state != null) {
          state.removeProps();
        }
        clearPlaybackState(playerId, null);
      }
    }
    tickers.clear();
    applied.clear();
    for (UUID playerId : List.copyOf(rigs.keySet())) {
      Player player = Bukkit.getPlayer(playerId);
      CameraRig rig = rigs.remove(playerId);
      if (rig != null && player != null) {
        rig.destroy(player);
      }
    }
    playStartLocations.clear();
    playSceneOrigins.clear();
    playDummyOrigins.clear();
    rigs.clear();
    if (dummies != null) {
      dummies.hideAllShown();
    }
    if (dolly != null) {
      dolly.restoreAll();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    Player player = event.getPlayer();
    cancelTicker(player.getUniqueId());
    cinematicService.stop(player.getUniqueId());
    AppliedState state = applied.remove(player.getUniqueId());
    if (state != null) {
      state.removeProps();
    }
    clearPlaybackState(player.getUniqueId(), player);
    if (dummies != null) {
      dummies.hideAllShown();
    }
    if (dolly != null && dolly.isHidden(player)) {
      dolly.restore(player);
    }
  }

  static CameraPose poseOf(Location location) {
    World world = location.getWorld();
    String worldIdentity = world == null ? "world" : world.getUID().toString();
    return new CameraPose(
        worldIdentity,
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch());
  }

  static CameraPose offsetIfRelative(CameraPose sampled, CameraPose origin, Location start) {
    if (sampled == null || start == null) {
      return sampled;
    }
    if (!"relative".equals(sampled.worldIdentity())) {
      return sampled;
    }
    World world = start.getWorld();
    if (world == null) {
      return sampled;
    }
    double originX = origin == null ? 0.0 : origin.x();
    double originY = origin == null ? 0.0 : origin.y();
    double originZ = origin == null ? 0.0 : origin.z();
    return new CameraPose(
        world.getUID().toString(),
        start.getX() + sampled.x() - originX,
        start.getY() + sampled.y() - originY,
        start.getZ() + sampled.z() - originZ,
        sampled.yaw(),
        sampled.pitch());
  }

  static Location locationOf(CameraPose pose) {
    World world = worldOf(pose.worldIdentity());
    if (world == null) {
      return null;
    }
    return new Location(world, pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
  }

  private static World worldOf(String worldIdentity) {
    World byId;
    try {
      byId = Bukkit.getWorld(UUID.fromString(worldIdentity));
    } catch (IllegalArgumentException notAUuid) {
      byId = null;
    }
    if (byId != null) {
      return byId;
    }
    return Bukkit.getWorld(worldIdentity);
  }

  private void startTicker(Player player) {
    UUID playerId = player.getUniqueId();
    cancelTicker(playerId);
    long startedNanos = System.nanoTime();
    ScheduledTask task =
        player
            .getScheduler()
            .runAtFixedRate(
                plugin,
                scheduled -> {
                  double elapsed = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
                  Optional<PlaybackSnapshot> frame =
                      cinematicService.samplePlayback(playerId, elapsed);
                  if (frame.isEmpty() || !frame.get().playing()) {
                    frame.ifPresent(snapshot -> apply(player, snapshot));
                    applied.remove(playerId);
                    scheduled.cancel();
                    tickers.remove(playerId, scheduled);
                    return;
                  }
                  apply(player, frame.get());
                },
                null,
                TICK_PERIOD,
                TICK_PERIOD);
    if (task != null) {
      tickers.put(playerId, task);
    }
  }

  private void cancelTicker(UUID playerId) {
    ScheduledTask task = tickers.remove(playerId);
    if (task != null) {
      task.cancel();
    }
  }

  private void apply(Player player, PlaybackSnapshot snapshot) {
    UUID playerId = player.getUniqueId();
    if (!snapshot.playing()) {
      CameraPose restored = snapshot.pose();
      CameraRig rig = rigs.remove(playerId);
      if (rig != null) {
        rig.destroy(player, restored);
      } else if (dolly != null && dolly.isHidden(player)) {
        dolly.restore(player, restored);
      } else {
        Location location = locationOf(restored);
        if (location != null) {
          player.teleport(location);
        }
      }
      playStartLocations.remove(playerId);
      playSceneOrigins.remove(playerId);
      playDummyOrigins.remove(playerId);
      if (dummies != null) {
        dummies.hide("me");
        dummies.destroy("me");
      }
      AppliedState state = applied.remove(playerId);
      if (state != null) {
        state.clear(player);
      }
      return;
    }

    Location start = playStartLocations.get(playerId);
    CameraPose cameraOrigin =
        snapshot.cameraOrigin() != null ? snapshot.cameraOrigin() : playSceneOrigins.get(playerId);
    CameraPose dummyOrigin =
        snapshot.dummyOrigin() != null ? snapshot.dummyOrigin() : playDummyOrigins.get(playerId);
    CameraPose cameraPose = offsetIfRelative(snapshot.pose(), cameraOrigin, start);
    CameraPose dummyPose =
        snapshot.dummyPose() == null
            ? null
            : offsetIfRelative(snapshot.dummyPose(), dummyOrigin, start);

    if (dolly != null) {
      rigs.compute(
          playerId,
          (id, existing) -> {
            if (existing == null) {
              return CameraRig.spawn(dolly, player, cameraPose);
            }
            existing.update(player, cameraPose);
            return existing;
          });
    } else {
      Location location = locationOf(cameraPose);
      if (location != null) {
        player.teleport(location);
      }
    }

    if (dummies != null && dummyPose != null) {
      dummies.moveTo("me", dummyPose);
    }

    AppliedState state = applied.computeIfAbsent(playerId, id -> new AppliedState());
    state.syncShaders(player, snapshot.shaders());
    state.syncProps(plugin, player, snapshot.props());
  }

  private void clearPlaybackState(UUID playerId, Player player) {
    CameraRig rig = rigs.remove(playerId);
    if (rig != null && player != null) {
      rig.destroy(player);
    }
    playStartLocations.remove(playerId);
    playSceneOrigins.remove(playerId);
    playDummyOrigins.remove(playerId);
  }

  private static final class AppliedState {
    private final Set<String> shaders = new LinkedHashSet<>();
    private final Map<PropCue, Entity> props = new LinkedHashMap<>();

    private void syncShaders(Player player, List<String> active) {
      Set<String> wanted = new LinkedHashSet<>(active);
      for (String overlayId : List.copyOf(shaders)) {
        if (!wanted.contains(overlayId)) {
          removeOverlay(player, overlayId);
          shaders.remove(overlayId);
        }
      }
      for (String overlayId : wanted) {
        applyOverlay(player, overlayId);
        shaders.add(overlayId);
      }
    }

    private void syncProps(Plugin plugin, Player player, List<PropCue> active) {
      Set<PropCue> wanted = new LinkedHashSet<>(active);
      for (PropCue cue : List.copyOf(props.keySet())) {
        if (!wanted.contains(cue)) {
          removeProp(props.remove(cue));
        }
      }
      for (PropCue cue : wanted) {
        if (!props.containsKey(cue)) {
          Entity spawned = spawnProp(plugin, player, cue);
          if (spawned != null) {
            props.put(cue, spawned);
          }
        }
      }
    }

    private void clear(Player player) {
      for (String overlayId : List.copyOf(shaders)) {
        removeOverlay(player, overlayId);
      }
      shaders.clear();
      removeProps();
    }

    private void removeProps() {
      for (Entity entity : new ArrayList<>(props.values())) {
        removeProp(entity);
      }
      props.clear();
    }
  }

  private static void applyOverlay(Player player, String overlayId) {
    PotionEffectType type = effectType(overlayId);
    if (type == null) {
      return;
    }
    player.addPotionEffect(new PotionEffect(type, OVERLAY_EFFECT_TICKS, 0, true, false, false));
  }

  private static void removeOverlay(Player player, String overlayId) {
    PotionEffectType type = effectType(overlayId);
    if (type != null) {
      player.removePotionEffect(type);
    }
  }

  private static PotionEffectType effectType(String overlayId) {
    Optional<String> key = VanillaShaderOverlays.potionEffectKey(overlayId);
    if (key.isEmpty()) {
      return null;
    }
    NamespacedKey namespaced = NamespacedKey.fromString(key.get());
    if (namespaced == null) {
      return null;
    }
    return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(namespaced);
  }

  private static Entity spawnProp(Plugin plugin, Player player, PropCue cue) {
    Location location = locationOf(cue.pose());
    if (location == null || location.getWorld() == null) {
      return null;
    }
    Material material = Material.matchMaterial(cue.propId());
    Display display;
    if (material != null && material.isBlock()) {
      display =
          location
              .getWorld()
              .spawn(
                  location,
                  BlockDisplay.class,
                  entity -> entity.setBlock(material.createBlockData()));
    } else {
      Material item = material == null ? Material.ARMOR_STAND : material;
      display =
          location
              .getWorld()
              .spawn(
                  location, ItemDisplay.class, entity -> entity.setItemStack(new ItemStack(item)));
    }
    display.setPersistent(false);
    display.setGravity(false);
    display.setInvulnerable(true);
    display.setVisibleByDefault(false);
    player.showEntity(plugin, display);
    return display;
  }

  private static void removeProp(Entity entity) {
    if (entity != null && entity.isValid()) {
      entity.remove();
    }
  }
}
