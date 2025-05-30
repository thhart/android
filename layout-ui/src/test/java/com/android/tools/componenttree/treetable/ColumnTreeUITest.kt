/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.componenttree.treetable

import com.android.SdkConstants
import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoCleanerRule
import com.android.tools.adtui.swing.laf.HeadlessTableUI
import com.android.tools.componenttree.api.ComponentTreeBuildResult
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.createIntColumn
import com.android.tools.componenttree.util.Item
import com.android.tools.componenttree.util.ItemNodeType
import com.android.tools.componenttree.util.StyleNodeType
import com.android.tools.idea.flags.StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_HORIZONTAL_SCROLLABLE_COMPONENT_TREE
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Dimension
import java.awt.Point
import javax.swing.JScrollPane
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class ColumnTreeUITest {
  companion object {
    @JvmField @ClassRule val rule = ApplicationRule()
  }

  private val item1 = Item(tagName = SdkConstants.TAG_ITEM, textValue = "t")
  private val item2 = Item(tagName = SdkConstants.FQCN_LINEAR_LAYOUT, textValue = "anotherTestText")
  private val item3 = Item(tagName = SdkConstants.FQCN_LINEAR_LAYOUT, textValue = "testText")

  @get:Rule val chain: RuleChain? = RuleChain.outerRule(MockitoCleanerRule()).around(EdtRule())

  @Test
  fun testHorizontalScrollBarVisibility() {
    val result = createTree() { withExpandableRoot() }
    val table = result.focusComponent as TreeTableImpl
    val scroll = result.component.getComponent(1) as ColumnTreeScrollPanel

    // When nodes are not expanded.
    assertThat(scroll.isVisible).isFalse()

    TreeUtil.expandAll(table.tree)

    // When nodes are expanded.
    assertThat(scroll.isVisible).isTrue()
  }

  @Test
  fun testScrollTreeHorizontally() {
    val result = createTree()
    val table = result.focusComponent as TreeTableImpl
    val scroll = result.hScrollPanel as ColumnTreeScrollPanel

    TreeUtil.expandAll(table.tree)

    assertThat(table.tree.getRowBounds(0).x).isEqualTo(0)

    // Scroll to right
    scroll.getModel().value = 30
    assertThat(table.tree.getRowBounds(0).x).isEqualTo(-30)

    // Scroll to left
    scroll.getModel().value = -30
    assertThat(table.tree.getRowBounds(0).x).isEqualTo(0)
  }

  @Test
  fun testAutoScrollTreeHorizontallyOnSelection() {
    val result = createTree()
    val table = result.focusComponent as TreeTableImpl
    val verticalScrollPane = result.vScrollPane as JScrollPane
    table.columnModel.getColumn(0).apply {
      preferredWidth = 80
      minWidth = 80
      maxWidth = 80
    }
    // Each item has a height of 16, which means the viewport only sees
    // the last two items in the tree.
    verticalScrollPane.viewport.viewPosition = Point(0, 20)
    verticalScrollPane.viewport.extentSize = Dimension(32, 32)

    TreeUtil.expandAll(table.tree)
    table.setRowSelectionInterval(2, 2)

    val horizontalScrollPane = result.hScrollPanel as ColumnTreeScrollPanel

    assertThat(horizontalScrollPane.getModel().value).isEqualTo(2)
  }

  @Test
  fun testAutoScrollTreeVerticallyOnSelection() {
    val result = createTree()
    val table = result.focusComponent as TreeTableImpl
    val verticalScrollPane = result.vScrollPane as JScrollPane

    // Each item has a height of 16, which means the viewport only sees
    // the last two items in the tree.
    verticalScrollPane.viewport.viewPosition = Point(0, 0)
    verticalScrollPane.viewport.extentSize = Dimension(16, 16)

    TreeUtil.expandAll(table.tree)
    table.setRowSelectionInterval(2, 2)

    assertThat(verticalScrollPane.verticalScrollBar.model.value).isAtLeast(32)
  }

  private fun createTree(
    customChange: ComponentTreeBuilder.() -> ComponentTreeBuilder = { this }
  ): ComponentTreeBuildResult {
    val result = createTreeWithScrollPane(customChange)
    val table = result.focusComponent as TreeTableImpl
    item2.add(item3)
    item1.add(item2)
    result.model.treeRoot = item1
    table.setUI(HeadlessTableUI())
    return result
  }

  private fun createTreeWithScrollPane(
    customChange: ComponentTreeBuilder.() -> ComponentTreeBuilder
  ): ComponentTreeBuildResult {
    return ComponentTreeBuilder()
      .withNodeType(ItemNodeType())
      .withNodeType(StyleNodeType())
      .withColumn(createIntColumn("c1", Item::column1))
      .withoutTreeSearch()
      .withInvokeLaterOption { it.run() }
      .withMultipleSelection()
      .customChange()
      .withAutoScroll()
      .build()
  }
}
