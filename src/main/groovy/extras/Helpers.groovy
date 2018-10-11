package extras
public class Helpers {
    static def checkout(REPO, DEPLOY_KEY) {
        def ROOT = "/tmp/$REPO/"
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

}