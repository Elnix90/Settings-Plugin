package io.github.elnix90.settings

import org.jetbrains.kotlin.cli.reportInfo
import org.jetbrains.kotlin.config.CompilerConfiguration
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Opt-in debug logging for the compiler plugin.
 *
 * Debugging a compiler plugin is awkward because the plugin code runs inside
 * the Kotlin compiler daemon, not inside your Gradle build logic, so the two
 * obvious logging approaches fail in practice:
 *
 * - `println` goes to the compiler daemon's stdout, which Gradle swallows
 *   unless the daemon runs in-process and even then buries in `--debug` noise;
 * - `logger`/`LoggingManager` from Gradle is simply not on the plugin classpath.
 *
 * This object therefore offers two independent sinks, usable together:
 *
 * 1. **The compiler's `MessageCollector`** - the official channel, reached
 *    through the sanctioned `CompilerConfiguration.reportInfo` helper (reading
 *    `MESSAGE_COLLECTOR_KEY` directly is a `MessageCollectorAccess` opt-in
 *    error). Messages reported here appear in the build output only with
 *    `./gradlew --info` (severity `LOGGING` needs `--debug`, `WARNING` always
 *    shows but pollutes normal builds). Results flow back through the compiler
 *    daemon correctly, which is why this is the recommended channel for
 *    user-visible diagnostics.
 * 2. **A log file** - immune to Gradle log levels and daemon process
 *    boundaries. Point it anywhere (`/tmp`, the project dir) and read it after
 *    the build. This is the channel to reach for when you are mid-investigation
 *    and do not want to fight log-level filtering.
 *
 * Enable it with the compiler plugin option `debug` (see
 * [SettingCommandLineProcessor]), which the Gradle plugin forwards when you
 * pass `-PsettingsPluginDebug=true` to the build:
 *
 * ```
 * ./gradlew :ui:main:compileDebugKotlin -PsettingsPluginDebug=true
 * tail -f /tmp/settings-plugin.log
 * ```
 */
internal object PluginDebug {
	/** Fixed file so successive compilations append to one readable timeline. */
	private const val DEFAULT_LOG_FILE: String = "/tmp/settings-plugin.log"

	@Volatile
	private var enabled: Boolean = false

	@Volatile
	private var configuration: CompilerConfiguration? = null

	@Volatile
	private var logFile: File = File(DEFAULT_LOG_FILE)

	fun configure(
		enabled: Boolean,
		configuration: CompilerConfiguration,
		logFilePath: String?
	) {
		this.enabled = enabled
		this.configuration = configuration
		this.logFile = File(logFilePath ?: DEFAULT_LOG_FILE)
	}

	/**
	 * True when debug logging is on. Guard expensive log *arguments* with this
	 * (e.g. `if (PluginDebug.isEnabled) PluginDebug.log(mirror.dump())`) so
	 * normal builds pay nothing for the instrumentation.
	 */
	val isEnabled: Boolean
		get() = enabled

	fun log(message: String) {
		if (!enabled) return

		// Sink 1: the compiler's own channel, visible with `--info`. Reading
		// MESSAGE_COLLECTOR_KEY directly is a `MessageCollectorAccess` opt-in
		// error; `reportInfo` is the sanctioned wrapper around it.
		configuration?.reportInfo("[settings-plugin] $message")

		// Sink 2: a plain file, visible without any Gradle log flags.
		runCatching {
			logFile.appendText(message + "\n")
		}
	}

	/** Logs [message] plus the stack trace of [error], for unexpected paths. */
	fun logError(message: String, error: Throwable) {
		val stackTrace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
		log("$message\n$stackTrace")
	}
}
