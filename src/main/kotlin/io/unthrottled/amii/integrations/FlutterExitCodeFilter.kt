package io.unthrottled.amii.integrations

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import io.unthrottled.amii.extensions.ExitCodeFilter

class FlutterExitCodeFilter : ExitCodeFilter {
  override fun shouldProcess(
    executorId: String,
    env: ExecutionEnvironment,
    handler: ProcessHandler,
    exitCode: Int
  ): Boolean {
    if (handler.isFlutterProcessHandler().not()) {
      return true
    }

    val isTerminatedDebugProcess =
      executorId == "Debug" &&
        exitCode == -1 &&
        handler.flutterCommandLine().orEmpty().contains("flutter.bat", ignoreCase = true)
    return !isTerminatedDebugProcess
  }

  override fun shouldProcess(testProxy: SMTestProxy.SMRootTestProxy): Boolean {
    val handler = testProxy.handler
    if (handler?.isFlutterProcessHandler() != true) {
      return true
    }

    return false
  }

  /**
   * Flutter is an optional dependency. Avoid linking its classes from the main
   * plugin classloader so AMII can be compiled and loaded without downloading
   * the Flutter plugin.
   */
  private fun ProcessHandler.isFlutterProcessHandler(): Boolean =
    generateSequence(javaClass as Class<*>?) { it.superclass }
      .any { it.name == FLUTTER_PROCESS_HANDLER_CLASS }

  private fun ProcessHandler.flutterCommandLine(): String? =
    runCatching {
      javaClass.getMethod("getCommandLine").invoke(this) as? String
    }.getOrNull()

  private companion object {
    const val FLUTTER_PROCESS_HANDLER_CLASS = "io.flutter.utils.MostlySilentColoredProcessHandler"
  }
}
