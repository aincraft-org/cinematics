package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.cinematics.api.CameraKeyframe;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.CinematicService;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.api.PlaybackSnapshot;
import dev.cinematics.api.SceneSample;
import dev.cinematics.core.DefaultCinematicService;
import dev.cinematics.core.JsonCinematicRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PaperCinematicControllerDualTickTest {

  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void offsetIfRelative_returnsSampledWhenNotRelative() {
    CameraPose absolute = new CameraPose("world", 1, 2, 3, 4, 5);
    Location start = new Location(mockWorld(), 10, 20, 30, 0, 0);
    assertEquals(absolute, PaperCinematicController.offsetIfRelative(absolute, null, start));
  }

  @Test
  void offsetIfRelative_nullChecks() {
    CameraPose relative = new CameraPose("relative", 1, 2, 3, 0, 0);
    Location start = new Location(mockWorld(), 0, 0, 0, 0, 0);
    assertNull(PaperCinematicController.offsetIfRelative(null, null, start));
    assertEquals(relative, PaperCinematicController.offsetIfRelative(relative, null, null));
  }

  @Test
  void offsetIfRelative_offsetsFromStartAndOrigin() {
    CameraPose origin = new CameraPose("relative", 1, 2, 3, 0, 0);
    CameraPose sampled = new CameraPose("relative", 4, 5, 6, 90, -10);
    Location start = new Location(mockWorld(), 100, 200, 300, 0, 0);
    CameraPose resolved = PaperCinematicController.offsetIfRelative(sampled, origin, start);
    assertEquals(WORLD_ID.toString(), resolved.worldIdentity());
    assertEquals(103.0, resolved.x(), 1e-6);
    assertEquals(203.0, resolved.y(), 1e-6);
    assertEquals(303.0, resolved.z(), 1e-6);
    assertEquals(90.0f, resolved.yaw(), 1e-6);
    assertEquals(-10.0f, resolved.pitch(), 1e-6);
  }

  @Test
  void play_recordsStartLocationAndSceneOrigins() throws Exception {
    var dir = Files.createTempDirectory("dual-tick-play");
    var cinematic = new DefaultCinematicService(new JsonCinematicRepository(dir));
    CameraPose cameraOrigin = new CameraPose("relative", 0, 0, 0, 0, 0);
    CameraPose dummyOrigin = new CameraPose("relative", 0, 0, -2, 0, 0);
    CameraPose cameraEnd = new CameraPose("relative", 10, 0, 0, 90, 0);
    cinematic.save(
        CinematicScene.load(
            "scene",
            List.of(new CameraKeyframe(0, cameraOrigin), new CameraKeyframe(1, cameraEnd)),
            List.of(new CameraKeyframe(0, dummyOrigin)),
            List.of(),
            List.of()));
    PaperCinematicController controller =
        new PaperCinematicController(
            mockPlugin(), cinematic, stubExperience((id, name, pose) -> CinematicResult.SUCCESS));
    Player player = mockPlayer(PLAYER_ID, new Location(mockWorld(), 5, 64, 5, 0, 0));
    withBukkit(() -> assertEquals(CinematicResult.SUCCESS, controller.play(player, "scene")));
    Location storedStart = locationMap(controller).get(PLAYER_ID);
    assertEquals(5.0, storedStart.getX(), 1e-6);
    assertEquals(cameraOrigin, poseMap(controller, "playSceneOrigins").get(PLAYER_ID));
    assertEquals(dummyOrigin, poseMap(controller, "playDummyOrigins").get(PLAYER_ID));
  }

  @Test
  void playExperience_clearsSceneOriginMaps() throws Exception {
    PaperCinematicController controller = newController();
    poseMap(controller, "playSceneOrigins")
        .put(PLAYER_ID, new CameraPose("relative", 1, 2, 3, 0, 0));
    poseMap(controller, "playDummyOrigins")
        .put(PLAYER_ID, new CameraPose("relative", 4, 5, 6, 0, 0));
    Player player = mockPlayer(PLAYER_ID, new Location(mockWorld(), 0, 64, 0, 0, 0));
    withBukkit(
        () ->
            assertEquals(
                CinematicResult.SUCCESS, controller.playExperience(player, "experience")));
    assertFalse(poseMap(controller, "playSceneOrigins").containsKey(PLAYER_ID));
    assertFalse(poseMap(controller, "playDummyOrigins").containsKey(PLAYER_ID));
    assertTrue(locationMap(controller).containsKey(PLAYER_ID));
  }

  @Test
  void apply_usesSnapshotOriginsAndSpawnsCameraRig() throws Exception {
    PaperCinematicController controller = newController();
    CameraDolly dolly = new CameraDolly(mockPlugin());
    controller.attachDolly(dolly);
    Location start = new Location(mockWorld(), 10, 64, 10, 0, 0);
    locationMap(controller).put(PLAYER_ID, start.clone());
    Player player = mockPlayer(PLAYER_ID, start);
    PlaybackSnapshot snapshot =
        PlaybackSnapshot.playing(
            new SceneSample(new CameraPose("relative", 2, 0, 0, 45, 0), null, List.of(), List.of()),
            new CameraPose("relative", 0, 0, 0, 0, 0),
            null);
    withBukkit(
        () -> {
          try {
            invokeApply(controller, player, snapshot);
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
    assertTrue(dolly.isHidden(player));
    assertNotNull(rigMap(controller).get(PLAYER_ID));
  }

  @Test
  void apply_movesDummyWhenPosePresent() throws Exception {
    PaperCinematicController controller = newController();
    PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
    controller.attachDummies(dummies);
    Location start = new Location(mockWorld(), 0, 64, 0, 0, 0);
    locationMap(controller).put(PLAYER_ID, start.clone());
    Player player = mockPlayer(PLAYER_ID, start);
    putDummy(
        dummies,
        "me",
        new CameraPose(WORLD_ID.toString(), 0, 64, 0, 0, 0),
        -7,
        true);
    PlaybackSnapshot snapshot =
        PlaybackSnapshot.playing(
            new SceneSample(
                new CameraPose(WORLD_ID.toString(), 0, 64, 0, 0, 0),
                new CameraPose("relative", 3, 0, 0, 180, 0),
                List.of(),
                List.of()),
            new CameraPose("relative", 0, 0, 0, 0, 0),
            new CameraPose("relative", 0, 0, 0, 0, 0));
    withBukkit(
        () -> {
          try {
            withPacketEvents(
                () -> {
                  try {
                    invokeApply(controller, player, snapshot);
                  } catch (Exception e) {
                    throw new AssertionError(e);
                  }
                });
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
    CameraPose moved = storedDummyPose(dummies, "me");
    assertEquals(3.0, moved.x(), 1e-6);
    assertEquals(WORLD_ID.toString(), moved.worldIdentity());
  }

  @Test
  void apply_clearsMapsAndDestroysRigWhenNotPlaying() throws Exception {
    PaperCinematicController controller = newController();
    CameraDolly dolly = new CameraDolly(mockPlugin());
    controller.attachDolly(dolly);
    Location start = new Location(mockWorld(), 0, 64, 0, 0, 0);
    locationMap(controller).put(PLAYER_ID, start.clone());
    poseMap(controller, "playSceneOrigins")
        .put(PLAYER_ID, new CameraPose("relative", 0, 0, 0, 0, 0));
    Player player = mockPlayer(PLAYER_ID, start);
    PlaybackSnapshot playing =
        PlaybackSnapshot.playing(
            new SceneSample(new CameraPose(WORLD_ID.toString(), 1, 64, 1, 0, 0), null, List.of(), List.of()),
            new CameraPose("relative", 0, 0, 0, 0, 0),
            null);
    withBukkit(
        () -> {
          try {
            invokeApply(controller, player, playing);
            assertNotNull(rigMap(controller).get(PLAYER_ID));
            invokeApply(
                controller,
                player,
                PlaybackSnapshot.restored(new CameraPose(WORLD_ID.toString(), 0, 64, 0, 0, 0)));
          } catch (Exception e) {
            throw new AssertionError(e);
          }
        });
    assertFalse(locationMap(controller).containsKey(PLAYER_ID));
    assertFalse(poseMap(controller, "playSceneOrigins").containsKey(PLAYER_ID));
    assertFalse(rigMap(controller).containsKey(PLAYER_ID));
    assertFalse(dolly.isHidden(player));
  }

  @Test
  void onQuit_clearsPlaybackMaps() throws Exception {
    PaperCinematicController controller = newController();
    locationMap(controller).put(PLAYER_ID, new Location(mockWorld(), 0, 64, 0, 0, 0));
    poseMap(controller, "playSceneOrigins")
        .put(PLAYER_ID, new CameraPose("relative", 0, 0, 0, 0, 0));
    Player player = mockPlayer(PLAYER_ID, new Location(mockWorld(), 0, 64, 0, 0, 0));
    withBukkit(
        () ->
            controller.onQuit(
                new org.bukkit.event.player.PlayerQuitEvent(player, "quit")));
    assertFalse(locationMap(controller).containsKey(PLAYER_ID));
    assertFalse(poseMap(controller, "playSceneOrigins").containsKey(PLAYER_ID));
  }

  private static PaperCinematicController newController() {
    return new PaperCinematicController(
        mockPlugin(),
        stubCinematic((id, name, pose) -> CinematicResult.SUCCESS),
        stubExperience((id, name, pose) -> CinematicResult.SUCCESS));
  }

  private static CinematicService stubCinematic(Play play) {
    return new CinematicService() {
      @Override
      public CinematicResult save(CinematicScene scene) {
        return CinematicResult.UNKNOWN_SCENE;
      }

      @Override
      public CinematicResult create(String name) {
        return CinematicResult.UNKNOWN_SCENE;
      }

      @Override
      public CinematicResult addKeyframe(String sceneName, CameraPose pose) {
        return CinematicResult.UNKNOWN_SCENE;
      }

      @Override
      public CinematicResult addKeyframe(String sceneName, CameraKeyframe keyframe) {
        return CinematicResult.UNKNOWN_SCENE;
      }

      @Override
      public CinematicResult addShader(String sceneName, dev.cinematics.api.OverlayCue cue) {
        return CinematicResult.UNKNOWN_SCENE;
      }

      @Override
      public CinematicResult addProp(String sceneName, dev.cinematics.api.PropCue cue) {
        return CinematicResult.UNKNOWN_SCENE;
      }

      @Override
      public java.util.Optional<CinematicScene> scene(String name) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Collection<CinematicScene> scenes() {
        return List.of();
      }

      @Override
      public CinematicResult play(UUID playerId, String sceneName, CameraPose currentPose) {
        return play.play(playerId, sceneName, currentPose);
      }

      @Override
      public java.util.Optional<PlaybackSnapshot> stop(UUID playerId) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Optional<PlaybackSnapshot> samplePlayback(UUID playerId, double elapsedSeconds) {
        return java.util.Optional.empty();
      }
    };
  }

  private static ExperienceService stubExperience(Start start) {
    return new ExperienceService() {
      @Override
      public CinematicResult save(dev.cinematics.api.Experience experience) {
        return CinematicResult.UNKNOWN_EXPERIENCE;
      }

      @Override
      public CinematicResult addTimelineBeat(
          String experienceName, dev.cinematics.api.TimelineBeat beat) {
        return CinematicResult.UNKNOWN_EXPERIENCE;
      }

      @Override
      public java.util.Optional<dev.cinematics.api.Experience> experience(String name) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Collection<dev.cinematics.api.Experience> experiences() {
        return List.of();
      }

      @Override
      public CinematicResult start(UUID playerId, String experienceName, CameraPose currentPose) {
        return start.start(playerId, experienceName, currentPose);
      }

      @Override
      public java.util.Optional<dev.cinematics.api.ExperienceSnapshot> stop(UUID playerId) {
        return java.util.Optional.empty();
      }

      @Override
      public java.util.Optional<dev.cinematics.api.ExperienceSnapshot> sample(
          UUID playerId, double elapsedSeconds) {
        return java.util.Optional.empty();
      }
    };
  }

  @FunctionalInterface
  private interface Play {
    CinematicResult play(UUID playerId, String sceneName, CameraPose currentPose);
  }

  @FunctionalInterface
  private interface Start {
    CinematicResult start(UUID playerId, String experienceName, CameraPose currentPose);
  }

  private static void invokeApply(
      PaperCinematicController controller, Player player, PlaybackSnapshot snapshot)
      throws Exception {
    Method apply =
        PaperCinematicController.class.getDeclaredMethod(
            "apply", Player.class, PlaybackSnapshot.class);
    apply.setAccessible(true);
    apply.invoke(controller, player, snapshot);
  }

  private static ConcurrentMap<UUID, Location> locationMap(PaperCinematicController controller)
      throws Exception {
    return mapField(controller, "playStartLocations");
  }

  private static ConcurrentMap<UUID, CameraPose> poseMap(
      PaperCinematicController controller, String name) throws Exception {
    return mapField(controller, name);
  }

  private static ConcurrentMap<UUID, CameraRig> rigMap(PaperCinematicController controller)
      throws Exception {
    return mapField(controller, "rigs");
  }

  @SuppressWarnings("unchecked")
  private static <K, V> ConcurrentMap<K, V> mapField(Object target, String name)
      throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (ConcurrentMap<K, V>) field.get(target);
  }

  private static void putDummy(
      PlayerSkinDummy dummies, String dummyId, CameraPose pose, Integer entityId, boolean shown)
      throws Exception {
    Class<?> dummyClass =
        Class.forName(
            "dev.cinematics.paper.PlayerSkinDummy$Dummy",
            false,
            dummies.getClass().getClassLoader());
    Constructor<?> constructor =
        dummyClass.getDeclaredConstructor(
            String.class,
            UUID.class,
            PlayerProfile.class,
            CameraPose.class,
            Integer.class,
            boolean.class);
    constructor.setAccessible(true);
    Object dummy =
        constructor.newInstance(dummyId, UUID.randomUUID(), mockProfile(), pose, entityId, shown);
    Field field = PlayerSkinDummy.class.getDeclaredField("dummies");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) field.get(dummies);
    map.put(dummyId, dummy);
  }

  private static CameraPose storedDummyPose(PlayerSkinDummy dummies, String dummyId)
      throws Exception {
    Field field = PlayerSkinDummy.class.getDeclaredField("dummies");
    field.setAccessible(true);
    Map<String, Object> map = (Map<String, Object>) field.get(dummies);
    Object dummy = map.get(dummyId);
    return (CameraPose) dummy.getClass().getMethod("pose").invoke(dummy);
  }

  private static void withBukkit(Runnable action) {
    try {
      Field serverField = Bukkit.class.getDeclaredField("server");
      serverField.setAccessible(true);
      Server previous = (Server) serverField.get(null);
      World world = mockWorld();
      serverField.set(null, mockServer(world));
      try {
        action.run();
      } finally {
        serverField.set(null, previous);
      }
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }


  private static void withPacketEvents(Runnable action) throws Exception {
    Method method =
        PlayerSkinDummyTest.class.getDeclaredMethod("withPacketEvents", List.class, Runnable.class);
    method.setAccessible(true);
    method.invoke(null, new java.util.ArrayList<>(), action);
  }

  private static World mockWorld() {
    return (World)
        java.lang.reflect.Proxy.newProxyInstance(
            PaperCinematicControllerDualTickTest.class.getClassLoader(),
            new Class[] {World.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getUID".equals(name)) {
                return WORLD_ID;
              }
              if ("getName".equals(name)) {
                return "world";
              }
              if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
              }
              if ("hashCode".equals(name)) {
                return WORLD_ID.hashCode();
              }
              return null;
            });
  }

  private static Server mockServer(World world) {
    return (Server)
        java.lang.reflect.Proxy.newProxyInstance(
            PaperCinematicControllerDualTickTest.class.getClassLoader(),
            new Class[] {Server.class},
            (proxy, method, args) -> {
              if ("getOnlinePlayers".equals(method.getName())) {
                return List.of();
              }
              if ("getWorld".equals(method.getName()) && args != null && args.length == 1) {
                Object arg = args[0];
                if (arg instanceof UUID uuid && uuid.equals(WORLD_ID)) {
                  return world;
                }
                if ("world".equals(arg)) {
                  return world;
                }
                return null;
              }
              if ("getName".equals(method.getName())) {
                return "test";
              }
              return null;
            });
  }

  private static Plugin mockPlugin() {
    Server server = mockServer(mockWorld());
    return (Plugin)
        java.lang.reflect.Proxy.newProxyInstance(
            PaperCinematicControllerDualTickTest.class.getClassLoader(),
            new Class[] {Plugin.class},
            (proxy, method, args) -> {
              if ("getName".equals(method.getName())) {
                return "test";
              }
              if ("getServer".equals(method.getName())) {
                return server;
              }
              return null;
            });
  }

  private static PlayerProfile mockProfile() {
    return (PlayerProfile)
        java.lang.reflect.Proxy.newProxyInstance(
            PaperCinematicControllerDualTickTest.class.getClassLoader(),
            new Class[] {PlayerProfile.class},
            (proxy, method, args) -> {
              String profileMethod = method.getName();
              if ("getId".equals(profileMethod)) {
                return UUID.randomUUID();
              }
              if ("getName".equals(profileMethod)) {
                return "dummy";
              }
              if ("getProperties".equals(profileMethod)) {
                return Set.of();
              }
              if ("equals".equals(profileMethod) && args != null && args.length == 1) {
                return proxy == args[0];
              }
              if ("hashCode".equals(profileMethod)) {
                return System.identityHashCode(proxy);
              }
              return null;
            });
  }

  private static Player mockPlayer(UUID id, Location location) {
    Server server = mockServer(location.getWorld());
    return (Player)
        java.lang.reflect.Proxy.newProxyInstance(
            PaperCinematicControllerDualTickTest.class.getClassLoader(),
            new Class[] {Player.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getUniqueId".equals(name)) {
                return id;
              }
              if ("getLocation".equals(name)) {
                return location;
              }
              if ("getServer".equals(name)) {
                return server;
              }
              if ("getScheduler".equals(name)) {
                return schedulerReturningNull();
              }
              if ("getName".equals(name)) {
                return "player";
              }
              if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
              }
              if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
              }
              if (method.getReturnType() == boolean.class) {
                return false;
              }
              return null;
            });
  }

  private static Object schedulerReturningNull() {
    return java.lang.reflect.Proxy.newProxyInstance(
        PaperCinematicControllerDualTickTest.class.getClassLoader(),
        new Class[] {io.papermc.paper.threadedregions.scheduler.EntityScheduler.class},
        (proxy, method, args) -> null);
  }
}
