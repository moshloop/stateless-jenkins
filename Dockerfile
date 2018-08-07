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
    apt-get install -y python-setuptools python-pip python-dev build-essential jq libkrb5-dev krb5-user wget openssh-client sshpass genisoimage bats git dnsutils \
    libseccomp2 libdevmapper1.02.1 libltdl7 iptables && \
    pip install \
    ansible==$ANSIBLE_VERSION ansible-run urllib3==1.22 jmespath certifi netaddr  \
    awscli aws-sudo s3cmd boto \
    pandevice f5-sdk dnspython \
    pywinrm[kerberos] pywinrm[credssp] \
    pyvmomi apache-libcloud vapi-client-bindings pyOpenSSL==16.2.0 \
    && ansible-galaxy install moshloop.java

RUN wget -nv -O /tmp/docker.deb https://download.docker.com/linux/debian/dists/stretch/pool/stable/amd64/docker-ce_$DOCKER_VER~ce~3-0~debian_amd64.deb && \
    dpkg -i /tmp/docker.deb && \
    update-rc.d docker defaults

RUN echo - {hosts: all, roles: [moshloop.java]} > /tmp/play && ansible-playbook -i "localhost," -c local /tmp/play
RUN wget -qO- -O tmp.zip https://releases.hashicorp.com/packer/1.2.4/packer_1.2.4_linux_amd64.zip && \
    unzip tmp.zip && mv packer /usr/bin/ && chmod +x /usr/bin/packer && rm tmp.zip
RUN chown jenkins:jenkins $JENKINS_HOME
USER jenkins
RUN mkdir -p $JENKINS_HOME/init.groovy.d
COPY plugins.txt $JENKINS_HOME/
RUN plugins.sh $JENKINS_HOME/plugins.txt
COPY config.groovy $JENKINS_HOME/init.groovy.d/