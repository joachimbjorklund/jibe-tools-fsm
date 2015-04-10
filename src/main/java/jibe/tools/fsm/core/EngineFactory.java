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

    public <T, E> Engine<T, E> newEngine(T fsm) {
        return new DefaultEngine(fsm);
    }

    public <T, E> Engine<T, E> newEngine(T fsm, DefaultEngine.ConfigurationBuilder builder) {
        return new DefaultEngine(fsm, builder.build());
    }

    public <T, E> Engine<T, E> newEngine(T fsm, Engine.Configuration configuration) {
        return new DefaultEngine(fsm, configuration);
    }
}
