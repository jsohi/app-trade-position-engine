package com.bofa.equity.position;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.ShutdownSignalBarrier;

// PicoContainer creates one instance of this class per Cucumber scenario.
// Placed in com.bofa.equity.position for package-private access to PositionAggregator.positionData().
public class PipelineWorld {
    public MediaDriver mediaDriver;
    public Aeron aeron;
    public Subscription subscription;
    public Publication publication;
    public AgentRunner sendRunner;
    public AgentRunner receiveRunner;
    public PositionAggregator aggregator;
    public ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
    public int totalTradeCount = 0;
}
