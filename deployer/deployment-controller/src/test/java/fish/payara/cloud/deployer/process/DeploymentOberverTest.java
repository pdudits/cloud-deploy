/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *  Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
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
 *  file and include the License.
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

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jonathan coustick
 */
public class DeploymentOberverTest {
    
    @Test
    public void obervationTest() throws IllegalAccessException, NoSuchFieldException {
        DeploymentObserver observer = new DeploymentObserver();
        
        Field sseField = observer.getClass().getDeclaredField("sse");
        sseField.setAccessible(true);
        sseField.set(observer, new EventBuilder());
        EventSink sink = new EventSink();
        DeploymentProcessState state = new DeploymentProcessState(new Namespace("p", "s"), "foo", null);
        observer.addRequest(sink, state.getId());
        StateChanged event = new StateChanged(state, ChangeKind.PROVISION_FINISHED);
        observer.eventListener(event);
        Assert.assertTrue(sink.isClosed());
        
    }
    
    private class EventSink implements SseEventSink {
        
        private boolean messageSent;

        @Override
        public boolean isClosed() {
            return messageSent;
        }

        @Override
        public CompletionStage<?> send(OutboundSseEvent event) {
            messageSent = true;
            return null;
        }

        @Override
        public void close() {
            if (!messageSent) {
                Assert.fail("No message sent");
            }
        }
        
    }
    
    private class EventBuilder implements Sse {

        @Override
        public OutboundSseEvent.Builder newEventBuilder() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public OutboundSseEvent newEvent(String data) {
            return new OutboundSseEvent() {
                @Override
                public Class<?> getType() {
                    return String.class;
                }
                public Type getGenericType() {
                    return String.class;
                }

                @Override
                public MediaType getMediaType() {
                    return MediaType.SERVER_SENT_EVENTS_TYPE;
                }

                @Override
                public Object getData() {
                    return data;
                }

                @Override
                public String getId() {
                    return "foo";
                }

                @Override
                public String getName() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }

                @Override
                public String getComment() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }

                @Override
                public long getReconnectDelay() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }

                @Override
                public boolean isReconnectDelaySet() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }
            };
        }

        @Override
        public SseBroadcaster newBroadcaster() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
}
