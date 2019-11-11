package extras

import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement

public class Helpers {
    static def checkout(REPO, DEPLOY_KEY) {
        def branch = "master"
        if (REPO.contains("?branch=")) {
            branch = REPO.split("\\?branch=")[1]
            REPO = REPO.split("\\?branch=")[0]
        }
        println "Checking out $REPO, branch: $branch"
        def ROOT = "/tmp/" + new File(REPO).name.split("\\.")[0] + "/"
        "rm -rf ${ROOT} || echo ok".execute()
        def process = new ProcessBuilder(["bash", "-c",
            "GIT_SSH_COMMAND='ssh -i ${DEPLOY_KEY} -oStrictHostKeyChecking=no' git clone $REPO $ROOT && git checkout ${branch} && git log --name-only -n 10".toString()
            ])
            .redirectErrorStream(true)
            .start()
        process.consumeProcessOutput(System.out, System.err)
        process.waitForOrKill(60000)
        return ROOT
    }

    static def evalJobDSL(String text){
        def jobManagement = new JenkinsJobManagement(System.out, [:], new File('.'))
        new DslScriptLoader(jobManagement).runScript(text)
    }
}
