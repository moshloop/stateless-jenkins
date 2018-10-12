package extras
public class JobBuilder {

    def job
    def credential

    public JobBuilder(def job, String credential) {
        this.job = job
        this.credential = credential;
    }

    public def build() {
        return this.job
    }

    public JobBuilder addJenkinsfile(REPO, path = 'Jenkinsfile', branch = 'master') {
        println "abc v1"
         this.job.definition {
            cpsScm {
                scm {
                    git {
                        remote {
                            url(REPO)
                            credentials(this.credential)
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
        def pollScm = (text =~ "pollSCM(.(.*).)")
        if (pollScm.find()) {
            println "Adding poll trigger ${pollScm[0][2]}"
            this.job.triggers {
                scm (pollScm[0][2].replaceAll("'|\"", ""))
            }
        }

        def cron = (text =~ "cron\\(.(.*).\\)")
        if (cron.find()) {
            println "Adding cron trigger: ${cron[0][2]}"
            this.job.triggers {
                cron(cron[0][2].replaceAll("'|\"", ""))
            }
        }
        return this
    }

}