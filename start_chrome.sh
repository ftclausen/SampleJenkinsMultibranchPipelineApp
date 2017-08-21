#!/usr/bin/env bash

DISPLAY=":0"
LD_LIBRARY_PATH="/opt/google/chrome/lib"
export DISPLAY LD_LIBRARY_PATH

# See: https://github.com/mark-adams/docker-chromium-xvfb/blob/master/images/base/xvfb-chromium
XVFB_WHD=${XVFB_WHD:-1920x1080x16}
if [[ ! -f "/tmp/.X0-lock" ]]; then
  Xvfb $DISPLAY -ac -screen 0 $XVFB_WHD -nolisten tcp &
else
  echo "DEBUG: Xvfb already running, not starting another."
fi
sleep 1
metacity &
dbus-launch --exit-with-session
eval $(dbus-launch --exit-with-session | while read -r var; do echo "export $var"; done)
