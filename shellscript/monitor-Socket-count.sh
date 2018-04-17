#!/usr/bin/env bash
watch -n 1 'netstat -an | grep .18573 | grep ESTABLISHED | wc -l'
