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

import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.StateChanged;
import org.junit.Before;
import org.junit.Test;
import org.mockito.verification.VerificationMode;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static fish.payara.cloud.deployer.process.ProcessAccessor.createProcess;
import static fish.payara.cloud.deployer.process.ProcessAccessor.makeEvent;
import static org.mockito.Mockito.*;

public class ProvisionMonitorTest {

    ProvisioningController controller;
    private DeploymentProcessState process;
    private StateChanged event;

    @Before
    public void prepareController() {
        controller = new ProvisioningController();
        controller.executorService = Executors.newScheduledThreadPool(1);
        controller.inactivityTimeout = Duration.ofMillis(100);
        controller.process = mock(DeploymentProcess.class);

        process = createProcess();
        event = makeEvent(process, ChangeKind.PROVISION_STARTED);
        controller.startMonitoring(event);
    }

    @Test
    public void provisionTimesOutWithoutActivity() {
        verifyFail(timeout(150));
    }

    @Test
    public void activityPostponesTimeout() {
        var activity = controller.executorService.scheduleAtFixedRate(
                () -> controller.monitorProgress(makeEvent(process, ChangeKind.POD_CREATED)),
                25, 50, TimeUnit.MILLISECONDS);

        verifyFail(after(150).never());
        activity.cancel(true);
        verifyFail(timeout(150));
    }

    @Test
    public void failedCancelsMonitoring() {
        controller.monitorProgress(makeEvent(process, ChangeKind.FAILED));
        verifyFail(after(150).never());
    }

    @Test
    public void provisionFinishedCancelsMonitoring() {
        controller.monitorProgress(makeEvent(process, ChangeKind.PROVISION_FINISHED));
        verifyFail(after(150).never());
    }

    @Test
    public void cleanupStartedCancelsMonitoring() {
        controller.monitorProgress(makeEvent(process, ChangeKind.CLEANUP_STARTED));
        verifyFail(after(150).never());
    }

    private void verifyFail(VerificationMode mode) {
        verify(controller.process, mode).fail(eq(process), any(), any());
    }
}
