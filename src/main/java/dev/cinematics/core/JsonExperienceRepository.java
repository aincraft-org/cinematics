package dev.cinematics.core;

import dev.cinematics.api.Audience;
import dev.cinematics.api.CompletionAction;
import dev.cinematics.api.Experience;
import dev.cinematics.api.ExperienceBeat;
import dev.cinematics.api.ExperienceFrame;
import dev.cinematics.api.FadeFrame;
import dev.cinematics.api.PathFrame;
import dev.cinematics.api.TimelineBeat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * File-backed {@link ExperienceRepository}: one JSON document per experience.
 *
 * <p>Unknown beat types are dropped. A corrupted document degrades to unknown rather than failing
 * load.
 */
public final class JsonExperienceRepository implements ExperienceRepository {

  private final Path dataDirectory;

  public JsonExperienceRepository(Path dataDirectory) {
    this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
    try {
      Files.createDirectories(dataDirectory);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Failed to create experience data directory: " + dataDirectory, e);
    }
  }

  @Override
  public void save(Experience experience) {
    Objects.requireNonNull(experience, "experience");
    Path file = fileFor(experience.name());
    try {
      Files.createDirectories(dataDirectory);
      Files.writeString(file, encode(experience), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to save experience " + experience.name(), e);
    }
  }

  @Override
  public Optional<Experience> find(String name) {
    Path file = fileFor(name);
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(decode(Files.readString(file, StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read experience " + name, e);
    }
  }

  @Override
  public Collection<Experience> loadAll() {
    List<Experience> loaded = new ArrayList<>();
    if (!Files.isDirectory(dataDirectory)) {
      return List.of();
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory, "*.json")) {
      for (Path file : stream) {
        readExperience(file).ifPresent(loaded::add);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list experiences", e);
    }
    return List.copyOf(loaded);
  }

  @Override
  public void close() {
    // File-backed store holds no open resources.
  }

  Path fileFor(String name) {
    return dataDirectory.resolve(name + ".json");
  }

  private static Optional<Experience> readExperience(Path file) {
    try {
      return Optional.ofNullable(decode(Files.readString(file, StandardCharsets.UTF_8)));
    } catch (IOException failedRead) {
      return Optional.empty();
    }
  }

  static String encode(Experience experience) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("{\n");
    sb.append("  \"name\": ")
        .append(JsonCinematicRepository.encodeString(experience.name()))
        .append(",\n");
    sb.append("  \"audience\": ")
        .append(
            JsonCinematicRepository.encodeString(
                experience.audience().name().toLowerCase(Locale.ROOT)))
        .append(",\n");
    sb.append("  \"on_complete\": ")
        .append(
            JsonCinematicRepository.encodeString(
                experience.onComplete().name().toLowerCase(Locale.ROOT)))
        .append(",\n");
    sb.append("  \"entry\": ")
        .append(JsonCinematicRepository.encodeString(experience.entry()))
        .append(",\n");
    sb.append("  \"frames\": [\n");
    List<ExperienceFrame> frames = experience.frames();
    for (int i = 0; i < frames.size(); i++) {
      sb.append("    ").append(encodeFrame(frames.get(i)));
      if (i + 1 < frames.size()) {
        sb.append(',');
      }
      sb.append('\n');
    }
    sb.append("  ]\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static String encodeFrame(ExperienceFrame frame) {
    String nextField =
        frame
            .next()
            .map(id -> ", \"next\": " + JsonCinematicRepository.encodeString(id))
            .orElse("");
    if (frame instanceof PathFrame path) {
      return "{\"id\": "
          + JsonCinematicRepository.encodeString(path.id())
          + ", \"type\": \"path\", \"scene\": "
          + JsonCinematicRepository.encodeString(path.sceneName())
          + nextField
          + "}";
    }
    if (frame instanceof FadeFrame fade) {
      return "{\"id\": "
          + JsonCinematicRepository.encodeString(fade.id())
          + ", \"type\": \"fade\", \"overlay\": "
          + JsonCinematicRepository.encodeString(fade.overlayId())
          + ", \"duration\": "
          + fade.durationSeconds()
          + nextField
          + "}";
    }
    return "{}";
  }

  static Experience decode(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    String name = JsonCinematicRepository.stringField(json, "name");
    if (name == null || name.isBlank()) {
      return null;
    }
    Audience audience = parseAudience(JsonCinematicRepository.stringField(json, "audience"));
    CompletionAction onComplete =
        parseCompletion(JsonCinematicRepository.stringField(json, "on_complete"));
    List<ExperienceFrame> frames = new ArrayList<>();
    for (String body : JsonCinematicRepository.objectArrayBodies(json, "frames")) {
      ExperienceFrame frame = decodeFrame(body);
      if (frame != null) {
        frames.add(frame);
      }
    }
    if (frames.isEmpty()) {
      List<ExperienceBeat> beats = new ArrayList<>();
      for (String body : JsonCinematicRepository.objectArrayBodies(json, "beats")) {
        ExperienceBeat beat = decodeBeat(body);
        if (beat != null) {
          beats.add(beat);
        }
      }
      try {
        return Experience.load(name, audience, onComplete, beats);
      } catch (IllegalArgumentException invalid) {
        return null;
      }
    }
    String entry = JsonCinematicRepository.stringField(json, "entry");
    if (entry == null) {
      entry = frames.getFirst().id();
    }
    try {
      return Experience.graph(name, audience, onComplete, entry, frames);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static ExperienceFrame decodeFrame(String body) {
    String id = JsonCinematicRepository.stringField(body, "id");
    String type = JsonCinematicRepository.stringField(body, "type");
    if (id == null) {
      return null;
    }
    String nextRaw = JsonCinematicRepository.stringField(body, "next");
    java.util.Optional<String> next =
        nextRaw == null || nextRaw.isBlank()
            ? java.util.Optional.empty()
            : java.util.Optional.of(nextRaw);
    String kind = type == null ? "path" : type.trim().toLowerCase(Locale.ROOT);
    try {
      if ("fade".equals(kind)) {
        String overlay = JsonCinematicRepository.stringField(body, "overlay");
        Double duration = JsonCinematicRepository.numberField(body, "duration");
        if (overlay == null || duration == null) {
          return null;
        }
        return new FadeFrame(id, overlay, duration, next);
      }
      if ("path".equals(kind) || "timeline".equals(kind)) {
        String scene = JsonCinematicRepository.stringField(body, "scene");
        if (scene == null) {
          return null;
        }
        return new PathFrame(id, scene, next);
      }
      return null;
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static ExperienceBeat decodeBeat(String body) {
    String id = JsonCinematicRepository.stringField(body, "id");
    String type = JsonCinematicRepository.stringField(body, "type");
    String scene = JsonCinematicRepository.stringField(body, "scene");
    if (id == null || scene == null) {
      return null;
    }
    if (type != null
        && !"timeline".equals(type.trim().toLowerCase(Locale.ROOT))
        && !"path".equals(type.trim().toLowerCase(Locale.ROOT))) {
      return null;
    }
    try {
      return new TimelineBeat(id, scene);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static Audience parseAudience(String raw) {
    if (raw == null) {
      return Audience.SUBJECT;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "spectators" -> Audience.SPECTATORS;
      case "public" -> Audience.PUBLIC;
      default -> Audience.SUBJECT;
    };
  }

  private static CompletionAction parseCompletion(String raw) {
    if (raw == null) {
      return CompletionAction.RESTORE;
    }
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "teleport" -> CompletionAction.TELEPORT;
      default -> CompletionAction.RESTORE;
    };
  }
}
