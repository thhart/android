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
package com.android.tools.idea.gradle.dcl.lang

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAbstractFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignableProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeAssignment
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlock
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeBlockGroup
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeEntry
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactoryPropertyReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFactoryReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeValueFieldOwner
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeFile
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifier
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeIdentifierOwner
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePair
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeProperty
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativePropertyReceiver
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeReceiverBasedFactory
import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeSimpleLiteral

import com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeValue
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.parents
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.UastQualifiedExpressionAccessType
import org.jetbrains.uast.internal.acceptList
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.classSetOf
import org.jetbrains.uast.visitor.UastVisitor
import org.jetbrains.uast.withMargin

class DeclarativeUastLanguagePlugin : UastLanguagePlugin {

  override val language: Language = DeclarativeLanguage.INSTANCE
  override val priority: Int = 42 // arbitrary

  override fun getPossiblePsiSourceTypes(vararg uastTypes: Class<out UElement>): ClassSet<PsiElement> =
    classSetOf(com.android.tools.idea.gradle.dcl.lang.psi.DeclarativeElement::class.java)

  override fun convertElement(element: PsiElement, parent: UElement?, requiredType: Class<out UElement>?): UElement? =
    convertElementWithParentProvider(element, { parent }, requiredType)

  override fun convertElementWithParent(element: PsiElement, requiredType: Class<out UElement>?): UElement? =
    convertElementWithParentProvider(element, { makeUParent(element) }, requiredType)

  private fun makeUParent(element: PsiElement) =
    element.parents(false).mapNotNull { convertElementWithParent(it, null) }.firstOrNull()

  private fun convertElementWithParentProvider(element: PsiElement,
                                               parentProvider: () -> UElement?,
                                               requiredType: Class<out UElement>?): UElement? {
    return when (element) {
      is DeclarativeFile -> DeclarativeUFile(element, this)
      is DeclarativeAssignment -> DeclarativeUAssignment(element, parentProvider)
      is DeclarativeBlock -> DeclarativeUBlock(element, parentProvider)
      is DeclarativeReceiverBasedFactory<*> -> DeclarativeUFactory(element, parentProvider)
      else -> null
    }
      ?.takeIf { requiredType?.isAssignableFrom(it.javaClass) ?: true }
  }

  override fun getConstructorCallExpression(element: PsiElement, fqName: String): UastLanguagePlugin.ResolvedConstructor? = null

  override fun getMethodCallExpression(
    element: PsiElement,
    containingClassFqName: String?,
    methodName: String
  ): UastLanguagePlugin.ResolvedMethod? = null

  override fun isExpressionValueUsed(element: UExpression) = true

  override fun isFileSupported(fileName: String) = fileName.endsWith(".gradle.dcl")
}

class DeclarativeUFile(override val sourcePsi: DeclarativeFile, override val languagePlugin: DeclarativeUastLanguagePlugin) : UFile {
  override val allCommentsInFile: List<UComment> = listOf()
  override val classes: List<UClass> = listOf()
  override val imports: List<UImportStatement> = listOf()
  override val packageName: String = "arbitrary"
  override val psi: PsiFile = sourcePsi
  override val uAnnotations: List<UAnnotation> = listOf()
  val uEntries: List<DeclarativeUEntry> =
    sourcePsi
      .getEntries()
      .mapNotNull { languagePlugin.convertElement(it, this, DeclarativeUEntry::class.java) as? DeclarativeUEntry }

  override fun accept(visitor: UastVisitor) {
    if (visitor.visitFile(this)) return
    uEntries.acceptList(visitor)
    visitor.afterVisitFile(this)
  }
}

interface DeclarativeUEntry : UExpression

abstract class AbstractUFactory: DeclarativeUEntry, UCallExpression {
  abstract override val sourcePsi: DeclarativeIdentifierOwner
  override val classReference: UReferenceExpression? = null
  override val kind: UastCallKind = UastCallKind.METHOD_CALL
  override val methodIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.identifier, this)
  override val methodName: String?
    get() = sourcePsi.identifier.name
  override val psi: PsiElement
    get() = sourcePsi
  override val receiverType: PsiType? = null
  override val returnType: PsiType? = null
  override val typeArgumentCount: Int = 0
  override val typeArguments: List<PsiType> = listOf()
  override val uAnnotations: List<UAnnotation> = listOf()
  override val valueArgumentCount: Int
    get() = valueArguments.size
  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)
  override fun resolve(): PsiMethod? = null
}

class DeclarativeUFactory(
  override val sourcePsi: DeclarativeReceiverBasedFactory<*>,
  parentProvider: () -> UElement?,
) : AbstractUFactory() {
  override val receiver: UExpression? = when (sourcePsi) {
    is DeclarativeFactoryPropertyReceiver-> sourcePsi.getReceiver()?.toDeclarativeUExpression(this)
    is DeclarativeFactoryReceiver-> sourcePsi.getReceiver()?.toDeclarativeUExpression(this)
    else -> null
  }


  override val valueArguments: List<UExpression> = sourcePsi.argumentsList?.arguments?.mapNotNull { it.toDeclarativeUExpression(this) }
                                                   ?: listOf()
  override val uastParent: UElement? by lazy(parentProvider)
}

open class DeclarativeUFactoryReceiver(
  override val sourcePsi: DeclarativeFactoryReceiver,
  parentProvider: () -> UElement?,
) : AbstractUFactory() {
  override val receiver: UExpression?
    get() = sourcePsi.getReceiver()?.toDeclarativeUExpression(this)

  override val valueArguments: List<UExpression>
    get() = sourcePsi.argumentsList?.arguments?.mapNotNull { it.toDeclarativeUExpression(this) }
                                                   ?: listOf()
  override val uastParent: UElement? by lazy(parentProvider)
}

class SimpleDeclarativeUFactoryReceiver(
  override val sourcePsi: DeclarativeFactoryReceiver,
  parentProvider: () -> UElement?,
) : DeclarativeUFactoryReceiver(sourcePsi, parentProvider) {
  override val receiver: UExpression? = null
}

class DeclarativeUBlock(override val sourcePsi: DeclarativeBlock, parentProvider: () -> UElement?) : DeclarativeUEntry, UCallExpression {
  override val classReference: UReferenceExpression? = null
  override val kind: UastCallKind = UastCallKind.METHOD_CALL
  override val methodIdentifier: UIdentifier = UIdentifier(sourcePsi.identifier, this)
  override val methodName: String? = sourcePsi.identifier.name
  override val receiver: UExpression? = null
  override val receiverType: PsiType? = null
  override val returnType: PsiType? = null
  override val typeArgumentCount: Int = 0
  override val typeArguments: List<PsiType> = listOf()
  override val uAnnotations: List<UAnnotation> = listOf()
  override val uastParent by lazy(parentProvider)
  override val psi: PsiElement
    get() = sourcePsi
  override val valueArguments: List<UExpression> =
    (sourcePsi.embeddedFactory?.argumentsList?.arguments?.mapNotNull { it.toDeclarativeUExpression(this) } ?: listOf()) +
    DeclarativeULambda(sourcePsi.blockGroup, this)
  override val valueArgumentCount: Int = valueArguments.size
  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)

  override fun resolve(): PsiMethod? = null
  override fun asRenderString(): String {
    val name = methodName ?: methodIdentifier.name ?: "<noref>"
    val argumentString = valueArguments.dropLast(1).takeIf { it.isNotEmpty() }
                           ?.joinToString(prefix = "(", postfix = ")") ?: ""
    val lambdaString = valueArguments.last().asRenderString()
    return "$name$argumentString $lambdaString"
  }
}

class DeclarativeULambda(override val sourcePsi: DeclarativeBlockGroup, override val uastParent: UElement?) : ULambdaExpression {
  override val body = DeclarativeUAction(sourcePsi.entries, this)
  override val functionalInterfaceType: PsiType? = null
  override val psi: PsiElement? = sourcePsi
  override val uAnnotations: List<UAnnotation> = listOf()
  override val valueParameters: List<UParameter> = listOf()

  override fun asRenderString(): String {
    val expressions = body.expressions.joinToString("\n") { it.asRenderString().withMargin }
    return "{\n$expressions\n}"
  }
}

class DeclarativeUAction(entries: List<DeclarativeEntry>, override val uastParent: UElement?) : UBlockExpression {
  override val expressions: List<UExpression> =
    entries.mapNotNull { UastFacade.convertElement(it, this, DeclarativeUEntry::class.java) as? DeclarativeUEntry }
  override val psi: PsiElement? = null
  override val uAnnotations: List<UAnnotation> = listOf()
}

class DeclarativeUAssignment(override val sourcePsi: DeclarativeAssignment,
                             parentProvider: () -> UElement?) : DeclarativeUEntry, UBinaryExpression {
  override val leftOperand: UExpression = sourcePsi.assignableProperty.toDeclarativeUExpression(this)
  override val operator: UastBinaryOperator = UastBinaryOperator.ASSIGN
  override val operatorIdentifier: UIdentifier? = null
  override val rightOperand: UExpression = sourcePsi.value?.toDeclarativeUExpression(this) ?: UastEmptyExpression(this)

  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolveOperator(): PsiMethod? = null
  override val psi: PsiElement
    get() = sourcePsi
  override val uastParent by lazy(parentProvider)
}

fun DeclarativePropertyReceiver.toDeclarativeUExpression(uastParent: UElement?): UExpression =
  getReceiver()?.let { DeclarativeUQualifiedProperty(this, uastParent, it) } ?: DeclarativeUSimpleProperty(
    this.field, uastParent)

fun DeclarativeFactoryReceiver.toDeclarativeUExpression(uastParent: UElement?): UExpression =
  getReceiver()?.let { DeclarativeUFactoryReceiver(this) { uastParent } } ?: SimpleDeclarativeUFactoryReceiver(this) { uastParent }

fun DeclarativeValue.toDeclarativeUExpression(uastParent: UElement?): UExpression =
  when (this) {
    is DeclarativeSimpleLiteral -> DeclarativeULiteral(this, uastParent)
    is DeclarativeReceiverBasedFactory<*> -> DeclarativeUFactory(this) { uastParent }
    is DeclarativeProperty -> getReceiver()?.let { DeclarativeUQualifiedProperty(this, uastParent, it) } ?: DeclarativeUSimpleProperty(
      this.field, uastParent)
    is DeclarativePropertyReceiver -> getReceiver()?.let { DeclarativeUQualifiedProperty(this, uastParent, it) } ?: DeclarativeUSimpleProperty(
      this.field, uastParent)
    is DeclarativePair -> DeclarativeUPair(this, uastParent)
    else -> error("Unexpected DeclarativeValue: $this")
  }

fun DeclarativeAssignableProperty.toDeclarativeUExpression(uastParent: UElement?): UExpression = getReceiver()?.let {
  DeclarativeUAssignableProperty(this, uastParent, it)
} ?: DeclarativeUSimpleProperty(this.field, uastParent)

class DeclarativeULiteral(override val sourcePsi: DeclarativeSimpleLiteral, override val uastParent: UElement?) : ULiteralExpression {
  override val psi: PsiElement = sourcePsi
  override val uAnnotations: List<UAnnotation> = listOf()
  override val value: Any? = sourcePsi.value
  override fun evaluate(): Any? = value
}

class DeclarativeUQualifiedProperty(override val sourcePsi: DeclarativeValueFieldOwner,
                                    override val uastParent: UElement?,
                                    receiver: DeclarativeValueFieldOwner) : UQualifiedReferenceExpression {
  override val accessType: UastQualifiedExpressionAccessType = UastQualifiedExpressionAccessType.SIMPLE
  override val psi: PsiElement = sourcePsi
  override val receiver: UExpression = receiver.toDeclarativeUExpression(this)
  override val resolvedName: String? = null
  override val selector: UExpression = DeclarativeUSimpleProperty(sourcePsi.field, this)
  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolve(): PsiElement? = null
}

class DeclarativeUAssignableProperty(override val sourcePsi: DeclarativeAssignableProperty,
                                     override val uastParent: UElement?,
                                     receiver: DeclarativeAssignableProperty) : UQualifiedReferenceExpression {
  override val accessType: UastQualifiedExpressionAccessType = UastQualifiedExpressionAccessType.SIMPLE
  override val psi: PsiElement = sourcePsi
  override val receiver: UExpression = receiver.toDeclarativeUExpression(this)
  override val resolvedName: String? = null
  override val selector: UExpression = DeclarativeUSimpleProperty(sourcePsi.field, this)
  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolve(): PsiElement? = null
}

class DeclarativeUSimpleProperty(override val sourcePsi: DeclarativeIdentifier,
                                 override val uastParent: UElement?) : USimpleNameReferenceExpression {
  override val identifier: String = sourcePsi.name ?: "<noref>"
  override val psi: PsiElement? = sourcePsi
  override val resolvedName: String? = null
  override val uAnnotations: List<UAnnotation> = listOf()
  override fun resolve(): PsiElement? = null
}

class DeclarativeUPair(
  override val sourcePsi: DeclarativePair,
  override val uastParent: UElement?
) : UCallExpression {
  override val classReference: UReferenceExpression? = null
  override val kind: UastCallKind = UastCallKind.METHOD_CALL
  override val methodIdentifier: UIdentifier
    get() = UIdentifier(sourcePsi.pairOperator, this)
  override val methodName: String
    get() = sourcePsi.pairOperator.toString()
  override val psi: PsiElement
    get() = sourcePsi
  override val receiverType: PsiType? = null
  override val returnType: PsiType? = null
  override val typeArgumentCount: Int = 0
  override val typeArguments: List<PsiType> = listOf()
  override val uAnnotations: List<UAnnotation> = listOf()
  override val valueArgumentCount: Int = 1
  override fun getArgumentForParameter(i: Int): UExpression? = valueArguments.getOrNull(i)
  override fun resolve(): PsiMethod? = null
  override val receiver: UExpression
    get() = sourcePsi.first.toDeclarativeUExpression(this)
  override val valueArguments: List<UExpression>
    get() = listOf(sourcePsi.second.toDeclarativeUExpression(this) )
}