PID=$(lsof -i tcp:7070| grep java| awk '{print $2}')
if [ -z "$PID" ]; then
  echo "process not exist, booting now..."
else
  echo "killing process，PID：" $PID
  kill -9 $PID
  echo "booting now..."
fi
sleep 2

export SPRING_PROFILES_ACTIVE=dev
export APP_HOME=/Users/build/workspace/gateway/f-api-gateway
export LOG_HOME=${APP_HOME}/logs

mvn clean package -U -DskipTests && nohup java -jar target/f-api-gateway*.jar &


