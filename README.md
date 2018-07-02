### Stateless Jenkins

Stateless jenkins is a dockerized configuration of jenkins that is intended to be 100% stateless. i.e. volume persistence is not required.

Jenkins will automatically create jobs by scanning the root repository for `Jenkinsfile` or `jobs.groovy` [dsl](https://github.com/jenkinsci/job-dsl-plugin) files.

e.g.

* A  SSH key stored in `/etc/jenkins/keys/ssh-private` will be added to a SSH credential called `deploy`. The public portion of this key should be loaded on Github / Bitbucket as a deploy key allowing Jenkins read only access to the repository.
* A `jobs.groovy` if found in the root of the repository will be executed
* The entire repository will be scanned for `Jenkinsfile`'s - A new project will be created for each file found with the name of the containing folder and configured to poll for changes in that folder only. This allows different jobs to run for changes found in different directories.


### Image Configuration

Jenkins installed to: `/usr/share/jenkins`
* ansible-2.4.4.0 with extended networking modules and WinRM support
* packer
* docker (Docker in Docker)
* aws cli
* +- 150 Jenkins plugins pre-installed

#### Environment Variables

| Environment Var  | Required | Description                              |
| ---------------- | -------- | ---------------------------------------- |
| REPO             | Yes      | Git checkout path of the job repository  |
| DEPLOY_KEY       | No       | Path to SSH deploy private key, can be referenced in jobs using `deploy` credential id.  defaults to `/etc/jenkins/keys/ssh-private` |
| URL              | Yes      | Public URL that the jenkins is accessible at    |
| EMAIL            | Yes      | Git / Notifications email                |
| JENKINS_PASSWORD | No       | `admin` user password, defaults to `admin` |
| BITBUCKET_KEY    | No       | [OAuth](https://confluence.atlassian.com/bitbucket/oauth-on-bitbucket-cloud-238027431.html#OAuthonBitbucketCloud-Createaconsumer) Key for Bitbucket build notifications. can be referenced in jobs using `bitbucket` credential id.  |
| BITBUCKET_SECRET | No       | OAuth Secret                             |
| JAVA_OPTS        | No       | JVM Arguments                            |
| JENKINS_OPTS     | No       | JVM Arguments                            |
| JENKINS_SLAVE_AGENT_PORT        | No       | Defaults to `50001`       |


### Running using docker
Run a docker container mapping the keys into the container using a volume:

```
docker run  -p 8080:8080 -e REPO=git@github.com/org/jenkins-jobs.git -e DEPLOY_KEY=/etc/jenkins/keys/ssh-private -v $PWD/ssh-private/ssh-keys:/etc/jenkins/keys/ssh-private --privileged --rm jenkins
```

#### Running using kubernetes
Create or import a Kubernetes SSH secret:

`kubectl create secret generic deploy-key --from-file=ssh-private=~/.ssh/id_rsa`

Mount the SSH keys as a volume

```yaml
apiVersion: apps/v1beta1
kind: Deployment
metadata:
  name: jenkins
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: jenkins
    spec:
      securityContext:
        privileged: true
      containers:
      - env:
        - name: REPO
          value: git@github.com/org/jenkins-jobs.git
        image: jenkinsci/jenkins:lts
        imagePullPolicy: Always
        name: jenkins
        ports:
        - containerPort: 8080
          protocol: TCP
        volumeMounts:
        - mountPath: /etc/jenkins/keys/
          name: deploy-keys
      volumes:
      - name: deploy-key
        secret:
          secretName: deploy-key
```


### To get the latest plugin versions

```groovy
def plugins = jenkins.model.Jenkins.instance.getPluginManager().getPlugins()
plugins.each {
  version = it.getVersion()
  if (it.getUpdateInfo() != null) {
    version = it.getUpdateInfo().version
  }
  println "${it.getShortName()}:${version}"
}
```
