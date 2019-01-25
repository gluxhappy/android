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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.tools.adtui.model.stdui.EditingErrorCategory.ERROR
import com.android.tools.idea.common.property2.api.PropertiesTable
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.property2.testutils.SupportTestUtil
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class NeleNewPropertyItemTest {
  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule
  val edtRule = EdtRule()

  @Test
  fun testSetNameWithPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_TEXT
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.delegate).isNotNull()
  }

  @Test
  fun testSetNameNoMatch() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = ATTR_ORIENTATION
    assertThat(property.delegate).isNull()
  }

  @Test
  fun testSetNameWithoutPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = ATTR_STYLE
    assertThat(property.namespace).isEqualTo("")
    assertThat(property.delegate).isNotNull()
  }

  @Test
  fun testSetNameWithAppPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = PREFIX_APP + ATTR_SRC_COMPAT
    assertThat(property.namespace).isEqualTo(AUTO_URI)
    assertThat(property.delegate).isNotNull()
  }

  @Test
  fun testDelegate() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_TEXT
    val delegate = property.delegate!!
    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_TEXT)

    property.value = "Hello"
    assertThat(property.value).isEqualTo("Hello")
    assertThat(property.resolvedValue).isEqualTo("Hello")
    assertThat(property.isReference).isFalse()
    assertThat(property.tooltipForName).isEqualTo(EXPECTED_TEXT_TOOLTIP)
    assertThat(property.tooltipForValue).isEqualTo("")
  }

  @Test
  fun testDelegateWithoutPrefix() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = ATTR_TEXT
    val delegate = property.delegate!!
    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_TEXT)
    assertThat(property.namespace).isEqualTo(ANDROID_URI)
    assertThat(property.name).isEqualTo(ATTR_TEXT)
  }

  @Test
  fun testFlagsDelegate() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_GRAVITY
    val delegate = property.delegate!!
    assertThat(delegate.namespace).isEqualTo(ANDROID_URI)
    assertThat(delegate.name).isEqualTo(ATTR_GRAVITY)
    assertThat(property.children.map { it.name }).containsExactly(
      "top", "center_vertical", "bottom", "start", "left", "center_horizontal", "right", "end",
      "clip_horizontal", "clip_vertical", "center", "fill", "fill_horizontal", "fill_vertical")

    property.flag("center")?.value = "true"
    assertThat(property.value).isEqualTo("center")
    assertThat(property.resolvedValue).isEqualTo("center")
    assertThat(property.isReference).isFalse()
    assertThat(property.tooltipForName).isEqualTo(
      "<html><b>android:gravity:</b>" +
      "<br/>Specifies how an object should position its content, on both the X and Y axes, within its own bounds.</html>")
    assertThat(property.tooltipForValue).isEqualTo("")
  }

  @Test
  fun testCompletion() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    val values = property.nameEditingSupport.completion()
    assertThat(values).containsExactly("style", "android:text", "android:textSize", "android:textColor", "android:gravity", "app:srcCompat",
                                       "tools:text", "tools:textSize", "tools:textColor", "tools:gravity", "tools:srcCompat")
  }

  @Test
  fun testAssignedAttributesAreNotInCompletions() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    properties[ANDROID_URI, ATTR_TEXT].value = "Hello"
    properties[ANDROID_URI, ATTR_TEXT_COLOR].value = "#445566"
    val values = property.nameEditingSupport.completion()
    assertThat(values).containsExactly("style", "android:textSize", "android:gravity", "app:srcCompat",
                                       "tools:text", "tools:textSize", "tools:textColor", "tools:gravity", "tools:srcCompat")                   }

  @Test
  fun testValidationErrors() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    properties[ANDROID_URI, ATTR_TEXT].value = "Hello"
    assertThat(property.nameEditingSupport.validation("android:xyz")).isEqualTo(Pair(ERROR, "No property found by the name: 'android:xyz'"))
    assertThat(property.nameEditingSupport.validation("android:text"))
      .isEqualTo(Pair(ERROR, "A property by the name: 'android:text' is already specified"))
  }

  @Test
  fun testIsSameProperty() {
    val properties = createTable()
    val model = properties.first!!.model
    val property = NeleNewPropertyItem(model, properties)
    property.name = PREFIX_ANDROID + ATTR_TEXT
    assertThat(property.isSameProperty("android:text")).isTrue()
    assertThat(property.isSameProperty("android:backgroundHint")).isFalse()
  }

  private fun createTable(): PropertiesTable<NelePropertyItem> {
    val util = SupportTestUtil(projectRule, IMAGE_BUTTON, parentTag = LINEAR_LAYOUT)
    val property0 = util.makeProperty("", ATTR_STYLE, NelePropertyType.STYLE)
    val property1 = util.makeProperty(ANDROID_URI, ATTR_TEXT, NelePropertyType.STRING)
    val property2 = util.makeProperty(ANDROID_URI, ATTR_TEXT_SIZE, NelePropertyType.DIMENSION)
    val property3 = util.makeProperty(ANDROID_URI, ATTR_TEXT_COLOR, NelePropertyType.COLOR_STATE_LIST)
    val property4 = util.makeProperty(AUTO_URI, ATTR_SRC_COMPAT, NelePropertyType.DRAWABLE)
    val property5 = util.makeProperty(ANDROID_URI, ATTR_GRAVITY, NelePropertyType.ENUM)
    val table: Table<String, String, NelePropertyItem> = HashBasedTable.create()
    add(table, property0)
    add(table, property1)
    add(table, property2)
    add(table, property3)
    add(table, property4)
    add(table, property5)
    return PropertiesTable.create(table)
  }

  private fun add(table: Table<String, String, NelePropertyItem>, property: NelePropertyItem) {
    table.put(property.namespace, property.name, property)
  }
}