#!/usr/bin/env bash
sudo docker run \
  -p 4444:4444 \
  -p 7900:7900 \
  --shm-size="2g" \
  selenium/standalone-firefox:99.0.1
