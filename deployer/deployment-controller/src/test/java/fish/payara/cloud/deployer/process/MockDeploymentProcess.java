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

package fish.payara.cloud.deployer.process;

import javax.enterprise.event.Event;
import javax.enterprise.event.NotificationOptions;
import javax.enterprise.util.TypeLiteral;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.Assert.assertTrue;

public class MockDeploymentProcess {
    public static final List<StateChanged> FIRED_EVENTS = Collections.synchronizedList(new ArrayList<>());

    private static final ProcessObserver observer = new ProcessObserver();

    private static final Event<StateChanged> mockEvent = new Event<>(){
        @Override
        public void fire(StateChanged event) {
            System.out.println("Fired "+event);
            FIRED_EVENTS.add(event);
            observer.generalObserver(event);
        }

        @Override
        public <U extends StateChanged> CompletionStage<U> fireAsync(U event) {
            fire(event);
            return CompletableFuture.completedFuture(event);
        }

        @Override
        public <U extends StateChanged> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            fire(event);
            return CompletableFuture.completedFuture(event);
        }

        @Override
        public Event<StateChanged> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends StateChanged> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
            return (Event<U>)this;
        }

        @Override
        public <U extends StateChanged> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            return (Event<U>)this;
        }
    };

    private static final DeploymentProcess deploymentProcess = new DeploymentProcess() {{
        deploymentEvent = mockEvent;
    }};

    public static DeploymentProcess get() {
        reset();
        return deploymentProcess;
    }

    public static ProcessObserver observer() {
        return observer;
    }

    public static void reset() {
        observer.reset();
    }

    public void assertFired(ChangeKind kind) {
        assertTrue("At least one change of kind "+kind+" should have fired", FIRED_EVENTS.stream().anyMatch(e -> e.getKind() == kind));
    }

    public static DeploymentProcessState startFixedIDProcess(String id, String name, Namespace namespace, File tempLocation) {
        return get().start(new DeploymentProcessState(id, namespace, name, tempLocation));
    }
}
