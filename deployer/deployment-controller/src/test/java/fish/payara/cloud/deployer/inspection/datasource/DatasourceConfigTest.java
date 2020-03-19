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

package fish.payara.cloud.deployer.inspection.datasource;

import fish.payara.cloud.deployer.process.ConfigurationValidationException;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DatasourceConfigTest {
    @Test
    public void defaultDatasourceIsComplete() {
        var conf = DatasourceConfiguration.createDefault();
        assertTrue(conf.isComplete());
        assertFalse(conf.hasOverrides());
    }

    @Test
    public void nonDefaultDatasourceRequiresUrl() {
        var conf = new DatasourceConfiguration("jdbc/main");
        assertFalse(conf.isComplete());
        ProcessAccessor.updateConfiguration(conf, Map.of("jdbcUrl", "jdbc:h2:mem"));
        assertTrue(conf.isComplete());
        assertTrue(conf.hasOverrides());
    }

    @Test(expected = ConfigurationValidationException.class)
    public void changeToDefaultDataSourceRequiresUrlNegative() {
        var conf = DatasourceConfiguration.createDefault();
        ProcessAccessor.updateConfiguration(conf, Map.of("maxWaitTime", "40000"));
    }

    @Test
    public void changeToDefaultDataSourceRequiresUrlPositive() {
        var conf = DatasourceConfiguration.createDefault();
        ProcessAccessor.updateConfiguration(conf, Map.of("maxWaitTime", "40000", "jdbcUrl", "jdbc:h2:mem"));
        ProcessAccessor.updateConfiguration(conf, Map.of("maxWaitTime", "30000"));
    }

    @Test
    public void testDefaultValue() {
        var conf = DatasourceConfiguration.createDefault();
        var value = DatasourceConfiguration.createValue(conf);
        assertEquals("java_comp_DefaultDataSource", value.poolName());
        assertEquals(1, value.steadyPoolSize());
        assertEquals(10, value.maxPoolSize());
        assertEquals(30000, value.maxWaitTime());
        assertEquals(Optional.empty(), value.password());
        assertEquals(Optional.empty(), value.user());
        assertNotNull(value.datasourceClass());
        assertNotNull(value.jdbcUrl());
        assertNotNull(value.resourceType());
    }
}
