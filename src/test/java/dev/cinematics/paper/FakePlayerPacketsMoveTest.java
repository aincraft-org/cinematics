package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FakePlayerPacketsMoveTest {

  private final List<PacketWrapper<?>> sentPackets = new ArrayList<>();
  private PacketEventsAPI<?> previousApi;

  @BeforeEach
  void installPacketEvents() {
    sentPackets.clear();
    previousApi = PacketEvents.getAPI();
    PacketEvents.setAPI(new StubPacketEventsApi(capturingPlayerManager()));
  }

  @AfterEach
  void restorePacketEvents() {
    PacketEvents.setAPI(previousApi);
  }

  @Test
  void rejectsNullPose() {
    FakePlayerPackets packets = new FakePlayerPackets();
    assertThrows(NullPointerException.class, () -> packets.move(1, null, List.of(mockPlayer())));
  }

  @Test
  void rejectsNullViewers() {
    FakePlayerPackets packets = new FakePlayerPackets();
    CameraPose pose = new CameraPose("world", 1.0, 2.0, 3.0, 90.0f, 45.0f);
    assertThrows(NullPointerException.class, () -> packets.move(1, pose, null));
  }

  @Test
  void noPacketsWhenViewersEmpty() {
    FakePlayerPackets packets = new FakePlayerPackets();
    CameraPose pose = new CameraPose("world", 1.0, 2.0, 3.0, 90.0f, 45.0f);
    packets.move(7, pose, List.of());
    assertEquals(0, sentPackets.size());
  }

  @Test
  void sendsTeleportAndHeadLookToEachViewer() {
    FakePlayerPackets packets = new FakePlayerPackets();
    CameraPose pose = new CameraPose("world", 10.0, 20.0, 30.0, 135.0f, -15.0f);
    Player first = mockPlayer();
    Player second = mockPlayer();

    packets.move(42, pose, List.of(first, second));

    assertEquals(4, sentPackets.size());
    assertTeleport(sentPackets.get(0), 42, pose);
    assertTeleport(sentPackets.get(1), 42, pose);
    assertHeadLook(sentPackets.get(2), 42, pose.yaw());
    assertHeadLook(sentPackets.get(3), 42, pose.yaw());
  }

  private PlayerManager capturingPlayerManager() {
    return (PlayerManager)
        java.lang.reflect.Proxy.newProxyInstance(
            FakePlayerPacketsMoveTest.class.getClassLoader(),
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

  private static void assertTeleport(PacketWrapper<?> packet, int entityId, CameraPose pose) {
    WrapperPlayServerEntityTeleport teleport = (WrapperPlayServerEntityTeleport) packet;
    assertEquals(entityId, teleport.getEntityId());
    assertEquals(pose.x(), teleport.getPosition().x);
    assertEquals(pose.y(), teleport.getPosition().y);
    assertEquals(pose.z(), teleport.getPosition().z);
    assertEquals(pose.yaw(), teleport.getYaw());
    assertEquals(pose.pitch(), teleport.getPitch());
    assertEquals(false, teleport.isOnGround());
  }

  private static void assertHeadLook(PacketWrapper<?> packet, int entityId, float yaw) {
    WrapperPlayServerEntityHeadLook headLook = (WrapperPlayServerEntityHeadLook) packet;
    assertEquals(entityId, headLook.getEntityId());
    assertEquals(yaw, headLook.getHeadYaw());
  }

  private static Player mockPlayer() {
    return (Player)
        java.lang.reflect.Proxy.newProxyInstance(
            FakePlayerPacketsMoveTest.class.getClassLoader(),
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
              FakePlayerPacketsMoveTest.class.getClassLoader(),
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
