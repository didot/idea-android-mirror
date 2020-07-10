/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.compose.plugins.idea

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.PropertyGetterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.diagnostics.reportFromPlugin
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getValueArgumentForExpression
import org.jetbrains.kotlin.resolve.calls.checkers.AdditionalTypeChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallChecker
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerContext
import org.jetbrains.kotlin.resolve.calls.context.ResolutionContext
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil.canBeInlineArgument
import org.jetbrains.kotlin.resolve.inline.InlineUtil.isInline
import org.jetbrains.kotlin.resolve.inline.InlineUtil.isInlineParameter
import org.jetbrains.kotlin.resolve.inline.InlineUtil.isInlinedArgument
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.lowerIfFlexible
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.types.upperIfFlexible
import org.jetbrains.kotlin.util.OperatorNameConventions


open class ComposableCallChecker : CallChecker, AdditionalTypeChecker,
                                   StorageComponentContainerContributor {
  override fun registerModuleComponents(
    container: StorageComponentContainer,
    platform: TargetPlatform,
    moduleDescriptor: ModuleDescriptor
  ) {
    //if (!platform.isJvm()) return
    container.useInstance(this)
  }

  override fun check(
    resolvedCall: ResolvedCall<*>,
    reportOn: PsiElement,
    context: CallCheckerContext
  ) {
    if (!resolvedCall.isComposableInvocation()) return
    val bindingContext = context.trace.bindingContext
    var node: PsiElement? = reportOn
    loop@while (node != null) {
      when (node) {
        is KtFunctionLiteral -> {
          // keep going, as this is a "KtFunction", but we actually want the
          // KtLambdaExpression
        }
        is KtLambdaExpression -> {
          val descriptor = bindingContext[BindingContext.FUNCTION, node.functionLiteral]
          if (descriptor == null) {
            illegalCall(context, reportOn)
            return
          }
          val composable = descriptor.isComposableCallable(bindingContext)
          if (composable) return
          val arg = getArgumentDescriptor(node.functionLiteral, bindingContext)
          if (arg?.type?.composablePreventCaptureContract() == true) {
            context.trace.record(
              ComposeWritableSlices.LAMBDA_CAPABLE_OF_COMPOSER_CAPTURE,
              descriptor,
              false
            )
            context.trace.reportFromPlugin(
              ComposeErrors.CAPTURED_COMPOSABLE_INVOCATION.on(
                reportOn,
                arg,
                arg.containingDeclaration
              ),
              ComposeErrorMessages
            )
            return
          }
          // TODO(lmr): in future, we should check for CALLS_IN_PLACE contract
          val inlined = arg != null &&
                        canBeInlineArgument(node.functionLiteral) &&
                        isInline(arg.containingDeclaration) &&
                        isInlineParameter(arg)
          if (!inlined) {
            illegalCall(context, reportOn)
            return
          } else {
            // since the function is inlined, we continue going up the PSI tree
            // until we find a composable context. We also mark this lambda
            context.trace.record(
              ComposeWritableSlices.LAMBDA_CAPABLE_OF_COMPOSER_CAPTURE,
              descriptor,
              true
            )
          }
        }
        is KtFunction -> {
          val descriptor = bindingContext[BindingContext.FUNCTION, node]
          if (descriptor == null) {
            illegalCall(context, reportOn)
            return
          }
          val composable = descriptor.isComposableCallable(bindingContext)
          if (!composable) {
            illegalCall(context, reportOn, node.nameIdentifier ?: node)
          }
          return
        }
        is KtProperty -> {
          // NOTE: since we're explicitly going down a different branch for
          // KtPropertyAccessor, the ONLY time we make it into this branch is when the
          // call was done in the initializer of the property/variable.
          val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, node]
          if (
            descriptor !is LocalVariableDescriptor &&
            node.annotationEntries.hasComposableAnnotation(bindingContext)
          ) {
            // composables shouldn't have initializers in the first place
            illegalCall(context, reportOn)
            return
          }
        }
        is KtPropertyAccessor -> {
          val property = node.property
          if (!property.annotationEntries.hasComposableAnnotation(bindingContext)) {
            illegalCall(context, reportOn, property.nameIdentifier ?: property)
          }
          return
        }
        is KtFile -> {
          // if we've made it this far, the call was made in a non-composable context.
          illegalCall(context, reportOn)
          return
        }
        is KtClass -> {
          // composable calls are never allowed in the initializers of a class
          illegalCall(context, reportOn)
          return
        }
      }
      node = node.parent as? KtElement
    }
  }

  private fun illegalCall(
    context: CallCheckerContext,
    callEl: PsiElement,
    functionEl: PsiElement? = null
  ) {
    context.trace.reportFromPlugin(
      ComposeErrors.COMPOSABLE_INVOCATION.on(callEl),
      ComposeErrorMessages
    )
    if (functionEl != null) {
      context.trace.reportFromPlugin(
        ComposeErrors.COMPOSABLE_EXPECTED.on(functionEl),
        ComposeErrorMessages
      )
    }
  }

  override fun checkType(
    expression: KtExpression,
    expressionType: KotlinType,
    expressionTypeWithSmartCast: KotlinType,
    c: ResolutionContext<*>
  ) {
    val bindingContext = c.trace.bindingContext
    val expectedType = c.expectedType
    if (expectedType === TypeUtils.NO_EXPECTED_TYPE) return
    if (expectedType === TypeUtils.UNIT_EXPECTED_TYPE) return
    val expectedComposable = expectedType.hasComposableAnnotation()
    if (expression is KtLambdaExpression) {
      val descriptor = bindingContext[BindingContext.FUNCTION, expression.functionLiteral]
                       ?: return
      val isComposable = descriptor.isComposableCallable(bindingContext)
      if (expectedComposable != isComposable) {
        val isInlineable = isInlinedArgument(
          expression.functionLiteral,
          c.trace.bindingContext,
          true
        )
        if (isInlineable) return

        val reportOn =
          if (expression.parent is KtAnnotatedExpression)
            expression.parent as KtExpression
          else expression
        c.trace.report(
          Errors.TYPE_MISMATCH.on(
            reportOn,
            expectedType,
            expressionTypeWithSmartCast
          )
        )
      }
      return
    } else {
      val nullableAnyType = expectedType.builtIns.nullableAnyType
      val anyType = expectedType.builtIns.anyType

      if (anyType == expectedType.lowerIfFlexible() &&
          nullableAnyType == expectedType.upperIfFlexible()) return

      val nullableNothingType = expectedType.builtIns.nullableNothingType

      // Handle assigning null to a nullable composable type
      if (expectedType.isMarkedNullable &&
          expressionTypeWithSmartCast == nullableNothingType) return
      val isComposable = expressionType.hasComposableAnnotation()

      if (expectedComposable != isComposable) {
        val reportOn =
          if (expression.parent is KtAnnotatedExpression)
            expression.parent as KtExpression
          else expression
        c.trace.report(
          Errors.TYPE_MISMATCH.on(
            reportOn,
            expectedType,
            expressionTypeWithSmartCast
          )
        )
      }
      return
    }
  }
}

internal fun ResolvedCall<*>.isComposableInvocation(): Boolean {
  if (this is VariableAsFunctionResolvedCall) {
    if (variableCall.candidateDescriptor.type.hasComposableAnnotation())
      return true
    if (functionCall.resultingDescriptor.hasComposableAnnotation()) return true
    return false
  }
  val candidateDescriptor = candidateDescriptor
  if (candidateDescriptor is FunctionDescriptor) {
    if (candidateDescriptor.isOperator &&
        candidateDescriptor.name == OperatorNameConventions.INVOKE) {
      if (dispatchReceiver?.type?.hasComposableAnnotation() == true) {
        return true
      }
    }
  }
  return when (candidateDescriptor) {
    is ValueParameterDescriptor -> false
    is LocalVariableDescriptor -> false
    is PropertyDescriptor -> candidateDescriptor.hasComposableAnnotation()
    is PropertyGetterDescriptor ->
      candidateDescriptor.correspondingProperty.hasComposableAnnotation()
    else -> candidateDescriptor.hasComposableAnnotation()
  }
}

internal fun CallableDescriptor.isMarkedAsComposable(): Boolean {
  return when (this) {
    is PropertyGetterDescriptor -> correspondingProperty.hasComposableAnnotation()
    is ValueParameterDescriptor -> type.hasComposableAnnotation()
    is LocalVariableDescriptor -> type.hasComposableAnnotation()
    is PropertyDescriptor -> hasComposableAnnotation()
    else -> hasComposableAnnotation()
  }
}

// if you called this, it would need to be a composable call (composer, changed, etc.)
internal fun CallableDescriptor.isComposableCallable(bindingContext: BindingContext): Boolean {
  // if it's marked as composable then we're done
  if (isMarkedAsComposable()) return true
  if (
    this is FunctionDescriptor &&
    bindingContext[ComposeWritableSlices.INFERRED_COMPOSABLE_DESCRIPTOR, this] == true
  ) {
    // even though it's not marked, it is inferred as so by the type system (by being passed
    // into a parameter marked as composable or a variable typed as one. This isn't much
    // different than being marked explicitly.
    return true
  }
  val functionLiteral = findPsi() as? KtFunctionLiteral
                        // if it isn't a function literal then we are out of things to try.
                        ?: return false

  if (functionLiteral.annotationEntries.hasComposableAnnotation(bindingContext)) {
    // in this case the function literal itself is being annotated as composable but the
    // annotation isn't in the descriptor itself
    return true
  }
  val lambdaExpr = functionLiteral.parent as? KtLambdaExpression
  if (
    lambdaExpr != null &&
    bindingContext[ComposeWritableSlices.INFERRED_COMPOSABLE_LITERAL, lambdaExpr] == true
  ) {
    // this lambda was marked as inferred to be composable
    return true
  }
  // TODO(lmr): i'm not sure that this is actually needed at this point, since this should have
  //  been covered by the TypeResolutionInterceptorExtension
  val arg = getArgumentDescriptor(functionLiteral, bindingContext) ?: return false
  return arg.type.hasComposableAnnotation()
}

private fun getArgumentDescriptor(
  argument: KtFunction,
  bindingContext: BindingContext
): ValueParameterDescriptor? {
  val call = KtPsiUtil.getParentCallIfPresent(argument) ?: return null
  val resolvedCall = call.getResolvedCall(bindingContext) ?: return null
  val valueArgument = resolvedCall.call.getValueArgumentForExpression(argument) ?: return null
  val mapping = resolvedCall.getArgumentMapping(valueArgument) as? ArgumentMatch ?: return null
  return mapping.valueParameter
}

internal fun List<KtAnnotationEntry>.hasComposableAnnotation(bindingContext: BindingContext): Boolean {
  for (entry in this) {
    val descriptor = bindingContext.get(BindingContext.ANNOTATION, entry) ?: continue
    if (descriptor.isComposableAnnotation) return true
  }
  return false
}