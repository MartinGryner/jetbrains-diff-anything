# Compare with Diff Anything

JetBrains integration for Diff Anything on macOS and Windows.

Open two local files or folders in Diff Anything, or compare a saved file with its Git `HEAD` version. Files stay on your computer; no AI is involved.

## Requirements

- A local JetBrains IDE using platform 2026.2 (build 262) or newer. WebStorm 2026.2.3 is the verified baseline. The plugin descriptor has no upper IDE build limit; later IDE releases are not blocked by Marketplace metadata, but have not all been independently tested. Other IDEs and remote development sessions have not been tested.
- Diff Anything 1.6.1 or later installed separately. macOS 13 or later, or Windows 10 build 17763 or later, are the desktop app's packaging minimums; the IDE may require a newer operating system.
- Git on the IDE's PATH for the Git HEAD command.

The plugin is free. Comparisons open in the desktop app and use its Free or Pro features and limits. Installing the plugin does not unlock Pro. Purchase or restore Pro in Diff Anything.

macOS uses the system's file-opening handoff, including for the sandboxed Mac App Store app. Windows uses the Microsoft Store `diff-anything.exe` app execution alias, or a configured installed executable. Version checks run locally. The plugin does not use the comparison CLI, batch mode, or merge mode.

## Setup and use

Install the plugin ZIP with **Settings → Plugins → gear → Install Plugin from Disk** in a compatible test IDE. Restart if prompted. The ZIP can be installed directly while Marketplace review is pending.

Under **Tools → Diff Anything → Configure Diff Anything**, set an absolute path if the default does not match your installation. On Mac, select the `.app` bundle. On Windows, select the installed `.exe` or its Store app execution alias. Only select an installation you trust.

Select exactly two saved files or two folders in the Project view, then choose **Diff Anything → Compare Selected Files or Folders**. The confirmation shows both paths and lets you swap left and right before opening. Save unsaved files first. The plugin does not save or alter the selected sources; once they open, the desktop app can edit them.

For a tracked, saved file, choose **Compare Saved File with Git HEAD** from its editor or Project menu. The committed snapshot opens on the left and the working file on the right. Untracked files, newly renamed files missing from HEAD, repositories without a commit, unreadable sources, and missing installations show an error. Snapshots preserve the original bytes and filename extension. This compares HEAD, not the staging area or an arbitrary revision.

## Privacy

The plugin sends no network requests, collects no telemetry, and sends no data to an AI model. It reads the selected paths and app version and invokes the installed app locally. It invokes local Git only for the HEAD command. The configured app path is saved in the IDE's local settings.

HEAD snapshots are stored under the IDE system directory at `diff-anything/git-snapshots`. A later Git comparison removes snapshots older than seven days. They can remain longer if the command is not used again. Unix snapshots use private directory/file permissions (0700/0600); Windows uses the current user's inherited filesystem permissions. Close comparisons before removing snapshots manually. Deletion is not secure erasure. Error dialogs may include file paths and local Git error text; the plugin does not upload them.

## Build and test

Python 3, a JDK with `java` and `javac` supporting Java 21, Git, and an extracted official JetBrains IDE SDK are required. Neither a global Gradle installation nor a change to your installed IDE is needed.

```sh
python3 build.py --ide /path/to/WebStorm.app/Contents --jdk /path/to/jdk
```

The script validates the descriptor, compiles the plugin, creates `build/compare-with-diff-anything-0.1.1.zip`, and runs integration tests with a temporary Git repository. No SDK files are bundled in the plugin.

Run the [official JetBrains Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) on the resulting archive:

```sh
java -Dplugin.verifier.home.dir=/tmp/diff-anything-verifier \
  -jar /path/to/verifier-cli-all.jar check-plugin \
  build/compare-with-diff-anything-0.1.1.zip /path/to/WebStorm.app/Contents \
  -verification-reports-dir build/verifier -runtime-dir /path/to/jdk
```

Interactive handoff has been verified on macOS with WebStorm 2026.2.3 and a Diff Anything 1.6.1 source build. Windows launch arguments have automated coverage; interactive Windows validation is pending.
