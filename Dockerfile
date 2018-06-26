ARG JENKINS_VER=2.98
FROM jenkins/jenkins:$JENKINS_VER
ENV JENKINS_VER=$JENKINS_VER
ENV JENKINS_HOME=/var/jenkins_home
ENV ANSIBLE_CONFIG /etc/ansible/ansible.cfg
ARG ANSIBLE_VERSION=2.4.4.0
ENV ANSIBLE_VERSION=$ANSIBLE_VERSION
ENV DEBIAN_FRONTEND=noninteractive
ADD ansible.cfg /etc/ansible/ansible.cfg
USER root
RUN apt-get update && \
    apt-get install -y python-setuptools python-pip python-dev build-essential jq libkrb5-dev krb5-user wget curl openssh-client sshpass git  && \
    pip install ansible==$ANSIBLE_VERSION awscli aws-sudo s3cmd boto pandevice f5-sdk pywinrm[kerberos] pywinrm[credssp] certifi urllib3==1.22 jmespath pyvmomi && \
    ansible-galaxy install geerlingguy.docker moshloop.java

RUN echo - {hosts: all, roles: [geerlingguy.docker]} > /tmp/play && ansible-playbook -i "localhost," -c local /tmp/play
RUN echo - {hosts: all, roles: [moshloop.java]} > /tmp/play && ansible-playbook -i "localhost," -c local /tmp/play -e JVM_ONLY=true
RUN wget -qO- -O tmp.zip https://releases.hashicorp.com/packer/1.2.4/packer_1.2.4_linux_amd64.zip && \
    unzip tmp.zip && mv packer /usr/bin/ && chmod +x /usr/bin/packer && rm tmp.zip

USER jenkins
COPY plugins.txt $JENKINS_HOME/
RUN plugins.sh $JENKINS_HOME/plugins.txt
COPY init.groovy $JENKINS_HOME/init.groovy.d/
RUN echo $JENKINS_VER > $JENKINS_HOME/jenkins.install.UpgradeWizard.state
RUN echo $JENKINS_VER > $JENKINS_HOME/jenkins.install.InstallUtil.lastExecVersion