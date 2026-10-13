package com.martingruner.diffanything;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public final class ConfigureAction extends AnAction implements DumbAware {
  @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
  @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(LocalComparison.supportedOS()); }
  @Override public void actionPerformed(@NotNull AnActionEvent e) {
    String value = Messages.showInputDialog(e.getProject(),
        "Absolute path to the installed Diff Anything 1.6.1+ " + (LocalComparison.isMac() ? ".app bundle" : ".exe or Store execution alias")
        + ".\nOnly choose an installation you trust. Leave blank to use the default.\nComparisons use the desktop app's Free or Pro plan.",
        "Configure Diff Anything", Messages.getQuestionIcon(), ActionSupport.app(), null);
    if (value == null) return;
    String path = value.trim();
    if (path.startsWith("\"") && path.endsWith("\"") && path.length() > 1) path = path.substring(1, path.length() - 1);
    try {
      if (!path.isBlank() && !Path.of(path).isAbsolute()) throw new IllegalArgumentException();
      PropertiesComponent.getInstance().setValue(ActionSupport.KEY, path.isBlank() ? null : path);
    } catch (IllegalArgumentException ex) { Messages.showErrorDialog(e.getProject(), "Enter an absolute local path.", ActionSupport.TITLE); }
  }
}
