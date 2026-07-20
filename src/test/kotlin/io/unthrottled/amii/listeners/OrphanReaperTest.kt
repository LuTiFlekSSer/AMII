package io.unthrottled.amii.listeners

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class OrphanReaperTest {

  @Test
  fun `fresh cache definitions preserve active Windows binaries`() {
    val contentDirectory = Files.createTempDirectory("amii-orphan-reaper")
    try {
      val definitions = contentDirectory.resolve("api/assets/visuals")
      Files.createDirectories(definitions.parent)
      Files.writeString(
        definitions,
        """[{"id":"welcome","path":"happy/welcome.gif"}]"""
      )

      val activeBinary = writeBinary(contentDirectory, "visuals/happy/welcome.gif")
      val orphanedBinary = writeBinary(contentDirectory, "visuals/old/orphan.gif")
      val inFlightBinary = writeBinary(contentDirectory, "visuals/happy/.incoming.gif.123.part")

      val activePaths = readActivePathsFromCache(
        contentDirectory,
        "assets/visuals?changedSince=123"
      )
      val orphans = findOrphanedBinaries(
        contentDirectory.resolve("visuals"),
        requireNotNull(activePaths)
      )

      assertThat(orphans).containsExactly(orphanedBinary)
      assertThat(activeBinary).exists()
      assertThat(inFlightBinary).exists()
    } finally {
      contentDirectory.toFile().deleteRecursively()
    }
  }

  @Test
  fun `malformed cache disables destructive cleanup`() {
    val contentDirectory = Files.createTempDirectory("amii-orphan-reaper-invalid")
    try {
      val definitions = contentDirectory.resolve("api/assets/visuals")
      Files.createDirectories(definitions.parent)
      Files.writeString(definitions, "not-json")

      assertThat(readActivePathsFromCache(contentDirectory, "assets/visuals"))
        .isNull()
    } finally {
      contentDirectory.toFile().deleteRecursively()
    }
  }

  private fun writeBinary(contentDirectory: Path, relativePath: String): Path {
    val binary = contentDirectory.resolve(relativePath)
    Files.createDirectories(binary.parent)
    return Files.writeString(binary, "content")
  }
}
