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

        val baseSettingObjectClassId = ClassId.topLevel(
            FqName(
                "org.elnix.dragonlauncher.settings.bases.objects.BaseSettingObject"
            )
        )

        val baseSettingObjectSymbol =
            session.symbolProvider
                .getClassLikeSymbolByClassId(baseSettingObjectClassId)
                ?: error("BaseSettingObject not found")

        @OptIn(SymbolInternals::class)
        val baseSettingObjectType: ConeKotlinType =
            baseSettingObjectSymbol.toLookupTag().constructClassType(
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

        val listType = listSymbol.toLookupTag().constructClassType(arrayOf(baseSettingObjectType))

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