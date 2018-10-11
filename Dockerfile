FROM jenkins/jenkinsfile-runner as JenkinsfileRunner
FROM jenkins/jenkins:2.143
ENV JENKINS_VER=$JENKINS_VER
ENV JENKINS_HOME=/var/jenkins_home
ENV DOCKER_VER=18.06.0
ENV ANSIBLE_CONFIG /etc/ansible/ansible.cfg
ARG ANSIBLE_VERSION=2.6.1
ARG SYSTOOLS_VERSION=3.2
ENV ANSIBLE_VERSION=$ANSIBLE_VERSION
ENV DEBIAN_FRONTEND=noninteractive
ADD ansible.cfg /etc/ansible/ansible.cfg
USER root
COPY --from=JenkinsfileRunner /usr/local/bin/jenkinsfile-runner.hpi /usr/local/bin/jenkinsfile-runner.hpi
COPY --from=JenkinsfileRunner /usr/local/bin/jenkinsfile-runner /usr/local/bin/jenkinsfile-runner
RUN apt-get update && \
    apt-get install -y python-setuptools python-pip python-dev build-essential jq libkrb5-dev krb5-user wget openssh-client sshpass genisoimage bats git dnsutils nano sudo \
    libseccomp2 libdevmapper1.02.1 libltdl7 iptables
RUN wget -O systools.deb https://github.com/moshloop/systools/releases/download/${SYSTOOLS_VERSION}/systools_${SYSTOOLS_VERSION}_amd64.deb && dpkg -i systools.deb
RUN install_bin https://github.com/moshloop/db-cli/releases/download/1.2/db-cli  \
     https://github.com/moshloop/fireviz/releases/download/1.3/fireviz \
     https://github.com/moshloop/waiter/releases/download/1.1/waiter \
     https://github.com/moshloop/smarti/releases/download/0.1/smarti \
     https://github.com/vmware/govmomi/releases/download/v0.18.0/govc_linux_386.gz \
     https://github.com/ivanilves/lstags/releases/download/v1.1.0/lstags-linux-v1.1.0.tar.gz \
     https://releases.hashicorp.com/packer/1.2.4/packer_1.2.4_linux_amd64.zip \
     https://github.com/genuinetools/reg/releases/download/v0.15.3/reg-linux-amd64 \
     https://master.dockerproject.org/linux/x86_64/docker && \
     mv /usr/bin/reg-linux-amd64 /usr/bin/reg && \
     install_deb \
         https://github.com/cyberark/summon/releases/download/v0.6.7/summon.deb \
         https://github.com/cyberark/summon-conjur/releases/download/v0.5.2/summon-conjur.deb && \
     BIN=/usr/local/lib/summon install_bin \
        https://github.com/cyberark/summon-aws-secrets/releases/download/v0.1.0/summon-aws-secrets-linux-amd64.tar.gz \
        https://github.com/conjurinc/summon-s3/releases/download/v0.2.0/summon-s3-linux-amd64.tar.gz \
        https://github.com/cyberark/summon-file/releases/download/v0.1.0/summon-file-linux-amd64.tar.gz
RUN pip install ansible==$ANSIBLE_VERSION ansible-run ansible-deploy ansible-dependencies[all] openpyxl pandas
RUN chown jenkins:jenkins $JENKINS_HOME
USER root
COPY plugins.txt $JENKINS_HOME/
RUN plugins.sh $JENKINS_HOME/plugins.txt
RUN chown -R jenkins:jenkins $JENKINS_HOME
RUN unzip /usr/share/jenkins/jenkins.war -d $JENKINS_HOME/war
USER jenkins
ENV JAVA_OPTS="-Dhudson.model.Hudson.killAfterLoad=true"
# startup jenkins once to create the home directory structure and unpack the plugins
RUN jenkins.sh
RUN ls /var/jenkins_home
VOLUME /var/jenkins_home
RUN ls /var/jenkins_home
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.model.UpdateCenter.never=true"
COPY config.groovy $JENKINS_HOME/init.groovy.d/
COPY build/libs/stateless-jenkins-0.0.1.jar  $JENKINS_HOME/war/WEB-INF/lib/
