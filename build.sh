#!/usr/bin/env bash

mvn clean package -Dmaven.test.skip=true -U

docker build -t voice2text:1.0 .

#docker push hub.c.163.com/shenshuaihu/voice2text