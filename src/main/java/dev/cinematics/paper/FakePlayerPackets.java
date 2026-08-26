package dev.cinematics.paper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import dev.cinematics.api.CameraPose;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class FakePlayerPackets {

  private static final byte SKIN_PARTS_ALL = (byte) 0x7F;
  private static final int PLAYER_SKIN_PARTS_INDEX = 17;

  UserProfile fromPlayerProfile(PlayerProfile profile) {
    Objects.requireNonNull(profile, "profile");
    return new UserProfile(
        profile.getId(),
        profile.getName(),
        profile.getProperties().stream()
            .filter(p -> "textures".equals(p.getName()))
            .map(p -> new TextureProperty(p.getName(), p.getValue(), p.getSignature()))
            .toList());
  }

  int spawn(UserProfile profile, int entityId, CameraPose pose, Collection<Player> viewers) {
    Objects.requireNonNull(profile, "profile");
    Objects.requireNonNull(pose, "pose");
    Objects.requireNonNull(viewers, "viewers");
    Vector3d position = new Vector3d(pose.x(), pose.y(), pose.z());

    PacketWrapper<?> addInfo =
        new WrapperPlayServerPlayerInfoUpdate(
            EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER),
            new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                profile, true, 0, GameMode.SURVIVAL, Component.text(profile.getName()), null));
    PacketWrapper<?> spawnEntity =
        new WrapperPlayServerSpawnEntity(
            entityId,
            Optional.of(profile.getUUID()),
            EntityTypes.PLAYER,
            position,
            pose.pitch(),
            pose.yaw(),
            pose.yaw(),
            0,
            Optional.empty());
    PacketWrapper<?> metadata =
        new WrapperPlayServerEntityMetadata(
            entityId,
            List.of(
                new EntityData<>(PLAYER_SKIN_PARTS_INDEX, EntityDataTypes.BYTE, SKIN_PARTS_ALL)));

    send(addInfo, viewers);
    send(spawnEntity, viewers);
    send(metadata, viewers);
    return entityId;
  }

  void destroy(UUID profileId, int entityId, Collection<Player> viewers) {
    Objects.requireNonNull(profileId, "profileId");
    Objects.requireNonNull(viewers, "viewers");
    send(new WrapperPlayServerDestroyEntities(entityId), viewers);
    send(new WrapperPlayServerPlayerInfoRemove(profileId), viewers);
  }

  private static void send(PacketWrapper<?> packet, Collection<Player> viewers) {
    for (Player viewer : viewers) {
      PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
    }
  }
}
