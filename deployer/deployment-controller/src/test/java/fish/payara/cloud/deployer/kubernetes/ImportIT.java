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

import com.fasterxml.jackson.databind.ObjectMapper;
import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.inspection.datasource.DatasourceConfiguration;
import fish.payara.cloud.deployer.inspection.mpconfig.MicroprofileConfiguration;
import fish.payara.cloud.deployer.kubernetes.crd.WebAppCustomResource;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.Namespace;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.IOException;

import static fish.payara.cloud.deployer.ArquillianDeployments.compose;
import static fish.payara.cloud.deployer.ArquillianDeployments.configuration;
import static fish.payara.cloud.deployer.ArquillianDeployments.fabric8;
import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class ImportIT {
    @Inject
    DeploymentProcess process;

    @Deployment
    public static WebArchive deployment() {
        return compose(configuration(), fabric8())
                .addAsResource("consumer.json")
                .addAsResource("consumer-manual.json")
                .addPackage(WebAppCustomResource.class.getPackage())
                ;
    }

    @Test
    public void complexAppImportable() throws IOException {
        var objectMapper = new ObjectMapper();
        var resource = objectMapper.readValue(getClass().getResource("/consumer.json"), WebAppCustomResource.class);
        var imported = process.importPersisted(resource.asPersistedDeployment(new Namespace("test", "dev")));

        assertEquals("consumer-app", imported.getName());
        assertEquals("eddbc170-a836-448f-a73a-8a1c7ba077bd", imported.getId());
        assertEquals("https://cloud3.blob.core.windows.net/deployment/test/eddbc170-a836-448f-a73a-8a1c7ba077bd/consumer-app.war", imported.getPersistentLocation().toString());

        var contextRoot = imported.findConfigurations(ContextRootConfiguration.KIND).findAny()
                .orElseThrow(() -> new AssertionError("Context root configuration should be present"));
        assertEquals("/consumer", contextRoot.getValue(ContextRootConfiguration.CONTEXT_ROOT).get());
        assertEquals("/consumer-app", contextRoot.getDefaultValue(ContextRootConfiguration.CONTEXT_ROOT).get());

        var mpConfig = imported.findConfigurations(MicroprofileConfiguration.KIND).findAny().orElseThrow(
                () -> new AssertionError("MP Config configuration should be present")
        );
        assertEquals(1, mpConfig.getKeys().size());

        var datasource = imported.findConfigurations(DatasourceConfiguration.KIND).findAny().orElseThrow(
                () -> new AssertionError("Datasource configuration should be present")
        );
        assertEquals("java:comp/DefaultDataSource", datasource.getId());
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDataSource", datasource.getValue("datasourceClass").get());
        assertEquals("java_comp_DefaultDataSource", datasource.getValue("poolName").get());
    }

    @Test
    public void complexAppWithoutDefaultsImportable() throws IOException {
        var objectMapper = new ObjectMapper();
        var resource = objectMapper.readValue(getClass().getResource("/consumer-manual.json"), WebAppCustomResource.class);
        var imported = process.importPersisted(resource.asPersistedDeployment(new Namespace("test", "dev")));

        assertEquals("consumer-app", imported.getName());
        assertEquals("cfc57c14-6f4e-11ea-97f6-ee0487eec6b0", imported.getId());
        assertEquals("https://cloud3.blob.core.windows.net/deployment/test/eddbc170-a836-448f-a73a-8a1c7ba077bd/consumer-app.war", imported.getPersistentLocation().toString());

        var contextRoot = imported.findConfigurations(ContextRootConfiguration.KIND).findAny()
                .orElseThrow(() -> new AssertionError("Context root configuration should be present"));
        assertEquals("/consumer", contextRoot.getValue(ContextRootConfiguration.CONTEXT_ROOT).get());

        var mpConfig = imported.findConfigurations(MicroprofileConfiguration.KIND).findAny().orElseThrow(
                () -> new AssertionError("MP Config configuration should be present")
        );
        assertEquals(1, mpConfig.getKeys().size());

        var datasource = imported.findConfigurations(DatasourceConfiguration.KIND).findAny().orElseThrow(
                () -> new AssertionError("Datasource configuration should be present")
        );
        assertEquals("java:comp/DefaultDataSource", datasource.getId());
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDataSource", datasource.getValue("datasourceClass").get());
        assertEquals("java_comp_DefaultDataSource", datasource.getValue("poolName").get());
    }
}
