sudo cp limit.max* /Library/LaunchDaemons/

sudo chown root:wheel /Library/LaunchDaemons/limit.maxfiles.plist
sudo chown root:wheel /Library/LaunchDaemons/limit.maxproc.plist

pause
sudo launchctl load -w /Library/LaunchDaemons/limit.maxfiles.plist
sudo launchctl load -w /Library/LaunchDaemons/limit.maxproc.plist
pause
sudo ulimit -n 524288
sudo ulimit -u 2048
pause
ulimit -a
