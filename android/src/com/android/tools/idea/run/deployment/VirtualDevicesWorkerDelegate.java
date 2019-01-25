/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.run.LaunchCompatibilityChecker;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.swing.SwingWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualDevicesWorkerDelegate extends SwingWorker<Collection<VirtualDevice>, Void> {
  @Nullable
  private final LaunchCompatibilityChecker myChecker;

  @NotNull
  private final ConnectionTimeService myService;

  VirtualDevicesWorkerDelegate(@Nullable LaunchCompatibilityChecker checker, @NotNull ConnectionTimeService service) {
    myChecker = checker;
    myService = service;
  }

  @NotNull
  @Override
  protected Collection<VirtualDevice> doInBackground() {
    return AvdManagerConnection.getDefaultAvdManagerConnection().getAvds(false).stream()
      .map(avdInfo -> VirtualDevice.newDisconnectedDeviceBuilder(avdInfo).build(myChecker, myService))
      .collect(Collectors.toList());
  }
}