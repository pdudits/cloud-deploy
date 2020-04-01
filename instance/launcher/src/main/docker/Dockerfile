FROM @docker.java.image@ AS os-base

# Default payara ports to expose
EXPOSE 6900 8080

# Configure environment variables
ENV PAYARA_HOME=/opt/payara\
    CONFIG_DIR=/config

# Create and set the Payara user and working directory owned by the new user
RUN true \
    && addgroup --gid 1000 payara \
    && adduser --system --uid 1000 --no-create-home --shell /bin/bash --home "${PAYARA_HOME}" --gecos "" --ingroup payara payara \
    && echo payara:payara | chpasswd \
    && mkdir -p ${PAYARA_HOME} ${CONFIG_DIR} \
    && chown -R payara:payara ${PAYARA_HOME} \
    && true

USER payara
WORKDIR ${PAYARA_HOME}

FROM os-base AS prepare-root

COPY maven/payara-micro.jar .
COPY maven/launcher.jar .

RUN true \
    && java -jar payara-micro.jar --rootdir micro/ --outputlauncher \
    && java -jar launcher.jar --outputlauncher micro/ \
    && java -jar micro/launch-micro.jar --warmup --nocluster \
    && true



FROM os-base as final

COPY --from=prepare-root ${PAYARA_HOME}/micro/ ${PAYARA_HOME}/

# Default command to run
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=90.0", "-jar", "cloud-instance.jar"]
# Cannot refer to CONFIG_DIR, because it's not launched in a shell
CMD /config
