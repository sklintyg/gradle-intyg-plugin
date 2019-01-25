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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

open class ArchiveDirectoryTask : Zip() {

    @Input var sourcePath: String = "."
        set(value) {
            field = value
            from(value)
        }

    @Input var archiveOnlyIfDiff: Boolean = false

    init {
        description = """
            Archives all files under folder specified by 'from' property. If property 'archiveOnlyIfDiff' is set
            to false a zip file will always be generate. But, if the property is set to true, a zip file
            will be generated only if there are changes between Git's remote repository and the local working tree.
            """.trimIndent()
    }

    @TaskAction
    override fun copy() {
        val baseDir = File("${project.projectDir}")
        val fromDir = baseDir.resolve(sourcePath)

        if (archiveOnlyIfDiff) {
            // Check if any file under 'from' folder has been touched
            val command = "git diff-index --name-only -B -R -M -C HEAD " + fromDir.getAbsolutePath()
            val result = command.runCommand()

            if (result!!.isNullOrBlank()) {
                println("Content under \'$sourcePath\' is unchanged, no need to create a new archive.")
            } else {
                super.copy()
                println("Content under \'$sourcePath\' has been changed, a new archive has been created $archiveFileName")
                println(result)
            }
        } else {
            super.copy()
            println("Content under \'$sourcePath\' has been archived to $archiveFile")
        }

    }

    @Throws(IOException::class)
    fun String.runCommand(workingDir: File = File("."),
                          timeoutAmount: Long = 60,
                          timeoutUnit: TimeUnit = TimeUnit.SECONDS): String? {

        return ProcessBuilder(*this.split("\\s".toRegex()).toTypedArray())
                .directory(workingDir)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .start().apply {
                    waitFor(timeoutAmount, timeoutUnit)
                }
                .inputStream.bufferedReader().use {
                    it.readText()
                }
    }

}
