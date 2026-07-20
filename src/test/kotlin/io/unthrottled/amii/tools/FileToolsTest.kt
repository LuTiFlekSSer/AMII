package io.unthrottled.amii.tools

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING

class FileToolsTest {

  @Test
  fun `atomic write replaces content and cleans temporary file`() {
    val directory = Files.createTempDirectory("amii-atomic-write")
    val target = directory.resolve("asset.json")
    try {
      Files.writeString(target, "old")

      writeAtomically(target) { temporaryFile ->
        Files.writeString(temporaryFile, "new", TRUNCATE_EXISTING)
      }

      assertThat(Files.readString(target)).isEqualTo("new")
      Files.list(directory).use { files ->
        assertThat(files.map { it.fileName.toString() }.toList())
          .containsExactly("asset.json")
      }
    } finally {
      Files.deleteIfExists(target)
      Files.deleteIfExists(directory)
    }
  }
}
