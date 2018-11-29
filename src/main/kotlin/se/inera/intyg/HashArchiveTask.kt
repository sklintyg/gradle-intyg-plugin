/*
 * Copyright (C) 2018 Inera AB (http://www.inera.se)
 *
 * This file is part of sklintyg (https://github.com/sklintyg).
 *
 * sklintyg is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sklintyg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.inera.intyg

import org.apache.commons.codec.digest.DigestUtils;

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.SequenceInputStream

import java.nio.file.Files

import java.util.Arrays
import java.util.Comparator
import java.util.Vector
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

open class HashArchiveTask : DefaultTask() {

    @Input lateinit var from: String
    @Input lateinit var archiveName: String
    @Input lateinit var archivePath: String

    init {
        description = """
            Calculates a hash from all files under folder specified by 'from' property.
            If hash differs from last run, folder content will be zipped and the digest
            file updated with new hash.
            """.trimIndent()
    }

    @TaskAction
    fun hashAndArchive() {
        val baseDir = File("${project.projectDir}")
        val fromDir = baseDir.resolve(from)

        val destinationDir = baseDir.resolve(archivePath)
        val destinationFile = File(destinationDir, archiveName)
        val digestFile = File(destinationDir, archiveName + ".md5")

        val includeHiddenFiles = false
        var isUpToDate = false

        // 1. Calculate checksum for all files under the 'from' directory
        val hash = hashDirectory(fromDir, includeHiddenFiles)

        // 2. Compare calculated checksum with the checksum of archive's md5-file
        if(digestFile.exists()) {
            if(digestFile.readText() == hash) {
                isUpToDate = true;
            }
        }

        // 3. If checksums differ then create a new zip file
        if (isUpToDate) {
            println("Content under \'${from}\' is unchanged, no need to create a new archive")
        } else {
            zipDirectory(fromDir, destinationFile.getAbsolutePath(), includeHiddenFiles)
            digestFile.writeText(hash)
            println("Content under \'${from}\' has been changed, a new archive has been created and the digest file updated")
        }
    }

    @Throws(IOException::class)
    private fun hashDirectory(directory: File, includeHiddenFiles: Boolean): String {
        if (!directory.isDirectory()) {
            throw IllegalArgumentException("Not a directory")
        }

        val fileStreams = Vector<FileInputStream>()
        collectFiles(directory, fileStreams, includeHiddenFiles)

        SequenceInputStream(fileStreams.elements()).use({ sequenceInputStream -> return DigestUtils.md5Hex(sequenceInputStream) })
    }

    @Throws(IOException::class)
    private fun collectFiles(directory: File,
                             fileInputStreams: MutableList<FileInputStream>,
                             includeHiddenFiles: Boolean) {

        val files: Array<File> = directory.listFiles()

        Arrays.sort(files, compareBy<File>({f -> f.getName()}))

        for (file in files) {
            if (includeHiddenFiles || !Files.isHidden(file.toPath())) {
                if (file.isDirectory()) {
                    collectFiles(file, fileInputStreams, includeHiddenFiles)
                } else {
                    fileInputStreams.add(FileInputStream(file))
                }
            }
        }
    }

    private fun zipDirectory(directory: File, zipFile: String, includeHiddenFiles: Boolean) {
        if (!directory.isDirectory()) {
            throw IllegalArgumentException("Not a directory")
        }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use {
            it.use {
                zipFiles(it, directory, "", includeHiddenFiles)
            }
        }
    }

    private fun zipFiles(zipOut: ZipOutputStream, sourceFile: File, parentDirPath: String, includeHiddenFiles: Boolean) {
        val data = ByteArray(2048)

        for (file in sourceFile.listFiles()) {
            if (includeHiddenFiles || !Files.isHidden(file.toPath())) {
                if (file.isDirectory) {
                    val entry = ZipEntry(file.name + File.separator)
                    entry.time = file.lastModified()
                    entry.isDirectory
                    entry.size = file.length()

                    zipOut.putNextEntry(entry)

                    //Call recursively to add files within this directory
                    zipFiles(zipOut, file, file.name, includeHiddenFiles)
                } else {

                    if (!file.name.contains(".zip")) { //If folder contains a file with extension ".zip", skip it
                        FileInputStream(file).use { f ->
                            BufferedInputStream(f).use { origin ->
                                val path = parentDirPath + File.separator + file.name
                                val entry = ZipEntry(path)
                                entry.time = file.lastModified()
                                entry.isDirectory
                                entry.size = file.length()
                                zipOut.putNextEntry(entry)
                                while (true) {
                                    val readBytes = origin.read(data)
                                    if (readBytes == -1) {
                                        break
                                    }
                                    zipOut.write(data, 0, readBytes)
                                }
                            }
                        }
                    } else {
                        zipOut.closeEntry()
                        zipOut.close()
                    }
                }
            }
        }
    }

}
