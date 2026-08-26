package dev.cinematics.paper;

import dev.cinematics.api.CameraKeyframe;
import dev.cinematics.api.CameraPose;
import dev.cinematics.api.CinematicResult;
import dev.cinematics.api.CinematicScene;
import dev.cinematics.api.CinematicService;
import dev.cinematics.api.Experience;
import dev.cinematics.api.ExperienceService;
import dev.cinematics.api.OverlayCue;
import dev.cinematics.api.PlaybackSnapshot;
import dev.cinematics.api.PropCue;
import dev.cinematics.api.TimelineBeat;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /cinematic} — operators create camera paths, overlay shaders and props, then play/stop
 * named scenes.
 */
public final class CinematicCommand implements BasicCommand {

  static final String USE_PERMISSION = "cinematics.use";
  private static final List<String> ACTIONS =
      List.of(
          "create",
          "camera",
          "shaders",
          "props",
          "play",
          "stop",
          "list",
          "experience",
          "dummy",
          "dolly");
  private static final List<String> EXPERIENCE_ACTIONS = List.of("create", "beat", "play", "list");
  private static final List<String> DUMMY_ACTIONS =
      List.of("create", "show", "hide", "destroy", "list");

  private final CinematicService cinematicService;
  private final ExperienceService experienceService;
  private final PaperCinematicController controller;
  private final PlayerSkinDummy dummies;
  private final CameraDolly dolly;

  public CinematicCommand(
      CinematicService cinematicService,
      ExperienceService experienceService,
      PaperCinematicController controller,
      PlayerSkinDummy dummies,
      CameraDolly dolly) {
    this.cinematicService = java.util.Objects.requireNonNull(cinematicService, "cinematicService");
    this.experienceService =
        java.util.Objects.requireNonNull(experienceService, "experienceService");
    this.controller = java.util.Objects.requireNonNull(controller, "controller");
    this.dummies = java.util.Objects.requireNonNull(dummies, "dummies");
    this.dolly = java.util.Objects.requireNonNull(dolly, "dolly");
  }

  enum Action {
    CREATE,
    CAMERA,
    SHADERS,
    PROPS,
    PLAY,
    STOP,
    LIST,
    EXPERIENCE,
    DUMMY_CREATE,
    DUMMY_SHOW,
    DUMMY_HIDE,
    DUMMY_DESTROY,
    DUMMY_LIST,
    DOLLY,
    UNKNOWN
  }

  enum ExperienceAction {
    CREATE,
    BEAT,
    PLAY,
    LIST,
    UNKNOWN
  }

  static Action parseAction(String[] args) {
    if (args.length == 0) {
      return Action.UNKNOWN;
    }
    return switch (args[0].toLowerCase(Locale.ROOT)) {
      case "create" -> Action.CREATE;
      case "camera" -> Action.CAMERA;
      case "shaders" -> Action.SHADERS;
      case "props" -> Action.PROPS;
      case "play" -> Action.PLAY;
      case "stop" -> Action.STOP;
      case "list" -> Action.LIST;
      case "experience" -> Action.EXPERIENCE;
      case "dummy" -> parseDummyAction(args);
      case "dolly" -> args.length >= 8 ? Action.DOLLY : Action.UNKNOWN;
      default -> Action.UNKNOWN;
    };
  }

  static Action parseDummyAction(String[] args) {
    if (args.length < 2) {
      return Action.UNKNOWN;
    }
    return switch (args[1].toLowerCase(Locale.ROOT)) {
      case "create" -> Action.DUMMY_CREATE;
      case "show" -> Action.DUMMY_SHOW;
      case "hide" -> Action.DUMMY_HIDE;
      case "destroy" -> Action.DUMMY_DESTROY;
      case "list" -> Action.DUMMY_LIST;
      default -> Action.UNKNOWN;
    };
  }

  static ExperienceAction parseExperienceAction(String[] args) {
    if (args.length < 2) {
      return ExperienceAction.UNKNOWN;
    }
    return switch (args[1].toLowerCase(Locale.ROOT)) {
      case "create" -> ExperienceAction.CREATE;
      case "beat" -> ExperienceAction.BEAT;
      case "play" -> ExperienceAction.PLAY;
      case "list" -> ExperienceAction.LIST;
      default -> ExperienceAction.UNKNOWN;
    };
  }

  static List<String> experienceSuggestions(String input) {
    String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (String action : EXPERIENCE_ACTIONS) {
      if (action.startsWith(prefix)) {
        result.add(action);
      }
    }
    result.sort(null);
    return result;
  }

  static List<String> suggestions(String input) {
    String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (String action : ACTIONS) {
      if (action.startsWith(prefix)) {
        result.add(action);
      }
    }
    result.sort(null);
    return result;
  }

  @Override
  public String permission() {
    return USE_PERMISSION;
  }

  @Override
  public void execute(CommandSourceStack stack, String[] args) {
    CommandSender sender = stack.getSender();
    if (!sender.hasPermission(USE_PERMISSION)) {
      sender.sendMessage("You do not have permission to use cinematics.");
      return;
    }
    switch (parseAction(args)) {
      case CREATE -> create(sender, args);
      case CAMERA -> camera(sender, args);
      case SHADERS -> shaders(sender, args);
      case PROPS -> props(sender, args);
      case PLAY -> play(sender, args);
      case STOP -> stop(sender, args);
      case LIST -> list(sender);
      case EXPERIENCE -> experience(sender, args);
      case DUMMY_CREATE -> dummyCreate(sender, args);
      case DUMMY_SHOW -> dummyShow(sender, args);
      case DUMMY_HIDE -> dummyHide(sender, args);
      case DUMMY_DESTROY -> dummyDestroy(sender, args);
      case DUMMY_LIST -> dummyList(sender);
      case DOLLY -> dolly(sender, args);
      default -> sendUsage(sender);
    }
  }

  @Override
  public Collection<String> suggest(CommandSourceStack stack, String[] args) {
    if (args.length == 0 || args.length == 1) {
      return suggestions(args.length == 0 ? "" : args[0]);
    }
    if (parseAction(args) == Action.EXPERIENCE && args.length == 2) {
      return experienceSuggestions(args[1]);
    }
    if (args.length == 2
        && (parseAction(args) == Action.CAMERA
            || parseAction(args) == Action.SHADERS
            || parseAction(args) == Action.PROPS)) {
      return filter(List.of("add"), args[1]);
    }
    if (args.length == 2 && parseAction(args) == Action.CREATE) {
      return List.of();
    }
    if (args.length == 2
        && (parseAction(args) == Action.PLAY || parseAction(args) == Action.STOP)) {
      if (parseAction(args) == Action.STOP) {
        return filter(onlinePlayerNames(), args[1]);
      }
      return filter(sceneNames(), args[1]);
    }
    if (args.length == 3 && parseAction(args) == Action.CAMERA) {
      return filter(sceneNames(), args[2]);
    }
    if (args.length == 3 && parseAction(args) == Action.PLAY) {
      return filter(onlinePlayerNames(), args[2]);
    }
    if (args.length == 2 && "dummy".equalsIgnoreCase(args[0])) {
      return filter(DUMMY_ACTIONS, args[1]);
    }
    if (args.length == 3
        && (parseAction(args) == Action.DUMMY_SHOW
            || parseAction(args) == Action.DUMMY_HIDE
            || parseAction(args) == Action.DUMMY_DESTROY)) {
      return filter(dummyNames(), args[2]);
    }
    if (args.length == 2 && parseAction(args) == Action.DOLLY) {
      return filter(onlinePlayerNames(), args[1]);
    }
    return List.of();
  }

  private void create(CommandSender sender, String... args) {
    if (args.length < 2) {
      sender.sendMessage("Usage: /cinematic create <name>");
      return;
    }
    sender.sendMessage(describe(cinematicService.create(args[1]), args[1]));
  }

  private void camera(CommandSender sender, String... args) {
    if (args.length < 3 || !"add".equalsIgnoreCase(args[1])) {
      sender.sendMessage("Usage: /cinematic camera add <name> [time]");
      return;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can add a camera keyframe from their view.");
      return;
    }
    CameraPose pose = PaperCinematicController.poseOf(player.getLocation());
    CinematicResult result;
    if (args.length >= 4) {
      Optional<Double> time = parseDouble(args[3]);
      if (time.isEmpty()) {
        sender.sendMessage("Time must be a number of seconds.");
        return;
      }
      result = cinematicService.addKeyframe(args[2], new CameraKeyframe(time.get(), pose));
    } else {
      result = cinematicService.addKeyframe(args[2], pose);
    }
    sender.sendMessage(describe(result, args[2]));
  }

  private void shaders(CommandSender sender, String... args) {
    if (args.length < 6 || !"add".equalsIgnoreCase(args[1])) {
      sender.sendMessage("Usage: /cinematic shaders add <name> <overlay> <start> <end>");
      return;
    }
    Optional<Double> start = parseDouble(args[4]);
    Optional<Double> end = parseDouble(args[5]);
    if (start.isEmpty() || end.isEmpty()) {
      sender.sendMessage("Start and end must be numbers of seconds.");
      return;
    }
    OverlayCue cue;
    try {
      cue = new OverlayCue(args[3], start.get(), end.get());
    } catch (IllegalArgumentException invalid) {
      sender.sendMessage("Invalid shader overlay cue.");
      return;
    }
    sender.sendMessage(describe(cinematicService.addShader(args[2], cue), args[2]));
  }

  private void props(CommandSender sender, String... args) {
    if (args.length < 6 || !"add".equalsIgnoreCase(args[1])) {
      sender.sendMessage("Usage: /cinematic props add <name> <prop> <start> <end>");
      return;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can add a prop from their view.");
      return;
    }
    Optional<Double> start = parseDouble(args[4]);
    Optional<Double> end = parseDouble(args[5]);
    if (start.isEmpty() || end.isEmpty()) {
      sender.sendMessage("Start and end must be numbers of seconds.");
      return;
    }
    PropCue cue;
    try {
      cue =
          new PropCue(
              args[3],
              start.get(),
              end.get(),
              PaperCinematicController.poseOf(player.getLocation()));
    } catch (IllegalArgumentException invalid) {
      sender.sendMessage("Invalid prop cue.");
      return;
    }
    sender.sendMessage(describe(cinematicService.addProp(args[2], cue), args[2]));
  }

  private void play(CommandSender sender, String... args) {
    if (args.length < 2) {
      sender.sendMessage("Usage: /cinematic play <name> [player]");
      return;
    }
    Player target = resolveTarget(sender, args, 2);
    if (target == null) {
      return;
    }
    CinematicResult result = controller.play(target, args[1]);
    if (result == CinematicResult.SUCCESS) {
      sender.sendMessage("Playing cinematic " + args[1] + " for " + target.getName() + ".");
      return;
    }
    sender.sendMessage(describe(result, args[1]));
  }

  private void stop(CommandSender sender, String... args) {
    Player target = resolveTarget(sender, args, 1);
    if (target == null) {
      return;
    }
    Optional<PlaybackSnapshot> stopped = controller.stop(target);
    if (stopped.isEmpty()) {
      sender.sendMessage(target.getName() + " is not playing a cinematic.");
      return;
    }
    sender.sendMessage("Stopped cinematic for " + target.getName() + ".");
  }

  private void list(CommandSender sender) {
    Collection<CinematicScene> scenes = cinematicService.scenes();
    if (scenes.isEmpty()) {
      sender.sendMessage("No cinematic scenes.");
      return;
    }
    sender.sendMessage("Cinematic scenes:");
    for (CinematicScene scene : scenes) {
      sender.sendMessage(
          "  "
              + scene.name()
              + " — "
              + scene.keyframes().size()
              + " camera keyframes, "
              + scene.shaders().size()
              + " shaders, "
              + scene.props().size()
              + " props");
    }
  }

  private Player resolveTarget(CommandSender sender, String[] args, int playerIndex) {
    if (args.length > playerIndex) {
      Player online = Bukkit.getPlayerExact(args[playerIndex]);
      if (online == null) {
        sender.sendMessage("Unknown player: " + args[playerIndex]);
        return null;
      }
      return online;
    }
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Specify a player from the console.");
      return null;
    }
    return player;
  }

  private void dummyCreate(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Usage: /cinematic dummy create <name> [player]");
      return;
    }
    Player target = resolveTarget(sender, args, 3);
    if (target == null) {
      return;
    }
    CinematicResult result = dummies.create(args[2], target);
    sender.sendMessage(describe(result, args[2]));
  }

  private void dummyShow(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Usage: /cinematic dummy show <name>");
      return;
    }
    sender.sendMessage(describe(dummies.show(args[2]), args[2]));
  }

  private void dummyHide(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Usage: /cinematic dummy hide <name>");
      return;
    }
    sender.sendMessage(describe(dummies.hide(args[2]), args[2]));
  }

  private void dummyDestroy(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Usage: /cinematic dummy destroy <name>");
      return;
    }
    sender.sendMessage(describe(dummies.destroy(args[2]), args[2]));
  }

  private void dummyList(CommandSender sender) {
    Collection<String> names = dummies.list();
    if (names.isEmpty()) {
      sender.sendMessage("No player-skin dummies.");
      return;
    }
    sender.sendMessage("Player-skin dummies:");
    for (String name : names) {
      sender.sendMessage("  " + name);
    }
  }

  private void dolly(CommandSender sender, String[] args) {
    if (args.length < 8) {
      sender.sendMessage("Usage: /cinematic dolly <player> <world> <x> <y> <z> <yaw> <pitch>");
      return;
    }
    Player target = Bukkit.getPlayerExact(args[1]);
    if (target == null) {
      sender.sendMessage("Unknown player: " + args[1]);
      return;
    }
    org.bukkit.World world = Bukkit.getWorld(args[2]);
    if (world == null) {
      sender.sendMessage("Unknown world: " + args[2]);
      return;
    }
    Optional<Double> x = parseDouble(args[3]);
    Optional<Double> y = parseDouble(args[4]);
    Optional<Double> z = parseDouble(args[5]);
    Optional<Double> yaw = parseDouble(args[6]);
    Optional<Double> pitch = parseDouble(args[7]);
    if (x.isEmpty() || y.isEmpty() || z.isEmpty() || yaw.isEmpty() || pitch.isEmpty()) {
      sender.sendMessage("x, y, z, yaw and pitch must be numbers.");
      return;
    }
    CameraPose pose =
        new CameraPose(
            world.getUID().toString(),
            x.get(),
            y.get(),
            z.get(),
            yaw.get().floatValue(),
            pitch.get().floatValue());
    dolly.apply(target, pose);
    sender.sendMessage(
        "Dollied "
            + target.getName()
            + " to "
            + world.getName()
            + " ("
            + x.get()
            + ", "
            + y.get()
            + ", "
            + z.get()
            + ").");
  }

  private List<String> dummyNames() {
    return new ArrayList<>(dummies.list());
  }

  private void experience(CommandSender sender, String[] args) {
    switch (parseExperienceAction(args)) {
      case CREATE -> experienceCreate(sender, args);
      case BEAT -> experienceBeat(sender, args);
      case PLAY -> experiencePlay(sender, args);
      case LIST -> experienceList(sender);
      default -> sendExperienceUsage(sender);
    }
  }

  private void experienceCreate(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Usage: /cinematic experience create <name>");
      return;
    }
    if (CinematicScene.normalizeName(args[2]) == null) {
      sender.sendMessage(describe(CinematicResult.INVALID_NAME, args[2]));
      return;
    }
    if (experienceService.experience(args[2]).isPresent()) {
      sender.sendMessage(describe(CinematicResult.ALREADY_EXISTS, args[2]));
      return;
    }
    sender.sendMessage(
        "Add the first beat with /cinematic experience beat add "
            + args[2]
            + " timeline <beatId> <scene>");
  }

  private void experienceBeat(CommandSender sender, String[] args) {
    if (args.length < 7
        || !"add".equalsIgnoreCase(args[2])
        || !"timeline".equalsIgnoreCase(args[4])) {
      sender.sendMessage("Usage: /cinematic experience beat add <name> timeline <beatId> <scene>");
      return;
    }
    TimelineBeat beat;
    try {
      beat = new TimelineBeat(args[5], args[6]);
    } catch (IllegalArgumentException invalid) {
      sender.sendMessage("Invalid beat id or scene name (use 1–64 [a-z0-9_-] characters).");
      return;
    }
    sender.sendMessage(describe(experienceService.addTimelineBeat(args[3], beat), args[3]));
  }

  private void experiencePlay(CommandSender sender, String[] args) {
    if (args.length < 3) {
      sender.sendMessage("Usage: /cinematic experience play <name> [player]");
      return;
    }
    Player target = resolveTarget(sender, args, 3);
    if (target == null) {
      return;
    }
    CinematicResult result = controller.playExperience(target, args[2]);
    if (result == CinematicResult.SUCCESS) {
      sender.sendMessage("Playing experience " + args[2] + " for " + target.getName() + ".");
      return;
    }
    sender.sendMessage(describe(result, args[2]));
  }

  private void experienceList(CommandSender sender) {
    Collection<Experience> experiences = experienceService.experiences();
    if (experiences.isEmpty()) {
      sender.sendMessage("No cinematic experiences.");
      return;
    }
    sender.sendMessage("Cinematic experiences:");
    for (Experience experience : experiences) {
      sender.sendMessage("  " + experience.name() + " — " + experience.beats().size() + " beats");
    }
  }

  private List<String> experienceNames() {
    List<String> names = new ArrayList<>();
    for (Experience experience : experienceService.experiences()) {
      names.add(experience.name());
    }
    return names;
  }

  private List<String> sceneNames() {
    List<String> names = new ArrayList<>();
    for (CinematicScene scene : cinematicService.scenes()) {
      names.add(scene.name());
    }
    return names;
  }

  private static Optional<Double> parseDouble(String raw) {
    try {
      return Optional.of(Double.parseDouble(raw));
    } catch (NumberFormatException invalid) {
      return Optional.empty();
    }
  }

  private static String describe(CinematicResult result, String scene) {
    return switch (result) {
      case SUCCESS -> "Updated cinematic " + scene + ".";
      case TOO_FEW_KEYFRAMES ->
          "Scene " + scene + " needs at least two camera keyframes before it can play.";
      case UNKNOWN_SCENE -> "Unknown cinematic scene: " + scene + ".";
      case UNKNOWN_EXPERIENCE -> "Unknown cinematic experience: " + scene + ".";
      case EMPTY_EXPERIENCE -> "Experience " + scene + " needs at least one beat.";
      case NOT_PLAYING -> "That player is not playing a cinematic.";
      case INVALID_NAME -> "Invalid scene name (use 1–64 [a-z0-9_-] characters).";
      case ALREADY_EXISTS -> "Cinematic scene " + scene + " already exists.";
      case ALREADY_PLAYING -> "That player is already playing a cinematic.";
      case INVALID_KEYFRAME -> "Invalid camera keyframe (time must be unique and >= 0).";
      case INVALID_CUE -> "Invalid shader or prop cue.";
    };
  }

  private static void sendUsage(CommandSender sender) {
    sender.sendMessage("Usage: /cinematic create <name>");
    sender.sendMessage("       /cinematic camera add <name> [time]");
    sender.sendMessage("       /cinematic shaders add <name> <overlay> <start> <end>");
    sender.sendMessage("       /cinematic props add <name> <prop> <start> <end>");
    sender.sendMessage("       /cinematic play <name> [player]");
    sender.sendMessage("       /cinematic stop [player]");
    sender.sendMessage("       /cinematic list");
    sender.sendMessage("       /cinematic dummy create <name> [player]");
    sender.sendMessage("       /cinematic dummy show <name>");
    sender.sendMessage("       /cinematic dummy hide <name>");
    sender.sendMessage("       /cinematic dummy destroy <name>");
    sender.sendMessage("       /cinematic dummy list");
    sender.sendMessage("       /cinematic dolly <player> <world> <x> <y> <z> <yaw> <pitch>");
    sender.sendMessage("       /cinematic experience create <name>");
    sender.sendMessage("       /cinematic experience beat add <name> timeline <beatId> <scene>");
    sender.sendMessage("       /cinematic experience play <name> [player]");
    sender.sendMessage("       /cinematic experience list");
  }

  private static void sendExperienceUsage(CommandSender sender) {
    sender.sendMessage("Usage: /cinematic experience create <name>");
    sender.sendMessage("       /cinematic experience beat add <name> timeline <beatId> <scene>");
    sender.sendMessage("       /cinematic experience play <name> [player]");
    sender.sendMessage("       /cinematic experience list");
  }

  private static List<String> filter(List<String> candidates, String prefix) {
    String lower = prefix.toLowerCase(Locale.ROOT);
    List<String> result = new ArrayList<>();
    for (String candidate : candidates) {
      if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
        result.add(candidate);
      }
    }
    return result;
  }

  private static List<String> onlinePlayerNames() {
    List<String> names = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      names.add(player.getName());
    }
    return names;
  }
}
