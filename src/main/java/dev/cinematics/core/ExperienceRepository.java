package dev.cinematics.core;

import dev.cinematics.api.Experience;
import java.util.Collection;
import java.util.Optional;

/** Persistence for complete experiences. */
interface ExperienceRepository extends AutoCloseable {

  void save(Experience experience);

  Optional<Experience> find(String name);

  Collection<Experience> loadAll();

  @Override
  void close();
}
