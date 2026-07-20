package io.unthrottled.amii.listeners

import com.intellij.openapi.startup.ProjectActivity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PluginPostStartUpActivityTest {

  @Test
  fun `startup hook uses only the coroutine project activity API`() {
    val activityType = PluginPostStartUpActivity::class.java

    assertThat(activityType.interfaces).contains(ProjectActivity::class.java)
    assertThat(activityType.interfaces.map { it.name })
      .doesNotContain("com.intellij.openapi.startup.StartupActivity")
  }
}
