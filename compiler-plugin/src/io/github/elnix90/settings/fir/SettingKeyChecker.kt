package io.github.elnix90.settings.fir

import io.github.elnix90.settings.util.ClassIds.settingKeyAnnotationClassId
import io.github.elnix90.settings.util.ClassIds.settingsStoreAnnotationClassId
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.symbols.SymbolInternals

internal class SettingKeyChecker : FirPropertyChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
    context(
        context: CheckerContext,
        reporter: DiagnosticReporter
    )
    override fun check(
        declaration: FirProperty
    ) {

        if (
            !declaration.hasAnnotation(
                settingKeyAnnotationClassId,
                context.session
            )
        ) {
            return
        }

//        val containingClass =
//            context.containingDeclarations
//                .asReversed()
//                .lastOrNull()

//                ?: run {
//                    reporter.reportOn(
//                        declaration.source,
//                        SettingsErrors.SETTING_KEY_OUTSIDE_STORE,
//                        context
//                    )
//                    return
//                }


        val containingClass =
            context.containingDeclarations
                .asReversed()
                .firstNotNullOfOrNull {
                    it.fir as? FirRegularClass
                }
         val isInsideStore = containingClass
             ?.hasAnnotation(settingsStoreAnnotationClassId, context.session
            ) == true

        require(isInsideStore) {
            "@SettingKey may only be used inside a @SettingsStore"
        }

//            reporter.reportOn(
//                declaration.source,
//                SettingsErrors.SETTING_KEY_OUTSIDE_STORE,
//                context
//            )
//        }
    }
}