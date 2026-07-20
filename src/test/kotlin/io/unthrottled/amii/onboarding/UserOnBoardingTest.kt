package io.unthrottled.amii.onboarding

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class UserOnBoardingTest {
  @Test
  fun `reads the packaged plugin version without IntelliJ internal APIs`() {
    assertThat(UserOnBoarding.getVersion()).contains("1.7.0")
  }
}
