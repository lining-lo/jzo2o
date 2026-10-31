@echo off
chcp 65001
title jzo2o-orders-manager
echo.
echo [信息] 打包订单工程。
echo.
call  mvn clean package -Dmaven.test.skip=true
echo.
echo [信息] 启动订单工程。
echo.
java -Dfile.encoding=utf-8 -Xmx128m -jar jzo2o-orders-manager/target/jzo2o-orders-manager.jar
