package dev.cinematics.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PlayerSkinDummy {

  private final Map<String, Dummy> dummies = new ConcurrentHashMap<>();
  private final FakePlayerPackets packets;
  private final AtomicInteger nextEntityId = new AtomicInteger();

  public PlayerSkinDummy(FakePlayerPackets packets) {
    this.packets = Objects.requireNonNull(packets, "packets");
  }

  public CinematicResult create(String dummyId, Player player) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    if (dummies.containsKey(normalized)) {
      return CinematicResult.ALREADY_EXISTS;
    }
    PlayerProfile profile = player.getPlayerProfile();
    CameraPose pose = PaperCinematicController.poseOf(player.getLocation());
    dummies.put(
        normalized, new Dummy(normalized, player.getUniqueId(), profile, pose, null, false));
    return CinematicResult.SUCCESS;
  }

  public CinematicResult show(String dummyId) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.get(normalized);
    if (dummy == null) {
      return CinematicResult.UNKNOWN_SCENE;
    }
    if (dummy.shown()) {
      return CinematicResult.SUCCESS;
    }
    int entityId = allocateEntityId();
    Collection<Player> viewers = List.copyOf(Bukkit.getServer().getOnlinePlayers());
    packets.spawn(packets.fromPlayerProfile(dummy.profile()), entityId, dummy.pose(), viewers);
    dummies.put(
        normalized,
        new Dummy(
            dummy.dummyId(), dummy.playerId(), dummy.profile(), dummy.pose(), entityId, true));
    return CinematicResult.SUCCESS;
  }

  public CinematicResult hide(String dummyId) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.get(normalized);
    if (dummy == null || !dummy.shown()) {
      return CinematicResult.SUCCESS;
    }
    Collection<Player> viewers = List.copyOf(Bukkit.getServer().getOnlinePlayers());
    packets.destroy(dummy.playerId(), dummy.entityId(), viewers);
    dummies.put(
        normalized,
        new Dummy(dummy.dummyId(), dummy.playerId(), dummy.profile(), dummy.pose(), null, false));
    return CinematicResult.SUCCESS;
  }

  public CinematicResult destroy(String dummyId) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.remove(normalized);
    if (dummy == null) {
      return CinematicResult.UNKNOWN_SCENE;
    }
    if (dummy.shown()) {
      packets.destroy(
          dummy.playerId(), dummy.entityId(), List.copyOf(Bukkit.getServer().getOnlinePlayers()));
    }
    return CinematicResult.SUCCESS;
  }

  public CinematicResult move(String dummyId, CameraPose pose) {
    String normalized = CinematicScene.normalizeName(dummyId);
    if (normalized == null) {
      return CinematicResult.INVALID_NAME;
    }
    Dummy dummy = dummies.get(normalized);
    if (dummy == null) {
      return CinematicResult.UNKNOWN_SCENE;
    }
    dummies.put(
        normalized,
        new Dummy(
            dummy.dummyId(),
            dummy.playerId(),
            dummy.profile(),
            pose,
            dummy.entityId(),
            dummy.shown()));
    if (dummy.shown()) {
      hide(normalized);
      show(normalized);
    }
    return CinematicResult.SUCCESS;
  }

  public Collection<String> list() {
    return List.copyOf(dummies.keySet());
  }

  void hideAllShown() {
    for (String name : List.copyOf(dummies.keySet())) {
      Dummy dummy = dummies.get(name);
      if (dummy != null && dummy.shown()) {
        hide(name);
      }
    }
  }

  int allocateEntityId() {
    return -1 * nextEntityId.incrementAndGet();
  }

  private record Dummy(
      String dummyId,
      UUID playerId,
      PlayerProfile profile,
      CameraPose pose,
      Integer entityId,
      boolean shown) {}
}
