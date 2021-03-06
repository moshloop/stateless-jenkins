#! /bin/bash -e

echo "Starting jenkins using $REPO"
: "${JENKINS_WAR:="/usr/share/jenkins/jenkins.war"}"
: "${JENKINS_HOME:="/var/jenkins_home"}"
touch "${COPY_REFERENCE_FILE_LOG}" || { echo "Can not write to ${COPY_REFERENCE_FILE_LOG}. Wrong volume permissions?"; exit 1; }
echo "--- Copying files at $(date)" >> "$COPY_REFERENCE_FILE_LOG"
find /usr/share/jenkins/ref/ \( -type f -o -type l \) -exec bash -c '. /usr/local/bin/jenkins-support; for arg; do copy_reference_file "$arg"; done' _ {} +

PRIMARY_REPO=$(echo $REPO | cut -d, -f1)
ROOT=$PRIMARY_REPO
if [[ ! -e $ROOT ]]; then
  ROOT=/tmp/repo
  export GIT_SSH_COMMAND="ssh -i $DEPLOY_KEY -oStrictHostKeyChecking=no"
  BRANCH=master
  if [[ "$PRIMARY_REPO" = *?branch=* ]]; then
    BRANCH=$(python -c "import sys; print sys.argv[1].split('?branch=')[1]" $PRIMARY_REPO)
  fi
  PRIMARY_REPO=$(python -c "import sys; print sys.argv[1].split('?branch=')[0]" $PRIMARY_REPO)
  git clone $PRIMARY_REPO $ROOT
  cd $ROOT
  git checkout $BRANCH

fi

if [[ -e $ROOT/jenkins.yaml ]]; then
  echo "Found Jenkins configuration as code config file in repo"
  export CASC_JENKINS_CONFIG=$ROOT/jenkins.yaml
fi


for plugin in $CUSTOM_PLUGINS; do
  echo "Downloading plugin $plugin"
  wget -O $JENKINS_HOME/plugins/$(basename $plugin) $plugin
done

# if `docker run` first argument start with `--` the user is passing jenkins launcher arguments
if [[ $# -lt 1 ]] || [[ "$1" == "--"* ]]; then

  # read JAVA_OPTS and JENKINS_OPTS into arrays to avoid need for eval (and associated vulnerabilities)
  java_opts_array=()
  while IFS= read -r -d '' item; do
    java_opts_array+=( "$item" )
  done < <([[ $JAVA_OPTS ]] && xargs printf '%s\0' <<<"$JAVA_OPTS")

  if [[ "$DEBUG" ]] ; then
    java_opts_array+=( \
      '-Xdebug' \
      '-Xrunjdwp:server=y,transport=dt_socket,address=5005,suspend=y' \
    )
  fi

  jenkins_opts_array=( )
  while IFS= read -r -d '' item; do
    jenkins_opts_array+=( "$item" )
  done < <([[ $JENKINS_OPTS ]] && xargs printf '%s\0' <<<"$JENKINS_OPTS")

  exec java -Duser.home="$JENKINS_HOME" "${java_opts_array[@]}" -jar ${JENKINS_WAR} "${jenkins_opts_array[@]}" "$@"
fi

# As argument is not jenkins, assume user want to run his own process, for example a `bash` shell to explore this image
exec "$@"