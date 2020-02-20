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

import fish.payara.cloud.deployer.inspection.InspectedArtifact;
import fish.payara.cloud.deployer.process.Configuration;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static fish.payara.cloud.deployer.inspection.InspectionHelper.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DatasourceInspectionTest {
    static final String SUN_NS = "http://java.sun.com/xml/ns/persistence";
    static final String JCP_NS = "http://xmlns.jcp.org/xml/ns/persistence/";

    @Test
    public void defaultDatasourceIdentified() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit());
        assertEquals("Without transaction-type and no jta-data-source, default datasource should be used", 1, configs.size());
        var config = configs.get(0);
        assertTrue(config.isComplete());
    }

    @Test
    public void jtaDatasourceIdentified() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withJta());
        assertEquals("Without transaction-type and with jta-data-source, jta datasource should be used", 1, configs.size());
        var config = configs.get(0);
        assertFalse(config.isComplete());
    }

    @Test
    public void jtaDatasourceIdentifiedWithExplicitNamespace() throws IOException {
        var namespacedXML = generate(SUN_NS, new Unit().withJta())
                .replace("xmlns","xmlns:p")
                .replaceAll("<(/?)(\\w)", "<$1p:$2");

        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsResource(new StringAsset(namespacedXML), "META-INF/persistence.xml"));
        var configs = discoveredConfig("test", testFile);
        assertEquals(1, configs.size());
        var config = configs.get(0);
        assertFalse(config.isComplete());
    }


    @Test
    public void nonJtaDatasourceIdentified() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withNonJta().withTransactionType("RESOURCE_LOCAL"));
        assertEquals("With resource local transaction type, non jta datasource should be used",1, configs.size());
        var config = configs.get(0);
        assertFalse(config.isComplete());
    }

    @Test
    public void nonJtaWithoutResourceLocalNotIdentified() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withNonJta());
        assertEquals("Without resource local transaction type and no jta datasource, no datasource should be configured", 0, configs.size());
    }

    @Test
    public void resourceLocalWithoutNonJtaNotIdentified() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withTransactionType("RESOURCE_LOCAL"));
        assertEquals("With resource local transaction type and no non-jta datasource, no datasource should be configured", 0, configs.size());
    }

    @Test
    public void attributeDeterminesWhenBothTypesPresentDefault() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withJta().withNonJta());
        assertEquals("When both datasources are specified, datasource should be picked", 1, configs.size());
        var config = configs.get(0);
        assertTrue("When no transaction type specified, jta datasource should be used", config.getId().endsWith("-jta"));
    }

    @Test
    public void attributeDeterminesWhenBothTypesPresentJta() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withJta().withNonJta().withTransactionType("JTA"));
        assertEquals("When both datasources are specified, datasource should be picked", 1, configs.size());
        var config = configs.get(0);
        assertTrue("When no transaction type specified, jta datasource should be used", config.getId().endsWith("-jta"));
    }

    @Test
    public void attributeDeterminesWhenBothTypesPresentNonJta() throws IOException {
        var configs = discoveredConfig(JCP_NS, new Unit().withJta().withNonJta().withTransactionType("RESOURCE_LOCAL"));
        assertEquals("When both datasources are specified, datasource should be picked", 1, configs.size());
        var config = configs.get(0);
        assertTrue("When no transaction type specified, jta datasource should be used", config.getId().endsWith("-nonjta"));
    }

    @Test
    public void multiplePersistenceUnitsWithDefaultDatasourceIdentified() throws IOException {
        var conf = discoveredConfig(JCP_NS, new Unit(), new Unit());
        assertEquals("One config per datasource should be found", 1, conf.size());
    }

    @Test
    public void multiplePersistenceUnitsIdentified() throws IOException {
        var conf = discoveredConfig(JCP_NS, new Unit().withJta(), new Unit().withNonJta());
        assertEquals("One config per datasource should be found", 1, conf.size());
    }

    @Test
    public void multiplePersistenceUnitsWithSingleDatasourceIdentified() throws IOException {
        var conf = discoveredConfig(JCP_NS, new Unit().withJta("jdbc/main"), new Unit().withJta("jdbc/main"));
        assertEquals("One config per datasource should be found", 1, conf.size());
    }

    @Test
    public void jpa20namespaceIdentified() throws IOException {
        var configs = discoveredConfig(SUN_NS, new Unit().withJta());
        assertEquals(1, configs.size());
        var config = configs.get(0);
        assertFalse(config.isComplete());
    }

    static class Unit {
        String name = UUID.randomUUID().toString();
        String transactionType;
        String jtaDatasourceName;
        String nonJtaDatasourceName;

        Unit withTransactionType(String transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        Unit withJta() {
            this.jtaDatasourceName = "jdbc/"+name+"-jta";
            return this;
        }

        Unit withJta(String name) {
            this.jtaDatasourceName = name;
            return this;
        }

        Unit withNonJta() {
            this.nonJtaDatasourceName = "jdbc/"+name+"-nonjta";
            return this;
        }
    }

    private List<Configuration> discoveredConfig(String namespace, Unit... units) throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsResource(new StringAsset(generate(namespace, units)), "META-INF/persistence.xml"));
        return discoveredConfig("test", testFile);
    }


    private String generate(String namespace, Unit... units) {
        StringBuilder xmlBuilder = new StringBuilder("<?xml version='1.0'?>\n");
        xmlBuilder.append("<persistence xmlns='").append(namespace).append("'>\n");
        for (Unit unit : units) {
            xmlBuilder.append("<persistence-unit name='").append(unit.name).append("'");
            if (unit.transactionType != null) {
                xmlBuilder.append(" transaction-type='").append(unit.transactionType).append("'");
            }
            xmlBuilder.append(">\n");
            if (unit.jtaDatasourceName != null) {
                xmlBuilder.append("  <jta-data-source>").append(unit.jtaDatasourceName).append("</jta-data-source>\n");
            }
            if (unit.nonJtaDatasourceName != null) {
                xmlBuilder.append("  <non-jta-data-source>").append(unit.nonJtaDatasourceName).append("</non-jta-data-source>\n");
            }
            xmlBuilder.append("</persistence-unit>\n");
        }
        xmlBuilder.append("</persistence>");
        return xmlBuilder.toString();
    }

    private List<Configuration> discoveredConfig(String name, File testFile) throws IOException {
        var inspection = new DatasourceInspection();
        var artifact = InspectedArtifact.inspect(name, new ZipFile(testFile), List.of(inspection));
        return artifact.getConfigurations().stream().filter(c -> c.getKind().equals(DatasourceConfiguration.KIND))
                .collect(Collectors.toList());
    }
}
