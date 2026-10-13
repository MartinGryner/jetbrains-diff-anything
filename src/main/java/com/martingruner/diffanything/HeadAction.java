package com.martingruner.diffanything;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class HeadAction extends AnAction implements DumbAware {
  @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
  @Override public void update(@NotNull AnActionEvent e) {
    var file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    e.getPresentation().setEnabled(LocalComparison.supportedOS() && ActionSupport.local(file) && !file.isDirectory()
        && (files == null || files.length == 1));
  }
  @Override public void actionPerformed(@NotNull AnActionEvent e) {
    var file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (!ActionSupport.local(file) || file.isDirectory() || !ActionSupport.saved(e.getProject(), file)) return;
    if (Messages.showOkCancelDialog(e.getProject(),
        "Compare Git HEAD (left) with the saved file (right):\n" + file.getPath()
        + "\n\nA local copy of HEAD is retained for seven days and removed on a later Git comparison. Diff Anything uses your Free or Pro plan.",
        "Compare with Git HEAD", "Open", "Cancel", Messages.getQuestionIcon()) != Messages.OK) return;
    String app = ActionSupport.app();
    Path path = Path.of(file.getPath());
    ActionSupport.background(e.getProject(), () -> {
      LocalComparison.validateApp(app);
      Path snapshot = LocalComparison.headSnapshot(path, ActionSupport.cache());
      LocalComparison.open(app, snapshot, path);
    });
  }
}
