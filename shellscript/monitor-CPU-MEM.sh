#!/usr/bin/env bash
watch -n 1 -d 'ps -ef |grep ServerMain|grep /usr/bin/java | awk '\''{print $2}'\'' | awk '\''{print $1}'\'' |xargs ps -o %cpu,%mem -p'