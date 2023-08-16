#!/bin/bash

[ -e /etc/profile ] && source /etc/profile

export APP_NAME={{.AppName}}

export APP_START_TIMEOUT=30    # 等待应用启动的时间
export APP_PORT={{.AppPort}}          # 应用端口
export HEALTH_CHECK_URL=http://127.0.0.1:${APP_PORT}/ping  # 应用健康检查URL

export APP_HOME=/home/admin/${APP_NAME} # 从package.tgz中解压出来的jar包放到这个目录下
export JAR_NAME=${APP_HOME}/${APP_NAME}*.jar # jar包的名字
export LOG_HOME=${APP_HOME}/logs #应用日志目录

export CUR_IP=`ifconfig eth0 | egrep 'inet ' | awk '{print$2}'`
export SPRING_PROFILES_ACTIVE={{.DeployEnv}}

[ -e "$APP_HOME/deploy/{{.DeployEnv}}/env.sh" ] && source "$APP_HOME/deploy/{{.DeployEnv}}/env.sh"

if [ "$APP_PORT" == "" ]; then
  echo "NO APP_PORT set"
  exit 2
fi
