package com.martingruner.diffanything;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** No shell, network, IDE state, or changes to the selected files. */
public final class LocalComparison {
  private LocalComparison() {}
  public static boolean supportedOS() { return isMac() || isWindows(); }
  public static boolean isMac() { return System.getProperty("os.name").startsWith("Mac"); }
  public static boolean isWindows() { return System.getProperty("os.name").startsWith("Windows"); }

  public static String defaultApp() {
    if (isMac()) return "/Applications/Diff Anything.app";
    String local = System.getenv("LOCALAPPDATA");
    return local == null ? "" : Path.of(local, "Microsoft", "WindowsApps", "diff-anything.exe").toString();
  }

  public static void validatePair(Path left, Path right) throws IOException {
    for (Path p : List.of(left, right)) {
      if (!p.isAbsolute() || !Files.isReadable(p) || (!Files.isRegularFile(p) && !Files.isDirectory(p)))
        throw new IOException("Choose readable files or folders on local disk: " + p);
    }
    if (Files.isSameFile(left, right)) throw new IOException("Choose two different files or folders.");
    if (Files.isDirectory(left) != Files.isDirectory(right))
      throw new IOException("Choose two files or two folders, not one of each.");
  }

  public static List<String> command(boolean mac, String app, Path left, Path right) {
    return mac ? List.of("/usr/bin/open", "-a", app, left.toString(), right.toString())
        : List.of(app, "--compare-paths", left.toString(), right.toString());
  }

  public static boolean recentVersion(String text) {
    var m = java.util.regex.Pattern.compile("(?:^|\\s)(\\d+)\\.(\\d+)\\.(\\d+)(?:$|\\s)").matcher(text.trim());
    if (!m.find()) return false;
    try {
      int major = Integer.parseInt(m.group(1)), minor = Integer.parseInt(m.group(2)), patch = Integer.parseInt(m.group(3));
      return major > 1 || (major == 1 && (minor > 6 || (minor == 6 && patch >= 1)));
    } catch (NumberFormatException e) { return false; }
  }

  public static void validateApp(String app) throws Exception {
    if (!supportedOS()) throw new IOException("This plugin requires macOS or Windows with Diff Anything installed locally.");
    if (app.isBlank() || !Path.of(app).isAbsolute() || !Files.exists(Path.of(app)))
      throw new IOException("Diff Anything was not found. Install version 1.6.1 or later, then use Tools → Diff Anything → Configure Diff Anything.");
    String version;
    if (isMac()) {
      Path info = Path.of(app, "Contents", "Info.plist");
      String id = capture(List.of("/usr/bin/plutil", "-extract", "CFBundleIdentifier", "raw", "-o", "-", info.toString()), null);
      if (!id.trim().equals("com.martingruner.diffanything")) throw new IOException("Choose the Diff Anything application bundle.");
      version = capture(List.of("/usr/bin/plutil", "-extract", "CFBundleShortVersionString", "raw", "-o", "-", info.toString()), null);
    } else {
      if (!app.toLowerCase(Locale.ROOT).endsWith(".exe")) throw new IOException("Choose the installed Diff Anything .exe or its Microsoft Store app execution alias.");
      version = capture(List.of(app, "--cli", "--version"), null);
      if (!version.startsWith("diff-anything ")) throw new IOException("The selected executable did not identify itself as Diff Anything.");
    }
    if (!recentVersion(version)) throw new IOException("Update Diff Anything to 1.6.1 or later. Installed version: " + version.trim());
  }

  public static void open(String app, Path left, Path right) throws Exception {
    validatePair(left, right);
    validateApp(app);
    Process p = new ProcessBuilder(command(isMac(), app, left, right))
        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    // macOS open exits after handoff. A Windows GUI process normally stays alive; do not kill it.
    if (p.waitFor(isMac() ? 15 : 1, TimeUnit.SECONDS)) {
      if (p.exitValue() != 0) throw new IOException("Diff Anything could not open the comparison (exit " + p.exitValue() + "). Open the app once, then try again.");
    } else if (isMac()) {
      p.destroyForcibly();
      throw new IOException("macOS did not finish opening Diff Anything. Open the app once, then try again.");
    }
  }

  static Path privateDirectory(Path parent, String prefix) throws IOException {
    Files.createDirectories(parent);
    if (parent.getFileSystem().supportedFileAttributeViews().contains("posix"))
      return Files.createTempDirectory(parent, prefix, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    return Files.createTempDirectory(parent, prefix);
  }

  static Path privateFile(Path parent, String name) throws IOException {
    Path file = parent.resolve(name);
    if (parent.getFileSystem().supportedFileAttributeViews().contains("posix"))
      return Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    return Files.createFile(file);
  }

  static void runTo(List<String> args, Path cwd, Path stdout, Path stderr) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(args).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
    if (cwd != null) pb.directory(cwd.toFile());
    pb.environment().put("GIT_TERMINAL_PROMPT", "0");
    Process p = pb.start();
    try {
      if (!p.waitFor(20, TimeUnit.SECONDS)) throw new IOException("The local command timed out.");
      if (p.exitValue() != 0) {
        String error;
        try (var input = Files.newInputStream(stderr)) { error = new String(input.readNBytes(4096), StandardCharsets.UTF_8).trim(); }
        throw new IOException(error.isBlank() ? "The local command failed (exit " + p.exitValue() + ")." : error);
      }
    } finally { if (p.isAlive()) p.destroyForcibly(); }
  }

  static String capture(List<String> args, Path cwd) throws Exception {
    Path dir = privateDirectory(Path.of(System.getProperty("java.io.tmpdir")), "diff-anything-command-");
    Path out = privateFile(dir, "out"), err = privateFile(dir, "err");
    try {
      runTo(args, cwd, out, err);
      if (Files.size(out) > 65536) throw new IOException("The local command returned too much output.");
      return Files.readString(out);
    } finally { Files.deleteIfExists(out); Files.deleteIfExists(err); Files.deleteIfExists(dir); }
  }

  public static Path headSnapshot(Path file, Path cache) throws Exception {
    if (!Files.isRegularFile(file)) throw new IOException("Select a saved local file tracked by Git.");
    Path absolute = file.toRealPath();
    String rootOutput = capture(List.of("git", "-C", absolute.getParent().toString(), "rev-parse", "--show-toplevel"), null);
    // Remove Git's line ending, not whitespace belonging to the directory name.
    Path root = Path.of(rootOutput.replaceFirst("\\r?\\n$", "")).toRealPath();
    if (!absolute.startsWith(root)) throw new IOException("The selected file is outside its Git repository.");
    String relative = root.relativize(absolute).toString().replace(java.io.File.separatorChar, '/');
    Files.createDirectories(cache);
    cleanup(cache, System.currentTimeMillis());
    Path dir = privateDirectory(cache, "head-");
    Path out = privateFile(dir, absolute.getFileName().toString());
    Path err = Files.createTempFile(dir, ".git-error-", ".txt");
    try {
      // Fixed HEAD, no revision supplied by a project or shell. Raw bytes preserve encodings.
      runTo(List.of("git", "-C", root.toString(), "show", "HEAD:" + relative), root, out, err);
      return out;
    } catch (Exception e) {
      Files.deleteIfExists(out);
      throw new IOException("Cannot read this file from Git HEAD. It may be untracked, renamed, or in a repository without a commit. " + e.getMessage(), e);
    } finally {
      Files.deleteIfExists(err);
      if (!Files.exists(out)) Files.deleteIfExists(dir);
    }
  }

  static void cleanup(Path cache, long now) throws IOException {
    try (var dirs = Files.list(cache)) {
      for (Path dir : dirs.filter(p -> p.getFileName().toString().startsWith("head-")).toList()) {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || now - Files.getLastModifiedTime(dir, LinkOption.NOFOLLOW_LINKS).toMillis() <= Duration.ofDays(7).toMillis()) continue;
        try (var paths = Files.walk(dir)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
      }
    }
  }
}
