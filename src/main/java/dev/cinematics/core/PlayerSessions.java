package dev.cinematics.core;

import dev.cinematics.api.CinematicResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Exclusive per-player playback sessions shared by scene play and experience start. */
final class PlayerSessions {

  private final ConcurrentMap<UUID, Playback> sessions = new ConcurrentHashMap<>();

  CinematicResult occupy(UUID playerId, Playback playback) {
    Playback existing = sessions.putIfAbsent(playerId, playback);
    return existing == null ? CinematicResult.SUCCESS : CinematicResult.ALREADY_PLAYING;
  }

  Optional<Playback> get(UUID playerId) {
    return Optional.ofNullable(sessions.get(playerId));
  }

  Optional<Playback> remove(UUID playerId) {
    return Optional.ofNullable(sessions.remove(playerId));
  }

  boolean remove(UUID playerId, Playback expected) {
    return sessions.remove(playerId, expected);
  }

  void clear() {
    sessions.clear();
  }
}
