/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ComposeViewInfo.serializeHits(x: Int, y: Int): String =
  findHitWithDepth(x, y)
    .sortedBy { it.first }
    .joinToString("\n") { "${it.first}: ${it.second.sourceLocation.fileName}" }

class ComposeViewInfoTest {
  private data class TestSourceLocation(
    override val fileName: String = "",
    override val lineNumber: Int = -1,
    override val packageHash: Int = -1,
  ) : SourceLocation

  @Test
  fun checkFindAllHitsWithPoint() {
    //              root
    //            /     \
    //          fileA    fileC
    //           /
    //        fileB (line 4)
    //       /              \
    //     fileA (line 5)    fileB  (line 7)
    //                             \
    //                             fileA (line 8)
    //                                \
    //                               fileC
    //
    // Given that all the components shown above contain the point x, y and the file A is passed
    // into the function findLeafHitsInFile will return both components on line 5 and line 8 of
    // file A.

    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("fileA"),
              PxBounds(0, 0, 0, 0),
              children = listOf(),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileB", lineNumber = 4),
              PxBounds(0, 0, 200, 200),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("fileA", lineNumber = 5),
                    PxBounds(0, 0, 200, 200),
                    children = listOf(),
                    name = "",
                  ),
                  ComposeViewInfo(
                    TestSourceLocation("fileB", lineNumber = 7),
                    PxBounds(0, 0, 200, 200),
                    children =
                      listOf(
                        ComposeViewInfo(
                          TestSourceLocation("fileA", lineNumber = 8),
                          PxBounds(0, 0, 200, 200),
                          children =
                            listOf(
                              ComposeViewInfo(
                                TestSourceLocation("fileC", lineNumber = 8),
                                PxBounds(0, 0, 200, 200),
                                children = listOf(),
                                name = "",
                              )
                            ),
                          name = "",
                        )
                      ),
                    name = "",
                  ),
                ),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
              name = "",
            ),
          ),
        name = "",
      )

    assertTrue(root.findAllHitsWithPoint(2000, 2000).isEmpty())

    val hits = root.findAllHitsWithPoint(125, 125)
    assertEquals(hits.size, 6)
    assertEquals("root", hits.first().sourceLocation.fileName)
    assertEquals("fileB", hits[1].sourceLocation.fileName)
    assertEquals(4, hits[1].sourceLocation.lineNumber)
    assertEquals("fileB", hits[2].sourceLocation.fileName)
    assertEquals(7, hits[2].sourceLocation.lineNumber)
    assertEquals("fileA", hits[3].sourceLocation.fileName)
    assertEquals(8, hits[3].sourceLocation.lineNumber)
    assertEquals("fileC", hits[4].sourceLocation.fileName)
    assertEquals(8, hits[4].sourceLocation.lineNumber)
    assertEquals("fileA", hits[5].sourceLocation.fileName)
    assertEquals(8, hits[3].sourceLocation.lineNumber)

  }

  @Test
  fun checkFindHitsWithDepth() {
    //                    root
    //              /      |       \
    //          fileA     fileB        fileC
    //           /
    //        fileB (line 4)
    //       /              \
    //     fileA (line 5)    fileB  (line 7)
    //                             \
    //                             fileA (line 8)
    //                                \
    //                               fileC
    //
    // Given that all the components shown above contain the point x, y and the file A is passed
    // into the function findLeafHitsInFile will return both components on line 5 and line 8 of
    // file A.

    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("fileA", lineNumber = 4),
              PxBounds(0, 0, 0, 0),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("fileA", lineNumber = 5),
                    PxBounds(0, 0, 200, 200),
                    children = listOf(),
                    name = "",
                  ),
                  ComposeViewInfo(
                    TestSourceLocation("fileB", lineNumber = 7),
                    PxBounds(0, 0, 200, 200),
                    children =
                      listOf(
                        ComposeViewInfo(
                          TestSourceLocation("fileA", lineNumber = 8),
                          PxBounds(0, 0, 0, 200),
                          children =
                            listOf(
                              ComposeViewInfo(
                                TestSourceLocation("fileC", lineNumber = 8),
                                PxBounds(0, 0, 200, 200),
                                children = listOf(),
                                name = "",
                              )
                            ),
                          name = "",
                        )
                      ),
                    name = "",
                  ),
                ),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileB"),
              PxBounds(0, 0, 0, 0),
              children = listOf(),
              name = "",
            ),
          ),
        name = "",
      )

    assertTrue(root.findHitWithDepth(2000, 2000).isEmpty())

    val hitsWithDepth = root.findHitWithDepth(125, 125)

    assertEquals(4, hitsWithDepth.size)

    val sortedHitsWithDepth = hitsWithDepth.sortedBy { it.first }
    assertEquals(0, sortedHitsWithDepth.first().first)
    assertEquals("root", sortedHitsWithDepth.first().second.sourceLocation.fileName)
    assertEquals(2, sortedHitsWithDepth.get(1).first)
    assertEquals("fileB", sortedHitsWithDepth.get(1).second.sourceLocation.fileName)
    assertEquals(7, sortedHitsWithDepth.get(1).second.sourceLocation.lineNumber)

    assertEquals(2, sortedHitsWithDepth.get(2).first)
    assertEquals("fileA", sortedHitsWithDepth.get(2).second.sourceLocation.fileName)
    assertEquals(5, sortedHitsWithDepth.get(2).second.sourceLocation.lineNumber)

    assertEquals(4, sortedHitsWithDepth.get(3).first)
    assertEquals("fileC", sortedHitsWithDepth.get(3).second.sourceLocation.fileName)
    assertEquals(8, sortedHitsWithDepth.get(3).second.sourceLocation.lineNumber)
  }

  @Test
  fun checkAllHitsInFile() {
    //                      root
    //                  /          \
    //          fileA (line 1)      fileC
    //           /
    //       fileB (line 4)
    //       /              \
    //     fileA (line 5)    fileB  (line 7)
    //                             \
    //                             fileA (line 8)
    //
    // Given that file A is passed the function findAllHitsInFile will return all components aka the
    // ones on line 1 line 5 and line 8 of file A.

    val root =
      ComposeViewInfo(
        TestSourceLocation("root"),
        PxBounds(0, 0, 1000, 300),
        children =
          listOf(
            ComposeViewInfo(
              TestSourceLocation("fileA", lineNumber = 1),
              PxBounds(0, 0, 0, 0),
              children = listOf(),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileB", lineNumber = 4),
              PxBounds(0, 0, 200, 200),
              children =
                listOf(
                  ComposeViewInfo(
                    TestSourceLocation("fileA", lineNumber = 5),
                    PxBounds(0, 0, 200, 200),
                    children = listOf(),
                    name = "",
                  ),
                  ComposeViewInfo(
                    TestSourceLocation("fileB", lineNumber = 7),
                    PxBounds(0, 0, 200, 200),
                    children =
                      listOf(
                        ComposeViewInfo(
                          TestSourceLocation("fileA", lineNumber = 8),
                          PxBounds(0, 0, 200, 200),
                          children = listOf(),
                          name = "",
                        )
                      ),
                    name = "",
                  ),
                ),
              name = "",
            ),
            ComposeViewInfo(
              TestSourceLocation("fileC", lineNumber = 10),
              PxBounds(400, 200, 1000, 300),
              children = listOf(),
              name = "",
            ),
          ),
        name = "",
      )

    val leafHits = root.findAllHitsInFile("fileA")
    assertEquals(leafHits.size, 3)
    assertEquals(leafHits.first().sourceLocation.fileName, "fileA")
    assertEquals(leafHits.first().sourceLocation.lineNumber, 8)
    assertEquals(leafHits[1].sourceLocation.fileName, "fileA")
    assertEquals(leafHits[1].sourceLocation.lineNumber, 5)
    assertEquals(leafHits[2].sourceLocation.fileName, "fileA")
    assertEquals(leafHits[2].sourceLocation.lineNumber, 1)
  }

  @Test
  fun checkBoundsMethods() {
    assertTrue(PxBounds(0, 0, 0, 0).isEmpty())
    assertFalse(PxBounds(0, 0, 0, 0).isNotEmpty())
    assertTrue(PxBounds(0, 0, 0, 1).isEmpty())
    assertFalse(PxBounds(0, 0, 0, 1).isNotEmpty())
    assertTrue(PxBounds(0, 0, 1, 1).isNotEmpty())
    assertFalse(PxBounds(0, 0, 1, 1).isEmpty())

    val testBounds = PxBounds(20, 30, 50, 100)
    assertTrue(testBounds.containsPoint(50, 100))
    assertTrue(testBounds.containsPoint(35, 100))
    assertTrue(testBounds.containsPoint(20, 30))
    assertEquals(2100, testBounds.area())
  }
}
