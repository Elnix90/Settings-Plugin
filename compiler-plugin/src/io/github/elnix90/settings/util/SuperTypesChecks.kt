package io.github.elnix90.settings.util

import io.github.elnix90.settings.util.ClassIds.mapSettingsStoreClassId
import io.github.elnix90.settings.util.ClassIds.settingsStoreClassId
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.isSupertypeOf
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.name.ClassId


internal fun FirClassSymbol<*>.isSettingsStore(
    session: FirSession
): Boolean {

    val mapSettingsStoreSymbol =
        session.symbolProvider
            .getClassLikeSymbolByClassId(settingsStoreClassId)
                as? FirClassSymbol<*>
            ?: return false

    return mapSettingsStoreSymbol.isSupertypeOf(this, session)
}

internal fun FirClassSymbol<*>.isMapSettingsStore(
    session: FirSession
): Boolean {

    val mapSettingsStoreSymbol =
        session.symbolProvider
            .getClassLikeSymbolByClassId(mapSettingsStoreClassId)
                as? FirClassSymbol<*>
            ?: return false

    return mapSettingsStoreSymbol.isSupertypeOf(this, session)
}


internal fun IrClass.isClassIdSupertype(classId: ClassId): Boolean {

    if (this.classId == classId) {
        return true
    }

    return superTypes.any { superType ->
        val superClass = superType.classOrNull?.owner ?: return@any false
        superClass.isClassIdSupertype(classId)
    }
}

internal fun IrClass.isMapSettingsStore(): Boolean = isClassIdSupertype(mapSettingsStoreClassId)