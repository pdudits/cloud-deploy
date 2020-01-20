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

import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import fish.payara.cloud.deployer.provisioning.ProvisioningException;
import org.junit.Test;

import java.net.URI;

import static org.mockito.Mockito.mock;

/**
 * The test assumes, that you've got active kubectl context, which gets picked up by DefaultKubernetesClient.
 * It will create namespace test-dev, and single set of app resources inside.
 *
 */
public class CreateTestManual {
    @Test
    public void createDirectly() throws ProvisioningException {
        var process = ProcessAccessor.createProcessWithName("ROOT.war");
        ProcessAccessor.setPersistentLocation(process, URI.create("https://cloud3.blob.core.windows.net/deployment/micro1/ROOT.war"));

        var contextRoot = new ContextRootConfiguration("ROOT.war", "ui", "/");
        ProcessAccessor.addConfiguration(process, contextRoot);

        var provisoner = new DirectProvisioner();
        provisoner.domain = "9ba44192900145a88bfb.westeurope.aksapp.io";
        provisoner.process = mock(DeploymentProcess.class);

        provisoner.connectApiServer();

        provisoner.provision(process);
    }
}
