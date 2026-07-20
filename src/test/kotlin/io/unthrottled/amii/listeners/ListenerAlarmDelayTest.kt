package io.unthrottled.amii.listeners

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ListenerAlarmDelayTest {

  @Test
  fun `delay remains exact after the old Int overflow boundary`() {
    val delay = minutesToAlarmDelayMillis(35_792L)

    assertThat(delay)
      .isEqualTo(2_147_520_000L)
      .isGreaterThan(Int.MAX_VALUE.toLong())
  }

  @Test
  fun `delay supports the full settings UI range`() {
    assertThat(minutesToAlarmDelayMillis(Int.MAX_VALUE.toLong()))
      .isEqualTo(128_849_018_820_000L)
  }

  @Test
  fun `invalid non-positive persisted values use the UI minimum`() {
    assertThat(minutesToAlarmDelayMillis(0L)).isEqualTo(60_000L)
    assertThat(minutesToAlarmDelayMillis(Long.MIN_VALUE)).isEqualTo(60_000L)
  }

  @Test
  fun `conversion saturates instead of overflowing for extreme persisted values`() {
    assertThat(minutesToAlarmDelayMillis(Long.MAX_VALUE)).isEqualTo(Long.MAX_VALUE)
  }
}
