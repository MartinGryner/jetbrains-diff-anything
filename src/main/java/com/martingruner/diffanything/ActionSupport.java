package com.martingruner.diffanything;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Path;

final class ActionSupport {
  static final String TITLE = "Diff Anything";
  static final String KEY = "diffAnything.applicationPath";
  static boolean local(VirtualFile file) { return file != null && file.isInLocalFileSystem() && file.isValid(); }
  static String app() { return PropertiesComponent.getInstance().getValue(KEY, LocalComparison.defaultApp()); }

  static boolean saved(Project project, VirtualFile... files) {
    for (VirtualFile file : files) {
      var document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null && FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
        Messages.showInfoMessage(project, "Save " + file.getName() + " before comparing. Diff Anything reads the file on disk.", TITLE);
        return false;
      }
    }
    return true;
  }

  interface Work { void run() throws Exception; }
  static void background(Project project, Work work) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try { work.run(); }
      catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        ApplicationManager.getApplication().invokeLater(() -> {
          if (project == null || !project.isDisposed())
            Messages.showErrorDialog(project, e.getMessage() == null ? "The local comparison could not be opened." : e.getMessage(), TITLE);
        });
      }
    });
  }
  static Path cache() { return Path.of(PathManager.getSystemPath(), "diff-anything", "git-snapshots"); }
}
