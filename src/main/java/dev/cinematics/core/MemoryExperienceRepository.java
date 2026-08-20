package dev.cinematics.core;

import dev.cinematics.api.Experience;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory experience store for tests that do not need JSON. */
final class MemoryExperienceRepository implements ExperienceRepository {

  private final ConcurrentMap<String, Experience> store = new ConcurrentHashMap<>();

  @Override
  public void save(Experience experience) {
    store.put(experience.name(), experience);
  }

  @Override
  public Optional<Experience> find(String name) {
    return Optional.ofNullable(store.get(name));
  }

  @Override
  public Collection<Experience> loadAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void close() {
    store.clear();
  }
}
