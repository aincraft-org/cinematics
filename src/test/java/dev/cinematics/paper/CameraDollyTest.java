package dev.cinematics.paper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class CameraDollyTest {

  @Test
  void rejectsNullPlugin() {
    assertThrows(NullPointerException.class, () -> new CameraDolly(null));
  }

  @Test
  void hiddenTrackingAndRestoreAll() throws Exception {
    Server server = mockServer();
    Plugin plugin = mockPlugin(server);
    CameraDolly dolly = new CameraDolly(plugin);
    UUID id = UUID.randomUUID();
    Player player = mockPlayer(id, server);
    assertFalse(dolly.isHidden(player));
    Field hiddenField = CameraDolly.class.getDeclaredField("hidden");
    hiddenField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<UUID> hidden = (Set<UUID>) hiddenField.get(dolly);
    hidden.add(id);
    assertTrue(dolly.isHidden(player));
    assertDoesNotThrow(() -> dolly.restore(player));
    assertFalse(dolly.isHidden(player));
    hidden.add(id);
    assertTrue(dolly.isHidden(player));
    assertDoesNotThrow(dolly::restoreAll);
    assertFalse(dolly.isHidden(player));
  }

  @Test
  void restoreAllOnEmptyDoesNotThrow() {
    Server server = mockServer();
    Plugin plugin = mockPlugin(server);
    CameraDolly dolly = new CameraDolly(plugin);
    assertDoesNotThrow(dolly::restoreAll);
  }

  private static Plugin mockPlugin(Server server) {
    return (Plugin)
        java.lang.reflect.Proxy.newProxyInstance(
            CameraDollyTest.class.getClassLoader(),
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

  private static Server mockServer() {
    return (Server)
        java.lang.reflect.Proxy.newProxyInstance(
            CameraDollyTest.class.getClassLoader(),
            new Class[] {Server.class},
            (proxy, method, args) -> {
              if ("getOnlinePlayers".equals(method.getName())) {
                return java.util.List.of();
              }
              if ("getName".equals(method.getName())) {
                return "test";
              }
              if ("getPlayer".equals(method.getName()) && args != null && args.length == 1) {
                return null;
              }
              return null;
            });
  }

  private static Player mockPlayer(UUID id, Server server) {
    return (Player)
        java.lang.reflect.Proxy.newProxyInstance(
            CameraDollyTest.class.getClassLoader(),
            new Class[] {Player.class},
            (proxy, method, args) -> {
              String name = method.getName();
              if ("getUniqueId".equals(name)) {
                return id;
              }
              if ("getServer".equals(name)) {
                return server;
              }
              if ("getName".equals(name)) {
                return "test-player";
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
}
