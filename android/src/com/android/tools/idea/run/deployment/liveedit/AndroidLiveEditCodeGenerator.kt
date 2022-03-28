/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.annotations.Trace
import com.android.tools.idea.editors.liveedit.LiveEditConfig
import com.google.common.collect.HashMultimap
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.FacadeClassSourceShimForFragmentCompilation
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensionsImpl
import org.jetbrains.kotlin.backend.jvm.jvmPhases
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotated
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.types.KotlinType
import org.objectweb.asm.ClassReader
import java.lang.Math.ceil

const val SLOTS_PER_INT = 10
const val BITS_PER_INT = 31

private fun handleCompilerErrors(e : Throwable) {
  // These should be rethrown as per the javadoc for ProcessCanceledException. This allows the
  // internal IDE code for handling read/write actions to function as expected.
  if (e is ProcessCanceledException) {
    throw e
  }

  // Given that the IDE already provide enough information about compilation errors, there is no
  // real need to surface any compilation exception. We will just print the true cause for the
  // exception for our own debugging purpose only.
  var cause = e;
  while (cause.cause != null) {
    cause = cause.cause!!

    // The Kotlin compiler probably shouldn't be swallowing these, but since we can't change that,
    // detect and re-throw them here as the proper exception type.
    if (cause is ProcessCanceledException) {
      throw cause
    }

    var message = cause.message!!
    if (message.contains("Unhandled intrinsic in ExpressionCodegen")) {
      // This bug should be fixed as of Dolphin C5. We should leave it in in case of regression / other scenerios that triggers it again.
      var nameStart = message.indexOf("name:") + "name:".length
      var nameEnd = message.indexOf(' ', nameStart)
      var name = message.substring(nameStart, nameEnd)

      throw LiveEditUpdateException.knownIssue(201728545,
                                               "unable to compile a file that reference a top level function in another source file.\n" +
                                               "For now work around this by moving function $name inside the class.")
    } else if (message.contains("Back-end (JVM) Internal error: Couldn't inline method call")) {
      // We currently don't support inline function calls to another source code file.

      var nameStart = message.indexOf("Couldn't inline method call: CALL '") + "Couldn't inline method call: CALL '".length
      var nameEnd = message.indexOf("'", nameStart)
      var name = message.substring(nameStart, nameEnd)

      throw LiveEditUpdateException.knownIssue(223485031,
                                               "Unable to update function that references" +
                                               " an inline function from another source file: $name")
    }
  }
  throw LiveEditUpdateException.compilationError(e.message?:"No error message", e)
}

/**
 * Compute the BindingContext of the input file that can be used for code generation.
 *
 * This function needs to be done in a read action.
 */
fun analyze(input: List<KtFile>, resolution: ResolutionFacade) : BindingContext {
  val trace = com.android.tools.tracer.Trace.begin("analyzeWithAllCompilerChecks")
  try {
    var exception : LiveEditUpdateException? = null
    val analysisResult = resolution.analyzeWithAllCompilerChecks(input) {
      if (it.severity== Severity.ERROR) {
        exception = LiveEditUpdateException.analysisError("Analyze Error. $it")
      }
    }
    if (exception != null) {
      throw exception!!
    }

    if (analysisResult.isError()) {
      throw LiveEditUpdateException.analysisError(analysisResult.error.message?:"No Error message")
    }

    for (diagnostic in analysisResult.bindingContext.diagnostics) {
      if (diagnostic.severity == Severity.ERROR) {
        throw LiveEditUpdateException.analysisError("Binding Context Error. $diagnostic")
      }
    }

    return analysisResult.bindingContext
  } finally {
    trace.close()
  }
}

/**
 * Invoke the Kotlin compiler that is part of the plugin. The compose plugin is also attached by the
 * the extension point to generate code for @composable functions.
 */
fun backendCodeGen(project: Project, resolution: ResolutionFacade, bindingContext: BindingContext,
                           input: List<KtFile>, langVersion: LanguageVersionSettings): GenerationState {
  val compilerConfiguration = CompilerConfiguration()
  compilerConfiguration.languageVersionSettings = langVersion

  compilerConfiguration.put(CommonConfigurationKeys.MODULE_NAME, input[0].module!!.name)

  val useComposeIR = LiveEditConfig.getInstance().useEmbeddedCompiler
  if (useComposeIR) {
    // Not 100% sure what causes the issue but not seeing this in the IR backend causes exceptions.
    compilerConfiguration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)

    // We don't support INVOKE_DYNAMIC in the interpeter at the moment.
    compilerConfiguration.put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
    compilerConfiguration.put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.CLASS)
  }

  val generationStateBuilder = GenerationState.Builder(project,
                                                       ClassBuilderFactories.BINARIES,
                                                       resolution.moduleDescriptor,
                                                       bindingContext,
                                                       input,
                                                       compilerConfiguration);

  if (useComposeIR) {
    generationStateBuilder.codegenFactory(AndroidLiveEditJvmIrCodegenFactory(
      compilerConfiguration,
      PhaseConfig(jvmPhases),
      jvmGeneratorExtensions = object : JvmGeneratorExtensionsImpl(compilerConfiguration) {
        override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
          val psiSourceFile =
            descriptor.toSourceElement.containingFile as? PsiSourceFile ?: return super.getContainerSource(descriptor)
          return FacadeClassSourceShimForFragmentCompilation(psiSourceFile)
        }
      }
    ))
  }

  val generationState = generationStateBuilder.build();

  try {
    KotlinCodegenFacade.compileCorrectFiles(generationState)
  } catch (e : Throwable) {
    handleCompilerErrors(e) // handleCompilerErrors() always throws.
  }

  return generationState
}

class AndroidLiveEditCodeGenerator(val project: Project){

  data class CodeGeneratorInput(val file: PsiFile, var function: KtNamedFunction, val state: FunctionState)

  enum class FunctionType {
    KOTLIN, COMPOSABLE
  }

  data class CodeGeneratorOutput(val className: String,
                                 val methodName: String,
                                 val methodDesc: String,
                                 val classData: ByteArray,
                                 val functionType: FunctionType,
                                 val offSet: FunctionState.Offset,
                                 val supportClasses: Map<String, ByteArray>)

  /**
   * Compile a given set of MethodReferences to Java .class files and populates the output list with the compiled code.
   * The compilation is wrapped in a cancelable read action, and will be interrupted by a PSI write action.
   *
   * Returns true if the compilation is successful, and false if the compilation was interrupted and did not complete.
   * If compilation fails due to issues with invalid syntax or other compiler-specific errors, throws a
   * LiveEditException detailing the failure.
   */
  @Trace
  fun compile(inputs: List<CodeGeneratorInput>, outputs: MutableList<CodeGeneratorOutput>) : Boolean {
    outputs.clear()

    // Bundle changes per-file to prevent wasted recompilation of the same file. The most common
    // scenario is multiple pending changes in the same file, so this is somewhat important.
    val changedFiles = HashMultimap.create<KtFile, CodeGeneratorInput>()
    for (input in inputs) {
      if (input.file is KtFile) {
        changedFiles.put(input.file, input)
      }
    }

    // Wrap compilation in a read action that can be interrupted by any other read or write action,
    // which prevents the UI from freezing during compilation if the user continues typing.
    val progressManager = ProgressManager.getInstance()
    return progressManager.runInReadActionWithWriteActionPriority(
      {
        for ((file, input) in changedFiles.asMap()) {
          outputs.addAll(compileKtFile(file, input))
        }
      }, progressManager.progressIndicator)
  }

  private fun compileKtFile(file: KtFile, inputs: Collection<CodeGeneratorInput>) : List<CodeGeneratorOutput> {
    val tracker = PerformanceTracker()
    val inputFiles = listOf(file)

    // This is a three-step process:
    // 1) Compute binding context based on any previous cached analysis results.
    //    On small edits of previous analyzed project, this operation should be below 30ms or so.
    ProgressManager.checkCanceled()
    val resolution = tracker.record({fetchResolution(inputFiles)}, "resolution_fetch")

    ProgressManager.checkCanceled()
    val bindingContext = tracker.record({analyze(inputFiles, resolution)}, "analysis")

    // 2) Invoke the backend with the inputs and the binding context computed from step 1.
    //    This is the one of the most time-consuming step with 80 to 500ms turnaround, depending on
    //    the complexity of the input .kt file.
    ProgressManager.checkCanceled()
    val generationState = tracker.record({backendCodeGen(project, resolution, bindingContext, inputFiles,
                                 AndroidLiveEditLanguageVersionSettings(file.languageVersionSettings))}, "codegen")

    // 3) From the information we gather at the PSI changes and the output classes of Step 2, we
    //    decide which classes we want to send to the device along with what extra meta-information the
    //    agent need.
    return inputs.map { getGeneratedCode(it, generationState)}
  }

  /**
   * Fetch the resolution based on the cached service.
   */
  fun fetchResolution(input: List<KtFile>): ResolutionFacade {
    val kotlinCacheService = KotlinCacheService.getInstance(project)
    return kotlinCacheService.getResolutionFacade(input)
  }

  /**
   * Pick out what classes we need from the generated list of .class files.
   */
  private fun getGeneratedCode(input: CodeGeneratorInput, generationState: GenerationState): CodeGeneratorOutput {
    var targetFunction = input.function
    val compilerOutput = generationState.factory.asList()
    var bindingContext = generationState.bindingContext
    val desc = bindingContext[BindingContext.FUNCTION, targetFunction]!!

    val methodSignature = remapFunctionSignatureIfNeeded(desc, generationState.typeMapper)
    val isCompose = desc.hasComposableAnnotation()

    var elem: PsiElement = targetFunction
    while (elem.getKotlinFqName() == null || elem !is KtNamedFunction) {
      if (elem.parent == null) {
        throw LiveEditUpdateException.internalError("Could not find a non-null named method");
      }
      elem = elem.parent
    }

    val function: KtNamedFunction = elem

    // Class name can be either the class containing the function fragment or a KtFile
    var className = KtNamedDeclarationUtil.getParentFqName(function).toString()
    if (function.isTopLevel) {
      val grandParent: KtFile = function.parent as KtFile
      className = grandParent.javaFileFacadeFqName.toString()
    }

    if (className.isEmpty() || methodSignature.isEmpty()) {
      throw LiveEditUpdateException.internalError("Empty class name / method signature.")
    }

    if (compilerOutput.isEmpty()) {
      throw LiveEditUpdateException.internalError("No compiler output.")
    }

    fun isProxiable(clazzFile : ClassReader) : Boolean = clazzFile.superName == "kotlin/jvm/internal/Lambda" ||
                                                         clazzFile.superName == "kotlin/coroutines/jvm/internal/SuspendLambda" ||
                                                         clazzFile.superName == "kotlin/coroutines/jvm/internal/RestrictedSuspendLambda" ||
                                                         clazzFile.className.contains("ComposableSingletons\$")

    val internalClassName = getInternalClassName(desc.containingPackage(), className)
    var primaryClass = ByteArray(0)
    val supportClasses = mutableMapOf<String, ByteArray>()
    // TODO: Remove all these println once we are more stable.
    println("Lived edit classes summary start")
    for (c in compilerOutput) {

      // We get things like folder path an
      if (!c.relativePath.endsWith(".class")) {
        println("   Skipping output: ${c.relativePath}")
        continue
      }

      // The class to become interpreted
      if (c.relativePath == "$internalClassName.class") {
        primaryClass = c.asByteArray()
        println("   Primary class: ${c.relativePath}")
        continue
      }

      // Lambdas and compose classes are proxied in the interpreted on device.
      val reader = ClassReader(c.asByteArray());
      if (isProxiable(reader)) {
        println("   Proxiable class: ${c.relativePath}")
        val name = c.relativePath.substringBefore(".class")
        supportClasses[name] = c.asByteArray()
        continue
      }

      println("   Ignored class: ${c.relativePath}")
      // TODO: New classes (or existing unmodified classes) are not handled here. We should let the user know here.
    }
    println("Lived edit classes summary end")
    val idx = methodSignature.indexOf('(')
    val methodName = methodSignature.substring(0, idx);
    val methodDesc = methodSignature.substring(idx)
    val functionType = if (isCompose) FunctionType.COMPOSABLE else FunctionType.KOTLIN

    return CodeGeneratorOutput(internalClassName, methodName, methodDesc, primaryClass, functionType,
                               input.state.initialOffsetOf(function)!!, supportClasses)
  }

  fun remapFunctionSignatureIfNeeded(desc: SimpleFunctionDescriptor, mapper: KotlinTypeMapper) : String {
    var target = "${desc.name}("
    for (param in desc.valueParameters) {
      target += remapComposableFunctionType(param.type, mapper)
    }

    var additionalParams = ""
    if (desc.hasComposableAnnotation()) {
      val totalSyntheticParamCount = calcStateParamCount(desc.valueParameters.size, desc.valueParameters.count { it.hasDefaultValue() })
      // Add the Composer parameter as well as number of additional ints computed above.
      additionalParams = "Landroidx/compose/runtime/Composer;"
      for (x in 1 .. totalSyntheticParamCount) {
        additionalParams += "I"
      }
    }
    target += "$additionalParams)"
    // We are done with parameters, last thing to do is append return type.
    target += remapComposableFunctionType(desc.returnType, mapper)
    return target
  }

  fun remapComposableFunctionType(type: KotlinType?, mapper: KotlinTypeMapper ) : String {
    val funInternalNamePrefix = "Lkotlin/jvm/functions/Function"
    if (type == null) {
      return "Lkotlin/Unit;"
    }
    val originalType = mapper.mapType(type).toString()
    val numParamStart = originalType.indexOf(funInternalNamePrefix)
    if (!type.hasComposableAnnotation() || numParamStart < 0) {
      return originalType
    }
    var numParam = originalType.substring(numParamStart + funInternalNamePrefix.length, originalType.length - 1).toInt()
    numParam += calcStateParamCount(numParam) + 1 // Add the one extra param for Composer.
    return "$funInternalNamePrefix$numParam;"
  }

  fun calcStateParamCount(realValueParamsCount : Int, numDefaults : Int = 0) : Int {
    // The number of synthetic int param added to a function is the total of:
    // 1. max (1, ceil(numParameters / 10))
    // 2. 0 default int parameters if none of the N parameters have default expressions
    // 3. ceil(N / 31) N parameters have default expressions if there are any defaults
    //
    // The formula follows the one found in ComposableFunctionBodyTransformer.kt
    var totalSyntheticParamCount = 0
    if (realValueParamsCount == 0) {
      totalSyntheticParamCount += 1;
    } else {
      val totalParams = realValueParamsCount
      totalSyntheticParamCount += ceil(totalParams.toDouble() / SLOTS_PER_INT.toDouble()).toInt()
    }

    if (realValueParamsCount != 0 && numDefaults != 0) {
      totalSyntheticParamCount += ceil(realValueParamsCount.toDouble() / BITS_PER_INT.toDouble()).toInt()
    }
    return totalSyntheticParamCount;
  }

  fun Annotated.hasComposableAnnotation() = this.annotations.hasAnnotation(FqName("androidx.compose.runtime.Composable"))

  // The PSI returns the class name in the same format it would be used in an import statement: com.package.Class.InnerClass; however,
  // java's internal name format requires the same class name to be formatted as com/package/Class$InnerClass. This method takes a package
  // and class name in "import" format and returns the same class name in "internal" format.
  private fun getInternalClassName(packageName : FqName?, className : String) : String {
    var packagePrefix = ""
    if (packageName != null && !packageName.isRoot) {
      packagePrefix = "$packageName."
    }
    if (!className.contains(packagePrefix)) {
      throw LiveEditUpdateException.internalError("Expected package prefix '$packagePrefix' not found in class name '$className'")
    }
    val classSuffix = className.substringAfter(packagePrefix)
    return packagePrefix.replace(".", "/") + classSuffix.replace(".", "$")
  }
}
