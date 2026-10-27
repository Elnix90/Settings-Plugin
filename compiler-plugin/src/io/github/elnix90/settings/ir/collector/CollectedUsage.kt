package io.github.elnix90.settings.ir.collector

import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

/** A single recorded `@SettingCall` or `@SettingEntry` call site. */
internal sealed interface CollectedUsage {
	/** Screen the usage is rendered on (FqName). */
	val screenFqName: String

	/** Route object from `@SettingScreen`, or null. */
	val routeClass: IrClassSymbol?

	/** `MyStore.enabled` - a plain property reference on a store object. */
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

	/** Any other expression, resolved lazily through a provider lambda. */
	class Dynamic(
		val argument: IrExpression,
		override val screenFqName: String,
		override val routeClass: IrClassSymbol?
	) : CollectedUsage

	/** Manual entry from a `@SettingEntry` call site; metadata is inlined into the index. */
	class Entry(
		val title: IrExpression,
		val description: IrExpression,
		val icon: IrExpression,
		override val screenFqName: String,
		override val routeClass: IrClassSymbol?
	) : CollectedUsage
}
