ARG JENKINS_VER=2.129
FROM jenkins/jenkins:$JENKINS_VER
ENV JAVA_OPTS=-Djenkins.install.runSetupWizard=false
ENV JENKINS_VER=$JENKINS_VER
ENV JENKINS_HOME=/var/jenkins_home
ENV DOCKER_VER=18.06.0
ENV ANSIBLE_CONFIG /etc/ansible/ansible.cfg
ARG ANSIBLE_VERSION=2.6.1
ENV ANSIBLE_VERSION=$ANSIBLE_VERSION
ENV DEBIAN_FRONTEND=noninteractive
ADD ansible.cfg /etc/ansible/ansible.cfg
USER root
RUN apt-get update && \
    apt-get install -y python-setuptools python-pip python-dev build-essential jq libkrb5-dev krb5-user wget openssh-client sshpass genisoimage bats git dnsutils nano sudo \
    libseccomp2 libdevmapper1.02.1 libltdl7 iptables
RUN wget -O systools.deb https://github.com/moshloop/systools/releases/download/2.9/systools_2.9_amd64.deb && dpkg -i systools.deb
RUN install_bin https://github.com/moshloop/db-cli/releases/download/1.2/db-cli  \
     https://github.com/moshloop/fireviz/releases/download/1.3/fireviz \
     https://github.com/moshloop/waiter/releases/download/1.1/waiter \
     https://github.com/moshloop/smarti/releases/download/0.1/smarti \
     https://github.com/vmware/govmomi/releases/download/v0.18.0/govc_linux_386.gz \
     https://releases.hashicorp.com/packer/1.2.4/packer_1.2.4_linux_amd64.zip  && \
     install_deb \
         https://github.com/cyberark/summon/releases/download/v0.6.7/summon.deb \
         https://github.com/cyberark/summon-conjur/releases/download/v0.5.2/summon-conjur.deb && \
     BIN=/usr/local/lib/summon install_bin \
        https://github.com/cyberark/summon-aws-secrets/releases/download/v0.1.0/summon-aws-secrets-linux-amd64.tar.gz \
        https://github.com/conjurinc/summon-s3/releases/download/v0.2.0/summon-s3-linux-amd64.tar.gz \
        https://github.com/cyberark/summon-file/releases/download/v0.1.0/summon-file-linux-amd64.tar.gz
RUN pip install \
    ansible==$ANSIBLE_VERSION ansible-run urllib3==1.22 jmespath certifi netaddr  \
    awscli aws-sudo s3cmd boto \
    pandevice f5-sdk dnspython cidrtrie textfsm pandas openpyxl \
    pywinrm[kerberos] pywinrm[credssp] \
    requests requests_ntlm cryptography \
    pyvmomi apache-libcloud vapi-client-bindings pyOpenSSL==16.2.0
RUN chown jenkins:jenkins $JENKINS_HOME
USER jenkins
RUN mkdir -p $JENKINS_HOME/init.groovy.d
COPY config.groovy $JENKINS_HOME/init.groovy.d/
COPY plugins.txt $JENKINS_HOME/
USER root
RUN plugins.sh $JENKINS_HOME/plugins.txt
RUN chown -R jenkins:jenkins $JENKINS_HOME