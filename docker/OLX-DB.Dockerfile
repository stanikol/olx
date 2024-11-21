FROM eclipse-temurin:17.0.13_11-jre-jammy

RUN apt-get update && apt-get install -y unzip wget

RUN wget --output-document=h2-2019-10-14.zip https://h2database.com/h2-2019-10-14.zip && \
    unzip h2-2019-10-14.zip -d /opt

COPY h2.server.properties /root/.h2.server.properties

EXPOSE 8081 1521


ENV PATH="${PATH}:/opt/h2/bin"
WORKDIR /olx/db
CMD java -cp /opt/h2/bin/h2*.jar org.h2.tools.Server \
 	-web -webAllowOthers -webPort 8081 \
 	-tcp -tcpAllowOthers -tcpPort 1521 \
 	-baseDir ${DATA_DIR} ${H2_OPTIONS}