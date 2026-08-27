package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
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

  @Test
  void moveToReturnsUnknownSceneForMissingDummy() {
    List<PacketWrapper<?>> sentPackets = new ArrayList<>();
    withPacketEvents(
        sentPackets,
        () ->
            withBukkitServer(
                List.of(),
                () -> {
                  PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
                  CameraPose pose = new CameraPose("world", 1.0, 2.0, 3.0, 0.0f, 0.0f);
                  assertEquals(CinematicResult.UNKNOWN_SCENE, dummies.moveTo("missing", pose));
                  assertEquals(0, sentPackets.size());
                }));
  }

  @Test
  void moveToReturnsSuccessWithoutPacketsWhenHidden() {
    List<PacketWrapper<?>> sentPackets = new ArrayList<>();
    withPacketEvents(
        sentPackets,
        () ->
            withBukkitServer(
                List.of(),
                () -> {
                  PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
                  CameraPose initial = new CameraPose("world", 0.0, 0.0, 0.0, 0.0f, 0.0f);
                  CameraPose target = new CameraPose("world", 5.0, 6.0, 7.0, 45.0f, -10.0f);
                  putDummy(dummies, "hero", initial, null, false);
                  assertEquals(CinematicResult.SUCCESS, dummies.moveTo("hero", target));
                  assertEquals(0, sentPackets.size());
                  assertEquals(initial, storedPose(dummies, "hero"));
                }));
  }

  @Test
  void moveToUpdatesPoseAndSendsPacketsWhenShown() {
    List<PacketWrapper<?>> sentPackets = new ArrayList<>();
    Player viewer = mockPlayer();
    withPacketEvents(
        sentPackets,
        () ->
            withBukkitServer(
                List.of(viewer),
                () -> {
                  PlayerSkinDummy dummies = new PlayerSkinDummy(new FakePlayerPackets());
                  CameraPose initial = new CameraPose("world", 0.0, 0.0, 0.0, 0.0f, 0.0f);
                  CameraPose target = new CameraPose("world", 8.0, 9.0, 10.0, 180.0f, 30.0f);
                  putDummy(dummies, "hero", initial, -7, true);
                  assertEquals(CinematicResult.SUCCESS, dummies.moveTo("hero", target));
                  assertEquals(2, sentPackets.size());
                  assertTeleport(sentPackets.get(0), -7, target);
                  assertHeadLook(sentPackets.get(1), -7, target.yaw());
                  assertEquals(target, storedPose(dummies, "hero"));
                }));
  }

  private static void withBukkitServer(List<Player> players, Runnable action) {
    try {
      Field field = Bukkit.class.getDeclaredField("server");
      field.setAccessible(true);
      Server previous = (Server) field.get(null);
      field.set(null, mockServer(players));
      try {
        action.run();
      } finally {
        field.set(null, previous);
      }
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static void withPacketEvents(List<PacketWrapper<?>> sentPackets, Runnable action) {
    PacketEventsAPI<?> previousApi = PacketEvents.getAPI();
    PacketEvents.setAPI(new StubPacketEventsApi(capturingPlayerManager(sentPackets)));
    try {
      action.run();
    } finally {
      PacketEvents.setAPI(previousApi);
    }
  }

  private static PlayerManager capturingPlayerManager(List<PacketWrapper<?>> sentPackets) {
    return (PlayerManager)
        java.lang.reflect.Proxy.newProxyInstance(
            PlayerSkinDummyTest.class.getClassLoader(),
            new Class[] {PlayerManager.class},
            (proxy, method, args) -> {
              if ("sendPacket".equals(method.getName())
                  && args != null
                  && args.length == 2
                  && args[1] instanceof PacketWrapper<?> packet) {
                sentPackets.add(packet);
                return null;
              }
              return defaultValue(method.getReturnType());
            });
  }

  private static void putDummy(
      PlayerSkinDummy dummies, String dummyId, CameraPose pose, Integer entityId, boolean shown) {
    try {
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
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static CameraPose storedPose(PlayerSkinDummy dummies, String dummyId) {
    try {
      Field field = PlayerSkinDummy.class.getDeclaredField("dummies");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) field.get(dummies);
      Object dummy = map.get(dummyId);
      return (CameraPose) dummy.getClass().getMethod("pose").invoke(dummy);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static PlayerProfile mockProfile() {
    return (PlayerProfile)
        java.lang.reflect.Proxy.newProxyInstance(
            PlayerSkinDummyTest.class.getClassLoader(),
            new Class[] {PlayerProfile.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getId".equals(name)) {
                return UUID.randomUUID();
              }
              if ("getName".equals(name)) {
                return "dummy";
              }
              if ("getProperties".equals(name)) {
                return List.of();
              }
              if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
              }
              if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
              }
              return null;
            });
  }

  private static Server mockServer(List<Player> onlinePlayers) {
    return (Server)
        java.lang.reflect.Proxy.newProxyInstance(
            PlayerSkinDummyTest.class.getClassLoader(),
            new Class[] {Server.class},
            (proxy, method, args) -> {
              if ("getOnlinePlayers".equals(method.getName())) {
                return onlinePlayers;
              }
              if ("getName".equals(method.getName())) {
                return "test";
              }
              return null;
            });
  }

  private static Player mockPlayer() {
    return (Player)
        java.lang.reflect.Proxy.newProxyInstance(
            PlayerSkinDummyTest.class.getClassLoader(),
            new Class[] {Player.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getUniqueId".equals(name)) {
                return UUID.randomUUID();
              }
              if ("equals".equals(name) && args != null && args.length == 1) {
                return proxy == args[0];
              }
              if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
              }
              return defaultValue(method.getReturnType());
            });
  }

  private static void assertTeleport(PacketWrapper<?> packet, int entityId, CameraPose pose) {
    WrapperPlayServerEntityTeleport teleport = (WrapperPlayServerEntityTeleport) packet;
    assertEquals(entityId, teleport.getEntityId());
    assertEquals(pose.x(), teleport.getPosition().x);
    assertEquals(pose.y(), teleport.getPosition().y);
    assertEquals(pose.z(), teleport.getPosition().z);
    assertEquals(pose.yaw(), teleport.getYaw());
    assertEquals(pose.pitch(), teleport.getPitch());
  }

  private static void assertHeadLook(PacketWrapper<?> packet, int entityId, float yaw) {
    WrapperPlayServerEntityHeadLook headLook = (WrapperPlayServerEntityHeadLook) packet;
    assertEquals(entityId, headLook.getEntityId());
    assertEquals(yaw, headLook.getHeadYaw());
  }

  private static Object defaultValue(Class<?> returnType) {
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == byte.class) {
      return (byte) 0;
    }
    if (returnType == short.class) {
      return (short) 0;
    }
    if (returnType == int.class) {
      return 0;
    }
    if (returnType == long.class) {
      return 0L;
    }
    if (returnType == float.class) {
      return 0.0f;
    }
    if (returnType == double.class) {
      return 0.0;
    }
    if (returnType == char.class) {
      return '\0';
    }
    return null;
  }

  private static final class StubPacketEventsApi extends PacketEventsAPI<Void> {
    private final PlayerManager playerManager;

    private StubPacketEventsApi(PlayerManager playerManager) {
      this.playerManager = playerManager;
    }

    @Override
    public boolean isLoaded() {
      return true;
    }

    @Override
    public void init() {}

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public Void getPlugin() {
      return null;
    }

    @Override
    public ServerManager getServerManager() {
      return (ServerManager)
          java.lang.reflect.Proxy.newProxyInstance(
              getClass().getClassLoader(),
              new Class[] {ServerManager.class},
              (proxy, method, args) ->
                  "getVersion".equals(method.getName())
                      ? com.github.retrooper.packetevents.manager.server.ServerVersion.V_1_21_11
                      : defaultValue(method.getReturnType()));
    }

    @Override
    public ProtocolManager getProtocolManager() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PlayerManager getPlayerManager() {
      return playerManager;
    }

    @Override
    public NettyManager getNettyManager() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChannelInjector getInjector() {
      throw new UnsupportedOperationException();
    }
  }
}
