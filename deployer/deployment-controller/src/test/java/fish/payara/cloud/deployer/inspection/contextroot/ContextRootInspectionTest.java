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
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipFile;

import static fish.payara.cloud.deployer.inspection.InspectionHelper.write;
import static org.junit.Assert.assertEquals;

public class ContextRootInspectionTest {

    @Test
    public void fileNameDeterminesContextRoot() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .add(new StringAsset("<html></html>"), "index.html"));
        var root = configuredContextRoot("from-filename", testFile);
        assertEquals("/from-filename", root.get());
    }

    @Test
    public void rootFileNameDeterminesRootContext() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties"));
        var root = configuredContextRoot("ROOT.war", testFile);
        assertEquals("/", root.get());
    }

    @Test
    public void oneMavenMetaDeterminesContextRoot() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties"));
        var root = configuredContextRoot("from-filename", testFile);
        assertEquals("/maven-artifact", root.get());
    }

    @Test
    public void multipleMavenMetaFallbackToFileName() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties")
                .addAsManifestResource(new StringAsset("artifactId=shaded-artifact"), "maven/fish.payara.cloud.test/shaded-artifact/pom.properties"));
        var root = configuredContextRoot("from-filename", testFile);
        assertEquals("/from-filename", root.get());
    }

    @Test
    public void deploymentDescriptorDeterminesContextRoot() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsWebInfResource(new StringAsset("<!DOCTYPE glassfish-web-app PUBLIC \n" +
                        "    \"-//GlassFish.org//DTD GlassFish Application Server 3.1 Servlet 3.0//EN\"\n" +
                        "    \"http://glassfish.org/dtds/glassfish-web-app_3_0-1.dtd\">\n" +
                        "<glassfish-web-app>\n" +
                        "    <context-root>/descriptor</context-root>\n" +
                        "</glassfish-web-app>"), "glassfish-web.xml"));
        var root = configuredContextRoot("from-filename", testFile);
        assertEquals("/descriptor", root.get());
    }

    @Test
    public void missingDeploymentDescriptorFallsbackToMaven() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties")
                .addAsWebInfResource(new StringAsset("<!DOCTYPE glassfish-web-app PUBLIC \n" +
                        "    \"-//GlassFish.org//DTD GlassFish Application Server 3.1 Servlet 3.0//EN\"\n" +
                        "    \"http://glassfish.org/dtds/glassfish-web-app_3_0-1.dtd\">\n" +
                        "<glassfish-web-app>\n" +
                        "    <not-context-root>/descriptor</not-context-root>\n" +
                        "</glassfish-web-app>"), "glassfish-web.xml"));
        var root = configuredContextRoot("from-filename", testFile);
        assertEquals("/maven-artifact", root.get());
    }

    @Test
    public void dDeploymentDescriptorOverridesMaven() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties")
                .addAsWebInfResource(new StringAsset("<!DOCTYPE glassfish-web-app PUBLIC \n" +
                        "    \"-//GlassFish.org//DTD GlassFish Application Server 3.1 Servlet 3.0//EN\"\n" +
                        "    \"http://glassfish.org/dtds/glassfish-web-app_3_0-1.dtd\">\n" +
                        "<glassfish-web-app>\n" +
                        "    <context-root>/descriptor</context-root>\n" +
                        "</glassfish-web-app>"), "glassfish-web.xml"));
        var root = configuredContextRoot("from-filename", testFile);
        assertEquals("/descriptor", root.get());
    }

    private Optional<String> configuredContextRoot(String name, File testFile) throws IOException {
        var inspection = new ContextRootInspection();
        var artifact = InspectedArtifact.inspect(name, new ZipFile(testFile), List.of(inspection));
        var config = artifact.getConfigurations().stream().filter(c -> c.getKind().equals(ContextRootConfiguration.KIND))
                .findAny().orElseThrow(() -> new AssertionError("context root configuration should be present"));
        return config.getValue(ContextRootConfiguration.CONTEXT_ROOT);
    }

}
