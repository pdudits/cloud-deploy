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

package fish.payara.cloud.instance.tasks;

import com.fasterxml.jackson.annotation.JsonTypeName;
import fish.payara.cloud.instance.tasks.value.StringValue;

import java.util.Optional;

@JsonTypeName("dataSource")
public class DataSource extends ConfigurationTask {
    public static final String DEFAULT_DATA_SOURCE = "java:comp/DefaultDataSource";
    private StringValue jndiName;
    private StringValue steadyPoolSize;
    private StringValue maxPoolSize;
    private StringValue maxWaitTime;
    private StringValue jdbcUrl;
    private StringValue user;
    private StringValue password;
    private StringValue poolName;
    private StringValue resourceType;
    private StringValue datasourceClass;

    public StringValue getJndiName() {
        return jndiName;
    }

    public void setJndiName(StringValue jndiName) {
        this.jndiName = jndiName;
    }

    public StringValue getSteadyPoolSize() {
        return steadyPoolSize;
    }

    public void setSteadyPoolSize(StringValue steadyPoolSize) {
        this.steadyPoolSize = steadyPoolSize;
    }

    public StringValue getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(StringValue maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public StringValue getMaxWaitTime() {
        return maxWaitTime;
    }

    public void setMaxWaitTime(StringValue maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    public StringValue getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(StringValue jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public StringValue getUser() {
        return user;
    }

    public void setUser(StringValue user) {
        this.user = user;
    }

    public StringValue getPassword() {
        return password;
    }

    public void setPassword(StringValue password) {
        this.password = password;
    }

    public StringValue getPoolName() {
        return poolName;
    }

    public void setPoolName(StringValue poolName) {
        this.poolName = poolName;
    }

    public StringValue getResourceType() {
        return resourceType;
    }

    public void setResourceType(StringValue resourceType) {
        this.resourceType = resourceType;
    }

    public StringValue getDatasourceClass() {
        return datasourceClass;
    }

    public void setDatasourceClass(StringValue datasourceClass) {
        this.datasourceClass = datasourceClass;
    }

    private static int defaultedInt(int defaultValue, StringValue stringValue) {
        if (stringValue == null) {
            return defaultValue;
        }
        var value = stringValue.value();
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static String defaultedString(String defaultValue, StringValue value) {
        if (value == null || value.value() == null || value.value().isBlank()) {
            return defaultValue;
        }
        return value.value();
    }

    @Override
    public void accept(TaskVisitor visitor) {
        visitor.dataSource(toDefinition());
    }

    public DataSourceDefinition toDefinition() {
        return new DataSourceDefinition() {
            @Override
            public String jndiName() {
                return defaultedString(DEFAULT_DATA_SOURCE, getJndiName());
            }

            @Override
            public boolean isDefaultDataSource() {
                return DEFAULT_DATA_SOURCE.equals(jndiName());
            }

            @Override
            public int steadyPoolSize() {
                return defaultedInt(1, getSteadyPoolSize());
            }

            @Override
            public int maxPoolSize() {
                return defaultedInt(10, getMaxPoolSize());
            }

            @Override
            public int maxWaitTime() {
                return defaultedInt(30000, getMaxWaitTime());
            }

            @Override
            public String jdbcUrl() {
                return getJdbcUrl().value();
            }

            @Override
            public Optional<String> user() {
                return getJdbcUrl() == null ? Optional.empty() : Optional.of(getJdbcUrl().value());
            }

            @Override
            public Optional<String> password() {
                return getPassword() == null ? Optional.empty() : Optional.of(getPassword().value());
            }

            @Override
            public String poolName() {
                return getPoolName().value();
            }

            @Override
            public String resourceType() {
                return defaultedString("javax.sql.DataSource", getResourceType());
            }

            @Override
            public String datasourceClass() {
                return defaultedString("org.h2.jdbcx.JdbcDataSource", getDatasourceClass());
            }
        };
    }

    public interface DataSourceDefinition {
        String jndiName();

        boolean isDefaultDataSource();

        int steadyPoolSize();

        int maxPoolSize();

        int maxWaitTime();

        String jdbcUrl();

        Optional<String> user();

        Optional<String> password();

        String poolName();

        String resourceType();

        String datasourceClass();
    }
}
