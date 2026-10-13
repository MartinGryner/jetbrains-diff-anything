package com.martingruner.diffanything;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.*;

public final class LocalComparisonTest {
  static int checks;
  static void check(boolean condition, String name) {
    if (!condition) throw new AssertionError(name);
    checks++;
  }
  interface Throwing { void run() throws Exception; }
  static void fails(Throwing body, String name) throws Exception {
    try { body.run(); } catch (java.io.IOException e) { checks++; return; }
    throw new AssertionError(name);
  }
  public static void main(String[] args) throws Exception {
    Path tmp = Files.createTempDirectory("diff-anything-plugin-test-");
    try {
      Path repo = Files.createDirectory(tmp.resolve(LocalComparison.isWindows() ? "repo with spaces" : "repo with trailing space "));
      Path file = repo.resolve("-odd ' ü ; $(echo test).json");
      byte[] before = new byte[]{0, 1, (byte)255, 13, 10, 7};
      Files.write(file, before);
      Path other = Files.writeString(repo.resolve("other.json"), "{\"value\":2}");
      LocalComparison.validatePair(file, other); checks++;
      fails(() -> LocalComparison.validatePair(file, file), "reject identical files");
      fails(() -> LocalComparison.validatePair(file, repo), "reject mixed files and folders");
      fails(() -> LocalComparison.validatePair(file, repo.resolve("missing")), "reject missing input");
      Path another = Files.createDirectory(tmp.resolve("another"));
      LocalComparison.validatePair(repo, another); checks++;
      check(LocalComparison.command(true, "/Applications/Diff Anything.app",file,other).equals(List.of("/usr/bin/open","-a","/Applications/Diff Anything.app",file.toString(),other.toString())), "macOS file-open event, no --args");
      check(LocalComparison.command(false,"C:\\Program Files\\Diff Anything\\diff-anything.exe",file,other).equals(List.of("C:\\Program Files\\Diff Anything\\diff-anything.exe","--compare-paths",file.toString(),other.toString())), "Windows uses argument array, not a shell");
      check(LocalComparison.recentVersion("diff-anything 1.6.1\n"), "minimum version");
      check(LocalComparison.recentVersion("2.0.0"), "new major version");
      check(!LocalComparison.recentVersion("1.6.0"), "reject old sandbox handoff");
      check(!LocalComparison.recentVersion("garbage"), "reject unknown version");
      LocalComparison.capture(List.of("git", "init", repo.toString()), null);
      LocalComparison.capture(List.of("git", "-C", repo.toString(), "add", "--", file.getFileName().toString()), null);
      LocalComparison.capture(List.of("git", "-C", repo.toString(), "-c", "user.name=Plugin Test", "-c", "user.email=test@example.invalid", "-c", "commit.gpgsign=false", "commit", "-m", "fixture"), null);
      Files.writeString(file, "changed working tree");
      Path cache = tmp.resolve("snapshots");
      Path snapshot = LocalComparison.headSnapshot(file, cache);
      check(Arrays.equals(before, Files.readAllBytes(snapshot)), "Git snapshot preserves binary bytes and odd names");
      check(Files.readString(file).equals("changed working tree"), "working copy remains unchanged");
      check(snapshot.getFileName().equals(file.getFileName()), "snapshot preserves extension");
      if (Files.getFileStore(snapshot).supportsFileAttributeView("posix")) {
        check(Files.getPosixFilePermissions(snapshot).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")), "private snapshot file");
        check(Files.getPosixFilePermissions(snapshot.getParent()).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")), "private snapshot directory");
      }
      fails(() -> LocalComparison.headSnapshot(other, cache), "untracked file reports failure");
      try (var dirs = Files.list(cache)) { check(dirs.count() == 1, "failed snapshots removed"); }
      Path old = Files.createDirectory(cache.resolve("head-old"));
      Files.writeString(old.resolve("secret"), "old snapshot");
      Files.setLastModifiedTime(old, FileTime.fromMillis(System.currentTimeMillis()-Duration.ofDays(8).toMillis()));
      LocalComparison.cleanup(cache, System.currentTimeMillis());
      check(!Files.exists(old), "old snapshot removed");
      check(Files.exists(snapshot), "recent snapshot retained for app");
      Path linked = cache.resolve("head-linked");
      Files.createSymbolicLink(linked, another);
      LocalComparison.cleanup(cache, System.currentTimeMillis());
      check(Files.exists(another), "cleanup does not follow symlinks");
      System.out.println("PASS: " + checks + " checks, including real Git and private snapshot integration");
    } finally {
      try (var paths = Files.walk(tmp)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
    }
  }
}
