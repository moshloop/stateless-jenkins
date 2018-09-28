### Stateless Jenkins

Stateless jenkins is a dockerized configuration of jenkins that is intended to be 100% stateless. i.e. volume persistence is not required.

Jenkins will automatically create jobs by scanning the root repository for `Jenkinsfile` or `Jenkinsfile.job` [dsl](https://github.com/jenkinsci/job-dsl-plugin) files.

e.g.

* A  SSH key stored in `/etc/jenkins/keys/ssh-private` will be added to a SSH credential called `deploy`. The public portion of this key should be loaded on Github / Bitbucket as a deploy key allowing Jenkins read only access to the repository.
* A `Jenkinsfile.job` if found in the root of the repository will be executed


### Image Configuration

Jenkins installed to: `/usr/share/jenkins`:
  * [Jenkinsfile-runner](https://github.com/jenkinsci/jenkinsfile-runner) installed to `/usr/local/bin/jenkinsfile-runner`
  * Blue Ocean
  * AWS, Azure, AD, LDAP, Bitbucket plugins

**Additional Tools**
* ansible-2.6.1 with python dependencies for most modules
* [systools](https://github.com/moshloop/systools)
* [fireviz](https://github.com/moshloop/fireviz)
* [waiter](https://github.com/moshloop/waiter)
* [govc](https://github.com/vmware/govmomi/tree/master/govc)
* packer
* docker, [lstags](https://github.com/ivanilves/lstags), [reg](https://github.com/genuinetools/reg)
* [summon](https://github.com/cyberark/summon)
* aws cli


#### Environment Variables

| Environment Var  | Required | Description                              |
| ---------------- | -------- | ---------------------------------------- |
| REPO             | Yes      | Git repo ( e.g. `ssh://git@github.com/acme/jenkins-config` or `/work/repos/jenkins-config`) |
| DEPLOY_KEY       | No       | Path to a SSH deploy private key, can be referenced in jobs using `deploy` credential id.  defaults to `/etc/jenkins/keys/ssh-private` |
| GLOBAL_SHARED_LIBRARY | No | Git repo to use as global shared library |
| URL              | Yes      | Public URL that the jenkins is accessible at    |
| EMAIL            | Yes      | Git / Notifications email                |
| ADMIN_PASS | No       | `admin` user password, defaults to `admin` |
| API_USER    | No       | Can be referenced in jobs using `api` credential id.  |
| API_PASS | No       |                              |
| JAVA_OPTS        | No       | JVM Arguments                            |
| JENKINS_OPTS     | No       | Jenkins Arguments                      |
| JENKINS_SLAVE_AGENT_PORT        | No       | Defaults to `50001`       |
| DISABLE_CSRF | No | Defaults to false, |
| LDAP_SERVER |  |  |
| LDAP_USER |  |  |
| LDAP_PASS |  |  |
| LDAP_ROOT_DN |  |  |
| AD_SERVER |  |  |
| AD_DOMAIN |  |  |
| AD_SITE |  |  |
| AD_USER |  |  |
| AD_PASS |  |  |
| READ_GROUP |  | authenticated |
| ADMIN_GROUP |  | Jenkins Admins |


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
        image: moshloop/stateless-jenkins:3.32
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


### To update to the latest plugins

```bash
python update-plugins.py plugins.txt
```
