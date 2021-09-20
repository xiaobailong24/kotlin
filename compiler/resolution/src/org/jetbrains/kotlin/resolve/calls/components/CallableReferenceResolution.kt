/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.components

import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.components.candidate.CallableReferenceCallCandidate
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemOperation
import org.jetbrains.kotlin.resolve.calls.inference.components.FreshVariableNewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind.DISPATCH_RECEIVER
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind.EXTENSION_RECEIVER
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.scopes.receivers.DetailedReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.captureFromExpression
import org.jetbrains.kotlin.types.expressions.CoercionStrategy
import org.jetbrains.kotlin.types.typeUtil.immediateSupertypes
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.utils.SmartList

sealed class CallableReceiver(val receiver: ReceiverValueWithSmartCastInfo) {
    class UnboundReference(receiver: ReceiverValueWithSmartCastInfo) : CallableReceiver(receiver)
    class BoundValueReference(receiver: ReceiverValueWithSmartCastInfo) : CallableReceiver(receiver)
    class ScopeReceiver(receiver: ReceiverValueWithSmartCastInfo) : CallableReceiver(receiver)
    class ExplicitValueReceiver(receiver: ReceiverValueWithSmartCastInfo) : CallableReceiver(receiver)
}

// todo investigate similar code in CheckVisibility
private val CallableReceiver.asReceiverValueForVisibilityChecks: ReceiverValue
    get() = receiver.receiverValue

class CallableReferenceAdaptation(
    val argumentTypes: Array<KotlinType>,
    val coercionStrategy: CoercionStrategy,
    val defaults: Int,
    val mappedArguments: Map<ValueParameterDescriptor, ResolvedCallArgument>,
    val suspendConversionStrategy: SuspendConversionStrategy
)

/**
 * cases: class A {}, class B { companion object }, object C, enum class D { E }
 * A::foo <-> Type
 * a::foo <-> Expression
 * B::foo <-> Type
 * C::foo <-> Object
 * D.E::foo <-> Expression
 */
fun createCallableReferenceProcessor(factory: CallableReferencesCandidateFactory): ScopeTowerProcessor<CallableReferenceCallCandidate> {
    when (val lhsResult = factory.kotlinCall.lhsResult) {
        LHSResult.Empty, LHSResult.Error, is LHSResult.Expression -> {
            val explicitReceiver = (lhsResult as? LHSResult.Expression)?.lshCallArgument?.receiver
            return factory.createCallableProcessor(explicitReceiver)
        }
        is LHSResult.Type -> {
            val static = lhsResult.qualifier?.let(factory::createCallableProcessor)
            val unbound = factory.createCallableProcessor(lhsResult.unboundDetailedReceiver)

            // note that if we use PrioritizedCompositeScopeTowerProcessor then static will win over unbound members
            val staticOrUnbound =
                if (static != null)
                    SamePriorityCompositeScopeTowerProcessor(static, unbound)
                else
                    unbound

            val asValue = lhsResult.qualifier?.classValueReceiverWithSmartCastInfo ?: return staticOrUnbound
            return PrioritizedCompositeScopeTowerProcessor(staticOrUnbound, factory.createCallableProcessor(asValue))
        }
        is LHSResult.Object -> {
            // callable reference to nested class constructor
            val static = factory.createCallableProcessor(lhsResult.qualifier)
            val boundObjectReference = factory.createCallableProcessor(lhsResult.objectValueReceiver)

            return SamePriorityCompositeScopeTowerProcessor(static, boundObjectReference)
        }
    }
}

fun CallableReferenceCallCandidate.addConstraints(
    constraintSystem: ConstraintSystemOperation,
    substitutor: FreshVariableNewTypeSubstitutor,
    callableReference: CallableReferenceResolutionAtom
) {
    val position = when (callableReference) {
        is CallableReferenceKotlinCallArgument -> ArgumentConstraintPositionImpl(callableReference)
        is CallableReferenceKotlinCall -> CallableReferenceConstraintPositionImpl(callableReference)
    }

    if (!ErrorUtils.isError(candidate)) {
        constraintSystem.addReceiverConstraint(substitutor, dispatchReceiver, candidate.dispatchReceiverParameter, position)
        constraintSystem.addReceiverConstraint(substitutor, extensionReceiver, candidate.extensionReceiverParameter, position)
    }

    if (expectedType != null && !TypeUtils.noExpectedType(expectedType) && !constraintSystem.hasContradiction) {
        constraintSystem.addSubtypeConstraint(substitutor.safeSubstitute(reflectionCandidateType), expectedType, position)
    }
}

private fun ConstraintSystemOperation.addReceiverConstraint(
    toFreshSubstitutor: FreshVariableNewTypeSubstitutor,
    receiverArgument: CallableReceiver?,
    receiverParameter: ReceiverParameterDescriptor?,
    position: ConstraintPosition
) {
    if (receiverArgument == null || receiverParameter == null) {
        assert(receiverArgument == null) { "Receiver argument should be null if parameter is: $receiverArgument" }
        assert(receiverParameter == null) { "Receiver parameter should be null if argument is: $receiverParameter" }
        return
    }

    val expectedType = toFreshSubstitutor.safeSubstitute(receiverParameter.value.type.unwrap())
    val receiverType = receiverArgument.receiver.stableType.let { captureFromExpression(it) ?: it }

    addSubtypeConstraint(receiverType, expectedType, position)
}

class CallableReferencesCandidateFactory(
    val kotlinCall: CallableReferenceResolutionAtom,
    val callComponents: KotlinCallComponents,
    val scopeTower: ImplicitScopeTower,
    val expectedType: UnwrappedType?,
    private val baseSystem: ConstraintStorage?,
    private val resolutionCallbacks: KotlinResolutionCallbacks
) : CandidateFactory<CallableReferenceCallCandidate> {
    override fun createErrorCandidate(): CallableReferenceCallCandidate {
        val errorScope = ErrorUtils.createErrorScope("Error resolution candidate for call $kotlinCall")
        val errorDescriptor = errorScope.getContributedFunctions(kotlinCall.rhsName, scopeTower.location).first()

        val (reflectionCandidateType, callableReferenceAdaptation) = buildReflectionType(
            errorDescriptor,
            dispatchReceiver = null,
            extensionReceiver = null,
            expectedType,
            callComponents.builtIns,
            buildTypeWithConversions = kotlinCall is CallableReferenceKotlinCallArgument
        )

        return CallableReferenceCallCandidate(
            errorDescriptor, dispatchReceiver = null, extensionReceiver = null,
            ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, reflectionCandidateType, callableReferenceAdaptation,
            kotlinCall, callComponents, scopeTower, resolutionCallbacks, expectedType, baseSystem
        )
    }

    fun createCallableProcessor(explicitReceiver: DetailedReceiver?) =
        createCallableReferenceProcessor(scopeTower, kotlinCall.rhsName, this, explicitReceiver)

    override fun createCandidate(
        towerCandidate: CandidateWithBoundDispatchReceiver,
        explicitReceiverKind: ExplicitReceiverKind,
        extensionReceiver: ReceiverValueWithSmartCastInfo?
    ): CallableReferenceCallCandidate {
        val dispatchCallableReceiver =
            towerCandidate.dispatchReceiver?.let { toCallableReceiver(it, explicitReceiverKind == DISPATCH_RECEIVER) }
        val extensionCallableReceiver = extensionReceiver?.let { toCallableReceiver(it, explicitReceiverKind == EXTENSION_RECEIVER) }
        val candidateDescriptor = towerCandidate.descriptor
        val diagnostics = SmartList<KotlinCallDiagnostic>()

        val (reflectionCandidateType, callableReferenceAdaptation) = buildReflectionType(
            candidateDescriptor,
            dispatchCallableReceiver,
            extensionCallableReceiver,
            expectedType,
            callComponents.builtIns,
            // conversions aren't needed for top-level callable references
            buildTypeWithConversions = kotlinCall is CallableReferenceKotlinCallArgument
        )

        fun createCallableReferenceCallCandidate(diagnostics: List<KotlinCallDiagnostic>) = CallableReferenceCallCandidate(
            candidateDescriptor, dispatchCallableReceiver, extensionCallableReceiver,
            explicitReceiverKind, reflectionCandidateType, callableReferenceAdaptation,
            kotlinCall, callComponents, scopeTower, resolutionCallbacks, expectedType, baseSystem
        ).also { diagnostics.forEach(it::addDiagnostic) }

        if (callComponents.statelessCallbacks.isHiddenInResolution(candidateDescriptor, kotlinCall.call, resolutionCallbacks)) {
            diagnostics.add(HiddenDescriptor)
            return createCallableReferenceCallCandidate(diagnostics)
        }

        if (needCompatibilityResolveForCallableReference(callableReferenceAdaptation, candidateDescriptor)) {
            markCandidateForCompatibilityResolve(diagnostics)
        }

        if (callableReferenceAdaptation != null && expectedType != null && hasNonTrivialAdaptation(callableReferenceAdaptation)) {
            if (!expectedType.isFunctionType && !expectedType.isSuspendFunctionType) { // expectedType has some reflection type
                diagnostics.add(AdaptedCallableReferenceIsUsedWithReflection(kotlinCall))
            }
        }

        if (callableReferenceAdaptation != null &&
            callableReferenceAdaptation.defaults != 0 &&
            !callComponents.languageVersionSettings.supportsFeature(LanguageFeature.FunctionReferenceWithDefaultValueAsOtherType)
        ) {
            diagnostics.add(CallableReferencesDefaultArgumentUsed(kotlinCall, candidateDescriptor, callableReferenceAdaptation.defaults))
        }

        if (candidateDescriptor !is CallableMemberDescriptor) {
            return createCallableReferenceCallCandidate(listOf(NotCallableMemberReference(kotlinCall, candidateDescriptor)))
        }

        diagnostics.addAll(towerCandidate.diagnostics)
        // todo smartcast on receiver diagnostic and CheckInstantiationOfAbstractClass

        return createCallableReferenceCallCandidate(diagnostics)
    }

    private fun needCompatibilityResolveForCallableReference(
        callableReferenceAdaptation: CallableReferenceAdaptation?,
        candidate: CallableDescriptor
    ): Boolean {
        // KT-13934: reference to companion object member via class name
        if (candidate.containingDeclaration.isCompanionObject() && kotlinCall.lhsResult is LHSResult.Type) return true

        if (callableReferenceAdaptation == null) return false

        return hasNonTrivialAdaptation(callableReferenceAdaptation)
    }

    private fun hasNonTrivialAdaptation(callableReferenceAdaptation: CallableReferenceAdaptation) =
        callableReferenceAdaptation.defaults != 0 ||
                callableReferenceAdaptation.suspendConversionStrategy != SuspendConversionStrategy.NO_CONVERSION ||
                callableReferenceAdaptation.coercionStrategy != CoercionStrategy.NO_COERCION ||
                callableReferenceAdaptation.mappedArguments.values.any { it is ResolvedCallArgument.VarargArgument }

    private enum class VarargMappingState {
        UNMAPPED, MAPPED_WITH_PLAIN_ARGS, MAPPED_WITH_ARRAY
    }

    private fun getCallableReferenceAdaptation(
        descriptor: FunctionDescriptor,
        expectedType: UnwrappedType?,
        unboundReceiverCount: Int,
        builtins: KotlinBuiltIns
    ): CallableReferenceAdaptation? {
        if (callComponents.languageVersionSettings.apiVersion < ApiVersion.KOTLIN_1_4) return null

        if (expectedType == null || TypeUtils.noExpectedType(expectedType)) return null

        // Do not adapt references against KCallable type as it's impossible to map defaults/vararg to absent parameters of KCallable
        if (ReflectionTypes.hasKCallableTypeFqName(expectedType)) return null

        val inputOutputTypes = extractInputOutputTypesFromCallableReferenceExpectedType(expectedType) ?: return null

        val expectedArgumentCount = inputOutputTypes.inputTypes.size - unboundReceiverCount
        if (expectedArgumentCount < 0) return null

        val fakeArguments = createFakeArgumentsForReference(descriptor, expectedArgumentCount, inputOutputTypes, unboundReceiverCount)
        val argumentMapping =
            callComponents.argumentsToParametersMapper.mapArguments(fakeArguments, externalArgument = null, descriptor = descriptor)
        if (argumentMapping.diagnostics.any { !it.candidateApplicability.isSuccess }) return null

        /**
         * (A, B, C) -> Unit
         * fun foo(a: A, b: B = B(), vararg c: C)
         */
        var defaults = 0
        var varargMappingState = VarargMappingState.UNMAPPED
        val mappedArguments = linkedMapOf<ValueParameterDescriptor, ResolvedCallArgument>()
        val mappedVarargElements = linkedMapOf<ValueParameterDescriptor, MutableList<KotlinCallArgument>>()
        val mappedArgumentTypes = arrayOfNulls<KotlinType?>(fakeArguments.size)

        for ((valueParameter, resolvedArgument) in argumentMapping.parameterToCallArgumentMap) {
            for (fakeArgument in resolvedArgument.arguments) {
                val index = (fakeArgument as FakeKotlinCallArgumentForCallableReference).index
                val substitutedParameter = descriptor.valueParameters.getOrNull(valueParameter.index) ?: continue

                val mappedArgument: KotlinType?
                if (substitutedParameter.isVararg) {
                    val (varargType, newVarargMappingState) = varargParameterTypeByExpectedParameter(
                        inputOutputTypes.inputTypes[index + unboundReceiverCount],
                        substitutedParameter,
                        varargMappingState,
                        builtins
                    )
                    varargMappingState = newVarargMappingState
                    mappedArgument = varargType

                    when (newVarargMappingState) {
                        VarargMappingState.MAPPED_WITH_ARRAY -> {
                            // If we've already mapped an argument to this value parameter, it'll always be a type mismatch.
                            mappedArguments[valueParameter] = ResolvedCallArgument.SimpleArgument(fakeArgument)
                        }
                        VarargMappingState.MAPPED_WITH_PLAIN_ARGS -> {
                            mappedVarargElements.getOrPut(valueParameter) { ArrayList() }.add(fakeArgument)
                        }
                        VarargMappingState.UNMAPPED -> {
                        }
                    }
                } else {
                    mappedArgument = substitutedParameter.type
                    mappedArguments[valueParameter] = resolvedArgument
                }

                mappedArgumentTypes[index] = mappedArgument
            }
            if (resolvedArgument == ResolvedCallArgument.DefaultArgument) {
                defaults++
                mappedArguments[valueParameter] = resolvedArgument
            }
        }
        if (mappedArgumentTypes.any { it == null }) return null

        for ((valueParameter, varargElements) in mappedVarargElements) {
            mappedArguments[valueParameter] = ResolvedCallArgument.VarargArgument(varargElements)
        }

        for (valueParameter in descriptor.valueParameters) {
            if (valueParameter.isVararg && valueParameter !in mappedArguments) {
                mappedArguments[valueParameter] = ResolvedCallArgument.VarargArgument(emptyList())
            }
        }

        // lower(Unit!) = Unit
        val returnExpectedType = inputOutputTypes.outputType

        val coercion =
            if (returnExpectedType.isUnit() && descriptor.returnType?.isUnit() == false)
                CoercionStrategy.COERCION_TO_UNIT
            else
                CoercionStrategy.NO_COERCION

        val adaptedArguments =
            if (ReflectionTypes.isBaseTypeForNumberedReferenceTypes(expectedType))
                emptyMap()
            else
                mappedArguments

        val suspendConversionStrategy =
            if (!descriptor.isSuspend && expectedType.isSuspendFunctionType) {
                SuspendConversionStrategy.SUSPEND_CONVERSION
            } else {
                SuspendConversionStrategy.NO_CONVERSION
            }

        return CallableReferenceAdaptation(
            @Suppress("UNCHECKED_CAST") (mappedArgumentTypes as Array<KotlinType>),
            coercion, defaults,
            adaptedArguments,
            suspendConversionStrategy
        )
    }

    private fun createFakeArgumentsForReference(
        descriptor: FunctionDescriptor,
        expectedArgumentCount: Int,
        inputOutputTypes: InputOutputTypes,
        unboundReceiverCount: Int
    ): List<FakeKotlinCallArgumentForCallableReference> {
        var afterVararg = false
        var varargComponentType: UnwrappedType? = null
        var vararg = false
        return (0 until expectedArgumentCount).map { index ->
            val inputType = inputOutputTypes.inputTypes.getOrNull(index + unboundReceiverCount)
            if (vararg && varargComponentType != inputType) {
                afterVararg = true
            }

            val valueParameter = descriptor.valueParameters.getOrNull(index)
            val name =
                if (afterVararg && valueParameter?.declaresDefaultValue() == true)
                    valueParameter.name
                else
                    null

            if (valueParameter?.isVararg == true) {
                varargComponentType = inputType
                vararg = true
            }
            FakeKotlinCallArgumentForCallableReference(index, name)
        }
    }

    private fun varargParameterTypeByExpectedParameter(
        expectedParameterType: KotlinType,
        substitutedParameter: ValueParameterDescriptor,
        varargMappingState: VarargMappingState,
        builtins: KotlinBuiltIns
    ): Pair<KotlinType?, VarargMappingState> {
        val elementType = substitutedParameter.varargElementType
            ?: error("Vararg parameter $substitutedParameter does not have vararg type")

        return when (varargMappingState) {
            VarargMappingState.UNMAPPED -> {
                if (KotlinBuiltIns.isArrayOrPrimitiveArray(expectedParameterType) ||
                    expectedParameterType.constructor is TypeVariableTypeConstructor
                ) {
                    val arrayType = builtins.getPrimitiveArrayKotlinTypeByPrimitiveKotlinType(elementType)
                        ?: builtins.getArrayType(Variance.OUT_VARIANCE, elementType)
                    arrayType to VarargMappingState.MAPPED_WITH_ARRAY
                } else {
                    elementType to VarargMappingState.MAPPED_WITH_PLAIN_ARGS
                }
            }
            VarargMappingState.MAPPED_WITH_PLAIN_ARGS -> {
                if (KotlinBuiltIns.isArrayOrPrimitiveArray(expectedParameterType))
                    null to VarargMappingState.MAPPED_WITH_PLAIN_ARGS
                else
                    elementType to VarargMappingState.MAPPED_WITH_PLAIN_ARGS
            }
            VarargMappingState.MAPPED_WITH_ARRAY ->
                null to VarargMappingState.MAPPED_WITH_ARRAY
        }
    }

    private fun buildReflectionType(
        descriptor: CallableDescriptor,
        dispatchReceiver: CallableReceiver?,
        extensionReceiver: CallableReceiver?,
        expectedType: UnwrappedType?,
        builtins: KotlinBuiltIns,
        buildTypeWithConversions: Boolean = true
    ): Pair<UnwrappedType, CallableReferenceAdaptation?> {
        val argumentsAndReceivers = ArrayList<KotlinType>(descriptor.valueParameters.size + 2)

        if (dispatchReceiver is CallableReceiver.UnboundReference) {
            argumentsAndReceivers.add(dispatchReceiver.receiver.stableType)
        }
        if (extensionReceiver is CallableReceiver.UnboundReference) {
            argumentsAndReceivers.add(extensionReceiver.receiver.stableType)
        }

        val descriptorReturnType = descriptor.returnType
            ?: ErrorUtils.createErrorType("Error return type for descriptor: $descriptor")

        return when (descriptor) {
            is PropertyDescriptor -> {
                val mutable = descriptor.isVar && run {
                    val setter = descriptor.setter
                    setter == null || DescriptorVisibilities.isVisible(
                        dispatchReceiver?.asReceiverValueForVisibilityChecks, setter,
                        scopeTower.lexicalScope.ownerDescriptor
                    )
                }

                callComponents.reflectionTypes.getKPropertyType(
                    Annotations.EMPTY,
                    argumentsAndReceivers,
                    descriptorReturnType,
                    mutable
                ) to null
            }
            is FunctionDescriptor -> {
                val callableReferenceAdaptation = getCallableReferenceAdaptation(
                    descriptor, expectedType,
                    unboundReceiverCount = argumentsAndReceivers.size,
                    builtins = builtins
                )

                val returnType = if (callableReferenceAdaptation == null || !buildTypeWithConversions) {
                    descriptor.valueParameters.mapTo(argumentsAndReceivers) { it.type }
                    descriptorReturnType
                } else {
                    val arguments = callableReferenceAdaptation.argumentTypes
                    val coercion = callableReferenceAdaptation.coercionStrategy
                    argumentsAndReceivers.addAll(arguments)

                    if (coercion == CoercionStrategy.COERCION_TO_UNIT)
                        descriptor.builtIns.unitType
                    else
                        descriptorReturnType
                }

                val suspendConversionStrategy = callableReferenceAdaptation?.suspendConversionStrategy
                val isSuspend = descriptor.isSuspend ||
                        (suspendConversionStrategy == SuspendConversionStrategy.SUSPEND_CONVERSION && buildTypeWithConversions)

                callComponents.reflectionTypes.getKFunctionType(
                    Annotations.EMPTY, null, argumentsAndReceivers, null,
                    returnType, descriptor.builtIns, isSuspend
                ) to callableReferenceAdaptation
            }
            else -> {
                assert(!descriptor.isSupportedForCallableReference()) { "${descriptor::class} isn't supported to use in callable references actually, but it's listed in `isSupportedForCallableReference` method" }
                ErrorUtils.createErrorType("Unsupported descriptor type: $descriptor") to null
            }
        }
    }

    private fun toCallableReceiver(receiver: ReceiverValueWithSmartCastInfo, isExplicit: Boolean): CallableReceiver {
        if (!isExplicit) return CallableReceiver.ScopeReceiver(receiver)

        return when (val lhsResult = kotlinCall.lhsResult) {
            is LHSResult.Expression -> CallableReceiver.ExplicitValueReceiver(receiver)
            is LHSResult.Type -> {
                if (lhsResult.qualifier?.classValueReceiver?.type == receiver.receiverValue.type) {
                    CallableReceiver.BoundValueReference(receiver)
                } else {
                    CallableReceiver.UnboundReference(receiver)
                }
            }
            is LHSResult.Object -> CallableReceiver.BoundValueReference(receiver)
            else -> throw IllegalStateException("Unsupported kind of lhsResult: $lhsResult")
        }
    }
}

data class InputOutputTypes(val inputTypes: List<UnwrappedType>, val outputType: UnwrappedType)

fun extractInputOutputTypesFromCallableReferenceExpectedType(expectedType: UnwrappedType?): InputOutputTypes? {
    if (expectedType == null) return null

    return when {
        expectedType.isFunctionType || expectedType.isSuspendFunctionType ->
            extractInputOutputTypesFromFunctionType(expectedType)

        ReflectionTypes.isBaseTypeForNumberedReferenceTypes(expectedType) ->
            InputOutputTypes(emptyList(), expectedType.arguments.single().type.unwrap())

        ReflectionTypes.isNumberedKFunction(expectedType) -> {
            val functionFromSupertype = expectedType.immediateSupertypes().first { it.isFunctionType }.unwrap()
            extractInputOutputTypesFromFunctionType(functionFromSupertype)
        }

        ReflectionTypes.isNumberedKSuspendFunction(expectedType) -> {
            val kSuspendFunctionType = expectedType.immediateSupertypes().first { it.isSuspendFunctionType }.unwrap()
            extractInputOutputTypesFromFunctionType(kSuspendFunctionType)
        }

        ReflectionTypes.isNumberedKPropertyOrKMutablePropertyType(expectedType) -> {
            val functionFromSupertype = expectedType.supertypes().first { it.isFunctionType }.unwrap()
            extractInputOutputTypesFromFunctionType(functionFromSupertype)
        }

        else -> null
    }
}

private fun extractInputOutputTypesFromFunctionType(functionType: UnwrappedType): InputOutputTypes {
    val receiver = functionType.getReceiverTypeFromFunctionType()?.unwrap()
    val parameters = functionType.getValueParameterTypesFromFunctionType().map { it.type.unwrap() }

    val inputTypes = listOfNotNull(receiver) + parameters
    val outputType = functionType.getReturnTypeFromFunctionType().unwrap()

    return InputOutputTypes(inputTypes, outputType)
}
