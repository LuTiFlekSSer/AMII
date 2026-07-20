package io.unthrottled.amii.tools

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Writes a complete replacement next to [target] and then swaps it into place. */
fun writeAtomically(target: Path, writer: (Path) -> Unit) {
  val parent = target.toAbsolutePath().parent
  Files.createDirectories(parent)
  val temporaryFile = Files.createTempFile(parent, ".${target.fileName}.", ".part")

  try {
    writer(temporaryFile)
    try {
      Files.move(
        temporaryFile,
        target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING
      )
    } catch (_: AtomicMoveNotSupportedException) {
      Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING)
    }
  } finally {
    Files.deleteIfExists(temporaryFile)
  }
}
