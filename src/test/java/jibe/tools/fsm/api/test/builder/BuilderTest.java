package jibe.tools.fsm.api.test.builder;

import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.builder.FSMBuilder;
import jibe.tools.fsm.builder.StartStateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jibe.tools.fsm.builder.FSMBuilder.transition;

/**
 *
 */
public class BuilderTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuilderTest.class);

    public static void main(String[] args) {
        FSMBuilder fsmBuilder = new FSMBuilder();

        new StartStateBuilder(fsmBuilder, "Start")
                .transitions(
                        transition("t1").toState("StateA").onEvent("a"),
                        transition("t2").toState("Start").onEvent("start"))
                .onEnter("e -> println(e)");

        Engine engine = fsmBuilder.build();

        engine.start();

        engine.event("a");
    }
}
