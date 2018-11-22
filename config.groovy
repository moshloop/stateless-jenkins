import com.cloudbees.jenkins.plugins.sshcredentials.impl.*;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource;
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*;
import hudson.model.*
import hudson.plugins.active_directory.*
import hudson.security.*
import hudson.util.*
import java.lang.reflect.*
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.GlobalJobDslSecurityConfiguration
import javaposse.jobdsl.plugin.JenkinsJobManagement
import jenkins.install.*
import jenkins.model.*
import jenkins.plugins.git.GitSCMSource
import jenkins.plugins.git.traits.BranchDiscoveryTrait
import jenkins.security.plugins.ldap.LDAPConfiguration
import org.jenkinsci.plugins.workflow.libs.*
import static hudson.security.LDAPSecurityRealm.DescriptorImpl.*
import static extras.Helpers.*
import extras.*

System.setProperty("hudson.model.UpdateCenter.never", "true")
def DEPLOY_KEY = System.getenv()['DEPLOY_KEY']?:"/etc/jenkins/keys/ssh-private"
def EMAIL = System.getenv()['EMAIL']?:"noreply@jenkins.local"
def API_USER = System.getenv()['API_USER']
def API_PASS = System.getenv()['API_PASS']
def URL = System.getenv()['URL']?:"http://localhost:8080"
def ADMIN_USER = System.getenv()['ADMIN_USER']?:"admin"
def ADMIN_PASS = System.getenv()['ADMIN_PASS']
def ADMIN_GROUP = System.getenv()['ADMIN_GROUP']?:"Jenkins Admin"
def READ_GROUP = System.getenv()['READ_GROUP']?:"authenticated"
def LDAP_SERVER = System.getenv()['LDAP_SERVER']?:""
def LDAP_USER = System.getenv()['LDAP_USER']
def LDAP_PASS = System.getenv()['LDAP_PASS']
def LDAP_ROOT = System.getenv()['LDAP_ROOT']
def AD_SERVER = System.getenv()['AD_SERVER']?:""
def GLOBAL_LIBRARY = System.getenv()['GLOBAL_SHARED_LIBRARY']
def REPOS = System.getenv()['REPO']

if (ADMIN_PASS == null && LDAP_SERVER == "" && AD_SERVER == "") {
     ADMIN_PASS = new File("${System.getenv()['JENKINS_HOME']}/secrets/initialAdminPassword").text
}

jenkins = Jenkins.instance

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(ADMIN_USER, ADMIN_PASS)
jenkins.setSecurityRealm(hudsonRealm)

if (System.getenv()['DISABLE_CSRF'] == "true") {
    jenkins.setCrumbIssuer(null)
}

jenkins.setInstallState(InstallState.RUNNING)
jenkins.save()

if (DEPLOY_KEY != null) {
    println "Adding deploy key: ${DEPLOY_KEY}"
    credentials = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
    ssh = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL,"deploy","deploy",new DirectEntryPrivateKeySource(new File(DEPLOY_KEY).text),"","")
    credentials.addCredentials( Domain.global(), ssh)
}

if (API_USER != null) {
    println "Adding api credentials"
    def bitbucket = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,"api", "api", System.getenv()['API_USER'],API_PASS)
    credentials.addCredentials( Domain.global(), bitbucket)

    def bitbucketBuildStatusNotifier = jenkins.getDescriptorByType(org.jenkinsci.plugins.bitbucket.BitbucketBuildStatusNotifier.DescriptorImpl)
    bitbucketBuildStatusNotifier.setGlobalCredentialsId('api')
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


if( LDAP_SERVER != "" && !(jenkins.securityRealm instanceof LDAPSecurityRealm)) {
    println("Configuring LDAP authentication")
    LDAPConfiguration conf = new LDAPConfiguration(LDAP_SERVER, LDAP_ROOT, true,LDAP_USER, Secret.fromString(LDAP_PASS));
    conf.userSearchBase = System.getenv()['LDAP_SEARCH_BASE']?:""
    conf.userSearch =  System.getenv()['LDAP_USER_SEARCH']?:DEFAULT_USER_SEARCH
    conf.groupSearchBase = System.getenv()['LDAP_GROUP_SEARCH_BASE']?:""
    conf.groupSearchFilter = System.getenv()['LDAP_GROUP_SEARCH']?:""
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

if (AD_SERVER != "" && !(jenkins.securityRealm instanceof ActiveDirectorySecurityRealm)) {
    jenkins.securityRealm = new ActiveDirectorySecurityRealm(
        System.getenv()['AD_DOMAIN'],
        System.getenv()['AD_SITE'],
        System.getenv()['AD_USER'],
        System.getenv()['AD_PASS'],
        AD_SERVER
    )
    jenkins.save()
}

if (AD_SERVER != "" || LDAP_SERVER != "" ) {
    ProjectMatrixAuthorizationStrategy roles = new ProjectMatrixAuthorizationStrategy()
    roles.add(Jenkins.ADMINISTER, ADMIN_GROUP)
    roles.add(Jenkins.READ, READ_GROUP)
    jenkins.setAuthorizationStrategy(roles)
    jenkins.save()
}

if (System.getenv()['DISABLE_DSL_SECURITY']?:"false" == "true") {
    GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).useScriptSecurity=false
    GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).save()
}


if (GLOBAL_LIBRARY != null) {
    println "Loading global library from: ${GLOBAL_LIBRARY}"

    def scm = new GitSCMSource(GLOBAL_LIBRARY)
    scm.credentialsId = 'deploy'
    scm.traits = [new BranchDiscoveryTrait()]

    def library = new LibraryConfiguration("Global Library", new SCMSourceRetriever(scm))
    library.defaultVersion = System.getenv()['GLOBAL_SHARED_LIBRARY_VERSION']?:"master"
    library.implicit = true
    library.allowVersionOverride = true
    library.includeInChangesets = true

    def global_settings = Jenkins.instance.getExtensionList(GlobalLibraries.class)[0]
    global_settings.libraries = [library]
    global_settings.save()
}

if (REPOS != null) {
    REPOS.split(",").each {REPO ->
        new DslScriptLoader(new JenkinsJobManagement(System.out, [:], new File('.')))
            .runScript("extras.JobBuilder.build(this, '${REPO}', '${DEPLOY_KEY}')")
    }


}


if (System.getenv()['DO_NOT_BUILD']){
    Jenkins.instance.doQuietDown()
}