#!/usr/bin/env bash
sudo cp limit.max* /Library/LaunchDaemons/

sudo chown root:wheel /Library/LaunchDaemons/limit.maxfiles.plist
sudo chown root:wheel /Library/LaunchDaemons/limit.maxproc.plist

sudo launchctl load -w /Library/LaunchDaemons/limit.maxfiles.plist
sudo launchctl load -w /Library/LaunchDaemons/limit.maxproc.plist

sudo ulimit -n 524288
sudo ulimit -u 2048

echo "limit maxfiles 1024 unlimited" | sudo tee -a /etc/launchd.conf

sudo sysctl -w net.inet.ip.portrange.first=32768
#sudo sysctl -w net.inet.ip.portrange.first=49152

ulimit -a

sysctl -a|grep port