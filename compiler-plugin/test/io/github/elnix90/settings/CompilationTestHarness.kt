package io.github.elnix90.settings

import io.github.elnix90.annotations.AllStores
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A single message reported by the compiler under test.
 */
internal data class CollectedMessage(
	val severity: CompilerMessageSeverity,
	val text: String
)

/**
 * Result of a compilation started through [CompilationTestHarness.compile].
 *
 * Provides assertion helpers for diagnostics and can load the classes produced by
 * the compilation so that code generation (keys, `ALL`, `@AllStores`) can be
 * verified at runtime through reflection.
 *
 * Always use the instance inside a `use { }` block to clean up the temporary
 * compilation directory.
 */
internal class CompilationResult internal constructor(
	messages: List<CollectedMessage>,
	private val outputDirectory: File,
	private val workDirectory: File
) : AutoCloseable {
	/**
	 * Rendered texts of everything that failed the compilation (errors and crashes).
	 */
	val errorMessages: List<String> = messages
		.filter { it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION }
		.map { it.text }

	val succeeded: Boolean
		get() = errorMessages.isEmpty()

	fun assertSucceeded() {
		assertTrue(succeeded, "Expected a successful compilation but got:\n${errorMessages.joinToString("\n")}")
	}

	fun assertErrorCount(expected: Int) {
		assertEquals(expected, errorMessages.size, "Unexpected compilation errors:\n${errorMessages.joinToString("\n")}")
	}

	fun assertErrorContaining(expectedText: String) {
		assertTrue(
			errorMessages.any { it.contains(expectedText) },
			"Expected an error containing:\n$expectedText\nActual errors:\n${errorMessages.joinToString("\n")}"
		)
	}

	/**
	 * Loads a class produced by this compilation (stubs and fixture sources are
	 * compiled together into [outputDirectory]).
	 */
	fun loadClass(fqName: String): Class<*> = classLoader().loadClass(fqName)

	private var classLoader: URLClassLoader? = null

	private fun classLoader(): URLClassLoader =
		classLoader ?: URLClassLoader(
			arrayOf(outputDirectory.toURI().toURL()),
			CompilationResult::class.java.classLoader
		).also { classLoader = it }

	override fun close() {
		classLoader?.close()
		classLoader = null
		workDirectory.deleteRecursively()
	}
}

/**
 * Runs the real Kotlin CLI compiler in-process with the settings compiler plugin
 * loaded through `-Xplugin`, exactly like a production build, and captures every
 * diagnostic it reports.
 *
 * Every compilation automatically includes minimal stand-ins for the `core` types
 * referenced by the fixtures (`io.github.elnix90.core.*`), because the real `core`
 * module is an Android library that cannot be consumed from plain JVM tests.
 */
internal object CompilationTestHarness {
	/** System property wired by Gradle to the jar under test. */
	private val pluginJarPath: String =
		System.getProperty("settingsCompilerPluginJar")
			?: error("Missing 'settingsCompilerPluginJar' system property; run these tests through Gradle")

	/** Minimal classpath seen by every tested compilation: Kotlin stdlib and the annotations library. */
	private val classpath: String = listOf(
		codeSourcePathOf(Unit::class.java),
		codeSourcePathOf(AllStores::class.java)
	).joinToString(File.pathSeparator)

	private fun codeSourcePathOf(clazz: Class<*>): String =
		clazz.protectionDomain.codeSource
			?.location
			?.let { Paths.get(it.toURI()).toString() }
			?: error("Cannot locate ${clazz.name} on the test classpath")

	fun compile(vararg files: Pair<String, String>): CompilationResult {
		val workDirectory: File = Files.createTempDirectory("settings-plugin-test").toFile().canonicalFile
		val outputDirectory = File(workDirectory, "out").apply { mkdirs() }

		val sourcePaths = (stubSources() + files).map { (relativePath, content) ->
			File(workDirectory, relativePath)
				.apply {
					parentFile?.mkdirs()
					writeText(content)
				}.absolutePath
		}

		val compiler = K2JVMCompiler()
		val arguments = compiler.createArguments()
		compiler.parseArguments(
			(
				listOf(
					"-no-stdlib",
					"-no-reflect",
					"-classpath",
					classpath,
					"-d",
					outputDirectory.absolutePath,
					"-Xplugin=$pluginJarPath"
				) + sourcePaths
			).toTypedArray(),
			arguments
		)

		val messages = mutableListOf<CollectedMessage>()
		val collector = object : MessageCollector {
			override fun clear() {
				messages.clear()
			}

			override fun report(
				severity: CompilerMessageSeverity,
				message: String,
				location: CompilerMessageSourceLocation?
			) {
				messages += CollectedMessage(severity, message)
			}

			override fun hasErrors(): Boolean =
				messages.any { it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION }
		}

		compiler.exec(collector, Services.EMPTY, arguments)

		return CompilationResult(messages.toList(), outputDirectory, workDirectory)
	}

	/**
	 * Stand-ins for the `core` module classes the compiler plugin resolves by class id.
	 * They mirror the shapes the plugin depends on: `SettingObject.key`, the
	 * `SettingsStore`/`MapSettingsStore` hierarchy, and a `boolean(...)` builder
	 * exposing the `key` parameter that key generation rewrites.
	 */
	private fun stubSources(): List<Pair<String, String>> = listOf(
		"stubs/SettingObjectStub.kt" to
			"""
			package io.github.elnix90.core.objects

			abstract class SettingObject<TYPED, ENCODED> {
				abstract val key: String
			}
			""".trimIndent(),
		"stubs/SettingsStoreStub.kt" to
			"""
			package io.github.elnix90.core.stores

			import io.github.elnix90.core.objects.SettingObject

			abstract class SettingsStore<TYPE, BACKUP> {
				open val ALL: Set<SettingObject<*, *>> = emptySet()
			}

			abstract class MapSettingsStore : SettingsStore<Map<String, Any?>, Any?>() {
				fun boolean(default: Boolean = false, key: String = ""): SettingObject<Boolean, Boolean> =
					KeyedSettingObject(key)
			}

			class KeyedSettingObject(override val key: String) : SettingObject<Boolean, Boolean>()
			""".trimIndent()
	)
}
