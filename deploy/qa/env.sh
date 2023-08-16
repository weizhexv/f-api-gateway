#!/bin/bash
# overriding settings in ../base-env.sh

export APP_START_TIMEOUT=30    # 等待应用启动的时间
export APP_PORT=20004          # 应用端口
export HEALTH_CHECK_URL=http://127.0.0.1:${APP_PORT}/ping  # 应用健康检查URL
export JAVA_OPT="-Xms1g -Xmx1g -DDUBBO_IP_TO_REGISTRY=${CUR_IP}"
