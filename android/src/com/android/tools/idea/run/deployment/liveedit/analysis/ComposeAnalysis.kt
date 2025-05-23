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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.SdkConstants
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode
import org.jetbrains.org.objectweb.asm.tree.IntInsnNode
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame

interface GroupTable {
  /**
   * Map of @Composable methods to their corresponding group
   */
  val groups: Map<IrMethod, ComposeGroup>

  /**
   * Map of restart lambda classes to the method they recompose. Restart lambdas are a cached lambda invoked by Compose to re-run the body
   * of a @Composable method when it is invalidated
   */
  val restartLambdas: Map<IrClass, IrMethod>

  /**
   * Map of @Composable lambda classes to the method where they are instantiated
   */
  val lambdaParents: Map<IrClass, IrMethod>

  /**
   * Map of inner classes of @Composable lambdas to their top-level lambda parent; i.e, ComposableSingletons$lambda-2$1$1$2$1 maps to
   * ComposableSingletons$lambda-2
   *
   * This information makes it possible to determine which inner classes can be changed without requiring an activity restart
   */
  val composableInnerClasses: Map<IrClass, IrMethod>

  /**
   * A method is associated with a group key if:
   *  - it is a @Composable method associated with a group key
   *  - it is defined in an inner class of a method associated with a group key
   *
   *  These rules apply recursively; an inner class of an inner class of a @Composable lambda group is treated as being in that group
   */
  fun getComposeGroup(method: IrMethod): ComposeGroup? {
    if (method in groups) {
      return groups[method]
    }

    if (method.clazz in composableInnerClasses) {
      val composableMethod = composableInnerClasses[method.clazz]!!
      return getComposeGroup(composableMethod)
    }

    return null
  }
}

class MutableGroupTable : GroupTable {
  override val groups = mutableMapOf<IrMethod, ComposeGroup>()
  override val restartLambdas = mutableMapOf<IrClass, IrMethod>()
  override val lambdaParents = mutableMapOf<IrClass, IrMethod>()
  override val composableInnerClasses = mutableMapOf<IrClass, IrMethod>()
}

fun computeGroupTable(classes: List<IrClass>): GroupTable {
  val classesByName = classes.associateBy { it.name }
  val singletons = classes.singleOrNull { isComposableSingleton(it.name) }

  val groupTable = MutableGroupTable()
  val analyzer = ComposeAnalyzer()

  val singletonMethods = singletons?.methods ?: emptyList()

  val singletonInit = singletonMethods.singleOrNull { it.name == SdkConstants.CLASS_CONSTRUCTOR }
  if (singletonInit != null) {
    analyzeMethod(analyzer, singletonInit, classesByName, groupTable)
    for (method in singletonMethods.filter { it != singletonInit }) {
      analyzeMethod(analyzer, method, classesByName, groupTable)
    }
  }

  for (method in classes.filter { it != singletons }.flatMap { it.methods }) {
    analyzeMethod(analyzer, method, classesByName, groupTable)
  }

  val inners = mutableMapOf<IrMethod, MutableList<IrClass>>()
  for (clazz in classes) {
    val outerClass = classesByName[clazz.enclosingMethod?.outerClass] ?: continue
    val outerMethod =
      outerClass.methods.singleOrNull { it.name == clazz.enclosingMethod?.outerMethod && it.desc == clazz.enclosingMethod.outerMethodDesc }
      ?: continue
    inners.computeIfAbsent(outerMethod) { mutableListOf() }.add(clazz)
  }

  // Starting from the lambda classes associated with group keys, walk down the inner class hierarchy and associate each inner class with
  // the lambda at the root of the tree
  for (entry in inners.filter { it.key in groupTable.groups }) {
    val queue = ArrayDeque(entry.value)
    while (queue.isNotEmpty()) {
      val cur = queue.removeFirst()
      groupTable.composableInnerClasses[cur] = entry.key
      cur.methods.mapNotNull { inners[it] }.forEach { queue.addAll(it) }
    }
  }
  return groupTable
}

fun GroupTable.toStringWithLineInfo(sourceFile: KtFile): String {
  val doc = sourceFile.fileDocument
  with(StringBuilder()) {
    appendLine("===Composable Groups===")
    for ((method, group) in groups) {
      val startLine = doc.getLineNumber(group.range.startOffset) + 1
      val endLine = doc.getLineNumber(group.range.endOffset) + 1
      appendLine("\t$method} - group: ${group.key} - lines: [$startLine, $endLine]")
    }
    appendLine("===Restart Lambdas===")
    for ((clazz, method) in restartLambdas) {
      appendLine("\t${clazz.name} - $method")
    }
    appendLine("===Composable Lambda Parents===")
    for ((clazz, method) in lambdaParents) {
      appendLine("\t${clazz.name} - parent: $method")
    }
    appendLine("===Inner Classes===")
    for ((clazz, method) in composableInnerClasses) {
      appendLine("\t${clazz.name} - $method")
    }
    return toString()
  }
}

private data class IntValue(val value: Int) : BasicValue(Type.INT_TYPE)
private data class ComposableLambdaValue(val key: Int, val block: Type) : BasicValue(COMPOSABLE_LAMBDA_TYPE)

private val COMPOSABLE_LAMBDA_TYPE = Type.getObjectType("androidx/compose/runtime/internal/ComposableLambda")
private val COMPOSABLE_LAMBDA_N_TYPE = Type.getObjectType("androidx/compose/runtime/internal/ComposableLambdaN")
private val COMPOSABLE_LAMBDA_TYPES = setOf(COMPOSABLE_LAMBDA_TYPE, COMPOSABLE_LAMBDA_N_TYPE)

private fun analyzeMethod(
  analyzer: ComposeAnalyzer,
  method: IrMethod,
  classesByName: Map<String, IrClass>,
  groupTable: MutableGroupTable
) {
  val frames = analyzer.analyze(method.clazz.name, method.node)

  val keyMeta = method.annotations.singleOrNull { it.desc == "Landroidx/compose/runtime/internal/FunctionKeyMeta;" }
  if (keyMeta != null) {
    if (method !in groupTable.groups) {
      val key = keyMeta.values["key"] as Int
      val startOffset = keyMeta.values["startOffset"] as Int
      val endOffset = keyMeta.values["endOffset"] as Int
      groupTable.groups[method] = ComposeGroup(key, TextRange(startOffset, endOffset))
    } else {
      throw RuntimeException("Multiple group keys associated with method $method")
    }
  }

  for (i in 0 until method.node.instructions.size()) {
    val instr = method.node.instructions[i]
    val frame = frames[i]
    when (instr.opcode) {
      INVOKEINTERFACE -> {
        val methodInstr = instr as MethodInsnNode
        if (methodInstr.owner == "androidx/compose/runtime/ScopeUpdateScope" && methodInstr.name == "updateScope") {
          val type = frame.getStackValue(0)?.type
          val clazz = classesByName[type?.internalName] ?: throw RuntimeException("Unexpected restart lambda type: $type")
          groupTable.restartLambdas[clazz] = method
        }
      }

      INVOKESTATIC -> {
        val methodInstr = instr as MethodInsnNode
        if (methodInstr.owner == "androidx/compose/runtime/internal/ComposableLambdaKt" && Type.getReturnType(
            methodInstr.desc) in COMPOSABLE_LAMBDA_TYPES) {
          val lambda = frames[i + 1].getStackValue(0) as ComposableLambdaValue
          val clazz = classesByName[lambda.block.internalName] ?: throw RuntimeException(
            "Unknown class type in ComposableLambda in $method: ${lambda.block} associated with key ${lambda.key}")
          groupTable.lambdaParents[clazz] = method
        }
      }

      INVOKEVIRTUAL -> {
        val methodInstr = instr as MethodInsnNode
        if (isComposableSingleton(methodInstr.owner)) { // Look at the next stack frame to see what was returned from this method invocation
          val lambda = frames[i + 1].getStackValue(0) as ComposableLambdaValue
          val clazz = classesByName[lambda.block.internalName] ?: throw RuntimeException("Unknown singleton lambda type: ${lambda.block}")
          groupTable.lambdaParents[clazz] = method
        }
      }
    }
  }
}

private fun Frame<BasicValue?>.getStackValue(idx: Int): BasicValue? {
  // getStack() treats index 0 as the bottom of the stack; we'd prefer to treat 0 as the most recently pushed entry
  return getStack(stackSize - 1 - idx)
}

private class ComposeAnalyzer private constructor(private val interpreter: ComposeInterpreter) : Analyzer<BasicValue?>(interpreter) {
  constructor() : this(ComposeInterpreter())

  override fun analyze(owner: String?, method: MethodNode?): Array<Frame<BasicValue?>> {
    interpreter.setCurrentMethod(owner, method)
    return super.analyze(owner, method)
  }
}

private class ComposeInterpreter : BasicInterpreter(ASM9) {
  private var owner: String? = null
  private var method: MethodNode? = null
  private val singletonFields = mutableMapOf<String, ComposableLambdaValue>()
  private val singletonGetters = mutableMapOf<String, ComposableLambdaValue>()

  fun setCurrentMethod(owner: String?, method: MethodNode?) {
    this.owner = owner
    this.method = method
  }

  override fun newValue(type: Type?): BasicValue? {
    val value = super.newValue(type)
    return if (value == BasicValue.REFERENCE_VALUE) {
      BasicValue(type)
    } else {
      value
    }
  }

  override fun newOperation(instr: AbstractInsnNode): BasicValue? {
    when (instr.opcode) {
      GETSTATIC -> {
        if (isComposableSingleton((instr as FieldInsnNode).owner) && instr.name in singletonFields) {
          return singletonFields[instr.name]
        }
      }

      LDC -> {
        val value = (instr as LdcInsnNode).cst
        if (value is Int) {
          return IntValue(value)
        }
      }

      // I'm not sure if "proper" Compose code will ever use these to load a group key, but I've caused it to happen in tests; thus,
      // adding a branch to handle this case so we don't get unexpected casting errors in the future.
      BIPUSH, SIPUSH -> {
        val value = (instr as IntInsnNode).operand
        return IntValue(value)
      }

      ICONST_M1 -> return IntValue(-1)
      ICONST_0 -> return IntValue(0)
      ICONST_1 -> return IntValue(1)
      ICONST_2 -> return IntValue(2)
      ICONST_3 -> return IntValue(3)
      ICONST_4 -> return IntValue(4)
      ICONST_5 -> return IntValue(5)
    }
    return super.newOperation(instr)
  }

  override fun unaryOperation(instr: AbstractInsnNode, value: BasicValue?): BasicValue? {
    val newValue = super.unaryOperation(instr, value)
    when (instr.opcode) {
      PUTSTATIC -> {
        if (!isComposableSingleton((instr as FieldInsnNode).owner) || value !is ComposableLambdaValue) {
          return newValue
        }

        if (instr.name in singletonFields) {
          throw RuntimeException("Repeated assignment of singleton field ${instr.name}")
        }

        singletonFields[instr.name] = value
      }

      CHECKCAST -> return value // Ignore type casting, so we can keep track of function interface implementations
    }
    return newValue
  }

  override fun naryOperation(instr: AbstractInsnNode, values: List<BasicValue?>): BasicValue? {
    when (instr.opcode) {
      INVOKESTATIC -> {
        if ((instr as MethodInsnNode).owner == "androidx/compose/runtime/internal/ComposableLambdaKt") {
          when (instr.name) {
            "composableLambda" -> {
              // fun composableLambda(composer: Composer, key: Int, tracked: Boolean, block: Any)
              val key = (values[1] as IntValue).value
              val block = values[3]!!.type
              return ComposableLambdaValue(key, block)
            }

            "composableLambdaInstance", "rememberComposableLambda" -> {
              // fun composableLambdaInstance(key: Int, tracked: Boolean, block: Any)
              // @Composable fun rememberComposableLambda(key: Int, tracked: Boolean, block: Any)
              val key = (values[0] as IntValue).value
              val block = values[2]!!.type
              return ComposableLambdaValue(key, block)
            }
          }
        }

        if (instr.owner == "androidx/compose/runtime/internal/ComposableLambdaNKt") {
          when (instr.name) {
            "composableLambdaN" -> {
              // fun composableLambdaN(composer: Composer, key: Int, tracked: Boolean, arity: Int, block: Any)
              val key = (values[1] as IntValue).value
              val block = values[3]!!.type
              return ComposableLambdaValue(key, block)
            }

            "composableLambdaNInstance", "rememberComposableLambdaN" -> {
              // fun composableLambdaInstance(key: Int, tracked: Boolean, arity: Int, block: Any)
              // @Composable fun rememberComposableLambda(key: Int, tracked: Boolean, arity: Int, block: Any)
              val key = (values[0] as IntValue).value
              val block = values[2]!!.type
              return ComposableLambdaValue(key, block)
            }
          }
        }
      }

      INVOKEVIRTUAL -> {
        if (isComposableSingleton((instr as MethodInsnNode).owner) && instr.name in singletonGetters) {
          return singletonGetters[instr.name]
        }
      }
    }
    return super.naryOperation(instr, values)
  }

  override fun returnOperation(instr: AbstractInsnNode?, value: BasicValue?, expected: BasicValue?) {
    if (!isComposableSingleton(owner!!) || value !is ComposableLambdaValue) {
      return
    }

    if (method!!.name in singletonGetters) {
      throw RuntimeException("Multiple return branches in singleton getter: $method")
    }

    singletonGetters[method!!.name] = value
  }
}

private fun isComposableSingleton(typeInternalName: String): Boolean {
  val classInternalName = typeInternalName.split('/').last()
  return classInternalName.split(
    '$'
  ).let { // Ensure we don't accidentally treat com/my/package/ComposableSingletons$MyActivity$lambda-1 as a singleton parent class
    it.size == 2 && it.first() == "ComposableSingletons"
  }
}