package io.github.elnix90.settings.fir

import io.github.elnix90.settings.ir.settingStoreClassId
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.constructClassType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name


/**
 * FIR declaration generation extension responsible for creating the synthetic
 * `ALL` property for classes annotated with `@SettingStore`.
 *
 * This extension represents the FIR half of the settings store generation
 * pipeline. Its responsibility is limited to declaring the property and its
 * type so that the compiler recognizes it as a real member of the store.
 *
 * For every class or object annotated with:
 *
 * ```
 * @SettingStore
 * ```
 *
 * the extension generates a declaration equivalent to:
 *
 * ```
 * override val ALL: List<SettingObject<*, *>>
 * ```
 *
 * without requiring the user to write the property manually.
 *
 * The generation process happens in two steps:
 *
 * 1. [getCallableNamesForClass] advertises that this extension may generate a
 *    callable named `ALL`. Returning the name unconditionally ensures that FIR
 *    later invokes [generateProperties], where annotation checks can be
 *    performed safely.
 *
 * 2. [generateProperties] verifies that the current class is annotated with
 *    `@SettingStore`, resolves the required generic type
 *    `List<SettingObject<*, *>>`, and generates the synthetic property.
 *
 * This extension intentionally does not provide an initializer. FIR is only
 * responsible for declaring the property and its type. The actual value:
 *
 * ```
 * listOf(settingA, settingB, settingC)
 * ```
 *
 * is generated later during the IR phase by [io.github.elnix90.settings.ir.SettingStoreTransformer], which
 * has access to the complete list of `@SettingKey` properties declared inside
 * the store.
 *
 * The generated declaration is marked using [Key] so it can be identified as a
 * compiler-generated member during later compilation stages.
 */
class SettingStoreFirExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {

    /**
     * Returns true for each class it visits, because I found it easier to check whether the object/class has an annotation during the second phase
     */
    @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> = setOf(Name.identifier("ALL"))


    /**
     * Checks whether the class it visits has the @SettingStore annotation, and if this is the case, adds a new value ALL to the settings store
     */
    @OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirPropertySymbol> {
        if (context == null) return emptyList()
        val owner = context.owner

        if (!owner.hasAnnotation(settingStoreClassId, session)) {
            return emptyList()
        }

        val settingObjectClassId = ClassId.topLevel(
            FqName(
                "org.elnix.dragonlauncher.settings.bases.objects.SettingObject"
            )
        )

        val settingObjectSymbol =
            session.symbolProvider
                .getClassLikeSymbolByClassId(settingObjectClassId)
                ?: error("SettingObject not found")

        @OptIn(SymbolInternals::class)
        val settingObjectType: ConeKotlinType =
            settingObjectSymbol.toLookupTag().constructClassType(
                arrayOf(
                    ConeStarProjection,
                    ConeStarProjection
                ),
                false
            )

        val listSymbol =
            session.symbolProvider.getClassLikeSymbolByClassId(
                StandardNames.FqNames.list
                    .let(ClassId::topLevel)
            ) ?: error("List not found")

        val listType = listSymbol.toLookupTag().constructClassType(arrayOf(settingObjectType))

        val allProperty = createMemberProperty(
            owner = owner,
            key = Key,
            name = Name.identifier("ALL"),
            returnType = listType,
            isVal = true,
        )

        return listOf(allProperty.symbol)
    }

    object Key : GeneratedDeclarationKey()
}