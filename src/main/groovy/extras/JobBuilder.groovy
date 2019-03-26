package extras
import static extras.Helpers.*
import hudson.model.*
import hudson.security.*
import hudson.util.*

public class JobBuilder {

    def job
    def credential
    def repo
    def name
    def branch = 'master'

    public static void build(def dsl, REPO, CREDS) {
        println "Building $REPO with creds=$CREDS"
        def ROOT = REPO
        if (!new File(ROOT).exists()) {
            ROOT = checkout(REPO, CREDS)
        }

        def hasFolder = false
        def folder = new File(ROOT).name
        def jenkinsfile = new File(ROOT, 'Jenkinsfile')
        def owners = new File(ROOT, "OWNERS")
        "find ${ROOT} -name Jenkinsfile".execute().text.eachLine {
            def path = new File(it).parentFile.absolutePath.replaceAll(ROOT, "");
            println "Found $it with path: $path"
            if (it == jenkinsfile.absolutePath) {
                println "Skipping root: ${jenkinsfile.absolutePath}"
                return
            }

            if (path != "") {
                path += "/"
            }
            if (path.startsWith('/')) {
                path = path.substring(1)
            }
            hasFolder = true
            dsl.folder(folder)
            def name = new File(it).parentFile.name
            new JobBuilder(dsl, folder + "/" + name, REPO, CREDS)
                .addJenkinsfile("${path}Jenkinsfile", "${path}.*")
                .parseTriggers(new File(it).text)
                .parseOwners(new File(new File(it).parentFile, 'OWNERS'))
        }

        if (jenkinsfile.exists()) {
            println "Adding base Jenkinsfiles"
            def name = hasFolder ? folder + "/Base" : folder
            new JobBuilder(dsl, name, REPO, CREDS)
              .addJenkinsfile()
              .parseTriggers(jenkinsfile.text)
              .parseOwners(owners)
        }

        if (new File(ROOT, "jobs").exists()) {
            new File(ROOT, "jobs").listFiles().each { job ->
                def name = job.name.split("\\.")[0]
                println "Adding job $name"
                new JobBuilder(dsl, name, REPO, CREDS)
                  .addJenkinsfile("jobs/${job.name}")
                  .parseTriggers(job.text)
                  .parseOwners(owners)
            }
        }


        def repos = new File(ROOT, "Jenkinsfile.repos")
        if (repos.exists()) {
            repos.text.eachLine {repo ->
                println "Initializing repo: $repo"
                JobBuilder.build(dsl, repo, CREDS)
            }
        }
    }

    public JobBuilder(def dsl, String name, String repo, String credential) {
        println "new job $name: $repo"
        def THIS = this
        this.repo = repo.split("\\?branch=")[0]
        if (repo.contains("?branch=")) {
            this.branch = repo.split("\\?branch=")[1]
        }

        this.name = name
        dsl.pipelineJob(name) {
            THIS.job = delegate
        }
        this.credential = credential;
    }

    public def build() {
        return this.job
    }

    public JobBuilder addJenkinsfile(path = 'Jenkinsfile', restriction = null) {
        this.job.definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(this.repo)
                            credentials('deploy')
                        }
                        if (restriction != null ) {
                            configure { gitScm ->
                                gitScm / 'extensions' << 'hudson.plugins.git.extensions.impl.PathRestriction' {
                                    includedRegions(restriction)
                                }
                            }
                        }
                        branches(this.branch)
                        scriptPath(path)
                        extensions {} // required as otherwise it may try to tag the repo, which you may not want
                    }
                }
            }
        }
        return this
    }

    public JobBuilder parseOwners(File owners) {
        if (!owners.exists()) {
            return this
        }
        println "Parsing OWNERS for ${this.name}"
        this.job.authorization {
            for (String owner : owners.text.split("\n")) {
                permission('hudson.model.Item.Build', owner)
                permission('hudson.model.Item.Read', owner)
            }
        }
        return this
    }

    public JobBuilder parseTriggers(String text) {
        // workaround for pipeline triggers that are only enabled after the first run
        def pollScm = (text =~ /pollSCM\W*\(.*['|"]([^'"]*)['|"]\)/)
        if (pollScm.find()) {
            println "Adding poll trigger ${pollScm[0][1]}"
            this.job.triggers {
                scm (pollScm[0][1].replaceAll("'|\"", ""))
            }
        }
        def cronRe = (text =~ /cron\W*\(.*['|"]([^'"]*)['|"]\)/)
        if (cronRe.find()) {
            println "Adding cron trigger: ${cronRe[0][1]}"
            this.job.triggers {
                cron(cronRe[0][1].replaceAll("'|\"", ""))
            }
        }
        return this
    }

}