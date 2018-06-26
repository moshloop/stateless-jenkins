import jenkins.model.*
import hudson.security.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*;
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*;

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount('admin',System.getenv()['JENKINS_PASSWORD']?:"admin")
Jenkins.instance.setSecurityRealm(hudsonRealm)

Jenkins.instance.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy())
Jenkins.instance.save()

DEPLOY_KEY = System.getenv()['DEPLOY_KEY']?:"/etc/jenkins/keys/ssh-private"
credentials= Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
ssh = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL,"deploy","deploy",new FileOnMasterPrivateKeySource(DEPLOY_KEY),"","")

credentials.addCredentials( Domain.global(), ssh)


if (System.getenv()['BITBUCKET_KEY'] != null) {
    println "Adding bitbucket credential"
    def bitbucket = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,"bitbucket", "bitbucket", System.getenv()['BITBUCKET_KEY'], System.getenv()['BITBUCKET_SECRET'])
    credentials.addCredentials( Domain.global(), bitbucket)

    def bitbucketBuildStatusNotifier = Jenkins.instance.getDescriptorByType(org.jenkinsci.plugins.bitbucket.BitbucketBuildStatusNotifier.DescriptorImpl)
    bitbucketBuildStatusNotifier.setGlobalCredentialsId('bitbucket')
    bitbucketBuildStatusNotifier.save()
}


def EMAIL="noreply@jenkins.local"
def URL = System.getenv()['URL']?:"http://localhost:8080"
def GitSCM = Jenkins.instance.getDescriptor("hudson.plugins.git.GitSCM")
GitSCM.setGlobalConfigName("Jenkins")
GitSCM.setGlobalConfigEmail(EMAIL)
GitSCM.save()

location = JenkinsLocationConfiguration.get()
location.setAdminAddress(EMAIL)
location.setUrl(URL)
location.save()


def jobDslScript = new File('/var/jenkins_home/jobs.groovy')
def workspace = new File('.')
def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
new DslScriptLoader(jobManagement).runScript(jobDslScript.text)