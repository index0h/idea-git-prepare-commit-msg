package com.github.sourcebucket.intellij_git_prepare_commit_msg;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.CommitMessageProvider;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class GitPrepareCommitMessageProvider implements CommitMessageProvider {
    @Nullable
    public String getCommitMessage(LocalChangeList localChangeList, Project project) {
        String basePath = project.getBasePath();
        String hookPath = basePath + "/.git/hooks/prepare-commit-msg";
        String commitFilePrefix = "prepare-commit-msg-";
        String commitFileSuffix = ".tmp";
        String commitMessageOld = localChangeList.getComment();
        String notifyGroupDisplayId = "prepare-commit-msg";
        String notifyTitleError = notifyGroupDisplayId + ": Error";

        if (basePath == null) {
            String message = "Can't get project base path";

            Notification notification = new Notification(
                    notifyGroupDisplayId,
                    notifyTitleError,
                    message,
                    NotificationType.ERROR
            );

            Notifications.Bus.notify(notification);

            return null;
        }

        File hookFile = new File(hookPath);

        if (!hookFile.exists() || !hookFile.canExecute()) {
            return null;
        }

        File commitFile;

        try {
            commitFile = File.createTempFile(commitFilePrefix, commitFileSuffix);
        } catch (Exception e) {
            Notification notification = new Notification(
                    notifyGroupDisplayId,
                    notifyTitleError,
                    e.getMessage(),
                    NotificationType.ERROR
            );

            Notifications.Bus.notify(notification);

            return null;
        }

        try {
            String[] commands = {hookPath, commitFile.getAbsolutePath()};
            String[] environment = {};
            Process process = Runtime.getRuntime().exec(commands, environment, new File(basePath));
            BufferedReader errorBuffer = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            if (process.waitFor() != 0) {
                String line;
                StringBuilder errorMessageBuilder = new StringBuilder();

                while ((line = errorBuffer.readLine()) != null) {
                    errorMessageBuilder.append(line).append("\n");
                }

                Notification notification = new Notification(
                        notifyGroupDisplayId,
                        notifyTitleError,
                        errorMessageBuilder.toString(),
                        NotificationType.ERROR
                );
                Notifications.Bus.notify(notification);
            }

            FileInputStream commitFileInputStream = new FileInputStream(commitFile);
            byte[] data = new byte[(int) commitFile.length()];

            //noinspection ResultOfMethodCallIgnored
            commitFileInputStream.read(data);
            commitFileInputStream.close();

            String commitMessageNew = new String(data, StandardCharsets.UTF_8).trim();

            if (commitMessageNew.isEmpty()) {
                return null;
            }

            if (commitMessageOld != null && commitMessageOld.startsWith(commitMessageNew)) {
                return commitMessageOld;
            }

            return commitMessageNew;
        } catch (Exception e) {
            Notification notification = new Notification(
                    notifyGroupDisplayId,
                    notifyTitleError,
                    e.getMessage(),
                    NotificationType.ERROR
            );

            Notifications.Bus.notify(notification);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            commitFile.delete();
        }

        return null;
    }
}
