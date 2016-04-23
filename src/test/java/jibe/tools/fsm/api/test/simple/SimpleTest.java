package jibe.tools.fsm.api.test.simple;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.core.EngineFactory;
import org.junit.Test;

public class SimpleTest {
    @Test
    public void testSimple() throws Exception {
        Engine<SimpleFSM, Object> engine = EngineFactory.newInstance().newEngine(new SimpleFSM());
        engine.start();

        Awaitility.await()
            .atMost(Duration.ONE_SECOND)
            .pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
            .until(() -> engine.getSnapshot().getCurrentState().isPresent() &&
                             engine.getSnapshot().getCurrentState().get().equals(SimpleFSM.State1.class));

        engine.event("state2");
        Awaitility.await()
            .atMost(Duration.ONE_SECOND)
            .pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
            .until(() -> engine.getSnapshot().getCurrentState().isPresent() &&
                             engine.getSnapshot().getCurrentState().get().equals(SimpleFSM.State2.class));

        engine.event("state1");
        Awaitility.await()
            .atMost(Duration.ONE_SECOND)
            .pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
            .until(() -> engine.getSnapshot().getCurrentState().isPresent() &&
                             engine.getSnapshot().getCurrentState().get().equals(SimpleFSM.State1.class));
    }
}
