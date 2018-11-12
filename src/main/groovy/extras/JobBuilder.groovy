package extras
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement
import static extras.Helpers.*

public class JobBuilder {

    def job
    def credential
    def repo

    public static void build(def dsl, REPO, CREDS) {
        println "Building $REPO with creds=$CREDS"
        def ROOT = REPO
        if (!new File(ROOT).exists()) {
            ROOT = checkout(REPO, CREDS)
        }

        def hasFolder = false
        def folder = new File(ROOT).name
        def jenkinsfile = new File(ROOT, 'Jenkinsfile')
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
                .addJenkinsfile("${path}Jenkinsfile", 'master',"${path}.*")
                .parseTriggers()
        }

        if (jenkinsfile.exists()) {
            println "Adding base Jenkinsfiles"
            def name = hasFolder ? folder + "/Base" : folder
            new JobBuilder(dsl, name, REPO, CREDS)
              .addJenkinsfile()
              .parseTriggers(jenkinsfile.text)
        }

        def jobDsl = new File(ROOT, "Jenkinsfile.job")
        if (jobDsl.exists()) {
            println "Adding Jenkinsfile.job for " + REPO
            new DslScriptLoader(new JenkinsJobManagement(System.out, [:], new File('.')))
                .runScript(jobDsl.text)
        }

        if (new File(ROOT, "jobs").exists()) {
            new File(ROOT, "jobs").listFiles().each { job ->
                def name = job.name.split("\\.")[0]
                println "Adding job $name"
                new JobBuilder(dsl, name, REPO, CREDS)
                  .addJenkinsfile("jobs/${job.name}")
                  .parseTriggers(job.text)
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
        this.repo = repo
        dsl.pipelineJob(name) {
            THIS.job = delegate
        }
        this.credential = credential;
    }

    public def build() {
        return this.job
    }

    public JobBuilder addJenkinsfile(path = 'Jenkinsfile', branch = 'master', restriction = null) {
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
                        branches(branch)
                        scriptPath(path)
                        extensions {} // required as otherwise it may try to tag the repo, which you may not want
                    }
                }
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