package dev.paulee.core.data

import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

internal object ZipService {

    private val logger = getLogger(ZipService::class.java)

    fun zip(sources: Set<Path>, destination: Path, filesToExclude: Set<String> = emptySet()): Boolean {
        if (destination.exists()) {
            logger.warn("Destination file already exists: $destination")
            return false
        }

        destination.parent?.let { parent ->
            if (parent.notExists()) parent.createDirectories()
        }

        val absDestination = destination.toAbsolutePath().normalize()

        var filesAdded = false

        try {
            ZipOutputStream(destination.outputStream().buffered()).use { stream ->
                stream.setLevel(Deflater.BEST_COMPRESSION)

                fun putEntry(entryName: String, path: Path? = null, isDirectory: Boolean = false) {
                    val name = if (isDirectory) {
                        if (entryName.endsWith("/")) entryName else "$entryName/"
                    } else entryName

                    val normalized = name.replace('\\', '/')

                    stream.putNextEntry(ZipEntry(normalized))

                    if (!isDirectory && path != null) {
                        Files.newInputStream(path).buffered().use { it.copyTo(stream) }

                        filesAdded = true
                    }

                    stream.closeEntry()
                }

                sources.map { it.toAbsolutePath().normalize() }.forEach { source ->
                    if (source.notExists()) {
                        logger.warn("Source path does not exist: $source")
                        return@forEach
                    }

                    val sourceName = source.fileName?.toString() ?: ""

                    if (sourceName.isEmpty()) {
                        logger.warn("Source has no filename: $source")
                        return@forEach
                    }

                    if (sourceName in filesToExclude) return@forEach

                    if (absDestination.startsWith(source)) {
                        logger.warn("Destination is inside source directory: $source")
                        return@forEach
                    }

                    if (source.isRegularFile()) {
                        putEntry(sourceName, source)
                        return@forEach
                    }

                    if (!source.isDirectory()) return@forEach

                    source.walk(PathWalkOption.INCLUDE_DIRECTORIES).forEach walker@{ child ->
                        val relativePath = source.relativize(child).toString()

                        if (child.isRegularFile()) {
                            if (child.fileName?.toString() in filesToExclude) return@walker

                            val file = child.toAbsolutePath().normalize()

                            val entryName = "$sourceName/$relativePath"

                            putEntry(entryName, file)
                        } else if (child.isDirectory()) {
                            val entryName = if (relativePath.isEmpty()) {
                                "$sourceName/"
                            } else {
                                "$sourceName/$relativePath/"
                            }

                            putEntry(entryName, isDirectory = true)
                        }
                    }
                }
            }

            if (!filesAdded) {
                logger.warn("No files were added to zip file.")

                if (destination.exists()) destination.deleteIfExists()
            }

            return filesAdded
        } catch (e: Exception) {
            logger.error("Failed to create zip file.", e)

            if (destination.exists()) destination.deleteIfExists()

            return false
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun unzip(archive: Path, destination: Path): Boolean {
        if (archive.notExists()) {
            logger.warn("Archive file does not exist: $archive")
            return false
        }

        if (!archive.isRegularFile()) {
            logger.warn("Archive path is not a file: $archive")
            return false
        }

        var wasCreated = false

        if (destination.notExists()) {
            destination.createDirectories()

            wasCreated = true
        }

        if (!destination.isDirectory()) {
            logger.warn("Destination path is not a directory: $destination")
            return false
        }

        val absDestination = destination.toAbsolutePath().normalize()

        var filesExtracted = false

        try {
            ZipFile(archive.toFile()).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val entryName = entry.name.replace('\\', '/')

                    if (Path(entryName).isAbsolute) {
                        logger.warn("Entry is absolute: $entryName")
                        return@forEach
                    }

                    val entryPath = absDestination.resolve(entryName).normalize()

                    if (!entryPath.startsWith(absDestination)) {
                        logger.warn("Entry is outside destination directory: $entryName")
                        return@forEach
                    }

                    if (entry.isDirectory) {
                        if (entryPath.notExists()) entryPath.createDirectories()
                    } else {
                        entryPath.parent?.let { if (it.notExists()) it.createDirectories() }

                        zip.getInputStream(entry).use { stream ->
                            Files.newOutputStream(entryPath).buffered().use { out ->
                                stream.copyTo(out)
                            }
                        }

                        filesExtracted = true
                    }
                }
            }

            if (!filesExtracted) {
                logger.warn("No files were extracted from zip file.")

                if (destination.exists() && wasCreated) destination.deleteRecursively()
            }

            return filesExtracted
        } catch (e: Exception) {
            logger.error("Failed to unzip archive file.", e)

            if (destination.exists() && wasCreated) destination.deleteRecursively()

            return false
        }
    }
}