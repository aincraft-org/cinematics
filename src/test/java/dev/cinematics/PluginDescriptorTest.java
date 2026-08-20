package dev.cinematics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Structural check that shipped plugin metadata declares the cinematic command. */
class PluginDescriptorTest {

  @Test
  void paperPluginYmlDeclaresMainCommandAndPermission() throws Exception {
    try (InputStream in =
        Objects.requireNonNull(
            PluginDescriptorTest.class.getClassLoader().getResourceAsStream("paper-plugin.yml"),
            "paper-plugin.yml missing")) {
      String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(
          yaml.contains("main: dev.cinematics.CinematicsPlugin"),
          "main class should be CinematicsPlugin");
      assertTrue(yaml.contains("folia-supported: true"), "folia-supported should be true");
      assertTrue(yaml.contains("api-version: '1.21'"), "api-version should be 1.21");
      assertTrue(yaml.contains("cinematic:"), "cinematic command should be declared");
      assertTrue(yaml.contains("cinematics.use:"), "cinematic permission should be declared");
      assertTrue(
          yaml.contains("aliases: [cinematics, cine]"), "cinematic aliases should be declared");
    }
  }

  @Test
  void mainClassExtendsJavaPlugin() throws ClassNotFoundException {
    Class<?> main = Class.forName("dev.cinematics.CinematicsPlugin");
    assertTrue(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(main));
  }

  @Test
  void pluginHoldsAndRegistersCinematicService() throws Exception {
    Class<?> plugin = Class.forName("dev.cinematics.CinematicsPlugin");
    plugin.getDeclaredField("cinematicService");
    Class<?> api = Class.forName("dev.cinematics.api.CinematicService");
    assertTrue(api.isInterface(), "CinematicService should be an SPI interface");
    String source =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/dev/cinematics/CinematicsPlugin.java"));
    assertTrue(
        source.contains("register(CinematicService.class"),
        "CinematicsPlugin should register CinematicService on ServicesManager");
    assertTrue(
        source.contains("new CinematicCommand("),
        "CinematicsPlugin should register the cinematic command");
    assertTrue(
        source.contains("register(ExperienceService.class"),
        "CinematicsPlugin should register ExperienceService on ServicesManager");
    String commandSource =
        java.nio.file.Files.readString(
            java.nio.file.Path.of("src/main/java/dev/cinematics/paper/CinematicCommand.java"));
    assertTrue(commandSource.contains("camera add"), "command must add a camera keyframe");
    assertTrue(commandSource.contains("/cinematic play"), "command must play a named scene");
    assertTrue(commandSource.contains("/cinematic stop"), "command must stop playback");
    assertTrue(
        commandSource.contains("/cinematic experience play"),
        "command must play a named experience");
  }
}
