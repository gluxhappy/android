/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui.data.builder

import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CriticalPathReportBuilderTest : AbstractBuildAttributionReportBuilderTest() {

  @Test
  fun testTasksCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 100, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskB = TaskData("taskB", ":app", pluginB, 400, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskC = TaskData("taskC", ":lib", pluginA, 300, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskD = TaskData("taskD", ":app", pluginB, 200, TaskData.TaskExecutionMode.FULL, emptyList())


    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTime(): Long = 1500
      override fun getCriticalPathDuration(): Long = 1000
      override fun getTasksCriticalPath(): List<TaskData> = listOf(taskA, taskB, taskC, taskD)
      override fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> = listOf(
        CriticalPathAnalyzer.PluginBuildData(pluginA, 400),
        CriticalPathAnalyzer.PluginBuildData(pluginB, 600)
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345).build()

    assertThat(report.buildSummary.totalBuildDuration.timeMs).isEqualTo(1500)
    assertThat(report.criticalPathTasks.criticalPathDuration).isEqualTo(TimeWithPercentage(1000, 1500))
    assertThat(report.criticalPathTasks.miscStepsTime).isEqualTo(TimeWithPercentage(500, 1500))
    assertThat(report.criticalPathTasks.size).isEqualTo(4)
    //Sorted by time descending
    report.criticalPathTasks.tasks[0].verifyValues(":app", "taskB", pluginB, TimeWithPercentage(400, 1500))
    report.criticalPathTasks.tasks[1].verifyValues(":lib", "taskC", pluginA, TimeWithPercentage(300, 1500))
    report.criticalPathTasks.tasks[2].verifyValues(":app", "taskD", pluginB, TimeWithPercentage(200, 1500))
    report.criticalPathTasks.tasks[3].verifyValues(":app", "taskA", pluginA, TimeWithPercentage(100, 1500))
  }

  @Test
  fun testPluginsCriticalPath() {
    val taskA = TaskData("taskA", ":app", pluginA, 100, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskB = TaskData("taskB", ":app", pluginB, 400, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskC = TaskData("taskC", ":lib", pluginA, 300, TaskData.TaskExecutionMode.FULL, emptyList())
    val taskD = TaskData("taskD", ":app", pluginB, 200, TaskData.TaskExecutionMode.FULL, emptyList())


    val analyzerResults = object : MockResultsProvider() {
      override fun getTotalBuildTime(): Long = 1500
      override fun getCriticalPathDuration(): Long = 1000
      override fun getTasksCriticalPath(): List<TaskData> = listOf(taskA, taskB, taskC, taskD)
      override fun getPluginsCriticalPath(): List<CriticalPathAnalyzer.PluginBuildData> = listOf(
        CriticalPathAnalyzer.PluginBuildData(pluginA, 400),
        CriticalPathAnalyzer.PluginBuildData(pluginB, 600)
      )
    }

    val report = BuildAttributionReportBuilder(analyzerResults, 12345).build()

    assertThat(report.criticalPathPlugins.criticalPathDuration).isEqualTo(TimeWithPercentage(1000, 1500))
    assertThat(report.criticalPathPlugins.miscStepsTime).isEqualTo(TimeWithPercentage(500, 1500))
    assertThat(report.criticalPathPlugins.plugins.size).isEqualTo(2)
    assertThat(report.criticalPathPlugins.plugins[0].name).isEqualTo("pluginB")
    assertThat(report.criticalPathPlugins.plugins[0].criticalPathTasks.size).isEqualTo(2)
    assertThat(report.criticalPathPlugins.plugins[0].criticalPathDuration).isEqualTo(TimeWithPercentage(600, 1500))
    assertThat(report.criticalPathPlugins.plugins[1].name).isEqualTo("pluginA")
    assertThat(report.criticalPathPlugins.plugins[1].criticalPathTasks.size).isEqualTo(2)
    assertThat(report.criticalPathPlugins.plugins[1].criticalPathDuration).isEqualTo(TimeWithPercentage(400, 1500))
  }

  private fun TaskUiData.verifyValues(project: String, name: String, plugin: PluginData, time: TimeWithPercentage) {
    assertThat(taskPath).isEqualTo("${project}:${name}")
    assertThat(module).isEqualTo(project)
    assertThat(pluginName).isEqualTo(plugin.displayName)
    assertThat(onCriticalPath).isTrue()
    assertThat(executionTime).isEqualTo(time)
  }
}