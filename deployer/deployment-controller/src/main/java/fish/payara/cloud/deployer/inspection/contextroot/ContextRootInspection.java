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

package fish.payara.cloud.deployer.inspection.contextroot;

import fish.payara.cloud.deployer.inspection.InspectedArtifact;
import fish.payara.cloud.deployer.inspection.Inspection;
import fish.payara.cloud.deployer.inspection.InspectionException;
import fish.payara.cloud.deployer.utils.Xml;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.enterprise.context.Dependent;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathNodes;
import java.io.IOException;
import java.util.Properties;

@Dependent
class ContextRootInspection implements Inspection {

    enum MavenMetaDiscovered {
        NONE,
        SINGLE,
        MULTIPLE
    }

    private MavenMetaDiscovered mavenMetaDiscovered = MavenMetaDiscovered.NONE;
    private String mavenArtifactId;
    private String descriptorRoot;

    @Override
    public void inspect(ArtifactEntry entry, InspectedArtifact artifact) throws IOException {
        if (entry.pathMatches("META-INF/maven/[^/]+/[^/]+/pom.properties")) {
            inspectPomProperties(entry);
        } else if (entry.pathMatches("WEB-INF/glassfish-web.xml")) {
            inspectGlassfishDescriptor(entry);
        }
    }

    private void inspectGlassfishDescriptor(ArtifactEntry entry) throws IOException {

        try {
            Document document = Xml.parse(entry.getInputStream());
            XPathNodes result = Xml.xpath(document, "/glassfish-web-app/context-root/text()");
            if (result.size() == 1) {
                descriptorRoot = result.get(0).getTextContent();
            }
        } catch (SAXException | XPathException e) {
            throw new InspectionException("Failed to parse glassfish-web.xml", e);
        }
    }

    private void inspectPomProperties(ArtifactEntry entry) throws IOException {
        if (mavenMetaDiscovered == MavenMetaDiscovered.NONE) {
            mavenMetaDiscovered = MavenMetaDiscovered.SINGLE;
            Properties properties = new Properties();
            properties.load(entry.getInputStream());
            this.mavenArtifactId = properties.getProperty("artifactId");
        } else {
            mavenMetaDiscovered = MavenMetaDiscovered.MULTIPLE;
        }
    }

    @Override
    public void finish(InspectedArtifact artifact) {
        var contextRoot = determineContextRoot(artifact);
        var appName = determineAppName(artifact);
        var config = new ContextRootConfiguration(artifact.getName(), appName, contextRoot);
        artifact.addConfiguration(config);
    }

    private String determineAppName(InspectedArtifact artifact) {
        // use maven artifact id, if exactly one is present
        if (mavenMetaDiscovered == MavenMetaDiscovered.SINGLE) {
            return mavenArtifactId;
        }
        // use file name without extension otherwise
        return artifact.getName().replaceFirst("\\.\\w+$","");
    }

    private String determineContextRoot(InspectedArtifact artifact) {
        // use context root from descriptor if present
        if (descriptorRoot != null) {
            return descriptorRoot;
        }
        // ROOT.war will refer to root
        if (artifact.getName().equals("ROOT.war")) {
            return "/";
        }
        // use maven artifact id, if exactly one is present
        if (mavenMetaDiscovered == MavenMetaDiscovered.SINGLE) {
            return "/"+mavenArtifactId;
        }
        // use file name without extension otherwise
        return "/"+artifact.getName().replaceFirst("\\.\\w+$","");
    }
}
