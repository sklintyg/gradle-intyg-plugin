package se.inera.intyg

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Credentials

class TagReleaseTask extends DefaultTask {

    public TagReleaseTask() {
        setDescription("Tags the current git head with the project's version.")
    }

    @TaskAction
    tagRelease() {
        def grgit = Grgit.open(dir: project.rootProject.projectDir,
                creds: new Credentials(System.properties['githubUser'], System.properties['githubPassword']))

        grgit.tag.add {
            name = "v" + project.version
            message = "Release of ${project.version}"
        }

        grgit.push(tags: true)
    }

}
