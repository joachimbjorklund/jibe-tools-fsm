package jibe.tools.fsm.api.test;

import jibe.tools.fsm.api.test.phone.PhoneFSM;
import jibe.tools.fsm.api.test.phone.events.OffHookEvent;
import jibe.tools.fsm.api.test.phone.states.OnHook;
import jibe.tools.fsm.core.DefaultEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 *
 */
public class FSMTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FSMTest.class);

    @Test
    public void test() throws Exception {
        PhoneFSM fsm = new PhoneFSM();
        DefaultEngine engine = new DefaultEngine(fsm);
        Assert.assertNotNull(engine.context().currentState());
        Assert.assertEquals(engine.context().currentState().getClass(), OnHook.class);

        engine.event("someone picks up the handset");
        Assert.assertEquals(engine.context().currentState(), fsm.stateOffHook);

        engine.event("dial 123");
    }

    @Test
    public void test2() throws Exception {
        PhoneFSM fsm = new PhoneFSM();
        DefaultEngine engine = new DefaultEngine(fsm);
        Assert.assertNotNull(engine.context().currentState());
        Assert.assertEquals(engine.context().currentState().getClass(), OnHook.class);

        engine.event(new OffHookEvent());
        Assert.assertEquals(engine.context().currentState(), fsm.stateOffHook);
    }
}
