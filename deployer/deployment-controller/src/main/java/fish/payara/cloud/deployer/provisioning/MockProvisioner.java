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

import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.setup.MockProvisioning;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@MockProvisioning
@ApplicationScoped
class MockProvisioner implements Provisioner {
    @Inject
    ScheduledExecutorService delay;

    @Inject
    @ConfigProperty(name = "provisioning.mock.fail-after", defaultValue = "PT5S")
    Duration failDelay;

    @Inject
    DeploymentProcess process;

    @Override
    public void provision(DeploymentProcessState deployment) throws ProvisioningException {
        if (deployment.getName().contains("fail")) {
            delay.schedule(() -> this.failDeployment(deployment), failDelay.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            step(0.5, () -> process.endpointDetermined(deployment, URI.create("http://mock-deployment")));
            step(1, () -> process.podCreated(deployment, "mock", "mock-pod-349sa4d"));
            for (int i=0; i<20; i++) {
                var step = i;
                step(1.5+i*0.2, () -> process.outputLogged(deployment, "log message "+step+"\n"));
            }
            step(3, () -> process.deploymentFinished(deployment));
            step(5, () -> process.endpointActivated(deployment));
        }
    }

    private void step(double number, Runnable action) {
        delay.schedule(action, (long)number*1000, TimeUnit.MILLISECONDS);
    }

    private void failDeployment(DeploymentProcessState deployment) {
        process.fail(deployment, "Provisioning is not enabled in this configuration", null);
    }

    @Override
    public List<Namespace> getNamespaces() {
        return List.of(new Namespace("foo", "bar"));   
    }
    
    @Override
    public DeploymentProcessState delete(DeploymentProcessState deployment) {
        return process.deletionFinished(deployment);
    }

    @Override
    public Map<String, List<String>> getDeploymentsWithIngress(Namespace namespaceId) {
        return Map.of("foo", List.of("http://www.example.com"));
    }
}
