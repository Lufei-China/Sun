#!/usr/bin/env bash
#param limit
if [ $# -eq 0 ];
then
    echo 'please enter ali cloud password'
    exit
fi
JAR_NAME=inmobi-supply-sun.jar
#login ali
pwd=$1
echo 'login ali repository'
docker login --username=inmobi_sy@1810587715285417  acr-prod-registry.cn-beijing.cr.aliyuncs.com -p $1
#build new jar
rm -rf inmobi-*.jar
nowPath=$(cd `dirname $0`; pwd)
cd ./../../
#mvn clean package -B -e -U -Dmaven.test.skip=true
mvn clean -U package -Dmaven.test.skip=true
cd $nowPath
cp ./../../target/$JAR_NAME .
#make docker
gitCommit=$(git rev-parse --short HEAD)
REPOSTORY_TAG=$(date +%Y%m%d%H%M)-${gitCommit}
echo tag is:$REPOSTORY_TAG

REPOSITORY_NAME=acr-prod-registry.cn-beijing.cr.aliyuncs.com/inmobi_sy/supply-sun
REPOSITORY_WITHTAG=$REPOSITORY_NAME:$REPOSTORY_TAG
docker build . -t $REPOSITORY_WITHTAG


#push
docker push $REPOSITORY_WITHTAG
