/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.projectmodel.DynamicResourceValue
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.testutils.waitForCondition
import com.android.tools.idea.editors.strings.StringResourceData.Companion.create
import com.android.tools.idea.editors.strings.StringResourceData.Companion.summarizeLocales
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.res.DynamicValueResourceRepository
import com.android.tools.idea.res.ModuleResourceRepository
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
@RunsInEdt
class StringResourceDataTest {

  @get:Rule
  val androidProjectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    }
  }
  private val project by lazy { androidProjectRule.project }
  private val module by lazy { fixture.module }
  private val facet by lazy { module.androidFacet!! }

  private lateinit var resourceDirectory: VirtualFile
  private lateinit var data: StringResourceData

  @Before
  fun setUp() {
    facet.properties.ALLOW_USER_CONFIGURATION = false
    resourceDirectory = fixture.copyDirectoryToProject("stringsEditor/base/res", "res")

    val field = DynamicResourceValue(ResourceType.STRING, "L'Étranger")

    val dynamicRepository =
      DynamicValueResourceRepository.createForTest(facet, ResourceNamespace.RES_AUTO, Collections.singletonMap("dynamic_key1", field))

    val moduleRepository = ModuleResourceRepository.createForTest(facet, listOf(resourceDirectory), ResourceNamespace.RES_AUTO,
                                                                  dynamicRepository)

    data = create(module.project, Utils.createStringRepository(moduleRepository, module.project))
  }

  @Test
  fun summarizeLocales() {
    fun localeListOf(vararg locales: String) = locales.map(Locale::create)

    assertThat(summarizeLocales(emptySet())).isEqualTo("")

    assertThat(summarizeLocales(localeListOf("fr", "en"))).isEqualTo("English (en) and French (fr)")

    assertThat(summarizeLocales(localeListOf("en", "fr", "hi"))).isEqualTo("English (en), French (fr) and Hindi (hi)")

    assertThat(summarizeLocales(localeListOf("en", "fr", "hi", "no"))).isEqualTo("English (en), French (fr), Hindi (hi) and 1 more")

    assertThat(summarizeLocales(localeListOf("en", "fr", "hi", "no", "ta", "es", "ro"))).isEqualTo(
      "English (en), French (fr), Hindi (hi) and 4 more")
  }

  @Test
  fun parser() {
    assertThat(data.localeSet.map(Locale::toLocaleId)).containsExactly("en", "en-IN", "en-GB", "fr", "hi")
    assertThat(data.localeList.map(Locale::toLocaleId)).containsExactly("en", "en-IN", "en-GB", "fr", "hi").inOrder()

    assertThat(data.getStringResource(newStringResourceKey("key1")).defaultValueAsResourceItem).isNotNull()

    assertThat(data.getStringResource(newStringResourceKey("key5")).isTranslatable).isFalse()

    assertThat(data.getStringResource(newStringResourceKey("key1")).getTranslationAsResourceItem(Locale.create("hi"))).isNull()
    assertThat(data.getStringResource(newStringResourceKey("key2")).getTranslationAsString(Locale.create("hi"))).isEqualTo("Key 2 hi")
  }

  @Test
  fun resourceToStringPsi() {
    val locale = Locale.create("fr")

    assertThat(data.getStringResource(newStringResourceKey("key8")).getTranslationAsString(locale)).isEqualTo("L'Étranger")
    assertThat(data.getStringResource(newStringResourceKey("key9")).getTranslationAsString(locale)).isEqualTo("<![CDATA[L'Étranger]]>")
    assertThat(data.getStringResource(newStringResourceKey("key10")).getTranslationAsString(locale)).isEqualTo(
      "<xliff:g>L'Étranger</xliff:g>")
  }

  @Test
  fun resourceToStringDynamic() {
    assertThat(data.getStringResource(StringResourceKey("dynamic_key1")).defaultValueAsString).isEqualTo("L'Étranger")
  }

  @Test
  fun validation() {
    assertThat(data.validateKey(newStringResourceKey("key1"))
    ).isEqualTo("Key 'key1' has translations missing for locales French (fr) and Hindi (hi)")

    assertThat(data.validateKey(newStringResourceKey("key2"))).isNull()
    assertThat(data.validateKey(newStringResourceKey("key3"))).isNull()
    assertThat(data.validateKey(newStringResourceKey("key4"))).isEqualTo("Key 'key4' missing default value")
    assertThat(data.validateKey(newStringResourceKey("key5"))).isNull()

    assertThat(data.validateKey(newStringResourceKey("key6"))
    ).isEqualTo("Key 'key6' is marked as non translatable, but is translated in locale French (fr)")

    assertThat(data.getStringResource(newStringResourceKey("key1")).validateTranslation(Locale.create("hi"))
    ).isEqualTo("Key \"key1\" is missing its Hindi (hi) translation")

    assertThat(data.getStringResource(newStringResourceKey("key2")).validateTranslation(Locale.create("hi"))).isNull()

    assertThat(data.getStringResource(newStringResourceKey("key6")).validateTranslation(Locale.create("fr"))
    ).isEqualTo("Key \"key6\" is untranslatable and should not be translated to French (fr)")

    assertThat(data.getStringResource(newStringResourceKey("key1")).validateDefaultValue()).isNull()
    assertThat(data.getStringResource(newStringResourceKey("key4")).validateDefaultValue()).isEqualTo(
      "Key \"key4\" is missing its default value")
  }

  @Test
  fun getMissingTranslations() {
    assertThat(data.getMissingTranslations(newStringResourceKey("key7")))
      .containsExactly(
        Locale.create("en"),
        Locale.create("en-rGB"),
        Locale.create("en-rIN"),
        Locale.create("fr"),
        Locale.create("hi"))
  }

  @Test
  fun isTranslationMissing() {
    assertThat(data.getStringResource(newStringResourceKey("key7")).isTranslationMissing(Locale.create("fr"))).isTrue()
  }

  @Test
  fun regionQualifier() {
    val en_rGB = Locale.create("en-rGB")
    assertThat(data.getStringResource(newStringResourceKey("key4")).isTranslationMissing(en_rGB)).isTrue()
    assertThat(data.getStringResource(newStringResourceKey("key3")).isTranslationMissing(en_rGB)).isFalse()
    assertThat(data.getStringResource(newStringResourceKey("key8")).isTranslationMissing(en_rGB)).isFalse()
  }

  @Test
  fun editingDoNotTranslate() {
    val stringsFile = requireNotNull(resourceDirectory.findFileByRelativePath("values/strings.xml"))
    val resource1 = data.getStringResource(newStringResourceKey("key1"))
    val resource5 = data.getStringResource(newStringResourceKey("key5"))

    assertThat(resource1.isTranslatable).isTrue()
    assertThat(resource1.getTagText(null)).isEqualTo("<string name=\"key1\">Key 1 default</string>")
    var tag = getNthXmlTag(stringsFile, 0)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key1")
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isNull()
    resource1.changeTranslatable(false)

    assertThat(resource1.isTranslatable).isFalse()
    assertThat(resource1.getTagText(null)).isEqualTo("<string name=\"key1\" translatable=\"false\">Key 1 default</string>")
    tag = getNthXmlTag(stringsFile, 0)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isEqualTo(SdkConstants.VALUE_FALSE)

    assertThat(resource5.isTranslatable).isFalse()
    assertThat(resource5.getTagText(null)).isEqualTo("<string name=\"key5\" translatable=\"false\">Key 5 default</string>")
    tag = getNthXmlTag(stringsFile, 3)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key5")
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isEqualTo(SdkConstants.VALUE_FALSE)
    resource5.changeTranslatable(true)

    assertThat(resource5.isTranslatable).isTrue()
    assertThat(resource5.getTagText(null)).isEqualTo("<string name=\"key5\">Key 5 default</string>")
    tag = getNthXmlTag(stringsFile, 3)
    assertThat(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE)).isNull()
  }

  @Test
  fun editingCdata() {
    var expected = """<![CDATA[
        <b>Google I/O 2014</b><br>
        Version %s<br><br>
        <a href="http://www.google.com/policies/privacy/">Privacy Policy</a>
  ]]>"""

    val resource = data.getStringResource(newStringResourceKey("key1"))
    val locale = Locale.create("en-rIN")

    assertThat(resource.getTranslationAsString(locale)).isEqualTo(expected)

    expected = """<![CDATA[
        <b>Google I/O 2014</b><br>
        Version %1${"$"}s<br><br>
        <a href="http://www.google.com/policies/privacy/">Privacy Policy</a>
  ]]>"""

    assertThat(putTranslation(resource, locale, expected)).isTrue()
    assertThat(resource.getTranslationAsString(locale)).isEqualTo(expected)

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml"))

    val tag = getNthXmlTag(file, 0)

    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key1")
    assertThat(tag.value.text).isEqualTo(expected)
  }

  @Test
  fun editingXliff() {
    val resource = data.getStringResource(newStringResourceKey("key3"))
    val locale = Locale.create("en-rIN")

    assertThat(resource.getTranslationAsString(locale)).isEqualTo("start <xliff:g>middle1</xliff:g>%s<xliff:g>middle3</xliff:g> end")

    val expected = "start <xliff:g>middle1</xliff:g>%1\$s<xliff:g>middle3</xliff:g> end"

    assertThat(putTranslation(resource, locale, expected)).isTrue()
    assertThat(resource.getTranslationAsString(locale)).isEqualTo(expected)

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en-rIN/strings.xml"))

    val tag = getNthXmlTag(file, 2)

    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key3")
    assertThat(tag.value.text).isEqualTo(expected)
  }

  @Test
  fun addingNewTranslation() {
    val resource = data.getStringResource(newStringResourceKey("key4"))
    val locale = Locale.create("en")

    assertThat(resource.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(resource, locale, "Hello")).isTrue()
    assertThat(resource.getTranslationAsString(locale)).isEqualTo("Hello")

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-en/strings.xml"))

    // There was no key4 in the default locale en, a new key would be appended to the end of file.
    val tag = getNthXmlTag(file, 4)

    assertThat(tag.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key4")
    assertThat(tag.value.text).isEqualTo("Hello")
  }

  @Test
  fun insertingTranslation() {
    // Adding key 2 first then adding key 1.
    // To follow the order of default locale file, the tag of key 1 should be before key 2, even key 2 is added first.
    val locale = Locale.create("zh")

    val resource2 = data.getStringResource(newStringResourceKey("key2"))
    assertThat(resource2.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(resource2, locale, "二")).isTrue()
    assertThat(resource2.getTranslationAsString(locale)).isEqualTo("二")

    val resource4 = data.getStringResource(newStringResourceKey("key1"))
    assertThat(resource4.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(resource4, locale, "一")).isTrue()
    assertThat(resource4.getTranslationAsString(locale)).isEqualTo("一")

    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values-zh/strings.xml"))

    val tag1 = getNthXmlTag(file, 0)
    assertThat(tag1.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key1")
    assertThat(tag1.value.text).isEqualTo("一")

    val tag2 = getNthXmlTag(file, 1)
    assertThat(tag2.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key2")
    assertThat(tag2.value.text).isEqualTo("二")
  }

  @Test
  fun insertTranslation2() {
    val file = requireNotNull(resourceDirectory.findFileByRelativePath("values/strings.xml"))
    val tag2 = getNthXmlTag(file, 1)
    assertThat(tag2.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key2")

    val resources = tag2.parentTag!!
    val newTag = resources.createChildTag("string", "", "New Text", false)
    newTag.setAttribute(SdkConstants.ATTR_NAME, "newKey")
    WriteCommandAction.runWriteCommandAction(project) {
      resources.addAfter(newTag, tag2)
    }
    val moduleRepository = ModuleResourceRepository.createForTest(facet, listOf(resourceDirectory), ResourceNamespace.RES_AUTO)
    data = create(module.project, Utils.createStringRepository(moduleRepository, module.project))

    val locale = Locale.create("fr")
    val newResource = data.getStringResource(newStringResourceKey("newKey"))
    assertThat(newResource.getTranslationAsResourceItem(locale)).isNull()
    assertThat(putTranslation(newResource, locale, "nouveau texte")).isTrue()

    val file2 = requireNotNull(resourceDirectory.findFileByRelativePath("values-fr/strings.xml"))

    val french1 = getNthXmlTag(file2, 0)
    assertThat(french1.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key2")
    assertThat(french1.value.text).isEqualTo("Key 2 fr")

    val french2 = getNthXmlTag(file2, 1)
    assertThat(french2.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("newKey")
    assertThat(french2.value.text).isEqualTo("nouveau texte")

    val french3 = getNthXmlTag(file2, 2)
    assertThat(french3.getAttributeValue(SdkConstants.ATTR_NAME)).isEqualTo("key3")
    assertThat(french3.value.text).isEqualTo("Key 3 fr")
  }

  @Test
  fun keyChangeKeepsIterationOrder() {
    assertThat(data.resources.map { it.key.name }).containsExactly(
      "key1", "key2", "key3", "key5", "key6", "key7", "key8", "key4", "key9", "key10", "dynamic_key1"
    ).inOrder()
    val key2 = data.resources.map { it.key }.single { it.name == "key2" }
    val value2 = data.getStringResource(key2)
    assertThat(value2.defaultValueAsString).isEqualTo("Key 2 default")
    val expectedTranslations =
      mapOf("en" to "Key 2 en", "en-GB" to "Key 2 en-rGB", "en-IN" to "Key 2 en-rIN", "fr" to "Key 2 fr", "hi" to "Key 2 hi")
    assertThat(value2.translatedLocales.associate { Pair(it.toLocaleId(), value2.getTranslationAsString(it)) }).isEqualTo(expectedTranslations)

    // Change the name of a key:
    val future = data.setKeyName(key2, "new_key2")
    waitForCondition(2, TimeUnit.SECONDS) { future.isDone }

    assertThat(data.keys.map { it.name }).containsExactly(
      "key1", "new_key2", "key3", "key5", "key6", "key7", "key8", "key4", "key9", "key10", "dynamic_key1"
    ).inOrder()
    val newKey2 = data.resources.map { it.key }.single { it.name == "new_key2" }
    val newValue2 = data.getStringResource(newKey2)
    assertThat(newValue2.defaultValueAsString).isEqualTo("Key 2 default")
    assertThat(newValue2.translatedLocales.associate { Pair(it.toLocaleId(), value2.getTranslationAsString(it)) }).isEqualTo(expectedTranslations)
  }

  private fun putTranslation(resource: StringResource, locale: Locale, value: String): Boolean {
    val futureResult = resource.putTranslation(locale, value)
    waitForCondition(2, TimeUnit.SECONDS) { futureResult.isDone }
    return futureResult.get()
  }

  private fun newStringResourceKey(name: String): StringResourceKey {
    return StringResourceKey(name, resourceDirectory)
  }

  private fun getNthXmlTag(file: VirtualFile, index: Int): XmlTag {
    val psiFile = requireNotNull(PsiManager.getInstance(facet.module.project).findFile(file) as XmlFile)
    val rootTag = requireNotNull(psiFile.rootTag)
    return rootTag.findSubTags("string")[index]
  }
}
