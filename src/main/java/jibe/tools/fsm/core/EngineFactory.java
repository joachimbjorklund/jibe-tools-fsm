package jibe.tools.fsm.core;

import jibe.tools.fsm.api.Engine;

/**
 *
 */
public class EngineFactory {
    private EngineFactory() {
    }

    public static EngineFactory newInstance() {
        return new EngineFactory();
    }

    public Engine newEngine(Object fsm) {
        return new DefaultEngine(fsm);
    }
}
