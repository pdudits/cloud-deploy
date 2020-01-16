/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fish.payara.cloud.deployer.process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.ws.rs.core.Context;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

/**
 *
 * @author jonathan coustick
 */
@ApplicationScoped
public class DeploymentObserver {

    private ConcurrentHashMap<String, List<SseEventSink>> broadcasts;

    @Context
    private Sse sse;

    public DeploymentObserver() {
        broadcasts = new ConcurrentHashMap<>();
    }

    public void addRequest(SseEventSink eventSink, String processID) {
        List<SseEventSink> sink = broadcasts.getOrDefault(processID, new ArrayList<>());
        sink.add(eventSink);
        broadcasts.put(processID, sink);
    }

    void eventListener(@ObservesAsync StateChanged event) {
        String processID = event.getProcess().getId();

        OutboundSseEvent outboundEvent = sse.newEvent(event.toString());
        System.out.println("");
        System.out.println("processID is" + processID);
        for (SseEventSink sink : broadcasts.get(processID)) {
            sink.send(outboundEvent);
        }
        if (event.isLastEvent()) {
            for (SseEventSink sink : broadcasts.get(processID)) {
                sink.close();
            }
            broadcasts.remove(processID);
        }

    }

}
