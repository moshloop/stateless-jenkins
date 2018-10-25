package extras

import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement

public class Helpers {
    static def checkout(REPO, DEPLOY_KEY) {
        def ROOT = "/tmp/" + new File(REPO).name.split("\\.")[0] + "/"
        "rm -rf ${ROOT} || echo ok".execute()
        def process = new ProcessBuilder(["bash", "-c",
                "GIT_SSH_COMMAND='ssh -i ${DEPLOY_KEY} -oStrictHostKeyChecking=no' git clone $REPO $ROOT".toString()
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