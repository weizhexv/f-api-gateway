#!/bin/bash
# enable following line for debugging
# set -x

ACTION=$1

cd "$(dirname "$0")"
echo "working directory: $(pwd)"

export APP_NAME=$(pwd_dir=$(dirname $(pwd)) && app_dir="${pwd_dir%"${pwd_dir##*[!/]}"}"  && echo "${app_dir##*/}")
echo "application name: ${APP_NAME}"

if [ "$DEPLOY_ENV" == "" ]; then
  echo "NO DEPLOY_ENV set"
  exit 2
else
  echo "invoking with deploy env: $DEPLOY_ENV"
  sed -i -e 's/{{.AppName}}/'"$APP_NAME"'/g' "./base-env.sh"
  sed -i -e 's/{{.AppPort}}/'"$APP_PORT"'/g' "./base-env.sh"
  sed -i -e 's/{{.DeployEnv}}/'"$DEPLOY_ENV"'/g' "./base-env.sh"
  source "./base-env.sh"
fi

usage() {
  echo "Usage: $PROG_NAME {deploy|undeploy|start|stop|restart}"
  exit 2
}

deploy() {
  echo "Deploying $APP_NAME"
  undeploy

  cp -f "./template.service" "./$APP_NAME.service"

  sed -i -e 's/{{.AppName}}/'"$APP_NAME"'/g' "./$APP_NAME.service"
  export APP_HOME_PATH=$(sed 's/\//\\\//g' <<< $APP_HOME) && sed -i -e 's/{{.AppHome}}/'"$APP_HOME_PATH"'/g' "./$APP_NAME.service"
  sed -i -e 's/{{.JavaOpt}}/'"$JAVA_OPT"'/g' "./$APP_NAME.service"
  sed -i -e 's/{{.DeployEnv}}/'"$DEPLOY_ENV"'/g' "./$APP_NAME.service"
  export JAR_PATH=$(sed 's/\//\\\//g' <<< $(ls $JAR_NAME)) && sed -i -e 's/{{.JarName}}/'"$JAR_PATH"'/g' "./$APP_NAME.service"

  sudo cp -f "./$APP_NAME.service" /etc/systemd/system/
  sudo systemctl enable "$APP_NAME" || (
    echo "enable service $APP_NAME failed"
    exit 1
  )
  sudo systemctl daemon-reload
  echo "Deployed $APP_NAME"
  start
}

undeploy() {
  echo "Undeploying $APP_NAME"
  sudo systemctl is-enabled "$APP_NAME" && (sudo systemctl stop "$APP_NAME")
  sudo systemctl disable "$APP_NAME"
  sudo rm -f "/etc/systemd/system/$APP_NAME.service"
  echo "Undeployed $APP_NAME"
}

start() {
  echo "Staring $APP_NAME"
  sudo systemctl start "$APP_NAME" || (
    echo "start service $APP_NAME failed"
    exit 1
  )
  health_check
  echo "Started $APP_NAME"
}

stop() {
  echo "Stopping $APP_NAME"
  sudo systemctl stop "$APP_NAME" || (
    echo "stop service $APP_NAME failed"
    exit 1
  )
  echo "Stopped $APP_NAME"
}

restart() {
  echo "Restaring $APP_NAME"
  sudo systemctl restart "$APP_NAME" || (
    echo "restart service $APP_NAME failed"
    exit 1
  )
  health_check
  echo "Restarted $APP_NAME"
}

health_check() {
  exptime=0
  echo "checking ${HEALTH_CHECK_URL}"
  while true; do
    status_code=$(/usr/bin/curl -L -o /dev/null --connect-timeout 5 -s -w %{http_code} ${HEALTH_CHECK_URL})
    if [ "$?" != "0" ]; then
      echo -n -e "\rapplication not started"
    else
      echo "code is $status_code"
      if [ "$status_code" == "200" ]; then
        break
      fi
    fi
    sleep 1
    ((exptime++))

    echo -e "\rWait app to pass health check: $exptime..."

    if [ $exptime -gt ${APP_START_TIMEOUT} ]; then
      echo 'app start failed'
      exit 1
    fi
  done
  echo "check ${HEALTH_CHECK_URL} success"
}

case "$ACTION" in
deploy)
  deploy
  ;;
undeploy)
  undeploy
  ;;
start)
  start
  ;;
stop)
  stop
  ;;
restart)
  restart
  ;;
*)
  usage
  ;;
esac
