#!/bin/bash
###
# Run on AWS EC2 instance running Ubuntu 20.04 or 18.04
# https://linuxize.com/post/how-to-install-and-use-docker-on-ubuntu-20-04/
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
#sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
sudo apt update
#apt-cache policy docker-ce #Ubuntu 18.04
#sudo apt install docker-ce #Ubuntu 18.04
sudo apt install docker-ce docker-ce-cli containerd.io
sudo systemctl -l status docker
sudo docker --version
sudo docker info
sudo docker pull unmtransinfo/drugcentral_db:latest
