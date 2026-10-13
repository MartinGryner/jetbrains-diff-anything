package com.martingruner.diffanything;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class CompareAction extends AnAction implements DumbAware {
  @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
  @Override public void update(@NotNull AnActionEvent e) {
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    e.getPresentation().setEnabled(LocalComparison.supportedOS() && files != null && files.length == 2
        && ActionSupport.local(files[0]) && ActionSupport.local(files[1]) && files[0].isDirectory() == files[1].isDirectory());
  }
  @Override public void actionPerformed(@NotNull AnActionEvent e) {
    var files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length != 2 || !ActionSupport.local(files[0]) || !ActionSupport.local(files[1])) return;
    if (!ActionSupport.saved(e.getProject(), files)) return;
    int choice = Messages.showYesNoCancelDialog(e.getProject(),
        "Left: " + files[0].getPath() + "\nRight: " + files[1].getPath()
        + "\n\nOpen in the installed Diff Anything app using your Free or Pro plan. You can edit files in that app.",
        "Compare in Diff Anything", "Open", "Swap and Open", "Cancel", Messages.getQuestionIcon());
    if (choice != Messages.YES && choice != Messages.NO) return;
    Path left = Path.of(files[choice == Messages.YES ? 0 : 1].getPath());
    Path right = Path.of(files[choice == Messages.YES ? 1 : 0].getPath());
    String app = ActionSupport.app();
    ActionSupport.background(e.getProject(), () -> LocalComparison.open(app, left, right));
  }
}
