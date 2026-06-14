package org.elnix.settings

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5

fun main() {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testDataRoot = "compiler-plugin/testData", testsRoot = "compiler-plugin/test-gen") {
            testClass<org.elnix.settings.runners.AbstractJvmDiagnosticTest> {
                model("diagnostics")
            }

            testClass<org.elnix.settings.runners.AbstractJvmBoxTest> {
                model("box")
            }
        }
    }
}
