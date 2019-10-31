#
# Copyright © 2019 Apple Inc. and the ServiceTalk project authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ARG centos_version=7
FROM centos:$centos_version
# needed to do again after FROM due to docker limitation
ARG centos_version

# install dependencies
RUN yum install -y \
 apr-devel \
 autoconf \
 automake \
 git \
 glibc-devel \
 libtool \
 lksctp-tools \
 lsb-core \
 make \
 openssl-devel \
 tar \
 wget

ENV LANG en_US.UTF-8

# `java_version` should be 1.x where x indicates the major JDK version, eg. 8, 11, etc.
ARG java_version=1.8
ENV JAVA_VERSION $java_version
# installing java with jabba
RUN curl -sL https://github.com/shyiko/jabba/raw/master/install.sh | bash
RUN echo ". /root/.bashrc ; jabba ls-remote adopt@ --latest=minor | grep @$JAVA_VERSION" | bash > /root/.jabba-jdk
RUN echo ". /root/.bashrc ; jabba install $(cat /root/.jabba-jdk) -o /jdk" | bash
RUN echo 'export JAVA_HOME="/jdk"' >> ~/.bashrc
RUN echo 'PATH=/jdk/bin:$PATH' >> ~/.bashrc
