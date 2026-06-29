#!/bin/bash
echo "----------------tracking deployment start---------------"

echo "---------------remove old image---------------"
docker rmi -f 172.16.200.26/quality/tracking:latest
echo "----------------build new image------------------"
docker build -t 172.16.200.26/quality/tracking .
echo "----------------push new image------------------"
docker push 172.16.200.26/quality/tracking:latest

echo "-----------------end--------------------------"