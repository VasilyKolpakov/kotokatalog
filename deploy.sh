#!/usr/bin/env bash
set -e

mvn clean package
ssh kotokatalog 'systemctl stop kotokatalog.service'
rsync kotokatalog.service kotokatalog:/etc/systemd/system/kotokatalog.service
ssh kotokatalog 'systemctl daemon-reload'

rsync target/kotokatalog-1.0-SNAPSHOT.jar kotokatalog:/srv/kotokatalog/kotokatalog.jar
ssh kotokatalog 'systemctl start kotokatalog.service'
