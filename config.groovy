import jenkins.model.*

import hudson.security.*
import hudson.model.*
import jenkins.install.*
import java.lang.reflect.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*;
import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement
import javaposse.jobdsl.plugin.GlobalJobDslSecurityConfiguration
import jenkins.model.GlobalConfiguration
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.FileOnMasterPrivateKeySource;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*;
import hudson.util.*
import com.michelin.cio.hudson.plugins.rolestrategy.*


def DEPLOY_KEY = System.getenv()['DEPLOY_KEY']?:"/etc/jenkins/keys/ssh-private"
def EMAIL = System.getenv()['EMAIL']?:"noreply@jenkins.local"
def URL = System.getenv()['URL']?:"http://localhost:8080"
def ADMIN_USER = System.getenv()['ADMIN_USER']?:"admin"
def ADMIN_PASS = System.getenv()['ADMIN_PASS']

if (ADMIN_PASS == null) {
     ADMIN_PASS = new File("/var/jenkins_home/secrets/initialAdminPassword").text
}

jenkins = Jenkins.instance

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount(ADMIN_USER, ADMIN_PASS)
jenkins.setSecurityRealm(hudsonRealm)

jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy())
jenkins.save()

if (DEPLOY_KEY != null) {
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

REPOS = System.getenv()['REPO']

if (REPOS != null) {
    REPOS.split(",").each {REPO ->
        ROOT = "/var/jenkins_home/$REPO/"
        def process = new ProcessBuilder([ "bash", "-c",
                                          "GIT_SSH_COMMAND='ssh -i ${DEPLOY_KEY} -oStrictHostKeyChecking=no' git clone $REPO $ROOT".toString()])
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
    }
}

import hudson.security.LDAPSecurityRealm
import hudson.util.Secret
import jenkins.model.IdStrategy
import jenkins.security.plugins.ldap.LDAPConfiguration
import static hudson.security.LDAPSecurityRealm.DescriptorImpl.*
import hudson.plugins.active_directory.*

def LDAP_SERVER = System.getenv()['LDAP_SERVER']?:""
def LDAP_USER = System.getenv()['LDAP_USER']
def LDAP_PASS = System.getenv()['LDAP_PASS']
def LDAP_ROOT = System.getenv()['LDAP_ROOT']

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


def AD_SERVER = System.getenv()['AD_SERVER']?:""

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
    RoleBasedAuthorizationStrategy roles = new RoleBasedAuthorizationStrategy()
    jenkins.setAuthorizationStrategy(roles)

    Constructor[] constrs = Role.class.getConstructors();
    for (Constructor<?> c : constrs) {
      c.setAccessible(true);
    }

    RoleBasedAuthorizationStrategy.class.getDeclaredMethod("assignRole", String.class, Role.class, String.class).setAccessible(true);

    Role adminRole = new Role('admin',new HashSet(Permission.all));
    roles.addRole(RoleBasedAuthorizationStrategy.GLOBAL, adminRole);
    roles.assignRole(RoleBasedAuthorizationStrategy.GLOBAL, adminRole, System.getenv()['ADMIN_GROUP']?:"Jenkins Admins");

    Set<Permission> readOnly = new HashSet<Permission>();
    readOnly.add(Permission.fromId("hudson.model.Hudson.Read"));
    readOnly.add(Permission.fromId("hudson.model.View.Read"));

    Role authenticatedRole = new Role('read', readOnly);
    roles.addRole(RoleBasedAuthorizationStrategy.GLOBAL, authenticatedRole);
    roles.assignRole(RoleBasedAuthorizationStrategy.GLOBAL, authenticatedRole, System.getenv()['READ_GROUP']?:"authenticated");

    jenkins.save()
}
if (System.getenv()['DISABLE_DSL_SECURITY']?:"false" == "true") {
    GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).useScriptSecurity=false
    GlobalConfiguration.all().get(GlobalJobDslSecurityConfiguration.class).save()
}