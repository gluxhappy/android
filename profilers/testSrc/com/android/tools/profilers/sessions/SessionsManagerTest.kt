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
package com.android.tools.profilers.sessions

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.CpuProfiler
import com.android.tools.profiler.proto.MemoryProfiler
import com.android.tools.profilers.FakeGrpcChannel
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeProfilerService
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.FakeCpuService
import com.android.tools.profilers.event.FakeEventService
import com.android.tools.profilers.memory.FakeMemoryService
import com.android.tools.profilers.memory.HprofSessionArtifact
import com.android.tools.profilers.memory.LegacyAllocationsSessionArtifact
import com.android.tools.profilers.network.FakeNetworkService
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Arrays

@RunWith(Parameterized::class)
class SessionsManagerTest(private val useUnifiedEvents: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun useNewEventPipelineParameter(): Collection<Boolean> {
      return Arrays.asList(false, true)
    }
  }

  private val myProfilerService = FakeProfilerService(false)
  private val myMemoryService = FakeMemoryService()
  private val myCpuService = FakeCpuService()

  @get:Rule
  val myThrown = ExpectedException.none()
  @get:Rule
  var myGrpcChannel = FakeGrpcChannel(
    "SessionsManagerTestChannel",
    myProfilerService,
    myMemoryService,
    myCpuService,
    FakeEventService(),
    FakeNetworkService.newBuilder().build()
  )

  private lateinit var myTimer: FakeTimer
  private lateinit var myProfilers: StudioProfilers
  private lateinit var myManager: SessionsManager
  private lateinit var myObserver: SessionsAspectObserver
  private lateinit var ideProfilerServices: FakeIdeProfilerServices

  @Before
  fun setup() {
    myTimer = FakeTimer()
    ideProfilerServices = FakeIdeProfilerServices().apply {
      enableEventsPipeline(useUnifiedEvents)
    }
    myObserver = SessionsAspectObserver()
    myProfilers = StudioProfilers(
        myGrpcChannel.client,
        ideProfilerServices,
        myTimer
    )
    myManager = myProfilers.sessionsManager
    myManager.addDependency(myObserver)
      .onChange(SessionAspect.SELECTED_SESSION) { myObserver.selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { myObserver.profilingSessionChanged() }
      .onChange(SessionAspect.SESSIONS) { myObserver.sessionsChanged() }

    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
  }

  @Test(expected = AssertionError::class)
  fun testBeginSessionWithOfflineDevice() {
    val offlineDevice = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.DISCONNECTED).build()
    val onlineProcess = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.DEAD).build()
    beginSessionHelper(offlineDevice, onlineProcess)
  }

  @Test(expected = AssertionError::class)
  fun testBeginSessionWithDeadProcess() {
    val onlineDevice = Common.Device.newBuilder().setDeviceId(2).setState(Common.Device.State.ONLINE).build()
    val offlineProcess = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.DEAD).build()
    beginSessionHelper(onlineDevice, offlineProcess)
  }

  @Test
  fun testBeginSession() {
    val streamId = 1L
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(streamId).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()
    beginSessionHelper(onlineDevice, onlineProcess)

    val session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    val sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)
  }

  @Test
  fun testBeginSessionCannotRunTwice() {
    val deviceId = 1
    val pid1 = 10
    val pid2 = 20
    val onlineDevice = Common.Device.newBuilder().setDeviceId(deviceId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess1 = Common.Process.newBuilder().setPid(pid1).setState(Common.Process.State.ALIVE).build()
    val onlineProcess2 = Common.Process.newBuilder().setPid(pid2).setState(Common.Process.State.ALIVE).build()
    beginSessionHelper(onlineDevice, onlineProcess1)

    myThrown.expect(AssertionError::class.java)
    beginSessionHelper(onlineDevice, onlineProcess2)
  }

  @Test
  fun testEndSession() {
    val streamId = 1
    val pid = 10
    val onlineDevice = Common.Device.newBuilder().setDeviceId(streamId.toLong()).setState(Common.Device.State.ONLINE).build()
    val onlineProcess = Common.Process.newBuilder().setPid(pid).setState(Common.Process.State.ALIVE).build()

    // endSession calls on no active session is a no-op
    myManager.endCurrentSession()
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.sessionArtifacts).isEmpty()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(0)

    beginSessionHelper(onlineDevice, onlineProcess)
    var session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(session)
    assertThat(myManager.isSessionAlive).isTrue()

    var sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(1)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(1)

    endSessionHelper()
    session = myManager.selectedSession
    assertThat(session.streamId).isEqualTo(streamId)
    assertThat(session.pid).isEqualTo(pid)
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.isSessionAlive).isFalse()

    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(1)
    assertThat(sessionItems.first().session).isEqualTo(session)

    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(2)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(2)
  }

  @Test
  fun testSetInvalidSession() {
    val session = Common.Session.newBuilder().setSessionId(1).build()
    myThrown.expect(AssertionError::class.java)
    myManager.setSession(session)
  }

  @Test
  fun testSetSession() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    val session2 = myManager.selectedSession
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(3)

    myManager.setSession(session1)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(4)

    myManager.setSession(session2)
    assertThat(myManager.selectedSession).isEqualTo(session2)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(5)

    myManager.setSession(Common.Session.getDefaultInstance())
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(6)
  }

  @Test
  fun testSetSessionStopsAutoProfiling() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()
    myProfilers.autoProfilingEnabled = true

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    assertThat(myProfilers.autoProfilingEnabled).isTrue()

    myManager.setSession(session1)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myProfilers.autoProfilingEnabled).isFalse()
  }

  @Test
  fun testSwitchingNonProfilingSession() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    // Create a finished session and a ongoing profiling session.
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    beginSessionHelper(device, process2)
    val session2 = myManager.selectedSession

    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(3)
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.isSessionAlive).isTrue()

    // Explicitly set to a different session should not change the profiling session.
    myManager.setSession(session1)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    assertThat(myObserver.profilingSessionChangedCount).isEqualTo(3)
    assertThat(myObserver.selectedSessionChangedCount).isEqualTo(4)
  }

  @Test
  fun testEndSessionIsNotAlive() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val session1Timestamp = 1L
    myProfilerService.setTimestampNs(session1Timestamp)
    beginSessionHelper(device, process1)
    endSessionHelper()
    assertThat(SessionsManager.isSessionAlive(myManager.profilingSession)).isFalse()
  }

  @Test
  fun testSessionArtifactsUpToDate() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setState(Common.Process.State.ALIVE).build()

    val session1Timestamp = 1L
    val session2Timestamp = 2L
    myProfilerService.setTimestampNs(session1Timestamp)
    beginSessionHelper(device, process1)
    endSessionHelper()
    val session1 = myManager.selectedSession
    myProfilerService.setTimestampNs(session2Timestamp)
    beginSessionHelper(device, process2)
    endSessionHelper()
    val session2 = myManager.selectedSession

    var sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(2)
    // Sessions are sorted in descending order.
    var sessionItem0 = sessionItems[0] as SessionItem
    var sessionItem1 = sessionItems[1] as SessionItem
    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.childArtifacts).isEmpty()
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.childArtifacts).isEmpty()

    val heapDumpTimestamp = 10L
    val cpuTraceTimestamp = 20L
    val legacyAllocationsInfoTimestamp = 30L
    val liveAllocationsInfoTimestamp = 40L
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    val allocationInfos = MemoryProfiler.MemoryData.newBuilder()
      .addAllocationsInfo(MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(legacyAllocationsInfoTimestamp).setEndTime(
        legacyAllocationsInfoTimestamp + 1).setLegacy(true).build())
      .addAllocationsInfo(MemoryProfiler.AllocationsInfo.newBuilder().setStartTime(liveAllocationsInfoTimestamp).build())
      .build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myMemoryService.setMemoryData(allocationInfos)
    myCpuService.addTraceInfo(cpuTraceInfo)
    myManager.update()

    // The Hprof and CPU capture artifacts are now included and sorted in ascending order
    sessionItems = myManager.sessionArtifacts
    assertThat(sessionItems).hasSize(8)
    sessionItem0 = sessionItems[0] as SessionItem
    val legacyAllocationsItem0 = sessionItems[1] as LegacyAllocationsSessionArtifact
    val cpuCaptureItem0 = sessionItems[2] as CpuCaptureSessionArtifact
    val hprofItem0 = sessionItems[3] as HprofSessionArtifact
    sessionItem1 = sessionItems[4] as SessionItem
    val legacyAllocationsItem1 = sessionItems[5] as LegacyAllocationsSessionArtifact
    val cpuCaptureItem1 = sessionItems[6] as CpuCaptureSessionArtifact
    val hprofItem1 = sessionItems[7] as HprofSessionArtifact

    assertThat(sessionItem0.session).isEqualTo(session2)
    assertThat(sessionItem0.timestampNs).isEqualTo(0)
    assertThat(sessionItem0.childArtifacts).containsExactly(legacyAllocationsItem0, cpuCaptureItem0, hprofItem0)
    assertThat(hprofItem0.session).isEqualTo(session2)
    assertThat(hprofItem0.timestampNs).isEqualTo(heapDumpTimestamp - session2Timestamp)
    assertThat(cpuCaptureItem0.session).isEqualTo(session2)
    assertThat(cpuCaptureItem0.timestampNs).isEqualTo(cpuTraceTimestamp - session2Timestamp)
    assertThat(legacyAllocationsItem0.session).isEqualTo(session2)
    assertThat(legacyAllocationsItem0.timestampNs).isEqualTo(legacyAllocationsInfoTimestamp - session2Timestamp)
    assertThat(sessionItem1.session).isEqualTo(session1)
    assertThat(sessionItem1.timestampNs).isEqualTo(0)
    assertThat(sessionItem1.childArtifacts).containsExactly(legacyAllocationsItem1, cpuCaptureItem1, hprofItem1)
    assertThat(hprofItem1.session).isEqualTo(session1)
    assertThat(hprofItem1.timestampNs).isEqualTo(heapDumpTimestamp - session1Timestamp)
    assertThat(cpuCaptureItem1.session).isEqualTo(session1)
    assertThat(cpuCaptureItem1.timestampNs).isEqualTo(cpuTraceTimestamp - session1Timestamp)
    assertThat(legacyAllocationsItem1.session).isEqualTo(session1)
    assertThat(legacyAllocationsItem1.timestampNs).isEqualTo(legacyAllocationsInfoTimestamp - session1Timestamp)
  }

  @Test
  fun testImportedSessionDoesNotHaveChildren() {
    Assume.assumeFalse(ideProfilerServices.featureConfig.isUnifiedPipelineEnabled)
    myManager.createImportedSession("fake.hprof", Common.SessionMetaData.SessionType.MEMORY_CAPTURE, 0, 0, 0)
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myManager.createImportedSession("fake.trace", Common.SessionMetaData.SessionType.CPU_CAPTURE, 0, 0, 1)
    val simpleperfTraceInfo = CpuProfiler.TraceInfo.newBuilder().setProfilerType(CpuProfiler.CpuProfilerType.SIMPLEPERF).build()
    myCpuService.addTraceInfo(simpleperfTraceInfo)
    myManager.update()

    Truth.assertThat(myManager.sessionArtifacts.size).isEqualTo(2)
    val cpuTraceSessionItem = myManager.sessionArtifacts[0] as SessionItem
    Truth.assertThat(cpuTraceSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.CPU_CAPTURE)
    val hprofSessionItem = myManager.sessionArtifacts[1] as SessionItem
    Truth.assertThat(hprofSessionItem.sessionMetaData.type).isEqualTo(Common.SessionMetaData.SessionType.MEMORY_CAPTURE)
  }

  @Test
  fun testSessionsAspectOnlyTriggeredWithChanges() {
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setState(Common.Process.State.ALIVE).build()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(0)

    beginSessionHelper(device, process1)
    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)

    // Triggering update with the same data should not fire the aspect.
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(1)

    val heapDumpTimestamp = 10L
    val heapDumpInfo = MemoryProfiler.HeapDumpInfo.newBuilder().setStartTime(heapDumpTimestamp).setEndTime(heapDumpTimestamp + 1).build()
    myMemoryService.addExplicitHeapDumpInfo(heapDumpInfo)
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)
    // Repeated update should not fire the aspect.
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(2)

    val cpuTraceTimestamp = 20L
    val cpuTraceInfo = CpuProfiler.TraceInfo.newBuilder().setFromTimestamp(cpuTraceTimestamp).setToTimestamp(cpuTraceTimestamp + 1).build()
    myCpuService.addTraceInfo(cpuTraceInfo)
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
    // Repeated update should not fire the aspect.
    myManager.update()
    assertThat(myObserver.sessionsChangedCount).isEqualTo(3)
  }

  @Test
  fun testDeleteProfilingSession() {
    Assume.assumeFalse(ideProfilerServices.featureConfig.isUnifiedPipelineEnabled)
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setDeviceId(1).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setDeviceId(1).setState(Common.Process.State.ALIVE).build()
    val process3 = Common.Process.newBuilder().setPid(30).setDeviceId(1).setState(Common.Process.State.ALIVE).build()
    myProfilerService.addDevice(device)
    myProfilerService.addProcess(device, process1)
    myProfilerService.addProcess(device, process2)
    myProfilerService.addProcess(device, process3)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process1)

    // Create a finished session and a ongoing profiling session.
    endSessionHelper()
    myProfilers.setProcess(device, process2)
    myManager.update()
    val session1 = myManager.sessionArtifacts[1].session
    val session2 = myManager.selectedSession

    // Selects the first session so the profiling session is unselected, then delete the profiling session
    myManager.setSession(session1)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myManager.isSessionAlive).isFalse()

    myManager.deleteSession(session2)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.selectedSession).isEqualTo(session1)
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    assertThat(myManager.sessionArtifacts[0].session).isEqualTo(session1)

    // Begin another profiling session and delete it while it is still selected
    myProfilers.setProcess(device, process3)
    myManager.update()
    val session3 = myManager.selectedSession
    assertThat(myManager.profilingSession).isEqualTo(session3)
    assertThat(myManager.selectedSession).isEqualTo(session3)
    assertThat(myManager.isSessionAlive).isTrue()

    myManager.deleteSession(session3)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.selectedSession).isEqualTo(Common.Session.getDefaultInstance())
    assertThat(myManager.isSessionAlive).isFalse()
    assertThat(myProfilers.device).isNull()
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    assertThat(myManager.sessionArtifacts[0].session).isEqualTo(session1)
  }
  @Test
  fun testGetAllSessions() {
    Assume.assumeTrue(ideProfilerServices.featureConfig.isUnifiedPipelineEnabled)
    val device1 = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setDeviceId(1).setPid(10).setState(Common.Process.State.ALIVE).build()
    val device2 = Common.Device.newBuilder().setDeviceId(2).setState(Common.Device.State.ONLINE).build()
    val process2 = Common.Process.newBuilder().setDeviceId(2).setPid(2).setState(Common.Process.State.ALIVE).build()
    myProfilerService.addDevice(device2)
    myProfilerService.addProcess(device2, process2)
    // Create session for device/process 2
    myManager.beginSession(2, device2, process2)
    myManager.endCurrentSession()
    myProfilerService.addDevice(device1)
    myProfilerService.addProcess(device1, process1)
    // Create session for device/process 1
    myManager.beginSession(1, device1, process1)
    myManager.endCurrentSession()
    // Because we haven't updated we should have 0 session data.
    assertThat(myManager.sessionArtifacts).hasSize(0)
    // Trigger an update to get both sesssions from both streams.
    myManager.update()
    assertThat(myManager.sessionArtifacts).hasSize(2)

  }

  @Test
  fun testDeleteUnselectedSession() {
    Assume.assumeFalse(ideProfilerServices.featureConfig.isUnifiedPipelineEnabled)
    val device = Common.Device.newBuilder().setDeviceId(1).setState(Common.Device.State.ONLINE).build()
    val process1 = Common.Process.newBuilder().setPid(10).setDeviceId(1).setState(Common.Process.State.ALIVE).build()
    val process2 = Common.Process.newBuilder().setPid(20).setDeviceId(1).setState(Common.Process.State.ALIVE).build()
    myProfilerService.addDevice(device)
    myProfilerService.addProcess(device, process1)
    myProfilerService.addProcess(device, process2)
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS)
    myProfilers.setProcess(device, process1)
    // Create a finished session and a ongoing profiling session.
    endSessionHelper()
    myProfilers.setProcess(device, process2)
    myManager.update()
    val session1 = myManager.sessionArtifacts[1].session
    val session2 = myManager.selectedSession
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.selectedSession).isEqualTo(session2)
    assertThat(myManager.isSessionAlive).isTrue()
    assertThat(myProfilers.device).isEqualTo(device)
    assertThat(myProfilers.process).isEqualTo(process2)

    myManager.deleteSession(session1)
    myManager.update()
    assertThat(myManager.profilingSession).isEqualTo(session2)
    assertThat(myManager.selectedSession).isEqualTo(session2)
    assertThat(myManager.isSessionAlive).isTrue()
    assertThat(myProfilers.device).isEqualTo(device)
    assertThat(myProfilers.process).isEqualTo(process2)
    assertThat(myManager.sessionArtifacts.size).isEqualTo(1)
    assertThat(myManager.sessionArtifacts[0].session).isEqualTo(session2)
  }

  private fun beginSessionHelper(device: Common.Device, process: Common.Process) {
    if (useUnifiedEvents) {
      myManager.beginSession(1, device, process)
      myManager.update()
    }
    else {
      myManager.beginSession(device, process)
    }
  }

  private fun endSessionHelper() {
    myManager.endCurrentSession()
    if (useUnifiedEvents) {
      myManager.update()
    }
  }


  private class SessionsAspectObserver : AspectObserver() {
    var selectedSessionChangedCount: Int = 0
    var profilingSessionChangedCount: Int = 0
    var sessionsChangedCount: Int = 0

    internal fun selectedSessionChanged() {
      selectedSessionChangedCount++
    }

    internal fun profilingSessionChanged() {
      profilingSessionChangedCount++
    }

    internal fun sessionsChanged() {
      sessionsChangedCount++
    }
  }
}