/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading


import com.android.tools.idea.editors.literals.internal.FILE_INFO_ANNOTATION
import com.android.tools.idea.editors.literals.internal.INFO_ANNOTATION
import com.android.tools.idea.editors.literals.internal.LiveLiteralsFinder
import com.android.tools.idea.editors.literals.internal.MethodData
import com.android.tools.idea.editors.literals.internal.isLiveLiteralsClassName
import com.android.tools.idea.rendering.classloading.LiveLiteralsTransform.Companion.log
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter
import org.jetbrains.org.objectweb.asm.commons.Method
import kotlin.reflect.jvm.javaMethod

/**
 * Returns the correct remap method for a given value. The remap method takes the constant value in the user code
 * and runs it through an Autobox/check/Unbox cycle. The user code deals with primitives and not with boxed types.
 */
private fun getRemapper(type: Type): Pair<java.lang.reflect.Method, Boolean> =
  when (type) {
    Type.INT_TYPE -> PrimitiveTypeRemapper::remapInt.javaMethod!! to true
    Type.FLOAT_TYPE -> PrimitiveTypeRemapper::remapFloat.javaMethod!! to true
    Type.DOUBLE_TYPE -> PrimitiveTypeRemapper::remapDouble.javaMethod!! to true
    Type.SHORT_TYPE -> PrimitiveTypeRemapper::remapShort.javaMethod!! to true
    Type.LONG_TYPE -> PrimitiveTypeRemapper::remapLong.javaMethod!! to true
    Type.CHAR_TYPE -> PrimitiveTypeRemapper::remapChar.javaMethod!! to true
    Type.BOOLEAN_TYPE -> PrimitiveTypeRemapper::remapBoolean.javaMethod!! to true
    else -> ComposePreviewConstantRemapper::remapAny.javaMethod!! to false
  }

private fun getDefaultTypeValue(type: Type): Any? =
  when (type) {
    Type.INT_TYPE -> 0
    Type.FLOAT_TYPE -> 0f
    Type.DOUBLE_TYPE -> 0.0
    Type.SHORT_TYPE -> 0.toShort()
    Type.LONG_TYPE -> 0L
    Type.CHAR_TYPE -> ' '
    Type.BOOLEAN_TYPE -> false
    else -> null
  }

/**
 * Outputs the code to load the constant from the [ComposePreviewConstantRemapper].
 */
private fun GeneratorAdapter.writeConstantLoadingCode(
  fileName: String, offset: Int, returnType: Type, initialValue: Any?) {
  val (remapMethod, isPrimitive) = getRemapper(returnType)
  // If the initial value is null and returnType is a primitive type, we need to get the default value
  // for that particular type.
  val value = initialValue ?: getDefaultTypeValue(returnType)
  val remapMethodDescriptor = Type.getMethodDescriptor(remapMethod)
  // Generate call to the static remapper method that loads the constant value
  // Load this as first parameter and the original constant as second

  // Load (this, fileName, offset, initialValue) and call the remap method.
  visitVarInsn(Opcodes.ALOAD, 0)
  visitLdcInsn(fileName)
  visitLdcInsn(offset)
  if (value != null) {
    visitLdcInsn(value)
  }
  else {
    visitInsn(Opcodes.ACONST_NULL)
  }
  invokeStatic(
    Type.getType(remapMethod.declaringClass),
    Method(remapMethod.name, remapMethodDescriptor))
  if (!isPrimitive && value != null) {
    checkCast(returnType)
  }

  log.debug { "Instrumented constant access for $fileName:$offset and original value '$value'" }
}

/**
 * Transformation applied to LiveLiterals classes as generated by the Compose compiler.
 *
 * The Compose compiler, when live literals is enabled, will generate a companion class with the suffix `$LiveLiterals`
 * for every class. This class will encapsulate the access to the literals from the original class and can be used to override the behaviour
 * of the constants.
 * This transformation, will find all this `$LiveLiterals` classes and rewrite the accessor methods so the constants are read
 * from the [ConstantRemapper].
 *
 * The `$LiveLiterals` classes are also tagged with additional metadata that allow to map the request of a constant back to the place in the
 * source file where this was used:
 * - The `$LiveLiterals` class will have a top level [FILE_INFO_ANNOTATION] that will include the filename.
 * - Each accessor method will be also annotated with [INFO_ANNOTATION] with the offset of the literal in the source file.
 *
 * By using those two constants, we can trace back the accessor to the source file.
 */
class LiveLiteralsTransform @JvmOverloads constructor(
  private val delegate: ClassVisitor,
  fileInfoAnnotationName: String = FILE_INFO_ANNOTATION,
  infoAnnotationName: String = INFO_ANNOTATION) :
  LiveLiteralsFinder(delegate,
                     fileInfoAnnotationName,
                     infoAnnotationName), ClassVisitorUniqueIdProvider {
  override fun onLiteralAccessor(
    fileName: String,
    offset: Int,
    initialValue: Any?,
    data: MethodData) {
    log.debug { "Rewriting LiveLiterals method ${data.name}" }
    val returnType = Type.getReturnType(data.descriptor)
    val methodVisitor = delegate.visitMethod(data.access, data.name, data.descriptor, data.signature, data.exceptions)
    val adapter = GeneratorAdapter(data.access,
                                   Method(data.name, data.descriptor),
                                   methodVisitor)

    methodVisitor.visitCode()
    adapter.writeConstantLoadingCode(fileName, offset, returnType, initialValue ?: getDefaultTypeValue(returnType))
    adapter.returnValue()
    adapter.endMethod()
  }

  companion object {
    val log = Logger.getInstance(LiveLiteralsTransform::class.java)
  }

  override val uniqueId: String = "${LiveLiteralsTransform::className},$fileInfoAnnotationName,$infoAnnotationName"
}

/**
 * Simple analyzer that checks if the visited class is a `$LiveLiterals` class.
 */
open class HasLiveLiteralsTransform @JvmOverloads constructor(
  delegate: ClassVisitor,
  private val fileInfoAnnotationName: String = FILE_INFO_ANNOTATION,
  private val onLiveLiteralsFound: () -> Unit) : ClassVisitor(Opcodes.ASM7, delegate), ClassVisitorUniqueIdProvider {

  override val uniqueId: String = "${HasLiveLiteralsTransform::class.qualifiedName!!},$fileInfoAnnotationName"

  private var className = ""
  private var hasLiveLiterals = false
    private set

  override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor {
    val annotationName = Type.getType(descriptor).className
    hasLiveLiterals = hasLiveLiterals
                      || (fileInfoAnnotationName == annotationName && isLiveLiteralsClassName(className))

    return super.visitAnnotation(descriptor, visible)
  }

  override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
    className = name ?: ""
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override fun visitEnd() {
    super.visitEnd()
    if (hasLiveLiterals) onLiveLiteralsFound()
  }
}