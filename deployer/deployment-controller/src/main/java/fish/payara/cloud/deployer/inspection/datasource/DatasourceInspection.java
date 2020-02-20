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
import fish.payara.cloud.deployer.inspection.Inspection;
import fish.payara.cloud.deployer.inspection.InspectionException;
import fish.payara.cloud.deployer.utils.Xml;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.enterprise.context.Dependent;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Dependent
class DatasourceInspection implements Inspection {
    private Map<String, DatasourceConfiguration> configs = new HashMap<>();


    @Override
    public void inspect(ArtifactEntry entry, InspectedArtifact artifact) throws IOException {
        if (entry.classpathMatches("META-INF/persistence.xml")) {
            try {
                parseJpa(entry.getInputStream());
            } catch (SAXException | IllegalArgumentException e) {
                throw new InspectionException("Cannot parse persistence.xml", e);
            }
        }
    }

    private void parseJpa(InputStream inputStream) throws IOException, SAXException {
        var persistenceXml = Xml.parse(inputStream);
        var persistenceUnits = Xml.xpath(persistenceXml, "/*[local-name()='persistence']/*[local-name() = 'persistence-unit']");
        for (Node persistenceUnit : persistenceUnits) {
            var ns = persistenceUnit.getNamespaceURI();
            var name = Xml.attr(persistenceUnit, "name", null);
            if (name == null) {
                throw new IllegalArgumentException("Name of the persistence unit not specified");
            }
            var transactionType = Xml.attr(persistenceUnit, "transaction-type", "JTA");
            var datasources = Xml.xpath(persistenceUnit, "*[local-name() = 'jta-data-source' or local-name()='non-jta-data-source']");
            if (datasources.size() == 0 && "JTA".equals(transactionType)) {
                addDefaultDatasourceConfig();
            }
            for (Node datasource : datasources) {
                if ("jta-data-source".equals(datasource.getLocalName()) && "JTA".equals(transactionType)) {
                    addJtaDatasourceConfig(datasource.getTextContent());
                }
                if ("non-jta-data-source".equals(datasource.getLocalName()) && "RESOURCE_LOCAL".equals(transactionType)) {
                    addNonJtaDataSourceConfig(datasource.getTextContent());
                }
            }
        }
    }

    private void addNonJtaDataSourceConfig(String jndiName) {
        addDataSourceConfig(jndiName);
    }

    private void addDataSourceConfig(String jndiName) {
        configs.computeIfAbsent(jndiName, DatasourceConfiguration::new);
    }

    private void addJtaDatasourceConfig(String jndiName) {
        addDataSourceConfig(jndiName);
    }

    private void addDefaultDatasourceConfig() {
        configs.computeIfAbsent(DatasourceConfiguration.DEFAULT_NAME, k -> DatasourceConfiguration.createDefault());
    }


    @Override
    public void finish(InspectedArtifact artifact) {
        configs.values().forEach(artifact::addConfiguration);
    }
}
