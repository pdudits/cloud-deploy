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

package fish.payara.cloud.deployer.inspection;

import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.process.ProcessObserver;
import fish.payara.cloud.deployer.utils.DurationConverter;
import fish.payara.cloud.deployer.utils.ManagedConcurrencyProducer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import static fish.payara.cloud.deployer.inspection.InspectionHelper.write;
import static org.junit.Assert.assertEquals;

@RunWith(Arquillian.class)
public class InspectionIT {
    @Deployment
    public static WebArchive deployment() {
        var shrinkwrap = Maven.resolver().loadPomFromFile("pom.xml").resolve("org.jboss.shrinkwrap:shrinkwrap-impl-base")
                .withTransitivity().asFile();
        return ShrinkWrap.create(WebArchive.class)
                .addPackage(DeploymentProcess.class.getPackage())
                .addPackage(Inspection.class.getPackage())
                .addPackage(ContextRootConfiguration.class.getPackage())
                .addClass(DurationConverter.class)
                .addClass(ManagedConcurrencyProducer.class)
                .addClass(InspectionObserver.class)
                .addAsLibraries(shrinkwrap);
    }

    @Inject
    InspectionObserver observer;

    @Inject
    DeploymentProcess deployment;

    @Test
    public void contextRootIsEventuallyDiscovered() throws InterruptedException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
            .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties"));
        observer.reset();
        deployment.start(testFile, "test-app.war", new Namespace("test","dev"));
        observer.await();
        var contextRoot = observer.getConfiguration(ContextRootConfiguration.KIND).orElseThrow(() -> new AssertionError("Context root should be discovered"));
        assertEquals("/maven-artifact", contextRoot.getValue(ContextRootConfiguration.CONTEXT_ROOT).get());
    }
}
