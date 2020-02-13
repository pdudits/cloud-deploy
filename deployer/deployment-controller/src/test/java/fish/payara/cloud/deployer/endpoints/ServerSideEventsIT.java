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

package fish.payara.cloud.deployer.endpoints;

import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.ProcessObserver;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.utils.ManagedConcurrencyProducer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;

import java.net.URI;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
public class ServerSideEventsIT {
    private SseEventSource sse;

    @Deployment
    public static WebArchive deployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addPackage(DeploymentProcess.class.getPackage())
                .addPackage(Provisioner.class.getPackage())
                .addPackage(Application.class.getPackage())
                .addClass(ManagedConcurrencyProducer.class);
    }

    @Inject
    DeploymentProcess process;

    @ArquillianResource
    URI uri;

    private CountDownLatch expectMessage = new CountDownLatch(2); // initial message, and fail event

    private static final Logger LOGGER = Logger.getLogger("test");

    @Test
    public void sseEventIsSentOut() throws InterruptedException {
        // this will not be allowed for long, but for now we're good with invalid arguments
        DeploymentProcessState state = process.start(null, null, null);

        var sseEndpoint = ClientBuilder.newClient().target(uri).path("api/deployment").path(state.getId());

        sse = SseEventSource.target(sseEndpoint).build();
        sse.register(this::onMessage);
        sse.open();

        process.fail(state, "A failure", new IllegalArgumentException());
        assertTrue("At least two messages should be received by SSE endpoint", expectMessage.await(3, TimeUnit.SECONDS));
    }

    @After
    public void disconnect() {
        if (sse != null) {
            sse.close();
        }
    }

    private void onMessage(InboundSseEvent event) {
        if (event.isEmpty()) {
            return;
        }
        LOGGER.info("Event: "+event.readData());
        expectMessage.countDown();
    }
}
