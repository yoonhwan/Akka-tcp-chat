#!/usr/bin/env bash
watch -n 1 'netstat -an | grep .38317 | grep ESTABLISHED | wc -l'
