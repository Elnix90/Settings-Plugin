package io.github.elnix90.settings

import io.github.elnix90.settings.fir.SettingKeyChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

internal class SettingsAdditionalCheckers(
    session: FirSession
) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers =
        object : DeclarationCheckers() {

            override val propertyCheckers =
                setOf(SettingKeyChecker())
        }
}