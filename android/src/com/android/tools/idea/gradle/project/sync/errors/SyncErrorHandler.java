/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.annotations.Nullable;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.google.common.base.Splitter;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE;

public abstract class SyncErrorHandler {
  private static final ExtensionPointName<SyncErrorHandler>
    EXTENSION_POINT_NAME = ExtensionPointName.create("com.android.gradle.sync.syncErrorHandler");
  protected static final String EMPTY_LINE = "\n\n";
  protected static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.ERROR;

  @NotNull
  public static SyncErrorHandler[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error), notification, project);
    if (text != null) {
      List<NotificationHyperlink> hyperlinks = getQuickFixHyperlinks(notification, project, text);
      SyncMessages.getInstance(project).updateNotification(notification, text, hyperlinks);
      return true;
    }
    return false;
  }

  @Nullable
  protected abstract String findErrorMessage(@NotNull Throwable rootCause,
                                             @NotNull NotificationData notification,
                                             @NotNull Project project);

  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    return Collections.emptyList();
  }

  @NotNull
  protected Throwable getRootCause(@NotNull Throwable error) {
    Throwable rootCause = error;
    while (true) {
      if (rootCause.getCause() == null || rootCause.getCause().getMessage() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }

  protected final void updateUsageTracker() {
    updateUsageTracker(null, null);
  }

  protected final void updateUsageTracker(@NotNull GradleSyncFailure syncFailure) {
    updateUsageTracker(syncFailure, null);
  }

  protected final void updateUsageTracker(@Nullable GradleSyncFailure gradleSyncFailure, @Nullable String gradleMissingSignature) {
    AndroidStudioEvent.Builder builder =
      AndroidStudioEvent.newBuilder();
    if (gradleSyncFailure == null) {
      gradleSyncFailure = UNKNOWN_GRADLE_FAILURE;
    }
    // @formatter:off
    builder.setCategory(GRADLE_SYNC)
           .setKind(GRADLE_SYNC_FAILURE)
           .setGradleSyncFailure(gradleSyncFailure);
    // @formatter:on
    if (gradleMissingSignature != null) {
      builder.setGradleMissingSignature(gradleMissingSignature);
    }
    UsageTracker.getInstance().log(builder);
  }

  @NotNull
  protected final String getFirstLineMessage(@NotNull String text) {
    return Splitter.on('\n').omitEmptyStrings().trimResults().splitToList(text).get(0);
  }
}