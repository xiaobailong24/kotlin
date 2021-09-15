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
import org.jetbrains.kotlin.resolve.calls.components.CreateFreshVariablesSubstitutor.createToFreshVariableSubstitutorAndAddInitialConstraints
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemOperation
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
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

interface CallableCandidate {
    val candidate: CallableDescriptor
    val numDefaults: Int
    val extensionReceiver: CallableReceiver?
    val dispatchReceiver: CallableReceiver?
    val reflectionCandidateType: UnwrappedType
    val freshSubstitutor: FreshVariableNewTypeSubstitutor?
    val explicitReceiverKind: ExplicitReceiverKind
    val callableReferenceAdaptation: CallableReferenceAdaptation?
}

class CallableReferenceCandidateForArgument(
    override val candidate: CallableDescriptor,
    override val dispatchReceiver: CallableReceiver?,
    override val extensionReceiver: CallableReceiver?,
    override val explicitReceiverKind: ExplicitReceiverKind,
    override val reflectionCandidateType: UnwrappedType,
    override val callableReferenceAdaptation: CallableReferenceAdaptation?,
    initialDiagnostics: List<KotlinCallDiagnostic>
) : Candidate, CallableCandidate {
    private val mutableDiagnostics = initialDiagnostics.toMutableList()
    val diagnostics: List<KotlinCallDiagnostic> = mutableDiagnostics

    override val resultingApplicability = getResultApplicability(diagnostics)

    override fun addCompatibilityWarning(other: Candidate) {
        if (this !== other && other is CallableReferenceCandidateForArgument) {
            mutableDiagnostics.add(CompatibilityWarning(other.candidate))
        }
    }

    override val isSuccessful get() = resultingApplicability.isSuccess

    override var freshSubstitutor: FreshVariableNewTypeSubstitutor? = null
        internal set

    override val numDefaults get() = callableReferenceAdaptation?.defaults ?: 0
}

/**
 * Suppose we have class A with staticM, memberM, memberExtM.
 * For A::staticM both receivers will be null
 * For A::memberM dispatchReceiver = UnboundReceiver, extensionReceiver = null
 * For a::memberExtM dispatchReceiver = ExplicitValueReceiver, extensionReceiver = ExplicitValueReceiver
 *
 * For class B with companion object B::companionM dispatchReceiver = BoundValueReference
 */
class CallableReferenceCandidate(
    override val candidate: CallableDescriptor,
    override val dispatchReceiver: CallableReceiver?,
    override val extensionReceiver: CallableReceiver?,
    override val explicitReceiverKind: ExplicitReceiverKind,
    override val reflectionCandidateType: UnwrappedType,
    override val callableReferenceAdaptation: CallableReferenceAdaptation?,
    initialDiagnostics: List<KotlinCallDiagnostic>,
    val kotlinCall: CallableReferenceResolutionAtom,
    override val callComponents: KotlinCallComponents,
    override val scopeTower: ImplicitScopeTower,
    override val resolutionCallbacks: KotlinResolutionCallbacks,
    override val callableReferenceResolver: CallableReferenceResolver,
    val expectedType: UnwrappedType?,
    override val resolutionSequence: List<ResolutionPart> = KotlinCallKind.CALLABLE_REFERENCE.resolutionSequence,
    private val baseSystem: NewConstraintSystem? = null,
    val diagnosticsHolder: KotlinDiagnosticsHolder? = null
) : ResolutionCandidate, CallableCandidate {
    private var newSystem: NewConstraintSystemImpl? = null

    override fun addResolvedKtPrimitive(resolvedAtom: ResolvedAtom) {
    }

    override val variableCandidateIfInvoke: ResolutionCandidate?
        get() = callComponents.statelessCallbacks.getVariableCandidateIfInvoke(resolvedCall.atom)
    override val knownTypeParametersResultingSubstitutor: TypeSubstitutor? = null

    override val resolvedCall: MutableResolvedCallAtom =
        when (kotlinCall) {
            is CallableReferenceCall -> MutableResolvedCallAtom(
                kotlinCall.call,
                candidate,
                explicitReceiverKind,
                if (dispatchReceiver != null) ReceiverExpressionKotlinCallArgument(dispatchReceiver.receiver) else null,
                if (extensionReceiver != null) ReceiverExpressionKotlinCallArgument(extensionReceiver.receiver) else null,
                reflectionCandidateType,
                this
            ).apply { this.setEmptyAnalyzedResults() }
            is CallableReferenceKotlinCallArgument -> MutableResolvedCallAtom(
                    kotlinCall.kotlinCall,
                    candidate,
                    explicitReceiverKind,
                    if (dispatchReceiver != null) ReceiverExpressionKotlinCallArgument(dispatchReceiver.receiver) else null,
                    if (extensionReceiver != null) ReceiverExpressionKotlinCallArgument(extensionReceiver.receiver) else null,
                    reflectionCandidateType,
                    this
                ).apply { this.setEmptyAnalyzedResults() }
        }

    override fun getSystem(): NewConstraintSystem {
//        if (baseSystem != null) return baseSystem
        if (newSystem == null) {
            newSystem = NewConstraintSystemImpl(callComponents.constraintInjector, callComponents.builtIns, callComponents.kotlinTypeRefiner)
            if (baseSystem != null) {
                newSystem!!.addOtherSystem(baseSystem.getBuilder().currentStorage())
            }
        }
        return newSystem!!
    }

    override val diagnosticsFromResolutionParts: MutableList<KotlinCallDiagnostic> = mutableListOf()

    override fun getSubResolvedAtoms(): List<ResolvedAtom> = emptyList()

    private val mutableDiagnostics = initialDiagnostics.toMutableList()
    val diagnostics: List<KotlinCallDiagnostic> = mutableDiagnostics

    override fun addCompatibilityWarning(other: Candidate) {
        if (this !== other && other is CallableReferenceCandidate) {
            mutableDiagnostics.add(CompatibilityWarning(other.candidate))
        }
    }

    override fun addDiagnostic(diagnostic: KotlinCallDiagnostic) {
        diagnosticsFromResolutionParts.add(diagnostic)
    }

    private val stepCount = resolutionSequence.sumOf { it.run { workCount() } }
    private var step = 0

    private fun processParts(stopOnFirstError: Boolean) {
        if (stopOnFirstError && step > 0) return // error already happened
        if (step == stepCount) return

        var partIndex = 0
        var workStep = step
        while (workStep > 0) {
            val workCount = resolutionSequence[partIndex].run { workCount() }
            if (workStep >= workCount) {
                partIndex++
                workStep -= workCount
            } else {
                break
            }
        }
        if (partIndex < resolutionSequence.size) {
            if (processPart(resolutionSequence[partIndex], stopOnFirstError, workStep)) return
            partIndex++
        }

        while (partIndex < resolutionSequence.size) {
            if (processPart(resolutionSequence[partIndex], stopOnFirstError)) return
            partIndex++
        }
    }

    // true if part was interrupted
    private fun processPart(part: ResolutionPart, stopOnFirstError: Boolean, startWorkIndex: Int = 0): Boolean {
        for (workIndex in startWorkIndex until (part.run { workCount() })) {
            if (stopOnFirstError) return true

            part.run { process(workIndex) }
            step++
        }
        return false
    }

    override val isSuccessful: Boolean
        get() {
            processParts(stopOnFirstError = true)
            val z = listOf(getResultApplicability(diagnostics + diagnosticsFromResolutionParts), getResultApplicability(getSystem().errors)).minOrNull()!!.isSuccess
            return z && !getSystem().hasContradiction
        }

    override val resultingApplicability: CandidateApplicability
        get() {
            processParts(stopOnFirstError = false)

            return listOf(getResultApplicability(diagnostics + diagnosticsFromResolutionParts), getResultApplicability(getSystem().errors)).minOrNull()!!
        }

    override var freshSubstitutor: FreshVariableNewTypeSubstitutor? = null
        internal set

    override val numDefaults get() = callableReferenceAdaptation?.defaults ?: 0
}

class CallableReferenceAdaptation(
    val argumentTypes: Array<KotlinType>,
    val coercionStrategy: CoercionStrategy,
    val defaults: Int,
    val mappedArguments: Map<ValueParameterDescriptor, ResolvedCallArgument>,
    val suspendConversionStrategy: SuspendConversionStrategy
)

fun createCallableReferenceProcessor(factory: CallableReferencesCandidateFactory): ScopeTowerProcessor<CallableReferenceCandidate> {
    val lhsResult = factory.kotlinCall.lhsResult
    when (lhsResult) {
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

fun ConstraintSystemOperation.checkCallableReference(
    dispatchReceiver: CallableReceiver?,
    extensionReceiver: CallableReceiver?,
    candidateDescriptor: CallableDescriptor,
    reflectionCandidateType: UnwrappedType,
    expectedType: UnwrappedType?,
    ownerDescriptor: DeclarationDescriptor,
    substitutor: FreshVariableNewTypeSubstitutor? = null,
    argument: CallableReferenceResolutionAtom? = null,
): Pair<FreshVariableNewTypeSubstitutor, KotlinCallDiagnostic?> {
    val position = if (argument is CallableReferenceKotlinCallArgument) {
        ArgumentConstraintPositionImpl(argument as KotlinCallArgument)
    } else {
        CallableReferenceConstraintPositionImpl()
    }

    val toFreshSubstitutor = substitutor ?: createToFreshVariableSubstitutorAndAddInitialConstraints(candidateDescriptor, this)

    if (!ErrorUtils.isError(candidateDescriptor)) {
        addReceiverConstraint(toFreshSubstitutor, dispatchReceiver, candidateDescriptor.dispatchReceiverParameter, position)
        addReceiverConstraint(toFreshSubstitutor, extensionReceiver, candidateDescriptor.extensionReceiverParameter, position)
    }

    if (expectedType != null && !hasContradiction) {
        addSubtypeConstraint(toFreshSubstitutor.safeSubstitute(reflectionCandidateType), expectedType, position)
    }

    val invisibleMember = DescriptorVisibilities.findInvisibleMember(
        dispatchReceiver?.asReceiverValueForVisibilityChecks,
        candidateDescriptor, ownerDescriptor
    )
    return toFreshSubstitutor to invisibleMember?.let(::VisibilityError)
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
    val kotlinCall: CallableReferenceResolutionAtom, // CallableReferenceResolutionAtom?
    val callComponents: KotlinCallComponents,
    val scopeTower: ImplicitScopeTower,
    val expectedType: UnwrappedType?,
    private val resolutionCallbacks: KotlinResolutionCallbacks,
    val callableReferenceResolver: CallableReferenceResolver,
    val resolvedAtom: ResolvedCallableReferenceAtom? = null,
    val baseSystem: NewConstraintSystem?,
    val diagnosticsHolder: KotlinDiagnosticsHolder?
) : CandidateFactory<CallableReferenceCandidate> {
    override fun createErrorCandidate(): CallableReferenceCandidate {
        val errorScope = ErrorUtils.createErrorScope("Error resolution candidate for call $kotlinCall")
        val errorDescriptor = errorScope.getContributedFunctions(kotlinCall.rhsName, scopeTower.location).first()

        val (reflectionCandidateType, callableReferenceAdaptation) = buildReflectionTypeWithoutAdaptation(
            errorDescriptor,
            null,
            null,
            expectedType,
            callComponents.builtIns
        )

        return CallableReferenceCandidate(
            errorDescriptor, null, null,
            ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, reflectionCandidateType, callableReferenceAdaptation, SmartList(),
            kotlinCall, callComponents, scopeTower, resolutionCallbacks, callableReferenceResolver, expectedType
        )
    }

    fun createCallableProcessor(explicitReceiver: DetailedReceiver?) =
        createCallableReferenceProcessor(scopeTower, kotlinCall.rhsName, this, explicitReceiver)

    override fun createCandidate(
        towerCandidate: CandidateWithBoundDispatchReceiver,
        explicitReceiverKind: ExplicitReceiverKind,
        extensionReceiver: ReceiverValueWithSmartCastInfo?
    ): CallableReferenceCandidate {
        val dispatchCallableReceiver =
            towerCandidate.dispatchReceiver?.let { toCallableReceiver(it, explicitReceiverKind == DISPATCH_RECEIVER) }
        val extensionCallableReceiver = extensionReceiver?.let { toCallableReceiver(it, explicitReceiverKind == EXTENSION_RECEIVER) }
        val candidateDescriptor = towerCandidate.descriptor
        val diagnostics = SmartList<KotlinCallDiagnostic>()

        val (reflectionCandidateType, callableReferenceAdaptation) = if (kotlinCall is CallableReferenceKotlinCallArgument) {
            buildReflectionType(
                candidateDescriptor,
                dispatchCallableReceiver,
                extensionCallableReceiver,
                expectedType,
                callComponents.builtIns
            )
        } else {
            buildReflectionTypeWithoutAdaptation(
                candidateDescriptor,
                dispatchCallableReceiver,
                extensionCallableReceiver,
                expectedType,
                callComponents.builtIns
            )
        }

        fun createReferenceCandidate(): CallableReferenceCandidate =
            CallableReferenceCandidate(
                candidateDescriptor, dispatchCallableReceiver, extensionCallableReceiver,
                explicitReceiverKind, reflectionCandidateType, callableReferenceAdaptation, diagnostics,
                kotlinCall, callComponents, scopeTower, resolutionCallbacks, callableReferenceResolver, expectedType, baseSystem = baseSystem, diagnosticsHolder = diagnosticsHolder
            )

        if (kotlinCall is CallableReferenceCall) {
            if (callComponents.statelessCallbacks.isHiddenInResolution(candidateDescriptor, kotlinCall.call, resolutionCallbacks)) {
                diagnostics.add(HiddenDescriptor)
                return createReferenceCandidate()
            }
        } else if (kotlinCall is CallableReferenceKotlinCallArgument) {
            if (callComponents.statelessCallbacks.isHiddenInResolution(candidateDescriptor, kotlinCall, resolutionCallbacks)) {
                diagnostics.add(HiddenDescriptor)
                return createReferenceCandidate()
            }
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
            return CallableReferenceCandidate(
                candidateDescriptor, dispatchCallableReceiver, extensionCallableReceiver,
                explicitReceiverKind, reflectionCandidateType, callableReferenceAdaptation,
                listOf(NotCallableMemberReference(kotlinCall, candidateDescriptor)), kotlinCall, callComponents, scopeTower, resolutionCallbacks,
                callableReferenceResolver, expectedType, baseSystem = baseSystem, diagnosticsHolder = diagnosticsHolder
            )
        }

        diagnostics.addAll(towerCandidate.diagnostics)
        // todo smartcast on receiver diagnostic and CheckInstantiationOfAbstractClass

        return createReferenceCandidate()
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
        builtins: KotlinBuiltIns
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

                val returnType = if (callableReferenceAdaptation == null) {
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
                val isSuspend = descriptor.isSuspend || suspendConversionStrategy == SuspendConversionStrategy.SUSPEND_CONVERSION

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

    fun buildReflectionTypeWithoutAdaptation(
        descriptor: CallableDescriptor,
        dispatchReceiver: CallableReceiver?,
        extensionReceiver: CallableReceiver?,
        expectedType: UnwrappedType?,
        builtins: KotlinBuiltIns
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

                descriptor.valueParameters.mapTo(argumentsAndReceivers) { it.type }
                val returnType = descriptorReturnType

                val isSuspend = descriptor.isSuspend

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
