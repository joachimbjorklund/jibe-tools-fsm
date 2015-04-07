# Jibe Tools Fsm

## Annotation-driven StateMachine (FSM)
With annotations you define your state-machine. No interfaces to implement... Just plain Java...

###Install
Add Jibe Tools Fsm to your project. for maven projects just add this dependency:
```xml
<dependency>
    <groupId>jibe</groupId>
    <artifactId>jibe-tools-fsm</artifactId>
    <version>0.9.0</version>
</dependency>
```
And this repository:
```xml
<repositories>
  <repository>
    <id>repository-jibe</id>
      <url>http://nexus.jibe.nu/nexus/content/repositories/public</url>
  </repository>
</repositories>
```
Or clone with
```
git clone https://github.com/joachimbjorklund/jibe-tools-fsm.git
```
And build it your self... (much more fun)

###Usage
First you have to define your machine:
```java
import jibe.tools.fsm.annotations.StateMachine;

@StateMachine
public class MyFSM {

    //- those guys will be "injected"
    //
    @State
    private Start startState;
    
    @State
    NextState nextState;

    @State
    AnotherState anotherState;
}
```
And some states:
```java
import jibe.tools.fsm.annotations.StartState;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.Transition;
import jibe.tools.fsm.annotations.Action;
import jibe.tools.fsm.api.ActionType;
@StartState // required to have a starting state...
public class Start {

    @State
    private NextState nextState;

    @Action(ActionType.OnEnter) // executes when entering the state
    public void onEnter(MyFSM fsm) { // if you want your machine...
        System.out.println("I'm in...");
    }

    @Action(ActionType.OnExit) // executes when exiting the state
    public void onExit() {
        System.out.println("Now I'm out...");
    }

    @Transition
    public NextState nextState(String event) { // event could be any type of object
        if ("nextState".equals(event)){
            return this.nextState;
        }
        return null; // return null to "guard" the transition ie no transition will take place
    }

}
    
import...
@State
public class NextState {
    @State
    private AnotherState anotherState;

    @Transition
    public AnotherState anotherState(AnotherStateEvent event) { // like this...
        return this.anotherState;
    }
}
@State
public class AnothertState {
    // ...
}
public class AnotherStateEvent {
}

```
And run it...

```java
public static void main(String[] args) {
    MyFSM fsm = new MyFSM();
    Engine engine = EngineFactory.newInstance().newEngine(fsm).start();

    engine.event("nextState"); // this pushes the event on the event-queue
    assert engine.getCurrentState().equals(fsm.nextState); // might have to wait for the transition to tke place depending

    engine.event(new AnotherStateEvent()); // this pushes the event on the event-queue
    assert engine.getCurrentState().equals(fsm.anotherState); // might have to wait for the transition to tke place depending
    
    // stop the engine
    engine.stop();
}
```

That's all folks!

_Cheers_