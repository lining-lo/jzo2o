@echo off
chcp 65001
title jzo2o-trade
echo.
echo [信息] 打包支付工程。
echo.
call  mvn clean package -Dmaven.test.skip=true
echo.
echo [信息] 启动支付工程。
echo.
java -Dfile.encoding=utf-8 -Xmx128m -jar target/jzo2o-trade.jar
