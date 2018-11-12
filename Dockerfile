FROM jenkins/jenkins:2.143
ENV JENKINS_VER=$JENKINS_VER
ENV JENKINS_HOME=/var/jenkins_home
ENV DOCKER_VER=18.06.0
ENV ANSIBLE_CONFIG /etc/ansible/ansible.cfg
ARG ANSIBLE_VERSION=2.6.1
ARG SYSTOOLS_VERSION=3.6
ENV ANSIBLE_VERSION=$ANSIBLE_VERSION
ENV DEBIAN_FRONTEND=noninteractive
ADD ansible.cfg /etc/ansible/ansible.cfg
USER root
RUN apt-get update && \
    apt-get install -y python-setuptools python-pip python-dev build-essential jq libkrb5-dev krb5-user wget openssh-client sshpass genisoimage bats git dnsutils nano sudo \
    libseccomp2 libdevmapper1.02.1 libltdl7 iptables && \
    rm -Rf /var/lib/apt/lists/*  && \
    rm -Rf /usr/share/doc && rm -Rf /usr/share/man  && \
    apt-get clean
RUN wget https://github.com/moshloop/systools/releases/download/${SYSTOOLS_VERSION}/systools.deb && dpkg -i systools.deb
RUN install_bin https://github.com/moshloop/db-cli/releases/download/1.2/db-cli  \
     https://github.com/moshloop/fireviz/releases/download/1.3/fireviz \
     https://github.com/moshloop/waiter/releases/download/1.1/waiter \
     https://github.com/moshloop/smarti/releases/download/0.1/smarti
 RUN bin_name=reg install_bin https://github.com/genuinetools/reg/releases/download/v0.15.3/reg-linux-amd64
 RUN install_bin https://releases.hashicorp.com/packer/1.2.4/packer_1.2.4_linux_amd64.zip && \
     install_bin https://github.com/vmware/govmomi/releases/download/v0.18.0/govc_linux_386.gz && \
     install_bin https://github.com/ivanilves/lstags/releases/download/v1.1.0/lstags-linux-v1.1.0.tar.gz && \
     install_bin https://master.dockerproject.org/linux/x86_64/docker && \
     install_bin https://storage.googleapis.com/kubernetes-release/release/v1.12.0/bin/linux/amd64/kubectl && \
     install_bin https://storage.googleapis.com/kubernetes-helm/helm-v2.11.0-linux-amd64.tar.gz && \
     install_bin https://github.com/kubernetes-sigs/kustomize/releases/download/v1.0.9/kustomize_1.0.9_linux_amd64 && \
     install_bin https://github.com/mayflower/docker-ls/releases/download/v0.3.2/docker-ls-linux-amd64.zip
RUN  install_deb \
         https://github.com/cyberark/summon/releases/download/v0.6.7/summon.deb \
         https://github.com/cyberark/summon-conjur/releases/download/v0.5.2/summon-conjur.deb
RUN  BIN=/usr/local/lib/summon install_bin \
        https://github.com/cyberark/summon-aws-secrets/releases/download/v0.1.0/summon-aws-secrets-linux-amd64.tar.gz \
        https://github.com/conjurinc/summon-s3/releases/download/v0.2.0/summon-s3-linux-amd64.tar.gz \
        https://github.com/cyberark/summon-file/releases/download/v0.1.0/summon-file-linux-amd64.tar.gz
RUN pip install ansible==$ANSIBLE_VERSION ansible-run ansible-provision ansible-deploy ansible-dependencies[all] openpyxl pandas sh
ENV JENKINS_HOME=/var/jenkins
RUN usermod -d $JENKINS_HOME jenkins
COPY plugins.txt $JENKINS_HOME/
RUN plugins.sh $JENKINS_HOME/plugins.txt
RUN chown -R jenkins:jenkins $JENKINS_HOME
USER jenkins
RUN ansible-provision install
ENV JAVA_OPTS="-Dhudson.model.Hudson.killAfterLoad=true"
# startup jenkins once to create the home directory structure and unpack the plugins
RUN jenkins.sh
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.model.UpdateCenter.never=true"
COPY config.groovy $JENKINS_HOME/init.groovy.d/
COPY build/libs/stateless-jenkins-0.0.1.jar  $JENKINS_HOME/war/WEB-INF/lib/
