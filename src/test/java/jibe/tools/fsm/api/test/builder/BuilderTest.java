package jibe.tools.fsm.api.test.builder;

import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.builder.FSMBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jibe.tools.fsm.builder.TransitionBuilder.transition;

/**
 *
 */
public class BuilderTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuilderTest.class);

    public static void main(String[] args) {
        Engine engine = new FSMBuilder()
                .startState("Start").transitions(
                        transition().onEvent("a").to("A"))
                .build();
        engine.start();
    }
}
