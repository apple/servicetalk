FROM redhat/ubi9

ARG java_version=17.0.19-zulu
ENV JAVA_VERSION=$java_version

RUN dnf install -y \
    procps-ng \
    git \
    openssl \
    tar \
    unzip \
    wget \
    zip \
    zlib-devel \
    && dnf clean all

# Downloading and installing SDKMAN!
RUN curl -s "https://get.sdkman.io?ci=true" | bash

# Installing Java removing some unnecessary SDKMAN files
RUN bash -c "source /root/.sdkman/bin/sdkman-init.sh && \
    sdk list java | grep zulu && \
    yes | sdk install java $JAVA_VERSION && \
    rm -rf /root/.sdkman/archives/* && \
    rm -rf /root/.sdkman/tmp/*"

RUN echo 'export JAVA_HOME="/root/.sdkman/candidates/java/current"' >> ~/.bashrc
RUN echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc
