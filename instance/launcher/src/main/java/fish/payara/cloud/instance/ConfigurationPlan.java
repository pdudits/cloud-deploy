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

package fish.payara.cloud.instance;

import fish.payara.cloud.instance.tasks.ConfigurationTask;
import fish.payara.cloud.instance.tasks.DataSource;
import fish.payara.cloud.instance.tasks.Deployment;
import fish.payara.cloud.instance.tasks.TaskVisitor;
import fish.payara.cloud.instance.tasks.value.FileValue;
import fish.payara.cloud.instance.tasks.value.StringValue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Sequence of configuration tasks to be applied to an instance
 * @see #plan(List)
 */
public class ConfigurationPlan {
    private final List<ConfigurationTask> tasks;

    private ConfigurationPlan(List<ConfigurationTask> tasks) {

        this.tasks = tasks;
    }

    /**
     * Process parsed configuration tasks, creating an ordered sequence of them
     * @param tasks
     * @return
     */
    public static ConfigurationPlan plan(List<ConfigurationTask> tasks) {
        var sortedTasks = new ArrayList<>(tasks);
        Collections.sort(sortedTasks, Comparator.comparingInt(ConfigurationTask::getPriority));

        // apply any other inter-task rules
        // (none right now)

        var plan = new ConfigurationPlan(sortedTasks);
        return plan;
    }


    /**
     * Apply the plan via an applicator
     * @param planApplicator
     */
    public void execute(Applicator planApplicator) {
        var visitor = new Visitor(planApplicator);
        tasks.forEach(task -> task.accept(visitor));
    }

    /**
     * Visitor translates configuration tasks to applicator invocations.
     */
    class Visitor implements TaskVisitor {
        private final Applicator applicator;

        Visitor(Applicator applicator) {
            this.applicator = applicator;
        }

        @Override
        public void deployment(Deployment deployment) {
            applicator.addDeployment(deployment.getArtifact().fileName(), deployment.getContextRoot().value());
        }

        @Override
        public void microprofileConfigProperties(FileValue file) {
            Properties p = new Properties();
            try {
                p.load(new StringReader(file.contents()));
                p.entrySet().forEach(e -> applicator.addSystemProperty(e.getKey().toString(), e.getValue().toString()));
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read properties from "+file.fileName(), e);
            }
        }

        @Override
        public void microprofileConfigValues(Map<String, StringValue> map) {
            map.entrySet().forEach(e -> applicator.addSystemProperty(e.getKey(), e.getValue().value()));
        }

        @Override
        public void dataSource(DataSource.DataSourceDefinition dataSource) {
            applicator.addPostBootCommand("create-jdbc-connection-pool",
                    "--datasourceclassname", dataSource.datasourceClass(),
                     "--restype", dataSource.resourceType(),
                     "--steadypoolsize", String.valueOf(dataSource.steadyPoolSize()),
                     "--maxpoolsize", String.valueOf(dataSource.maxPoolSize()),
                     "--maxwait", String.valueOf(dataSource.maxWaitTime()),
                     "--property", constructProperties(dataSource),
                    dataSource.poolName());

            if (dataSource.isDefaultDataSource()) {
                applicator.addPostBootCommand("set", "resources.jdbc-resource.jdbc/__default.pool-name="+dataSource.poolName());
            } else {
                applicator.addPostBootCommand("create-jdbc-resource", "--connectionpoolid", dataSource.poolName(), dataSource.jndiName());
            }
        }

        private String constructProperties(DataSource.DataSourceDefinition dataSource) {
            var propertyString = new StringBuilder();
            propertyString.append("url=")
                    .append(escapeProperty(dataSource.jdbcUrl()));
            if (dataSource.user().isPresent()) {
                propertyString.append(":user=")
                        .append(escapeProperty(dataSource.user().get()));
            }
            if (dataSource.password().isPresent()) {
                propertyString.append(":password=")
                        .append(escapeProperty(dataSource.password().get()));
            }
            return propertyString.toString();
        }
    }

    static String escapeProperty(String jdbcUrl) {
        return jdbcUrl.replaceAll("([=:\\\\])","\\\\$1");
    }
}
