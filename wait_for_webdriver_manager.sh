#!/usr/bin/env bash
# Wait max 60 seconds prior to failing the build because webdriver did not start
timeout=60
while :; do
  # Use some literal character class on the 444[4] to avoid grep matching itself.
  if netstat -tnlp | grep :444[4]; then 
    echo "Started webdriver-manager"
    sleep 5 # It still seems to need a bit of time after the port opens
    exit 0
  else
    echo "Waiting for webdriver-manger to start listening on port 4444"
  fi
  sleep 1
done

exit 1
