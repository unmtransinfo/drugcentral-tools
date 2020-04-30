#!/bin/bash
###
if [ $(whoami) != "root" ]; then
	echo "${0} should be run as root or via sudo."
	exit
fi
#
set -e
#

# Push commands for drugcentral_db

# Make sure that you have the latest version of the AWS CLI and Docker
# installed. For more information, see Getting Started with Amazon ECR.

# http://docs.aws.amazon.com/AmazonECR/latest/userguide/getting-started-cli.html

# Use the following steps to authenticate and push an image to your
# repository. For additional registry authentication methods, including
# the Amazon ECR credential helper, see Registry Authentication.

# http://docs.aws.amazon.com/AmazonECR/latest/userguide/Registries.html#registry_auth

# Retrieve an authentication token and authenticate your Docker client
# to your registry.  Use the AWS CLI:

#aws ecr get-login-password --region us-west-2 |docker login --username AWS --password-stdin 045259486626.dkr.ecr.us-west-2.amazonaws.com/drugcentral_db

AWS_ECR_PWD=$(aws ecr get-login-password --region us-west-2)

if [ ! "$AWS_ECR_PWD" ]; then
	echo "Failed get-login-password."
	exit
fi

echo "$AWS_ECR_PWD" |docker login --username AWS --password-stdin 045259486626.dkr.ecr.us-west-2.amazonaws.com/drugcentral_db

#Build your Docker image using the following command. For information
# on building a Docker file from scratch see the instructions here.

# http://docs.aws.amazon.com/AmazonECS/latest/developerguide/docker-basics.html

# You can skip this step if your image is already built:

# docker build -t drugcentral_db .

# After the build completes, tag your image so you can push the image to
# this repository:

docker tag drugcentral_db:latest 045259486626.dkr.ecr.us-west-2.amazonaws.com/drugcentral_db:latest

# Run the following command to push this image to your newly created AWS
# repository:

docker push 045259486626.dkr.ecr.us-west-2.amazonaws.com/drugcentral_db:latest

