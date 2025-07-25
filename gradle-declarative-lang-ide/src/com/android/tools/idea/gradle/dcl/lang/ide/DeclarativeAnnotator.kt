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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAbstractFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeArgumentsList
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignableProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElement
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeEntry
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactoryReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifierOwner
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePropertyReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeReceiverPrefixed
import com.android.tools.idea.gradle.dcl.lang.sync.AugmentationKind
import com.android.tools.idea.gradle.dcl.lang.sync.BlockFunction
import com.android.tools.idea.gradle.dcl.lang.sync.ClassModel
import com.android.tools.idea.gradle.dcl.lang.sync.ClassType
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRef
import com.android.tools.idea.gradle.dcl.lang.sync.DataClassRefWithTypes
import com.android.tools.idea.gradle.dcl.lang.sync.DataProperty
import com.android.tools.idea.gradle.dcl.lang.sync.Entry
import com.android.tools.idea.gradle.dcl.lang.sync.EnumModel
import com.android.tools.idea.gradle.dcl.lang.sync.Named
import com.android.tools.idea.gradle.dcl.lang.sync.ParameterizedClassModel
import com.android.tools.idea.gradle.dcl.lang.sync.PlainFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SchemaFunction
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleDataType
import com.android.tools.idea.gradle.dcl.lang.sync.SimpleTypeRef
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport

class DeclarativeAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!DeclarativeIdeSupport.isEnabled()) return
    if (element !is DeclarativeElement) return

    fun getSchema(): BuildDeclarativeSchemas? {
      val schema = DeclarativeService.getInstance(element.project).getDeclarativeSchema() ?: return null
      return schema
    }

    // check element naming
    if (element is DeclarativeIdentifier && element.parent !is DeclarativeProperty) {
      val schema = getSchema() ?: return
      val path = getPath(element)
      val result = mutableListOf<SearchResult>()
      result.addAll(searchForType(path, schema, element.containingFile.name))
      // for anything with receiver - need to check if it comes from root object
      val parent = element.parent
      if (parent is DeclarativeReceiverPrefixed<*>) {
        // search for plain function like uri, file etc
        element.name?.let {
          result.addAll(searchForType(getReceiversPath(parent), schema, element.containingFile.name))
          // also check direct parent if it has function like dependenciesDcl.project
          findParentBlock(element)?.let { parentBlock ->
            result.addAll(searchForType(getPath(parentBlock.identifier) + it, schema, element.containingFile.name))
          }
        }
      }
      if (result.isEmpty()) {
        showUnknownName(holder)
      }
    }
    // check element type
    // checking the whole element - assignment, function, block
    // does not check embedded elements
    if (element.isMainLevelElement()) {
      val identifier = element.findIdentifier() ?: return
      val path = getPath(identifier)
      val schema = getSchema() ?: return
      val result = searchForType(path, schema, element.containingFile.name)
      if (result.isEmpty()) return
      val types = result.map { it.toElementType() }.distinctBy { it.elementType }
      // check types
      val foundType = types.any { it.elementType == element.getElementType()?.elementType }
      if (!foundType) {
        showWrongType(types.map { it.elementType.str }, holder)
      }
      // check augmentation
      val missedAugmented = element.getElementType()?.augmented?.find {
        augmentedType -> types.none { it.augmented.contains(augmentedType) }
      }
      if (missedAugmented !=null ) {
        showWrongAugmentation(missedAugmented, holder)
      }
    }
  }

  // psi element that has identifier we can build path to the root element
  private fun PsiElement.isMainLevelElement() =
    this is DeclarativeEntry

  private fun PsiElement.findIdentifier() =
    when (this) {
      is DeclarativeIdentifierOwner -> identifier
      else -> null
    }

  private fun findParentBlock(psi: PsiElement): DeclarativeBlock? {
    var current = psi

    while (current.parent != null && current.parent !is DeclarativeBlock) {
      current = current.parent
    }
    return current.parent as? DeclarativeBlock
  }

  class ElementTypeWithAugmentation(val elementType: ElementType, val augmented: List<AugmentationKind>)

  sealed class SearchResult {
    fun toElementType(): ElementTypeWithAugmentation {
      return when (this) {
        is FoundFunction -> when (type.semantic) {
          is PlainFunction -> ElementTypeWithAugmentation(ElementType.FACTORY, listOf())
          is BlockFunction -> if (type.parameters.isNotEmpty()) ElementTypeWithAugmentation(ElementType.FACTORY_BLOCK, listOf())
          else ElementTypeWithAugmentation(ElementType.BLOCK, listOf())
        }

        is FoundBlock ->
          ElementTypeWithAugmentation(ElementType.BLOCK, listOf())

        is FoundObjectProperty -> ElementTypeWithAugmentation(ElementType.PROPERTY, listOf())
        is FoundSimpleProperty -> ElementTypeWithAugmentation(getSimpleType(type), listOf())
        is FoundEnum -> ElementTypeWithAugmentation(ElementType.ENUM, listOf())
        is FoundParametrizedType -> ElementTypeWithAugmentation(ElementType.PROPERTY, augmented)
      }
    }
  }

  data class FoundBlock(val type: ClassModel) : SearchResult()
  data class FoundEnum(val type: EnumModel) : SearchResult()
  // FoundParametrizedType its non gradle class like List or Array that does not have properties or methods
  data class FoundParametrizedType(val type: ParameterizedClassModel, val augmented: List<AugmentationKind>) : SearchResult()
  data class FoundFunction(val type: SchemaFunction) : SearchResult()
  data class FoundSimpleProperty(val type: SimpleDataType) : SearchResult()
  data class FoundObjectProperty(val type: ClassModel) : SearchResult()

  private fun searchForType(path: List<String>, schema: BuildDeclarativeSchemas, fileName: String): List<SearchResult> {
    if (path.isEmpty()) return listOf()

    var receivers: List<EntryWithContext> = schema.getTopLevelEntriesByName(path[0], fileName)
    val last = path.size - 1
    for (index in 1..last) {
      if (receivers.isEmpty()) {
        return listOf()
      }

      receivers = receivers.flatMap { it.getNextLevel(path[index]) }
    }

    return receivers.distinct().flatMap { receiver ->
      when (val entry = receiver.entry) {
        is SchemaFunction -> listOf(FoundFunction(entry))
        is DataProperty -> {
          val augmentedType = entry.getAugmentedTypes(schema, fileName)
          when (val type = entry.valueType) {
            is DataClassRef -> receiver.resolveRef(type.fqName)?.let { listOf(it.wrap(augmentedType)) } ?: listOf()
            is SimpleTypeRef -> listOf(FoundSimpleProperty(type.dataType))
            is DataClassRefWithTypes -> receiver.resolveRef(type.fqName)?.let { listOf(it.wrap(augmentedType)) } ?: listOf()
            else -> listOf()
          }
        }
      }
    }
  }

  private fun Entry.getAugmentedTypes(schema: BuildDeclarativeSchemas, fileName: String):List<AugmentationKind>{
    val fullName = if(this is DataProperty && this.valueType is Named) (valueType as Named).fqName else null
    fullName ?: return listOf()
    return schema.getAugmentedTypes(fileName)[fullName] ?: listOf()
  }

  private fun ClassType.wrap(augmented: List<AugmentationKind>) = when (this) {
    is ClassModel -> FoundObjectProperty(this)
    is EnumModel -> FoundEnum(this)
    is ParameterizedClassModel -> FoundParametrizedType(this, augmented)
  }

  private fun showUnknownName(holder: AnnotationHolder) {
    holder.newAnnotation(HighlightSeverity.ERROR, "Unknown identifier").create()
  }

  private fun showWrongType(types: List<String>, holder: AnnotationHolder) {
    if (types.size == 1)
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Element type should be of type: ${types.first()}").create()
    else
      holder.newAnnotation(HighlightSeverity.ERROR,
                           "Element type should be of one of types: ${types.joinToString(", ")}").create()
  }

  private fun showWrongAugmentation(augmented: AugmentationKind, holder: AnnotationHolder) {
    val operation = when (augmented) {
      AugmentationKind.PLUS -> "`+=`"
    }
    holder.newAnnotation(HighlightSeverity.ERROR,
                         "Cannot do $operation for this property type").create()
  }

  private fun getPath(element: DeclarativeIdentifier): List<String> {
    val result = mutableListOf<String>()
    var current: PsiElement = element
    while (current.parent != null && current is DeclarativeElement) {
      when (current) {
        is DeclarativeArgumentsList -> current = skip<DeclarativeAbstractFactory>(current)
        is DeclarativeAssignableProperty -> current = parseReceiver<DeclarativeAssignableProperty>(current, result).parent
        is DeclarativePropertyReceiver -> current = parseReceiver<DeclarativePropertyReceiver>(current, result).parent

        is DeclarativeFactoryReceiver ->
          current = parseReceiver<DeclarativeFactoryReceiver>(current, result)

        else -> {
          (current as? DeclarativeIdentifierOwner)?.identifier?.name?.let { result.add(it) }
          current = current.parent
        }
      }
    }
    return result.reversed()
  }

  private fun getReceiversPath(element: DeclarativeReceiverPrefixed<*>): List<String> {
    val result = mutableListOf<String>()
    var current: DeclarativeReceiverPrefixed<*>? = element
    do {
      current?.identifier?.name?.let { result.add(it) }
      current = current?.getReceiver()
    }
    while (current != null)
    return result.reversed()
  }

  private inline fun <reified T : DeclarativeElement> skip(current: PsiElement): PsiElement {
    var element: PsiElement = current
    while (element.parent != null && element.parent is T) {
      element = element.parent
    }
    return element.parent
  }

  private inline fun <reified T : DeclarativeReceiverPrefixed<T>> parseReceiver(property: DeclarativeReceiverPrefixed<T>,
                                                                                list: MutableList<String>): PsiElement {
    var currentProperty = property
    currentProperty.identifier.name?.let { list.add(it) }

    while (currentProperty.getReceiver() != null) {
      currentProperty = currentProperty.getReceiver()!!
      currentProperty.identifier.name?.let { list.add(it) }
    }
    return skip<T>(currentProperty)
  }
}