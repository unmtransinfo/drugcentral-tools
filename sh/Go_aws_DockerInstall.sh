#!/bin/bash
###
# Run on AWS EC2 instance running Ubuntu 18.04.4 LTS
#
if [ $(whoami) != "root" ]; then
	echo "${0} should be run as root or via sudo."
	exit
fi
#
set -e
#
#
sudo apt update
sudo apt install apt-transport-https ca-certificates curl software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
sudo apt update
apt-cache policy docker-ce
sudo apt install docker-ce
sudo systemctl -l status docker
sudo docker --version
sudo docker info
sudo docker pull unmtransinfo/drugcentral_db:latest
