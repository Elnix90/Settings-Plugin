package io.github.elnix90.settings.ir.transformers

import io.github.elnix90.settings.PluginDebug
import io.github.elnix90.settings.util.ClassIds.allSettingUsagesAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.kotlinListClassId
import io.github.elnix90.settings.util.ClassIds.settingCallAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingEntryAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingHiddenAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingObjectClassId
import io.github.elnix90.settings.util.ClassIds.settingScreenAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageObjectClassId
import io.github.elnix90.settings.util.ClassIds.settingUsageOtherClassId
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.SimpleTypeNullability
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

/**
 * IR transformer that implements settings usage discovery for `@AllSettingUsages`.
 *
 * Mirrors the structure of [io.github.elnix90.settings.ir.generators.AllStoresGenerator]:
 * the FIR phase declares nothing; the annotated property is written by hand as
 *
 * ```
 * @AllSettingUsages
 * val usages: List<SettingUsage> = emptyList()
 * ```
 *
 * and this transformer replaces its initializer with a generated
 * `listOf(SettingUsage.Object(...), SettingUsage.Entry(...), ...)` built from
 * every call site of a function annotated with `@SettingCall` or
 * `@SettingEntry` in the current module.
 *
 * For each recorded `@SettingCall` call site:
 *
 * 1. The argument passed for the parameter of type `SettingObject<*, *>`
 *    (falling back to the parameter named `setting`) is the usage.
 * 2. The nearest enclosing function annotated with `@SettingScreen` - or,
 *    failing that, the nearest enclosing non-anonymous function - is recorded
 *    as the screen (`screenFqName`) used for navigation from search results.
 *    When an annotated screen is found, its route object (a `data object`
 *    implementing the app's `NavKey`) is recorded as the usage's `screenRoute`
 *    so search results navigate directly, with no name-to-route mapping.
 * 3. The argument is classified:
 *    - **Static** - a property getter call on an object
 *      (`Setting(MyStore.enabled)`) produces a direct `SettingUsage.Object`
 *      entry that also knows the owning store (`storeFqName`).
 *    - **Dynamic** - any other expression (`Setting(if (x) a else b)`) is
 *      wrapped in a `() -> SettingObject` provider lambda, resolved at runtime.
 *    - **Skipped** - dynamic expressions that capture local state (parameters
 *      or variables of the enclosing function) cannot be re-evaluated from the
 *      module-level index, so they are left out. This is silent by design: a
 *      valid dynamic usage never fails the compilation.
 *
 * For each recorded `@SettingEntry` call site the arguments passed for the
 * metadata parameters (`title`, `description`, `icon`) are snapshotted into a
 * `SettingUsage.Entry` entry, together with the same screen information; the
 * call-site argument is used when present, else the callee's default value.
 * Only statically evaluable expressions (constants, `R` field reads,
 * parameterless property getters) are inlined into the generated list, so the
 * index costs one field read at initialization and nothing per search. An
 * entry without a readable title (or with a `0` title) and metadata that
 * captures local state are skipped rather than recorded incomplete.
 *
 * The initializer is only replaced when the property is actually typed
 * `List<SettingUsage>`; otherwise generation is skipped and the
 * `ALL_SETTING_USAGES_WRONG_TYPE` diagnostic (or an explicit `@Suppress` of it)
 * is the only signal.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class SettingUsageCollector(
	private val ctx: IrPluginContext
) {
	/**
	 * Discovers every `@SettingCall` usage of the module and fills the value of
	 * the single `@AllSettingUsages` property with the generated list.
	 */
	fun generateAllSettingUsagesPropertyBody(
		module: IrModuleFragment
	) {
		val usageHolders =
			module
				.files
				.flatMap { it.declarations }
				.filterIsInstance<IrProperty>()
				.filter { it.hasAnnotation(allSettingUsagesAnnotationClassId) }

		if (usageHolders.isEmpty()) {
			PluginDebug.log("module ${module.name}: no @AllSettingUsages holder, nothing generated")
			return
		}
		if (usageHolders.size > 1) {
			throw IllegalStateException("There must be at most 1 @AllSettingUsages value")
		}

		val holder = usageHolders.first()
		val holderField = holder.backingField ?: return

		// The property may be mistyped when the ALL_SETTING_USAGES_WRONG_TYPE
		// diagnostic was explicitly suppressed; never inject wrong-typed data.
		if (!holderField.type.isSettingUsageList()) return

		val settingUsageClass =
			ctx.finderForBuiltins().findClass(settingUsageClassId)
				?: return

		// Without the runtime search types on the classpath nothing can be
		// generated; degrade to the declared emptyList() instead of crashing.
		val settingObjectClass =
			ctx.finderForBuiltins().findClass(settingObjectClassId)
				?: return

		// WHY IS THIS NOT USED?
		val settingUsageObjectClass =
			ctx.finderForBuiltins().findClass(settingUsageObjectClassId)
				?: return

		// An older runtime without SettingUsage.Entry still indexes
		// SettingObject-backed usages; manual entries are dropped instead.
		val settingUsageOtherClass = ctx.finderForBuiltins().findClass(settingUsageOtherClassId)

		val usageType =
			IrSimpleTypeImpl(
				classifier = settingUsageClass,
				hasQuestionMark = false,
				arguments = emptyList(),
				annotations = emptyList()
			)

		val parent = holder.parent
		val usages = collectUsages(module)

		val objectConstructors = ctx.finderForBuiltins().findConstructors(settingUsageObjectClassId)
		val primaryConstructor = objectConstructors.firstOrNull { it.owner.isPrimary } ?: return
		val staticConstructor = objectConstructors.firstOrNull { !it.owner.isPrimary }

		val otherConstructor =
			settingUsageOtherClass
				?.let { ctx.finderForBuiltins().findConstructors(settingUsageOtherClassId).firstOrNull { ctor -> ctor.owner.isPrimary } }

		val elements =
			usages.mapNotNull { usage ->
				when (usage) {
					is CollectedUsage.Static -> {
						usage.toSettingUsageExpression(
							primaryConstructor = primaryConstructor,
							staticConstructor = staticConstructor,
							usageType = usageType,
							settingObjectClass = settingObjectClass,
							parent = parent
						)
					}

					is CollectedUsage.Dynamic -> {
						usage.toSettingUsageExpression(
							primaryConstructor = primaryConstructor,
							usageType = usageType,
							settingObjectClass = settingObjectClass,
							parent = parent
						)
					}

					is CollectedUsage.Entry -> {
						// Without the runtime class for manual entries there is
						// nothing to construct; skip rather than emit broken IR.
						val constructor = otherConstructor ?: return@mapNotNull null
						usage.toSettingUsageExpression(constructor, usageType)
					}
				}
			}

		if (PluginDebug.isEnabled) {
			val otherCollected = usages.count { it is CollectedUsage.Entry }
			PluginDebug.log(
				"index ${holder.fqNameWhenAvailable}: collected=${usages.size} " +
					"(settingCalls=${usages.size - otherCollected}, entries=$otherCollected) " +
					"-> emitted=${elements.size}" +
					(
						if (otherCollected > 0 && otherConstructor == null) {
							" [DROPPED all $otherCollected @SettingEntry usages: " +
								"SettingUsage.Entry constructor not found on classpath]"
						} else {
							""
						}
					)
			)
		}

		val listOfSymbol =
			ctx
				.finderForBuiltins()
				.findFunctions(
					CallableId(
						FqName("kotlin.collections"),
						Name.identifier("listOf")
					)
				).first {
					it.owner.parameters
						.singleOrNull()
						?.isVararg == true
				}

		val call =
			IrCallImpl(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				type = holderField.type,
				origin = null,
				symbol = listOfSymbol,
				superQualifierSymbol = null
			)

		val varargParameter = listOfSymbol.owner.parameters.single()

		val vararg =
			IrVarargImpl(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				type = varargParameter.type,
				varargElementType = usageType,
				elements = elements
			)

		// THIS IS NEEDED!! otherwise the resolved type doesn't work and everything's messed up
		call.typeArguments[0] = usageType
		call.arguments[0] = vararg

		holderField.initializer = ctx.irFactory.createExpressionBody(call)
	}

	/**
	 * Walks every function body of the module and records each call site of a
	 * `@SettingCall` or `@SettingEntry` function, together with the screen it
	 * is rendered on.
	 */
	private fun collectUsages(
		module: IrModuleFragment
	): List<CollectedUsage> {
		val usages = mutableListOf<CollectedUsage>()
		val functionStack = mutableListOf<IrFunction>()
		val screenRanges = module.collectScreenRanges()

		module.accept(
			object : IrVisitorVoid() {
				private var currentFile: IrFile? = null
				private var callsInFile: Int = 0
				private var entryMatchesInFile: Int = 0

				override fun visitElement(element: IrElement) {
					element.acceptChildren(this, null)
				}

				override fun visitFile(element: IrFile) {
					currentFile = element
					callsInFile = 0
					entryMatchesInFile = 0
					super.visitFile(element)
					// Per-file stats expose the silent blind spot: a call site
					// whose callee lost its @SettingEntry annotation matches
					// nothing and never reaches recordEntry's own logs.
					PluginDebug.log(
						"file ${element.fileEntry.name}: walked $callsInFile calls, " +
							"$entryMatchesInFile @SettingEntry callee matches"
					)
					currentFile = null
				}

				override fun visitFunction(declaration: IrFunction) {
					functionStack += declaration
					super.visitFunction(declaration)
					functionStack.removeLast()
				}

				override fun visitCall(expression: IrCall) {
					val callee = expression.symbol.owner
					callsInFile++

					// @SettingHidden on any enclosing function excludes every
					// setting rendered inside it, wherever it is nested.
					val hiddenByFunction =
						functionStack.any { it.hasAnnotation(settingHiddenAnnotationClassId) }

					if (!hiddenByFunction) {
						when {
							callee.hasAnnotation(settingCallAnnotationClassId) -> {
								val screen = screenInfo(functionStack, currentFile, expression.startOffset, screenRanges)
								recordUsage(expression, expression.symbol, screen)
									?.let { usages += it }
							}

							callee.hasAnnotation(settingEntryAnnotationClassId) -> {
								entryMatchesInFile++
								if (PluginDebug.isEnabled) {
									val fileEntry = currentFile?.fileEntry
									val line = fileEntry
										?.let { it.getLineNumber(expression.startOffset) + 1 }
										?: -1
									PluginDebug.log(
										"entry CALL ${callee.fqNameWhenAvailable} at " +
											"${fileEntry?.name ?: "?"}:$line " +
											"args=${expression.arguments.map { arg -> arg?.let { it::class.simpleName } ?: "null" }}"
									)
								}
								val screen = screenInfo(functionStack, currentFile, expression.startOffset, screenRanges)
								recordEntry(expression, screen)
									?.let { usages += it }
							}
						}
					}

					super.visitCall(expression)
				}
			},
			null
		)

		return usages
	}

	/**
	 * Extracts the recorded setting argument of a `@SettingCall` invocation and
	 * classifies it as static or dynamic. Returns null when the argument is
	 * absent (default parameter value) or captures local state.
	 */
	private fun recordUsage(
		expression: IrCall,
		callee: IrSimpleFunctionSymbol,
		screen: ScreenInfo
	): CollectedUsage? {
		val settingIndex =
			callee
				.owner
				.parameters
				.indexOfFirst { it.isSettingObjectParameter() }
				.takeIf { it >= 0 }
				?: callee.owner.parameters.indexOfFirst { it.name.asString() == "setting" }

		if (settingIndex < 0) return null

		val argument = expression.arguments.getOrNull(settingIndex) ?: return null

		if (argument is IrCall) {
			val getter = argument.symbol.owner
			val receiver = argument.dispatchReceiver

			if (getter.correspondingPropertySymbol != null && receiver is IrGetObjectValue) {
				// @SettingHidden on the store property excludes this setting
				// everywhere it is rendered.
				val isHidden =
					getter
						.correspondingPropertySymbol
						?.owner
						?.hasAnnotation(settingHiddenAnnotationClassId) == true

				if (isHidden) return null

				return CollectedUsage.Static(
					getter = argument.symbol,
					storeSymbol = receiver.symbol,
					argumentType = argument.type,
					screenFqName = screen.fqName,
					routeClass = screen.routeClass
				)
			}
		}

		if (argument.containsLocalCapture()) return null

		return CollectedUsage.Dynamic(
			argument = argument,
			screenFqName = screen.fqName,
			routeClass = screen.routeClass
		)
	}

	/**
	 * Snapshots the metadata parameters (`title`, `description`, `icon`) of a
	 * `@SettingEntry` call site into a manual entry.
	 *
	 * Each metadata value is the call-site argument when present, else the
	 * callee's default value. Returns null when no statically evaluable title
	 * exists - no such parameter, an omitted argument of an external
	 * declaration, a `0` title, or an expression that cannot be evaluated once
	 * at index initialization. The entry is then skipped (the checker already
	 * reports a missing `title: Int` parameter at its declaration).
	 */
	private fun recordEntry(
		expression: IrCall,
		screen: ScreenInfo
	): CollectedUsage? {
		val callee = expression.symbol

		val title =
			expression
				.metadataArgument(callee, TITLE_PARAMETER_NAME)
				?.takeUnless { it is IrConst && it.value == 0 }
				?: run {
					PluginDebug.log(
						"entry SKIP ${callee.owner.fqNameWhenAvailable} on ${screen.fqName}: " +
							"no statically evaluable title " +
							"(argOrNull=${expression.arguments.getOrNull(0)}, " +
							"hasDefault=${callee.owner.parameters.any { it.defaultValue != null }}, "
					)
					return null
				}

		val description = expression.metadataArgument(callee, DESCRIPTION_PARAMETER_NAME) ?: intConstant(0)
		val icon = expression.metadataArgument(callee, ICON_PARAMETER_NAME) ?: intConstant(0)

		return CollectedUsage.Entry(
			title = title,
			description = description,
			icon = icon,
			screenFqName = screen.fqName,
			routeClass = screen.routeClass
		)
	}

	/**
	 * The value passed for the metadata parameter named [name]: the call-site
	 * argument when present, else the callee's default value (readable for
	 * declarations compiled in this module - for `@Composable` functions,
	 * recovered from the body where the Compose transform moved it; an omitted
	 * argument of an external declaration has no readable default and yields
	 * null).
	 *
	 * The expression is deep-copied for embedding into the module-level index
	 * only when [isStaticIntResource] accepts it. Local captures, branches and
	 * calls with arguments would mean different values than at the render site
	 * (or none at all), so null is returned and the caller skips - silent by
	 * design: a valid annotation never fails the build.
	 */
	private fun IrCall.metadataArgument(
		callee: IrSimpleFunctionSymbol,
		name: Name
	): IrExpression? {
		val index = callee.owner.parameters.indexOfFirst { it.name == name }
		if (index < 0) {
			PluginDebug.log("meta SKIP ${callee.owner.fqNameWhenAvailable}: no parameter named '$name'")
			return null
		}

		val parameter = callee.owner.parameters[index]
		val argument = arguments.getOrNull(index)
		val defaultExpression = parameter.defaultValue?.expression
		// The Compose transform of @Composable functions strips `defaultValue`
		// from the parameters and fills omitted call-site slots with a
		// placeholder composite (origin DEFAULT_VALUE); the real default is
		// assigned to the parameter inside the callee body under the
		// `$default` mask, so it is recovered from there instead.
		val expression =
			when {
				argument is IrComposite && argument.origin == IrStatementOrigin.DEFAULT_VALUE -> {
					callee.bodyDefault(name)
				}

				argument != null -> {
					argument
				}

				else -> {
					defaultExpression ?: callee.bodyDefault(name)
				}
			}
		if (expression == null) {
			if (PluginDebug.isEnabled) {
				PluginDebug.log(
					"meta SKIP ${callee.owner.fqNameWhenAvailable}.$name: no usable value " +
						"(arg=${argument?.let { it::class.simpleName } ?: "none"}, " +
						"paramDefault=${defaultExpression != null})\n" +
						(
							callee.owner.body
								?.dump()
								?.take(2000) ?: "<no body>"
						).prependIndent("    ")
				)
			}
			return null
		}

		if (!expression.isStaticIntResource()) {
			if (PluginDebug.isEnabled) {
				// The IR tree itself is the decisive evidence: class names
				// alone never told anyone why an expression was rejected.
				PluginDebug.log(
					"meta SKIP ${callee.owner.fqNameWhenAvailable}.$name: not statically evaluable, " +
						"source=${if (argument != null) "call-site" else "default"}, " +
						"expr=${expression::class.simpleName}\n" +
						expression.dump().prependIndent("    ").take(3000)
				)
			}
			return null
		}

		return expression.deepCopyWithSymbols()
	}

	/**
	 * The default expression for parameter [name] recovered from the callee's
	 * body.
	 *
	 * For `@Composable` functions the Compose transform removes `defaultValue`
	 * from the parameters and instead assigns the default to the parameter at
	 * the top of the body, guarded by the `$default` mask - so the value lives
	 * in an `IrSetValue` targeting the parameter. Returns the still-attached
	 * expression (deep-copied by the caller) or null when the body holds no
	 * such assignment; external declarations have no body at all.
	 */
	private fun IrSimpleFunctionSymbol.bodyDefault(name: Name): IrExpression? {
		var recovered: IrExpression? = null
		owner.body?.accept(
			object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					element.acceptChildren(this, null)
				}

				override fun visitSetValue(expression: IrSetValue) {
					val target = expression.symbol.owner
					if (recovered == null && target is IrValueParameter && target.name == name) {
						recovered = expression.value
					}
					super.visitSetValue(expression)
				}
			},
			null
		)
		return recovered
	}

	/**
	 * True when the expression produces a non-null `Int` and can be evaluated
	 * once at index initialization without changing semantics: constants,
	 * `R` field reads and parameterless property getters, with any receiver
	 * subject to the same rule. Everything else - local captures (`this`
	 * included), branches, calls with arguments, nullable or non-`Int` values -
	 * is rejected, because the value would either be wrong or unrepresentable
	 * in the generated `SettingUsage.Entry(title: Int, ...)` call.
	 */
	private fun IrExpression.isStaticIntResource(): Boolean {
		val expressionType = type as? IrSimpleType ?: return false
		if (expressionType.nullability == SimpleTypeNullability.MARKED_NULLABLE) return false
		if ((expressionType.classifier as? IrClassSymbol)?.owner?.classId != INT_CLASS_ID) return false

		var staticallyEvaluable = true

		accept(
			object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					val allowed =
						element is IrConst ||
							element is IrGetField ||
							element is IrGetObjectValue ||
							(
								element is IrCall &&
									element.symbol.owner.correspondingPropertySymbol != null
							)

					if (!allowed) {
						staticallyEvaluable = false
						return
					}

					element.acceptChildren(this, null)
				}
			},
			null
		)

		return staticallyEvaluable
	}

	/** Builds `SettingUsage.Object(MyStore.setting, screen, store, route)`. */
	private fun CollectedUsage.Static.toSettingUsageExpression(
		primaryConstructor: IrConstructorSymbol,
		staticConstructor: IrConstructorSymbol?,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression {
		val storeType = storeSymbol.owner.defaultType

		val settingExpression =
			IrCallImpl(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				type = argumentType,
				origin = null,
				symbol = getter,
				superQualifierSymbol = null
			).apply {
				dispatchReceiver =
					IrGetObjectValueImpl(
						startOffset = UNDEFINED_OFFSET,
						endOffset = UNDEFINED_OFFSET,
						type = storeType,
						symbol = storeSymbol
					)
			}

		// Without the dedicated (setting, screen, store, route) constructor the
		// static entry degrades to a provider lambda - still correct, just lazy.
		if (staticConstructor == null) {
			return primaryConstructor.callWithProvider(settingExpression, usageType, settingObjectClass, parent, screenFqName, storeFqName, routeClass)
		}

		return staticConstructor.callWithSetting(settingExpression, usageType, screenFqName, storeFqName, routeClass)
	}

	/** Builds `SettingUsage.Object({ argument }, screen, "", route)`. */
	private fun CollectedUsage.Dynamic.toSettingUsageExpression(
		primaryConstructor: IrConstructorSymbol,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression =
		primaryConstructor.callWithProvider(argument, usageType, settingObjectClass, parent, screenFqName, storeFqName = "", routeClass = routeClass)

	/**
	 * Builds `SettingUsage.Entry(title, description, icon, screen, route)`,
	 * deep-copying the metadata expressions so the call site keeps its own nodes.
	 */
	private fun CollectedUsage.Entry.toSettingUsageExpression(
		constructor: IrConstructorSymbol,
		usageType: IrType
	): IrExpression =
		IrConstructorCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = usageType,
			symbol = constructor,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0
		).apply {
			arguments[0] = title.deepCopyWithSymbols()
			arguments[1] = description.deepCopyWithSymbols()
			arguments[2] = icon.deepCopyWithSymbols()
			arguments[3] = stringConstant(screenFqName)
			arguments[4] = routeClass.routeExpression()
		}

	private fun IrConstructorSymbol.callWithSetting(
		setting: IrExpression,
		usageType: IrType,
		screenFqName: String,
		storeFqName: String,
		routeClass: IrClassSymbol?
	): IrExpression =
		IrConstructorCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = usageType,
			symbol = this,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0
		).apply {
			arguments[0] = setting
			arguments[1] = stringConstant(screenFqName)
			arguments[2] = stringConstant(storeFqName)
			arguments[3] = routeClass.routeExpression()
		}

	private fun IrConstructorSymbol.callWithProvider(
		argument: IrExpression,
		usageType: IrType,
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent,
		screenFqName: String,
		storeFqName: String,
		routeClass: IrClassSymbol?
	): IrExpression =
		IrConstructorCallImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = usageType,
			symbol = this,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0
		).apply {
			arguments[0] = argument.createProviderLambda(settingObjectClass, parent)
			arguments[1] = stringConstant(screenFqName)
			arguments[2] = stringConstant(storeFqName)
			arguments[3] = routeClass.routeExpression()
		}

	/**
	 * Wraps this expression in a `() -> SettingObject<*, *>` lambda. The
	 * expression is deep-copied so the original call site keeps its own node.
	 */
	private fun IrExpression.createProviderLambda(
		settingObjectClass: IrClassSymbol,
		parent: IrDeclarationParent
	): IrExpression {
		val settingObjectType =
			IrSimpleTypeImpl(
				classifier = settingObjectClass,
				hasQuestionMark = false,
				arguments = listOf(IrStarProjectionImpl, IrStarProjectionImpl),
				annotations = emptyList()
			)

		val lambdaType =
			IrSimpleTypeImpl(
				classifier = ctx.irBuiltIns.functionN(0).symbol,
				hasQuestionMark = false,
				arguments = listOf(makeTypeProjection(settingObjectType, Variance.INVARIANT)),
				annotations = emptyList()
			)

		val lambda =
			ctx.irFactory.createSimpleFunction(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA,
				name = Name.special("<anonymous>"),
				visibility = DescriptorVisibilities.LOCAL,
				isInline = false,
				isExpect = false,
				returnType = settingObjectType,
				modality = Modality.FINAL,
				symbol = IrSimpleFunctionSymbolImpl(),
				isTailrec = false,
				isSuspend = false,
				isOperator = false,
				isInfix = false
			)

		lambda.parent = parent
		lambda.body = ctx.irFactory.createExpressionBody(deepCopyWithSymbols())

		return IrFunctionExpressionImpl(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = lambdaType,
			function = lambda,
			origin = IrStatementOrigin.LAMBDA
		)
	}

	private fun stringConstant(value: String): IrExpression =
		IrConstImpl.string(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = ctx.irBuiltIns.stringType,
			value = value
		)

	private fun intConstant(value: Int): IrExpression =
		IrConstImpl.int(
			startOffset = UNDEFINED_OFFSET,
			endOffset = UNDEFINED_OFFSET,
			type = ctx.irBuiltIns.intType,
			value = value
		)

	/**
	 * The navigation route expression of a usage: an `IrGetObjectValue` of the
	 * route object recorded by `@SettingScreen`, or `null` when the screen
	 * declared no (usable) route.
	 */
	private fun IrClassSymbol?.routeExpression(): IrExpression =
		if (this == null) {
			IrConstImpl.constNull(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				type = ctx.irBuiltIns.anyNType
			)
		} else {
			IrGetObjectValueImpl(
				startOffset = UNDEFINED_OFFSET,
				endOffset = UNDEFINED_OFFSET,
				type = owner.defaultType,
				symbol = this
			)
		}

	/**
	 * Returns true when the IR value parameter is typed `SettingObject<*, *>`,
	 * matching the primary parameter the [io.github.elnix90.settings.fir.SettingCallChecker] enforces.
	 */
	private fun IrValueParameter.isSettingObjectParameter(): Boolean {
		val classifier = (type as? IrSimpleType)?.classifier as? IrClassSymbol ?: return false
		return classifier.owner.classId == settingObjectClassId
	}

	/** Returns true when the expression references locals it could not capture at module level. */
	private fun IrExpression.containsLocalCapture(): Boolean {
		var captured = false

		accept(
			object : IrVisitorVoid() {
				override fun visitElement(element: IrElement) {
					element.acceptChildren(this, null)
				}

				override fun visitGetValue(expression: IrGetValue) {
					when (expression.symbol.owner) {
						is IrVariable,
						is IrValueParameter -> captured = true

						else -> Unit
					}
				}
			},
			null
		)

		return captured
	}

	/** Returns true when the type is exactly `List<SettingUsage>`. */
	private fun IrType.isSettingUsageList(): Boolean {
		val listType = this as? IrSimpleType ?: return false
		val listClassifier = listType.classifier as? IrClassSymbol ?: return false
		if (listClassifier.owner.classId != kotlinListClassId) return false

		val elementClassifier =
			(listType.arguments.singleOrNull() as? IrSimpleType)
				?.classifier as? IrClassSymbol
				?: return false

		return elementClassifier.owner.classId == settingUsageClassId
	}

	/**
	 * Resolves the screen a usage is rendered on: the nearest enclosing
	 * function annotated with `@SettingScreen` wins - its FqName becomes the
	 * screen identifier and its route object the navigation target - so helper
	 * composables between the screen and the setting do not skew the result.
	 *
	 * The Compose compiler hoists composable lambdas (e.g. the content of
	 * `AnimatedContent { ... }`) into standalone functions, so a `Setting()`
	 * call inside such a lambda no longer has its `@SettingScreen` function as
	 * an IR ancestor. When the stack lookup finds no annotated screen, the
	 * source range of the enclosing `@SettingScreen` function in the same file
	 * is consulted, recovering the correct screen and route.
	 *
	 * Without any `@SettingScreen` in scope, the nearest enclosing non-anonymous
	 * function is used (anonymous wrappers such as remember blocks or collection
	 * items are skipped so the identifier stays meaningful for navigation).
	 */
	private fun screenInfo(
		functionStack: List<IrFunction>,
		file: IrFile?,
		callStartOffset: Int,
		screenRanges: Map<IrFile, List<ScreenRange>>
	): ScreenInfo {
		val annotatedScreen =
			functionStack
				.lastOrNull { it.hasAnnotation(settingScreenAnnotationClassId) }

		if (annotatedScreen != null) {
			return ScreenInfo(
				fqName = annotatedScreen.fqNameWhenAvailable?.asString() ?: currentScreen(functionStack),
				routeClass = annotatedScreen.routeClass()
			)
		}

		// The annotated screen was hoisted off the stack by Compose; recover it
		// from the call's source position instead.
		val byRange = file?.let { screenRanges[it]?.screenContaining(callStartOffset) }
		if (byRange != null) {
			return ScreenInfo(fqName = byRange.fqName, routeClass = byRange.routeClass)
		}

		return ScreenInfo(fqName = currentScreen(functionStack), routeClass = null)
	}

	/** The nearest enclosing non-anonymous function, as the screen fallback identifier. */
	private fun currentScreen(functionStack: List<IrFunction>): String =
		functionStack
			.lastOrNull { !it.name.asString().startsWith("<") }
			?.fqNameWhenAvailable
			?.asString()
			?: functionStack.lastOrNull()?.name?.asString()
			?: "<unknown>"

	/**
	 * Reads the `route` KClass argument of a function's `@SettingScreen`
	 * annotation into the route object symbol, or null when the argument is
	 * absent or not an object (the [io.github.elnix90.settings.fir.SettingScreenChecker]
	 * reports the latter; the usage keeps working without the typed route).
	 */
	private fun IrFunction.routeClass(): IrClassSymbol? {
		val annotation = getAnnotation(settingScreenAnnotationClassId.asSingleFqName()) ?: return null

		val routeReference = annotation.argumentMapping[ROUTE_ARGUMENT_NAME] as? IrClassReference ?: return null
		val routeSymbol = routeReference.symbol as? IrClassSymbol ?: return null

		return routeSymbol.takeIf { routeSymbol.owner.kind == ClassKind.OBJECT }
	}

	/**
	 * Collects the source range of every `@SettingScreen` function in the
	 * module, keyed by the file that declares it.
	 *
	 * The Compose compiler hoists composable lambdas out of their enclosing
	 * function, so a `Setting()` call inside such a lambda no longer sees its
	 * `@SettingScreen` function as an IR ancestor. Recording each screen's
	 * `[startOffset, endOffset]` range lets the collector recover the screen
	 * from the call's source position, independent of the post-hoist tree shape.
	 */
	private fun IrModuleFragment.collectScreenRanges(): Map<IrFile, List<ScreenRange>> {
		val ranges = mutableMapOf<IrFile, MutableList<ScreenRange>>()

		files.forEach { file ->
			file.accept(
				visitor = object : IrVisitorVoid() {
					override fun visitElement(element: IrElement) {
						element.acceptChildren(this, null)
					}

					override fun visitFunction(declaration: IrFunction) {
						val start = declaration.startOffset
						val end = declaration.endOffset
						val fqName = declaration.fqNameWhenAvailable?.asString()

						if (declaration.hasAnnotation(settingScreenAnnotationClassId) &&
							start != UNDEFINED_OFFSET &&
							end != UNDEFINED_OFFSET &&
							fqName != null
						) {
							ranges.getOrPut(file) { mutableListOf() } +=
								ScreenRange(
									startOffset = start,
									endOffset = end,
									fqName = fqName,
									routeClass = declaration.routeClass()
								)
						}

						super.visitFunction(declaration)
					}
				},
				data = null
			)
		}

		return ranges
	}

	/**
	 * Returns the innermost (smallest) screen range containing [offset], or
	 * null when no range does. The innermost wins so that nested screens - a
	 * screen function declared inside another - resolve to the closest one.
	 */
	private fun List<ScreenRange>.screenContaining(offset: Int): ScreenRange? =
		if (offset == UNDEFINED_OFFSET) {
			null
		} else {
			asSequence()
				.filter { offset >= it.startOffset && offset <= it.endOffset }
				.minByOrNull { it.endOffset - it.startOffset }
		}

	/**
	 * The source range of a `@SettingScreen` function, used to recover the
	 * screen when Compose has hoisted a call out of its enclosing function.
	 *
	 * @property startOffset Inclusive start of the function in its file.
	 * @property endOffset Inclusive end of the function in its file.
	 * @property fqName FqName of the screen function.
	 * @property routeClass Route object class recorded by `@SettingScreen`, or
	 * null when the screen declares none.
	 */
	private data class ScreenRange(
		val startOffset: Int,
		val endOffset: Int,
		val fqName: String,
		val routeClass: IrClassSymbol?
	)

	/**
	 * The screen a usage is rendered on.
	 *
	 * @property fqName FqName of the screen function (annotated with
	 * `@SettingScreen` when present, else the nearest enclosing named function).
	 * @property routeClass Route object class recorded by `@SettingScreen`, or
	 * null when the screen declares none.
	 */
	private data class ScreenInfo(
		val fqName: String,
		val routeClass: IrClassSymbol?
	)

	/**
	 * A single recorded `@SettingCall` or `@SettingEntry` call site.
	 *
	 * @property screenFqName FqName of the screen function the entry is rendered on.
	 * @property routeClass Route object class recorded by `@SettingScreen`, or
	 * null when the screen declares none.
	 */
	private sealed interface CollectedUsage {
		val screenFqName: String

		val routeClass: IrClassSymbol?

		/** A simple property reference on a store object, e.g. `MyStore.enabled`. */
		class Static(
			val getter: IrSimpleFunctionSymbol,
			val storeSymbol: IrClassSymbol,
			val argumentType: IrType,
			override val screenFqName: String,
			override val routeClass: IrClassSymbol?
		) : CollectedUsage {
			val storeFqName: String =
				storeSymbol
					.owner
					.fqNameWhenAvailable
					?.asString()
					?: ""
		}

		/** Any other expression, resolved lazily at runtime through a provider lambda. */
		class Dynamic(
			val argument: IrExpression,
			override val screenFqName: String,
			override val routeClass: IrClassSymbol?
		) : CollectedUsage

		/**
		 * A manually described entry recorded at a `@SettingEntry` call site.
		 * The metadata fields are expressions snapshotted from the call site (or
		 * the callee's defaults), inlined into the generated index at emission.
		 */
		class Entry(
			val title: IrExpression,
			val description: IrExpression,
			val icon: IrExpression,
			override val screenFqName: String,
			override val routeClass: IrClassSymbol?
		) : CollectedUsage
	}

	private companion object {
		val TITLE_PARAMETER_NAME = Name.identifier("title")
		val DESCRIPTION_PARAMETER_NAME = Name.identifier("description")
		val ICON_PARAMETER_NAME = Name.identifier("icon")
		val ROUTE_ARGUMENT_NAME = Name.identifier("route")
		val INT_CLASS_ID: ClassId = ClassId.topLevel(FqName("kotlin.Int"))
	}
}
