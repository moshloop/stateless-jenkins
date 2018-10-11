package extras
public class JobBuilder {

    def job
    def credential

    /**
     * Create job
     *
     * @String name   job name
     * @String type   job type (freestyle, pipeline)
     */
    public JobBuilder(def job, String credential) {
        this.job = job
        this.credential = credential;
    }

    public def build() {
        return this.job
    }

    public JobBuilder addJenkinsfile(REPO, path = 'Jenkinsfile', branch = 'master') {
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
    }

}