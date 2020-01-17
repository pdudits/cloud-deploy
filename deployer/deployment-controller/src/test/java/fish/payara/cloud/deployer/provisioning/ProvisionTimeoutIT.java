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

package fish.payara.cloud.deployer.provisioning;

import fish.payara.cloud.deployer.artifactstorage.ArtifactStorage;
import fish.payara.cloud.deployer.artifactstorage.TempArtifactStorage;
import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.process.ProcessObserver;
import fish.payara.cloud.deployer.utils.ManagedConcurrencyProducer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.File;

@RunWith(Arquillian.class)
public class ProvisionTimeoutIT {
    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addPackage(DeploymentProcess.class.getPackage())
                .addClass(ProcessObserver.class)
                .addClass(ProvisioningController.class)
                .addClass(ArtifactStorage.class)
                .addClass(TempArtifactStorage.class)
                .addClass(ManagedConcurrencyProducer.class)
                .addAsResource(new StringAsset("provision.timeout=PT2S"), "META-INF/microprofile-config.properties");
    }

    @Inject
    DeploymentProcess deployment;

    @Inject
    ProcessObserver observer;

    @Test
    public void provisionWithoutActivityTimesOut() {
        DeploymentProcessState process = deployment.start(new File("target/test.war"), "test.war", new Namespace("test", "dev"));
        // submittings configs triggers provisioning
        observer.reset();
        deployment.submitConfigurations(process);
        observer.await(ChangeKind.PROVISION_STARTED);
        // and without further activity it will fail
        observer.await(ChangeKind.FAILED);
    }
}
