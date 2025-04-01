#FROM --platform=linux/amd64 keppel.eu-de-1.cloud.sap/ccloud-dockerhub-mirror/library/openjdk:11-jdk-slim as builder
FROM --platform=linux/amd64 keppel.eu-de-1.cloud.sap/ccloud-dockerhub-mirror/library/openjdk:11-jdk-slim as builder
LABEL source_repository="https://github.com/sapcc/cerebro"

ARG CEREBRO_VERSION=0.9.5


RUN apt-get update && apt upgrade -y && apt-get install -y curl git
RUN cd /
RUN curl -fL https://github.com/coursier/coursier/releases/download/v2.1.23/cs-x86_64-pc-linux.gz | gzip -d > cs && chmod +x cs
RUN ./cs setup -y

COPY . cerebro
RUN . ~/.profile && cd /cerebro && sbt universal:packageZipTarball
RUN cp /cerebro/target/universal/cerebro-${CEREBRO_VERSION}.tgz /opt
RUN mkdir -p /opt/cerebro/logs
RUN tar xzvf /opt/cerebro-${CEREBRO_VERSION}.tgz --strip-components 1 -C /opt/cerebro \
    && sed -i '/<appender-ref ref="FILE"\/>/d' /opt/cerebro/conf/logback.xml

#FROM --platform=linux/amd64 keppel.eu-de-1.cloud.sap/ccloud-dockerhub-mirror/library/openjdk:11-jdk-slim
FROM --platform=linux/amd64 keppel.eu-de-1.cloud.sap/ccloud-dockerhub-mirror/library/sapmachine:11-jdk-ubuntu
LABEL source_repository="https://github.com/sapcc/cerebro"


COPY --from=builder /opt/cerebro /opt/cerebro
RUN apt-get update && apt upgrade -y && apt-get install -y curl
RUN addgroup -gid 1001 cerebro \
    && adduser -q --system --no-create-home --disabled-login -gid 1001 -uid 1001 cerebro \
    && chown -R root:root /opt/cerebro \
    && chown -R cerebro:cerebro /opt/cerebro/logs \
    && chown cerebro:cerebro /opt/cerebro

WORKDIR /opt/cerebro
USER cerebro

COPY application.conf /opt/cerebro/application.conf
ENTRYPOINT [ "/opt/cerebro/bin/cerebro", "-Dconfig.file=/opt/cerebro/application.conf" ]
