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

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElement
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifierOwner
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.declarative.dsl.schema.DataMemberFunction
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.DataTypeRef
import org.gradle.internal.declarativedsl.analysis.DefaultFqName

class DeclarativeAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (!DeclarativeIdeSupport.isEnabled()) return
    if (element !is DeclarativeElement) return

    fun getSchema(): DeclarativeSchema? {
      val schema = element.project.getSchema() ?: return null
      if (schema.failureHappened) return null // partial schema is useless here
      return schema
    }

    // check element naming
    if (element is DeclarativeIdentifier) {
      val schema = getSchema() ?: return
      val path = getPath(element)
      val result = searchForType(path, schema, element.containingFile.name)
      if (result.isEmpty()) {
        showUnknownName(holder)
      }
    }
    // check element type
    if (element.isMainLevelElement()) {
      val identifier = element.findIdentifier() ?: return
      val path = getPath(identifier)
      val schema = getSchema() ?: return
      val result = searchForType(path, schema, element.containingFile.name)
      if (result.isEmpty()) return
      val types = result.mapNotNull { it.toElementType() }
      val foundType = types.any { it == element.getElementType() }
      if (!foundType) {
        showWrongType(types.map { it.str }, holder)
      }
    }
  }

  private fun Project.getSchema(): DeclarativeSchema? {
    val service = DeclarativeService.getInstance(this)
    return service.getSchema()
  }

  // psi element that has identifier we can build path to the root element
  private fun PsiElement.isMainLevelElement() =
    this is DeclarativeIdentifierOwner

  private fun PsiElement.findIdentifier() =
    when (this) {
      is DeclarativeIdentifierOwner -> identifier
      else -> null
    }

  sealed class SearchResult {
    fun toElementType(): ElementType? {
      return when (this) {
        is FoundFunction -> ElementType.FACTORY
        is FoundClass -> ElementType.BLOCK
        is FoundProperty -> getType(type)
        else -> null
      }
    }
  }

  data class FoundClass(val type: DataClass) : SearchResult()
  data class FoundFunction(val type: DataMemberFunction) : SearchResult()
  data class FoundProperty(val type: DataType) : SearchResult()
  data object NDOC : SearchResult() // dynamic naming - we cannot verify it for now

  private fun searchForType(path: List<String>, schema: DeclarativeSchema, fileName: String): List<SearchResult> {
    if (path.isEmpty()) return listOf()

    var currentReceiver: List<Receiver> = getTopLevelReceiversByName(path[0], schema, fileName)
    var hasNdocFlag = hasNDOC(currentReceiver, schema)
    val last = path.size - 1
    for (index in 1..last) {
      if (currentReceiver.isEmpty()) {
        return if (hasNdocFlag) listOf(NDOC) else listOf()
      }

      val name = path[index]

      currentReceiver = currentReceiver.flatMap { element ->
        when (element) {
          // TODO need to verify type of function parameter
          is Function -> when (val receiver = element.type.receiver) {
            // assumption is that receiver is the same as for parent function for example `implementation(project(...))`
            // TODO need to make it universal (b/355179149)
            is DataTypeRef.Name -> schema.getDataClassesByFqName()[receiver.fqName]?.let {
              getAllMembersByName(it, name)
            } ?: listOf()

            is DataTypeRef.Type -> listOf() // no children for simple type
          }

          is ObjectRef -> getAllMembersByName(element, name, schema)
          is SimpleType -> listOf() // no children for simple type
        }
      }
      if (currentReceiver.isNotEmpty()) hasNdocFlag = hasNDOC(currentReceiver, schema)
    }

    return currentReceiver.flatMap { receiver ->
      when (receiver) {
        is Function -> listOf(FoundFunction(receiver.type))
        is ObjectRef -> schema.getDataClassesByFqName()[receiver.fqName]?.let { listOf(FoundClass(it)) } ?: listOf()
        is SimpleType -> listOf(FoundProperty(receiver.type))
      }
    }
  }

  private fun getAllMembersByName(o: ObjectRef, memberName: String, schema: DeclarativeSchema): List<Receiver> {
    val dataClass = schema.getDataClassesByFqName()[o.fqName] ?: return listOf()
    return getAllMembersByName(dataClass, memberName)
  }

  private fun hasNDOC(receivers: List<Receiver>, schema: DeclarativeSchema): Boolean =
    receivers.any { element ->
      when (element) {
        is ObjectRef -> {
          schema.getDataClassesByFqName()[element.fqName]?.let { isNDOC(it) } ?: false
        }

        else -> false
      }
    }

  private fun isNDOC(parentDataClass: DataClass?) =
    parentDataClass?.supertypes?.contains(DefaultFqName("org.gradle.api", "NamedDomainObjectContainer")) == true

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

  private fun getPath(element: DeclarativeIdentifier): List<String> {
    val result = mutableListOf<String>()
    var current: PsiElement = element
    while (current.parent != null && current is DeclarativeElement) {
      if (current is DeclarativeIdentifierOwner) {
        current.identifier?.name?.let { result.add(it) }
      }
      current = current.parent
    }
    return result.reversed()
  }
}

