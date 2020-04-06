/*
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */

package fish.payara.cloud.deployer.kubernetes;

import fish.payara.cloud.deployer.inspection.datasource.DatasourceConfiguration;
import fish.payara.cloud.deployer.process.Configuration;

import java.util.Optional;
import java.util.stream.Stream;

class DatasourcePostbootCommands {
    private final StringBuilder postboot = new StringBuilder();

    public void addDatasource(Configuration dsConfig) {
        var values = DatasourceConfiguration.Value.forConfiguration(dsConfig);
        postboot.append("create-jdbc-connection-pool")
                .append(" --datasourceclassname ")
                .append(values.datasourceClass())
                .append(" --restype ")
                .append(values.resourceType())
                .append(" --steadypoolsize ")
                .append(values.steadyPoolSize())
                .append(" --maxpoolsize ")
                .append(values.maxPoolSize())
                .append(" --maxwait ")
                .append(values.maxWaitTime())
                .append(" --property ")
                .append("url=")
                .append(escapeProperty(values.jdbcUrl()));

        if (values.user().isPresent()) {
            postboot.append(":user=")
                    .append(escapeProperty(values.user().get()));
        }
        if (values.password().isPresent()) {
            postboot.append(":password=")
                    .append(escapeProperty(values.password().get()));
        }
        postboot.append(" ")
                .append(values.poolName())
                .append("\n");

        if (dsConfig.getId().equals(DatasourceConfiguration.DEFAULT_NAME)) {
            postboot.append("set resources.jdbc-resource.jdbc/__default.pool-name=")
                    .append(values.poolName());
        } else {
            postboot.append("create-jdbc-resource")
                    .append(" --connectionpoolid ")
                    .append(dsConfig.getId())
                    .append(" ")
                    .append(dsConfig.getId())
                    .append("\n");
        }
    }

    static String escapeProperty(String jdbcUrl) {
        return jdbcUrl.replaceAll("([=:\\\\])","\\\\$1");
    }

    @Override
    public String toString() {
        return this.postboot.toString();
    }
}
