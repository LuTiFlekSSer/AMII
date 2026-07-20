package io.unthrottled.amii

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDescriptorTest {
  @Test
  fun `platform libraries are declared as modules, not plugin ids`() {
    val descriptor = requireNotNull(javaClass.classLoader.getResource("META-INF/plugin.xml"))
      .readText()

    listOf("intellij.platform.tasks", "intellij.platform.smRunner").forEach { moduleName ->
      assertTrue(
        "$moduleName must be declared with the module dependency syntax",
        descriptor.contains("<module name=\"$moduleName\""),
      )
      assertFalse(
        "$moduleName must not be declared as an installable plugin id",
        descriptor.contains("<depends>$moduleName</depends>"),
      )
    }

    assertTrue(
      "JCEF must be required because MemePanel directly extends HwFacadeJPanel",
      descriptor.contains("<depends>com.intellij.modules.jcef</depends>"),
    )
    assertFalse(
      "JCEF cannot be optional while its classes are loaded unconditionally",
      descriptor.contains("optional=\"true\" config-file=\"io.unthrottled.amii-com.intellij.modules.jcef.xml\""),
    )
  }
}
