package jibe.tools.fsm.api.test;

import com.jayway.awaitility.Awaitility;
import com.jayway.awaitility.Duration;
import com.jayway.awaitility.core.ConditionTimeoutException;
import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.api.test.phone.PhoneFSM;
import jibe.tools.fsm.api.test.phone.events.OffHookEvent;
import jibe.tools.fsm.api.test.phone.states.OnHook;
import jibe.tools.fsm.core.EngineFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/**
 *
 */
public class FSMTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FSMTest.class);
    private Engine engine;
    private PhoneFSM fsm;

    @Before
    public void before() {
        fsm = new PhoneFSM();
        engine = EngineFactory.newInstance().newEngine(fsm).start();
    }

    @After
    public void after() {
        engine.stop();
    }

    @Test
    public void test() throws Exception {
        Assert.assertNotNull(engine.currentState());
        Assert.assertEquals(OnHook.class, engine.currentState().getClass());

        engine.event("someone picks up the handset");
        Assert.assertTrue(awaitCurrentState(fsm.stateOffHook));

        engine.event("dial 123");
        Assert.assertTrue(awaitCurrentState(fsm.stateDialing));
    }

    @Test
    public void test2() throws Exception {
        Assert.assertNotNull(engine.currentState());
        Assert.assertEquals(OnHook.class, engine.currentState().getClass());

        engine.event(new OffHookEvent());
        Assert.assertTrue(awaitCurrentState(fsm.stateOffHook));
    }

    private boolean awaitCurrentState(final Object state) {
        try {
            Awaitility.await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    boolean equals = engine.currentState().equals(state);
                    if (!equals) {
                        LOGGER.debug("not yet...");
                    }
                    return equals;
                }
            });
        } catch (ConditionTimeoutException e) {
            return false;
        }
        return true;
    }
}
