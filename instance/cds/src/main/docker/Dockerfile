# Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
#
# The contents of this file are subject to the terms of either the GNU
# General Public License Version 2 only ("GPL") or the Common Development
# and Distribution License("CDDL") (collectively, the "License").  You
# may not use this file except in compliance with the License.  You can
# obtain a copy of the License at
# https://github.com/payara/Payara/blob/master/LICENSE.txt
# See the License for the specific
# language governing permissions and limitations under the License.
#
# When distributing the software, include this License Header Notice in each
# file and include the License file at glassfish/legal/LICENSE.txt.
#
# GPL Classpath Exception:
# The Payara Foundation designates this particular file as subject to the "Classpath"
# exception as provided by the Payara Foundation in the GPL Version 2 section of the License
# file that accompanied this code.
#
# Modifications:
# If applicable, add the following below the License Header, with the fields
# enclosed by brackets [] replaced by your own identifying information:
# "Portions Copyright [year] [name of copyright owner]"
#
# Contributor(s):
# If you wish your version of this file to be governed by only the CDDL or
# only the GPL Version 2, indicate your decision by adding "[Contributor]
# elects to include this software in this distribution under the [CDDL or GPL
# Version 2] license."  If you don't indicate a single choice of license, a
# recipient has the option to distribute your version of this file under
# either the CDDL, the GPL Version 2 or to extend the choice of license to
# its licensees as provided above.  However, if you add GPL Version 2 code
# and therefore, elected the GPL Version 2 license, then the option applies
# only if the new code is made subject to such option by the copyright
# holder.
FROM azul/zulu-openjdk-alpine:11.0.6 AS base
ENV PAYARA_HOME=/opt/payara\
    DEPLOY_DIR=/opt/payara/deployments

# Create and set the Payara user and working directory owned by the new user
RUN addgroup payara && \
    adduser -D -h ${PAYARA_HOME} -H -s /bin/bash payara -G payara && \
    echo payara:payara | chpasswd && \
    mkdir -p ${PAYARA_HOME} ${DEPLOY_DIR} && \
    chown -R payara:payara ${PAYARA_HOME}

USER payara

WORKDIR ${PAYARA_HOME}

FROM base AS micro_launcher
# Create base image which utilizes root launcher

COPY maven/payara-micro.jar ${PAYARA_HOME}/payara-micro.jar

# 1. Create micro-root/launch-payara.jar
# 2. payara-micro.jar is not needed anymore
RUN java -jar payara-micro.jar --rootdir micro-root --outputlauncher && \
    rm ${PAYARA_HOME}/payara-micro.jar

FROM base AS cds

COPY --from=micro_launcher ${PAYARA_HOME} ${PAYARA_HOME}/
COPY --chown=payara:payara maven/warmup.war maven/postdeploy /tmp/warmup/

# Image that utilizes warmed-up Class Data Sharing archive

# 1. Collect list of classes to put into CDS archive by doing warmup deployment, and then undeploying it
# 2. Create CDS archive payara.jsa
# 3. Cleanup warmup files
RUN java -XX:DumpLoadedClassList=classes.lst -jar micro-root/launch-micro.jar \
         --warmup --deploy /tmp/warmup/warmup.war --postdeploycommandfile /tmp/warmup/postdeploy && \
    java -Xshare:dump -XX:SharedClassListFile=classes.lst -XX:SharedArchiveFile=payara.jsa \
         -jar micro-root/launch-micro.jar && \
    rm -rf /tmp/warmup classes.lst

FROM base AS final_output

COPY --from=cds ${PAYARA_HOME} ${PAYARA_HOME}/

# Run Payara Micro, requiring CDS to be utilized
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=90.0", \
    "-Xshare:on", "-XX:SharedArchiveFile=payara.jsa", \
    "-jar", "micro-root/launch-micro.jar"]
CMD ["--deploymentDir","/opt/payara/deployments"]


