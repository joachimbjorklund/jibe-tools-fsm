package jibe.tools.fsm.api.test.mediaplayer;

import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.annotations.Transition;

/**
 *
 */
@StateMachine
public class MediaPlayerFSM {

    private final MediaPlayerFacade mediaPlayer;
    @StartState
    StateIdle stateIdle;
    @State
    StatePreparing statePreparing;
    @State
    StateInitialized stateInitialized;
    @State
    StatePrepared statePrepared;
    @State
    StateStarted stateStarted;
    @State
    StatePaused statePaused;
    @State
    StateStopped stateStopped;
    @State
    StatePlaybackCompleted statePlaybackCompleted;
    @State
    StateError stateError;
    @State
    StateEnd stateEnd;

    public MediaPlayerFSM(MediaPlayerFacade mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }

    public StateIdle getStartState() {
        return stateIdle;
    }

    public static class MediaPlayerFacade {
        private String dataSource;

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }
    }

    public class StateIdle {
        @Transition
        public StateInitialized setDataSource(String dataSource) {
            mediaPlayer.setDataSource(dataSource);
            return stateInitialized;
        }
    }

    public class StateInitialized {
    }

    public class StatePrepared {
    }

    public class StateStarted {
    }

    public class StatePaused {
    }

    public class StateStopped {
    }

    public class StatePreparing {
    }

    public class StatePlaybackCompleted {
    }

    public class StateError {
    }

    public class StateEnd {
    }
}
