import jenkins.model.*

import hudson.security.*
import hudson.model.*
import jenkins.install.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*;
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*;
import hudson.util.*


def DEPLOY_KEY = System.getenv()['DEPLOY_KEY']?:"/etc/jenkins/keys/ssh-private"
def EMAIL = System.getenv()['EMAIL']?:"noreply@jenkins.local"
def URL = System.getenv()['URL']?:"http://localhost:8080"
def ADMIN_USER = System.getenv()['ADMIN_USER']?:"admin"
def ADMIN_PASS = System.getenv()['ADMIN_PASS']?:"admin"

jenkins = Jenkins.instance

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(ADMIN_USER, ADMIN_PASS)
jenkins.setSecurityRealm(hudsonRealm)

jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy())
jenkins.save()

if (System.getenv()['DEPLOY_KEY'] != null) {
    credentials= jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
    ssh = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL,"deploy","deploy",new FileOnMasterPrivateKeySource(DEPLOY_KEY),"","")
    credentials.addCredentials( Domain.global(), ssh)
}

if (System.getenv()['BITBUCKET_KEY'] != null) {
    println "Adding bitbucket credential"
    def bitbucket = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,"bitbucket", "bitbucket", System.getenv()['BITBUCKET_KEY'], System.getenv()['BITBUCKET_SECRET'])
    credentials.addCredentials( Domain.global(), bitbucket)

    def bitbucketBuildStatusNotifier = jenkins.getDescriptorByType(org.jenkinsci.plugins.bitbucket.BitbucketBuildStatusNotifier.DescriptorImpl)
    bitbucketBuildStatusNotifier.setGlobalCredentialsId('bitbucket')
    bitbucketBuildStatusNotifier.save()
}


def GitSCM = jenkins.getDescriptor("hudson.plugins.git.GitSCM")
GitSCM.setGlobalConfigName("Jenkins")
GitSCM.setGlobalConfigEmail(EMAIL)
GitSCM.save()

location = JenkinsLocationConfiguration.get()
location.setAdminAddress(EMAIL)
location.setUrl(URL)
location.save()

// if (!jenkins.installState.isSetupComplete()) {
    InstallState.INITIAL_SETUP_COMPLETED.initializeState()
// }


REPO = System.getenv()['REPO']
ROOT = "/var/jenkins_home/REPO/"
def process = new ProcessBuilder([ "bash", "-c",
                                  "GIT_SSH_COMMAND='ssh -i /etc/jenkins/keys/ssh-private -oStrictHostKeyChecking=no' git clone $REPO $ROOT".toString()])
                                  .redirectErrorStream(true)
                                  .start()
process.consumeProcessOutput(System.out, System.err)
process.waitForOrKill(60000)


def jobDslScript = new File(ROOT, "Jenkinsfile.job")
if (jobDslScript.exists()) {
    println("Found Jenkinsfile.job, creating jobs using jobs-dsl")
    def workspace = new File('.')
    def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
    new DslScriptLoader(jobManagement).runScript(jobDslScript.text)
}

import hudson.security.LDAPSecurityRealm
import hudson.util.Secret
import jenkins.model.IdStrategy
import jenkins.security.plugins.ldap.LDAPConfiguration
import static hudson.security.LDAPSecurityRealm.DescriptorImpl.*

def LDAP_SERVER = System.getenv()['LDAP_SERVER']?:""
def LDAP_USER = System.getenv()['LDAP_USER']
def LDAP_PASS = System.getenv()['LDAP_PASS']
def LDAP_DOMAIN = System.getenv()['LDAP_DOMAIN']


if( LDAP_SERVER != "" && !(jenkins.securityRealm instanceof LDAPSecurityRealm)) {
    println("Configuring LDAP authentication")
    LDAPConfiguration conf = new LDAPConfiguration(LDAP_SERVER, LDAP_DOMAIN, false,'', Secret.fromString(LDAP_PASS));
    conf.userSearchBase = System.getenv()['LDAP_SEARCH_BASE']
    conf.userSearch =  System.getenv()['LDAP_USER_SEARCH']?:DEFAULT_USER_SEARCH
    conf.groupSearchBase = System.getenv()['LDAP_GROUP_SEARCH_BASE']
    conf.groupSearchFilter = System.getenv()['LDAP_GROUP_SEARCH']
    conf.displayNameAttributeName = System.getenv()['LDAP_DISPLAY_NAME']?:DEFAULT_DISPLAYNAME_ATTRIBUTE_NAME
    conf.mailAddressAttributeName = System.getenv()['LDAP_MAIL_NAME']?:DEFAULT_MAILADDRESS_ATTRIBUTE_NAME
    jenkins.securityRealm = new LDAPSecurityRealm(
            [conf],
            false,
            null,
            IdStrategy.CASE_INSENSITIVE,
            IdStrategy.CASE_INSENSITIVE)
    jenkins.save()
}