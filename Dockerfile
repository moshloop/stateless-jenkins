FROM jenkins/jenkins:2.255
ENV JENKINS_VER=$JENKINS_VER
ENV JENKINS_HOME=/var/jenkins_home
ENV DOCKER_VER=18.06.0
ENV K8S_VER=v1.14.0
ARG SYSTOOLS_VERSION=3.6
ENV DEBIAN_FRONTEND=noninteractive
USER root
RUN apt-get update && \
  apt-get install -y python-setuptools python-pip python-dev build-essential jq libkrb5-dev krb5-user wget openssh-client sshpass genisoimage bats git dnsutils nano sudo gnupg2 \
  libseccomp2 libdevmapper1.02.1 libltdl7 iptables && \
  rm -Rf /var/lib/apt/lists/*  && \
  rm -Rf /usr/share/doc && rm -Rf /usr/share/man  && \
  apt-get clean
RUN wget https://github.com/moshloop/systools/releases/download/${SYSTOOLS_VERSION}/systools.deb && dpkg -i systools.deb
RUN install_bin https://github.com/moshloop/db-cli/releases/download/1.3/db-cli && \
  install_bin https://github.com/moshloop/waiter/releases/download/1.1/waiter && \
  install_bin https://github.com/vmware/govmomi/releases/download/v0.18.0/govc_linux_386.gz && \
  install_bin https://github.com/ivanilves/lstags/releases/download/v1.1.0/lstags-linux-v1.1.0.tar.gz && \
  install_bin https://master.dockerproject.org/linux/x86_64/docker && \
  install_bin https://storage.googleapis.com/kubernetes-release/release/${K8S_VER}/bin/linux/amd64/kubectl && \
  install_bin https://storage.googleapis.com/kubernetes-helm/helm-v2.11.0-linux-amd64.tar.gz && \
  install_bin https://github.com/kubernetes-sigs/kustomize/releases/download/v1.0.9/kustomize_1.0.9_linux_amd64 && \
  install_bin https://github.com/mayflower/docker-ls/releases/download/v0.3.2/docker-ls-linux-amd64.zip && \
  install_bin https://github.com/PowerShell/PowerShell/releases/download/v7.0.2/powershell-7.0.2-linux-x64.tar.gz

ENV JENKINS_HOME=/var/jenkins
RUN usermod -d $JENKINS_HOME jenkins
COPY plugins.txt $JENKINS_HOME/
RUN plugins.sh $JENKINS_HOME/plugins.txt
RUN chown -R jenkins:jenkins $JENKINS_HOME
USER jenkins
ENV JAVA_OPTS="-Dhudson.model.Hudson.killAfterLoad=true"
# startup jenkins once to create the home directory structure and unpack the plugins
RUN jenkins.sh
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.model.UpdateCenter.never=true"
COPY config.groovy $JENKINS_HOME/init.groovy.d/
COPY build/libs/stateless-jenkins-0.0.1.jar  $JENKINS_HOME/war/WEB-INF/lib/
COPY jenkins.sh /usr/local/bin/jenkins.sh
