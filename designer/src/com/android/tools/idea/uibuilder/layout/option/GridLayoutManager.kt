/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.layout.option

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.margin
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.common.surface.ZoomConstants.DEFAULT_MAX_SCALE
import com.android.tools.idea.common.surface.ZoomConstants.DEFAULT_MIN_SCALE
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.layout.padding.DEFAULT_LAYOUT_PADDING
import com.android.tools.idea.uibuilder.layout.padding.OrganizationPadding
import com.android.tools.idea.uibuilder.layout.positionable.GridLayoutGroup
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.NO_GROUP_TRANSFORM
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.MAX_ITERATION_TIMES
import com.android.tools.idea.uibuilder.surface.layout.SCALE_UNIT
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.Point
import kotlin.math.max
import org.jetbrains.annotations.VisibleForTesting

/**
 * Experimental grid layout. All previews are organized in groups.
 *
 * @param padding The padding used for content organized in groups (with a header for each group).
 * @param transform The lambda that converts the [PositionableContent]s in [PositionableGroup]s
 *   taking care to add headers when needed.
 */
open class GridLayoutManager(
  private val padding: OrganizationPadding = DEFAULT_LAYOUT_PADDING,
  val transform: (Collection<PositionableContent>) -> List<PositionableGroup> = NO_GROUP_TRANSFORM,
) : SurfaceLayoutManager {

  override fun getRequiredSize(
    content: Collection<PositionableContent>,
    @SwingCoordinate availableWidth: Int,
    @SwingCoordinate availableHeight: Int,
    @SwingCoordinate dimension: Dimension?,
  ) = getSize(content, { scale }, availableWidth)

  /**
   * Special case of single content (non-header) is handled differently. Returns this single content
   * or null.
   */
  private fun Collection<PositionableContent>.singleContentOrNull() =
    this.singleOrNull().takeIf { it !is HeaderPositionableContent }

  /** Get the total required size to layout the [content] with the given conditions. */
  fun getSize(
    content: Collection<PositionableContent>,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
  ): Dimension {
    return calculateSize(content = content, scaleFunc = scaleFunc, availableWidth = availableWidth)
  }

  private fun calculateSize(
    content: Collection<PositionableContent>,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
  ): Dimension {
    // Special case for single content
    content.singleContentOrNull()?.let {
      val previewSize = it.sizeForScale(it.scaleFunc())
      return Dimension(
        previewSize.width + padding.canvasSinglePadding * 2 + getResizableHoveringArea(),
        previewSize.height + padding.canvasSinglePadding * 2 + getResizableHoveringArea(),
      )
    }
    val groups = createLayoutGroups(transform(content), scaleFunc, availableWidth)
    val groupSizes = groups.map { group -> getGroupSize(group, scaleFunc) }
    val requiredWidth = groupSizes.maxOfOrNull { it.width } ?: 0
    val requiredHeight = groupSizes.sumOf { it.height }
    return Dimension(
      max(0, padding.canvasLeftPadding + requiredWidth),
      max(0, padding.canvasVerticalPadding + requiredHeight),
    )
  }

  /** Creates a layout from the given [PositionableGroup]s considering the available width. */
  @VisibleForTesting
  fun createLayoutGroups(
    groups: List<PositionableGroup>,
    scaleFunc: PositionableContent.() -> Double,
    availableWidth: Int,
  ): List<GridLayoutGroup> = groups.map { calculateNewLayout(it, scaleFunc, availableWidth) }

  /** Get the total required size of the [PositionableContent]s in grid layout. */
  @VisibleForTesting
  fun getGroupSize(
    layoutGroup: GridLayoutGroup,
    scaleFunc: PositionableContent.() -> Double,
  ): Dimension {
    var groupRequiredWidth = 0
    var groupRequiredHeight = 0
    layoutGroup.header?.let {
      val scale = it.scaleFunc()
      val bottomPadding = padding.previewBottomPadding(scale, it)
      groupRequiredHeight += it.sizeForScale(scale).height + bottomPadding
    }

    for (row in layoutGroup.rows) {
      var rowX = 0
      var currentHeight = 0
      for (view in row) {
        val scale = view.scaleFunc()
        val scaledSize = view.sizeForScale(scale)
        // What we need to take into account to calculate the width:
        // * the total scaled width of the view.
        // * the bottom padding.
        rowX += scaledSize.width + padding.previewRightPadding(scale, view)
        // To calculate the height that we might add in addition to currentHeight:
        // * the total scaled height of the view.
        // * the right padding.
        val newHeight = scaledSize.height + padding.previewBottomPadding(scale, view)
        currentHeight = max(currentHeight, newHeight)
      }
      groupRequiredWidth = max(groupRequiredWidth, rowX)
      groupRequiredHeight += currentHeight
    }
    groupRequiredWidth += if (layoutGroup.header != null) padding.groupLeftPadding else 0
    return Dimension(groupRequiredWidth, groupRequiredHeight)
  }

  @SurfaceScale
  override fun getFitIntoScale(
    content: Collection<PositionableContent>,
    @SwingCoordinate availableWidth: Int,
    @SwingCoordinate availableHeight: Int,
  ): Double {
    if (content.none { it !is HeaderPositionableContent }) {
      // No content or only Headers are showing.
      // Use 100% as zoom level with JBUIScale.sysScale() taken into account.
      return 1.0 / JBUIScale.sysScale()
    }

    // Use binary search to find the proper zoom-to-fit value, we use lowerBound and upperBound as
    // the min and max values of zoom-to-fit
    return getMaxZoomToFitScale(
      content = content,
      min = DEFAULT_MIN_SCALE * JBUIScale.sysScale(),
      max = DEFAULT_MAX_SCALE * JBUIScale.sysScale(),
      width = availableWidth,
      height = availableHeight,
      depth = 0,
    )
  }

  /** Binary search to find the largest scale for [width] x [height] space. */
  @SurfaceScale
  private fun getMaxZoomToFitScale(
    content: Collection<PositionableContent>,
    @SurfaceScale min: Double,
    @SurfaceScale max: Double,
    @SwingCoordinate width: Int,
    @SwingCoordinate height: Int,
    depth: Int = 0,
  ): Double {
    if (depth >= MAX_ITERATION_TIMES) {
      // because we want to show the content within the available space we get minimum resulting fit
      // scale even in case we reach the max iteration number and we haven't found the perfect zoom
      // to fit value.
      // It could be that the applying the resulting zoom to fit there could be additional available
      // space, for a better granularity we can increase MAX_ITERATION_TIMES.
      return min
    }
    if (max - min <= SCALE_UNIT) {
      // max and min are minor than the unit scale, because we want to show the content within the
      // available space we get minimum resulting fit scale
      return min
    }
    val scale = (min + max) / 2
    // We get the sizes of the content with the new scale applied.
    val dim = calculateSize(content = content, scaleFunc = { scale }, availableWidth = width)
    return if (dim.height <= height && dim.width <= width) {
      // We want the resulting content fitting into the height we try to lower the scale
      getMaxZoomToFitScale(content, scale, max, width, height, depth + 1)
    } else {
      // We want can increase the scale as the scaled dimension results on a lower height than the
      // available height
      getMaxZoomToFitScale(content, min, scale, width, height, depth + 1)
    }
  }

  /**
   * Arrange [PositionableGroup]s into a 2-dimension list which represent a list of row of
   * [PositionableContent]. The [widthFunc] is for getting the preferred widths of
   * [PositionableContent]s when filling the horizontal spaces.
   */
  private fun calculateNewLayout(
    group: PositionableGroup,
    scaleFunc: PositionableContent.() -> Double,
    @SwingCoordinate availableWidth: Int,
  ): GridLayoutGroup {
    if (group.content.isEmpty()) {
      return GridLayoutGroup(group.header, emptyList())
    }

    // The value that is going to be added to the width of the row.
    var nextX = 0
    // This list represents the final content in row and columns.
    val gridList = mutableListOf<List<PositionableContent>>()
    // The current column in the layout.
    var columnList = mutableListOf<PositionableContent>()

    // Actual width to fill is less as there is also canvas and group paddings.
    val widthToFill =
      availableWidth -
        padding.canvasLeftPadding -
        if (group.hasHeader) padding.groupLeftPadding else 0

    // Once we have initialized columnList and nextX for the first preview, we can proceed checking
    // all the remaining content.
    group.content.forEach { view ->
      val scale = view.scaleFunc()
      val scaledWidth = view.sizeForScale(scale).width
      // The next space occupied in the row is represented by:
      //    * The total scaled width of the view.
      //    * The right preview padding of the view.
      val nextViewWidth = scaledWidth + padding.previewRightPadding(scale, view)
      if (nextX + nextViewWidth > widthToFill && columnList.isNotEmpty()) {
        // If the current calculated width and the one we have calculated in the previous iterations
        // is exceeding the available space, we reset the column and nextX so we can add the view on
        // a new line.
        nextX = nextViewWidth
        gridList.add(columnList)
        columnList = mutableListOf(view)
      } else {
        // If we are still within the available width we can add the view in the row
        // We can update nextX with the calculated width.
        nextX += nextViewWidth
        columnList.add(view)
      }
    }
    gridList.add(columnList)
    return GridLayoutGroup(group.header, gridList)
  }

  override fun measure(
    content: Collection<PositionableContent>,
    availableWidth: Int,
    availableHeight: Int,
    keepPreviousPadding: Boolean,
  ): Map<PositionableContent, Point> {
    if (content.isEmpty()) {
      return emptyMap()
    }

    //    Special case - position one element at the center
    //    (1) canvasSinglePadding
    //
    //     ←------------            availableWidth        ------------→
    //
    //     ___________________________________________________________
    //     | surface                                                  |     ↑
    //     |                         (1) ↕                            |     |
    //     |                  _________________                       |     |
    //     |  (1)             | preview        |    (1)               |     |
    //     |   ↔              |                |     ↔                |     | availableHeight
    //     |                  |                |                      |     |
    //     |                  |________________|                      |     |
    //     |                        (1) ↕                             |     |
    //     |                                                          |     |
    //     ------------------------------------------------------------     ↓

    content.singleContentOrNull()?.let { singleContent ->
      // When there is only one visible preview, centralize it as a special case.
      val previewSize = singleContent.sizeForScale(singleContent.scale)
      // Try to centralize the content.
      val x =
        maxOf(
          (availableWidth - previewSize.width - getResizableHoveringArea()) / 2,
          padding.canvasSinglePadding,
        )
      val y =
        maxOf(
          (availableHeight - previewSize.height - getResizableHoveringArea()) / 2,
          padding.canvasSinglePadding,
        )
      return mapOf(singleContent to getContentPosition(singleContent, x, y))
    }

    val groups = createLayoutGroups(transform(content), { scale }, availableWidth)
    var nextY = padding.canvasTopPadding
    var nextX = 0
    var maxYInRow = 0
    val positionMap = mutableMapOf<PositionableContent, Point>()

    //     (1) canvasLeftPadding                      Y1 - maxYInRow for 1st row
    //     (2) previewRightPadding                    Y1 - maxYInRow for 1st row
    //     (3) previewBottomPadding                   Y1 - maxYInRow for 1st row
    //     (4) groupLeftPadding
    //
    //    Each (preview) includes PositionableContent.getMargin(scale) +
    // PositionableContent.contentSize * scale
    //    - which is the total size of the PositionableContent for currently set scale.
    //
    //    -------------------------------------(X)----------------------------------------→
    //
    //
    //     _______________________________________________________________
    //     |surface                 canvasTopPadding                     |    |     |     |
    //     |_____|______________________________________________________ |    |     |     |
    //     |     |_________________       _________________              |    |     |     |
    //     | (1) || preview 1      |  ↔   | preview 2      |             |    |     |     |
    //     |     ||                | (2)  |                |             |    |     |     |
    //     |     ||                |      |                |             |    |     |     |
    //     |     ||________________|      |                |             |    |     |     |
    //     |     |      ↕ (3)             |                |             |    |     |     |
    //     |     |                        |                |             |    |     |     |
    //     |     |                        |________________|             |    |     |     |
    //     |     |                                  ↕ (3)                |    ↓Y1   |     |
    //     |     |__________________                                     |          |     |
    //     |     || header         |  ↔                                  |          |     |
    //     |     |------------------ (2)                                 |          |     |
    //     |     |      ↕ (3)                                            |          |     |
    //     |     |    _________________       ____________________       |          |     |
    //     |     | ↔  | preview 3      |  ↔   | preview 4         |  ↔   |          |     |
    //     |     |(4) |                | (2)  |                   | (2)  |          |     |
    //     |     |    |                |      |                   |      |          |     |
    //     |     |    |________________|      |                   |      |          |     |
    //     |     |         ↕ (3)              |                   |      |          |     |
    //     |     |                            |                   |      |          |     |
    //     |     |                            |___________________|      |          |     |
    //     |     |                                  ↕ (3)                |          ↓Y2   |
    //     |     |     ____________________                              |                |
    //     |     | ↔  | preview 5         |  ↔                           |                |
    //     |     |(4) |                   | (2)                          |                |
    //     |     |    |                   |                              |                |
    //     |     |    |                   |                              |                |
    //     |     |    |                   |                              |                |
    //     |     |    |___________________|                              |                |
    //     |     |           ↕ (3)                                       |                ↓Y3
    //     ---------------------------------------------------------------
    //
    //  There is no available groups:
    //     _______________________________________________________________
    //     |surface                 canvasTopPadding                     |    |     |     |
    //     |_____|______________________________________________________ |    |     |     |
    //     |     |_________________       _________________              |    |     |     |
    //     | (1) || preview 1      |  ↔   | preview 2      |             |    |     |     |
    //     |     ||                | (2)  |                |             |    |     |     |
    //     |     ||                |      |                |             |    |     |     |
    //     |     ||________________|      |                |             |    |     |     |
    //     |     |      ↕ (3)             |                |             |    |     |     |
    //     |     |                        |                |             |    |     |     |
    //     |     |                        |________________|             |    |     |     |
    //     |     |                                  ↕ (3)                |    ↓Y1   |     |
    //     |     |_____________________       ____________________       |          |     |
    //     |     || preview 3          |  ↔   | preview 4         |  ↔   |          |     |
    //     |     ||                    | (2)  |                   | (2)  |          |     |
    //     |     ||                    |      |                   |      |          |     |
    //     |     ||____________________|      |                   |      |          |     |
    //     |     |     ↕ (3)                  |                   |      |          |     |
    //     |     |                            |                   |      |          |     |
    //     |     |                            |___________________|      |          |     |
    //     |     |                                  ↕ (3)                |          ↓Y2   |
    //     |     |________________________                               |                |
    //     |     || preview 5             |  ↔                           |                |
    //     |     ||                       | (2)                          |                |
    //     |     ||                       |                              |                |
    //     |     ||                       |                              |                |
    //     |     ||                       |                              |                |
    //     |     ||_______________________|                              |                |
    //     |     |       ↕ (3)                                           |                ↓Y3
    //     ---------------------------------------------------------------

    groups.forEach { layoutGroup ->
      val groupLeftPadding = if (layoutGroup.header != null) padding.groupLeftPadding else 0

      fun measure(view: PositionableContent) {
        positionMap[view] = getContentPosition(view, nextX, nextY)
        val previewRightPadding = padding.previewRightPadding(view.scale, view)
        val previewBottomPadding = padding.previewBottomPadding(view.scale, view)
        val scaledSize = view.sizeForScale(view.scale)
        nextX += scaledSize.width + previewRightPadding
        maxYInRow = max(maxYInRow, nextY + scaledSize.height + previewBottomPadding)
      }
      layoutGroup.header?.let {
        nextX = padding.canvasLeftPadding
        measure(it)
        nextY = maxYInRow
      }
      layoutGroup.rows.forEach { row ->
        nextX = padding.canvasLeftPadding + groupLeftPadding
        row.forEach { measure(it) }
        nextY = maxYInRow
      }
    }
    return positionMap
  }

  /** Get the actual position should be set to the given [PositionableContent] */
  @SwingCoordinate
  private fun getContentPosition(
    content: PositionableContent,
    @SwingCoordinate previewX: Int,
    @SwingCoordinate previewY: Int,
  ): Point {
    // The new compose layout consider the toolbar size as the anchor of location.
    val margin = content.margin
    val shiftedX = previewX + margin.left
    val shiftedY = previewY + margin.top
    return Point(shiftedX, shiftedY)
  }

  /** @return [NlConstants.RESIZING_HOVERING_SIZE] if [containsResizableContent], 0 otherwsie */
  private fun getResizableHoveringArea(): Int =
    NlConstants.RESIZING_HOVERING_SIZE.takeIf { containsResizableContent } ?: 0
}
