package jibe.tools.fsm.api;

/**
 *
 */
public interface Engine {
    void event(Object event);

    Context context();
}
