/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.naveditor.surface

import com.android.testutils.TestUtils
import com.android.testutils.ImageDiffUtil
import com.android.testutils.delayUntilCondition
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.IconLoaderRule
import com.android.tools.editor.zoomActionPlace
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.naveditor.model.NavComponentRegistrar
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createNoSecurityRenderService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.uibuilder.scene.AsyncDisplayRule
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JPanel
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class NavDesignSurfaceZoomControlsTest {
  private val androidProjectRule = AndroidProjectRule.withSdk()
  private val asyncDisplayRule = AsyncDisplayRule()

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(IconLoaderRule()) // Must be before AndroidProjectRule
      .around(asyncDisplayRule)
      .around(androidProjectRule)!!

  @Before
  fun setup() {
    androidProjectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/nav/editor/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    StudioRenderService.setForTesting(androidProjectRule.project, createNoSecurityRenderService())
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait { RenderTestUtil.afterRenderTestCase() }
  }

  /** Populate the [NavigationSchema] by using a fake androidx navigation library. */
  private fun populateSchema() {
    androidProjectRule.fixture.addFileToProject(
      "src/main/java/androidx/fragment/app/Fragment.kt", // language=kotlin
      """
        package androidx.fragment.app

        class Fragment
      """
        .trimIndent(),
    )

    @Suppress("RemoveEmptyClassBody")
    androidProjectRule.fixture.addFileToProject(
      "src/main/java/androidx/navigation/Navigators.kt", // language=kotlin
      """
        package androidx.navigation

        import android.app.Activity

        import androidx.fragment.app.Fragment

        open class Navigator<T> {
          annotation class Name(val value: String)
        }

        class NavGraph {
        }

        @Navigator.Name("navigation")
        class NavGraphNavigator : Navigator<NavGraph>()

        @Navigator.Name("activity")
        class ActivityNavigator : Navigator<Activity>()

        @Navigator.Name("fragment")
        class FragmentNavigator : Navigator<Fragment>()
      """
        .trimIndent(),
    )

    androidProjectRule.fixture.addFileToProject(
      "src/main/java/com/example/myapplication/FirstFragment.java", // language=Java
      """
        package com.example.myapplication;

        import androidx.fragment.app.Fragment;

        public class FirstFragment extends Fragment {
        }
      """
        .trimIndent(),
    )
    androidProjectRule.fixture.addFileToProject(
      "src/main/java/com/example/myapplication/SecondFragment.java", // language=Java
      """
        package com.example.myapplication;

        import androidx.fragment.app.Fragment;

        public class SecondFragment extends Fragment {
        }
      """
        .trimIndent(),
    )

    executeOnPooledThread {
        runReadAction { NavigationSchema.createIfNecessary(androidProjectRule.module) }
        IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects()
      }
      .get()
  }

  private fun addLayout() =
    androidProjectRule.fixture.addFileToProject(
      "res/layout/test.xml", // language=xml
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:background="#0000FF"
              android:padding="30dp"
              android:orientation="vertical">
              <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize='40sp'
                android:text="Hello world"/>
        </LinearLayout>
      """
        .trimIndent(),
    )

  private fun addNavGraph() =
    androidProjectRule.fixture.addFileToProject(
      "res/navigation/nav_graph.xml", // language=xml
      """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            android:id="@+id/nav_graph"
            app:startDestination="@id/FirstFragment">

            <fragment
                android:id="@+id/FirstFragment"
                android:name="com.example.myapplication.FirstFragment"
                android:label="First"
                tools:layout="@layout/test">

                <action
                    android:id="@+id/action_FirstFragment_to_SecondFragment"
                    app:destination="@id/SecondFragment" />
            </fragment>
            <fragment
                android:id="@+id/SecondFragment"
                android:name="com.example.myapplication.SecondFragment"
                android:label="Second"
                tools:layout="@layout/test">
                <action
                    android:id="@+id/action_SecondFragment_to_FirstFragment"
                    app:destination="@id/FirstFragment" />
            </fragment>
        </navigation>
      """
        .trimIndent(),
    )

  private fun getGoldenImagePath(testName: String) =
    Paths.get("${androidProjectRule.fixture.testDataPath}/zoomGoldenImages/$testName.png")

  @Test
  fun testNavDesignSurfaceZoomIn() = runTest {
    populateSchema()
    val facet = androidProjectRule.module.androidFacet!!
    val layout = addLayout()
    val navGraph = addNavGraph()

    waitForResourceRepositoryUpdates(androidProjectRule.fixture.module)
    val configuration =
      RenderTestUtil.getConfiguration(androidProjectRule.fixture.module, layout.virtualFile)
    val surface =
      NavDesignSurface(androidProjectRule.project).also {
        Disposer.register(androidProjectRule.testRootDisposable, it)
      }

    surface.activate()

    val model =
      NlModel.Builder(
          androidProjectRule.testRootDisposable,
          AndroidBuildTargetReference.gradleOnly(facet),
          navGraph.virtualFile,
          configuration,
        )
        .withComponentRegistrar(NavComponentRegistrar)
        .build()

    surface.addModelWithoutRender(model).join()
    surface.setCurrentNavigation(surface.model?.treeReader?.find("FirstFragment")!!).join()

    val fakeUi =
      withContext(Dispatchers.EDT) {
        val outerPanel =
          JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.RED)
            add(surface, BorderLayout.CENTER)
            setBounds(0, 0, 1000, 1000)
          }
        FakeUi(outerPanel, 1.0, true)
      }

    // Ensure the initial zoom of NavDesignSurface is zoom-to-fit.
    delayUntilImageSimilar(fakeUi, "zoomFit")

    val zoomActionsToolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place.contains(zoomActionPlace) }!!
    val zoomInAction = zoomActionsToolbar.actions.filterIsInstance<ZoomInAction>().single()

    val event =
      TestActionEvent.createTestEvent(
        DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface)
      )

    // Verify zoom in
    run {
      repeat(3) {
        zoomInAction.actionPerformed(event)
        withContext(Dispatchers.EDT) { fakeUi.layoutAndDispatchEvents() }
      }
      Assert.assertEquals(3.0, surface.zoomController.scale, 0.01)
      delayUntilImageSimilar(fakeUi, "zoomIn")
    }
  }

  private suspend fun delayUntilImageSimilar(fakeUi: FakeUi, goldenImageName: String) {
    delayUntilCondition(100, 5.seconds) {
      val output = asyncDisplayRule.renderInFakeUi(fakeUi)
      try {
        ImageDiffUtil.assertImageSimilar(getGoldenImagePath(goldenImageName), output, 0.1, 1)
        return@delayUntilCondition true
      } catch (e: AssertionError) {
        return@delayUntilCondition false
      }
    }
  }

  @Test
  fun testNavDesignSurfaceZoomOut() = runTest {
    populateSchema()
    val facet = androidProjectRule.module.androidFacet!!
    val layout = addLayout()
    val navGraph = addNavGraph()

    waitForResourceRepositoryUpdates(androidProjectRule.fixture.module)
    val configuration =
      RenderTestUtil.getConfiguration(androidProjectRule.fixture.module, layout.virtualFile)
    val surface =
      NavDesignSurface(androidProjectRule.project).also {
        Disposer.register(androidProjectRule.testRootDisposable, it)
      }

    surface.activate()

    val model =
      NlModel.Builder(
          androidProjectRule.testRootDisposable,
          AndroidBuildTargetReference.gradleOnly(facet),
          navGraph.virtualFile,
          configuration,
        )
        .withComponentRegistrar(NavComponentRegistrar)
        .build()

    surface.addModelWithoutRender(model).join()
    surface.setCurrentNavigation(surface.model?.treeReader?.find("FirstFragment")!!).join()

    val fakeUi =
      withContext(Dispatchers.EDT) {
        val outerPanel =
          JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.RED)
            add(surface, BorderLayout.CENTER)
            setBounds(0, 0, 1000, 1000)
          }
        FakeUi(outerPanel, 1.0, true)
      }

    // Ensure the initial zoom of NavDesignSurface is zoom-to-fit.
    delayUntilImageSimilar(fakeUi, "zoomFit")

    val zoomActionsToolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place.contains(zoomActionPlace) }!!

    val zoomOutAction = zoomActionsToolbar.actions.filterIsInstance<ZoomOutAction>().single()

    val event =
      TestActionEvent.createTestEvent(
        DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface)
      )

    // Verify zoom out
    run {
      repeat(3) {
        zoomOutAction.actionPerformed(event)
        withContext(Dispatchers.EDT) { fakeUi.layoutAndDispatchEvents() }
      }
      Assert.assertEquals(1.1, surface.zoomController.scale, 0.01)
      delayUntilImageSimilar(fakeUi, "zoomOut")
    }
  }

  @Test
  fun testNavDesignSurfaceZoomToFit() = runTest {
    populateSchema()
    val facet = androidProjectRule.module.androidFacet!!
    val layout = addLayout()
    val navGraph = addNavGraph()

    waitForResourceRepositoryUpdates(androidProjectRule.fixture.module)
    val configuration =
      RenderTestUtil.getConfiguration(androidProjectRule.fixture.module, layout.virtualFile)
    val surface =
      NavDesignSurface(androidProjectRule.project).also {
        Disposer.register(androidProjectRule.testRootDisposable, it)
      }

    surface.activate()

    val model =
      NlModel.Builder(
          androidProjectRule.testRootDisposable,
          AndroidBuildTargetReference.gradleOnly(facet),
          navGraph.virtualFile,
          configuration,
        )
        .withComponentRegistrar(NavComponentRegistrar)
        .build()

    surface.addModelWithoutRender(model).join()
    surface.setCurrentNavigation(surface.model?.treeReader?.find("FirstFragment")!!).join()

    val fakeUi =
      withContext(Dispatchers.EDT) {
        val outerPanel =
          JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBColor.RED)
            add(surface, BorderLayout.CENTER)
            setBounds(0, 0, 1000, 1000)
          }
        FakeUi(outerPanel, 1.0, true)
      }

    // Ensure the initial zoom of NavDesignSurface is zoom-to-fit.
    delayUntilImageSimilar(fakeUi, "zoomFit")

    val zoomActionsToolbar =
      fakeUi.findComponent<ActionToolbarImpl> { it.place.contains(zoomActionPlace) }!!

    val zoomToFitAction = zoomActionsToolbar.actions.filterIsInstance<ZoomToFitAction>().single()
    val zoomInAction = zoomActionsToolbar.actions.filterIsInstance<ZoomInAction>().single()

    val event =
      TestActionEvent.createTestEvent(
        DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, surface)
      )

    // Perform zoom-in action twice and make sure NavDesignSurface is not zoom-to-fit anymore.
    zoomInAction.actionPerformed(event)
    zoomInAction.actionPerformed(event)
    // If we can apply zoom-to-fit we confirm NavDesignSurface is currently not zoom-to-fit.
    assertTrue(surface.zoomController.canZoomToFit())

    // Perform zoom to fit action
    zoomToFitAction.actionPerformed(event)
    val zoomToFitScale = surface.zoomController.scale

    // Verify zoom to fit
    run {
      zoomToFitAction.actionPerformed(event)
      withContext(Dispatchers.EDT) { fakeUi.layoutAndDispatchEvents() }

      // We can't zoom-to-fit anymore because zoom-to-fit is already applied.
      assertFalse(surface.zoomController.canZoomToFit())
      Assert.assertEquals(zoomToFitScale, surface.zoomController.scale, 0.01)
      delayUntilImageSimilar(fakeUi, "zoomFit")
    }
  }
}
