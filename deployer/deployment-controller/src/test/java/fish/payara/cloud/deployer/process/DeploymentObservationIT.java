/*
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.cloud.deployer.process;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class DeploymentObservationIT {
    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addPackage(DeploymentProcess.class.getPackage())
                .addClass(ProcessObserver.class);
    }

    @Inject
    DeploymentProcess process;

    @Inject
    ProcessObserver observer;

    @Before
    public void resetObserver() {
        observer.reset();
    }

    @Test
    public void generalAndSpecificObserversAreInvoked() {
        observer.expect(1);
        // this will not be allowed for long, but for now we're good with invalid arguments
        process.start(null, null, null);
        observer.await();
        assertEquals("PROCESS_STARTED observer should receive an event", 1, observer.getProcessStart());
    }

    @Test
    public void failedDeploymentIsComplete() {
        observer.expect(2);
        // this will not be allowed for long, but for now we're good with invalid arguments
        DeploymentProcessState state = process.start(null, null, null);
        process.fail(state, "A failure", new IllegalArgumentException());
        observer.await();
        state = observer.getLastProcess();
        assertTrue("Process should be complete", state.isComplete());
        assertTrue("Process should be marked as failed", state.isFailed());
        assertNotNull("Process should contain completion time", state.getCompletion());
        assertEquals("PROCESS_STARTED observer should receive an event", 1, observer.getProcessStart());
    }

    @Test
    public void provisionedDeploymentIsComplete() {
        // this will not be allowed for long, but for now we're good with invalid arguments
        DeploymentProcessState state = process.start(null, null, null);
        process.provisioningFinished(state);
        observer.await(ChangeKind.PROVISION_FINISHED);
        state = observer.getLastProcess();
        assertTrue("Process should be complete", state.isComplete());
        assertFalse("Process should not be marked as failed", state.isFailed());
        assertNotNull("Process should contain completion time", state.getCompletion());
        
        process.delete(state);
        observer.await(ChangeKind.DELETION_STARTED);
        state = observer.getLastProcess();
        assertNull("Location of artifact has been deleted", state.getPersistentLocation());
        assertNotNull("Process should contain completion time", state.getCompletion());
    }
}
