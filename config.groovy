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
jenkins = Jenkins.instance

// SetupWizard.completeSetup()

def hudsonRealm = new HudsonPrivateSecurityRealm(false)
hudsonRealm.createAccount('admin',System.getenv()['JENKINS_PASSWORD']?:"admin")
jenkins.setSecurityRealm(hudsonRealm)

jenkins.setAuthorizationStrategy(new FullControlOnceLoggedInAuthorizationStrategy())
jenkins.save()
// PluginServletFilter.removeFilter(FORCE_SETUP_WIZARD_FILTER);

// User u = User.get("jenkins-job-builder")
// ApiTokenProperty t = u.getProperty(ApiTokenProperty.class)
// // use admin account to retrieve API token of any user
// // NOTE: jenkins has changed the way to get api token , only a user logged in as itself will be able to retrieve its token
// // refer : http://stackoverflow.com/questions/37035319/as-a-jenkins-administrator-how-do-i-get-a-users-api-token-without-logging-in-a
// def token = t.getApiToken()
// println(token)




    if (System.getenv()['DEPLOY_KEY'] != null) {
        DEPLOY_KEY = System.getenv()['DEPLOY_KEY']?:"/etc/jenkins/keys/ssh-private"
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


    // jenkins.createProjectFromXML(jobName, xmlStream)

    def EMAIL="noreply@jenkins.local"
    def URL = System.getenv()['URL']?:"http://localhost:8080"
    def GitSCM = jenkins.getDescriptor("hudson.plugins.git.GitSCM")
    GitSCM.setGlobalConfigName("Jenkins")
    GitSCM.setGlobalConfigEmail(EMAIL)
    GitSCM.save()

    location = JenkinsLocationConfiguration.get()
    location.setAdminAddress(EMAIL)
    location.setUrl(URL)
    location.save()


    def jobDslScript = new File('/var/jenkins_home/jobs.groovy')
    if (jobDslScript.exists()) {
        def workspace = new File('.')
        def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
        new DslScriptLoader(jobManagement).runScript(jobDslScript.text)

    }
    if (!jenkins.installState.isSetupComplete()) {
        InstallState.INITIAL_SETUP_COMPLETED.initializeState()
    }




    // import hudson.security.LDAPSecurityRealm
    // import hudson.util.Secret
    // import jenkins.model.IdStrategy
    // import jenkins.security.plugins.ldap.LDAPConfiguration
    // import net.sf.json.JSONObject


    // def env = System.getenv()

    // println(env)
    // // ldap_settings = ldap_settings as JSONObject

    // // if(!(jenkins.securityRealm instanceof LDAPSecurityRealm)) {
    //     LDAPConfiguration conf = new LDAPConfiguration(
    //             env['LDAP_SERVER'],
    //             env['LDAP_DOMAIN'],
    //             env['inhibitInferRootDN'] == "true" ? true : false,
    //             env['LDAP_MANAGER'],
    //             Secret.fromString(env['LDAP_PASSWORD'])
    //             )
    //     conf.userSearchBase = ldap_settings.optString('userSearchBase')
    //     conf.userSearch = ldap_settings.optString('userSearch', LDAPSecurityRealm.DescriptorImpl.DEFAULT_USER_SEARCH)
    //     conf.groupSearchBase = ldap_settings.optString('groupSearchBase')
    //     conf.groupSearchFilter = ldap_settings.optString('groupSearchFilter')
    //     conf.environmentProperties = (ldap_settings.opt('environmentProperties')?:[:]).collect { k, v ->
    //         new LDAPSecurityRealm.EnvironmentProperty(k.toString(), v.toString())
    //     } as LDAPSecurityRealm.EnvironmentProperty[]
    //     conf.displayNameAttributeName = ldap_settings.optString('displayNameAttributeName', LDAPSecurityRealm.DescriptorImpl.DEFAULT_DISPLAYNAME_ATTRIBUTE_NAME)
    //     conf.mailAddressAttributeName = ldap_settings.optString('mailAddressAttributeName', LDAPSecurityRealm.DescriptorImpl.DEFAULT_MAILADDRESS_ATTRIBUTE_NAME)

    //     List<LDAPConfiguration> configurations = [conf]
    //     jenkins.securityRealm = new LDAPSecurityRealm(
    //             configurations,
    //             ldap_settings.optBoolean('disableMailAddressResolver'),
    //             null,
    //             IdStrategy.CASE_INSENSITIVE,
    //             IdStrategy.CASE_INSENSITIVE)
    //     jenkins.save()
    // }