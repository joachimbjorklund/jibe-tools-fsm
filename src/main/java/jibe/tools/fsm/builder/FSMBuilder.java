package jibe.tools.fsm.builder;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import jibe.tools.fsm.annotations.State;
import jibe.tools.fsm.annotations.StateMachine;
import jibe.tools.fsm.api.Engine;
import jibe.tools.fsm.core.EngineFactory;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static jibe.tools.fsm.core.DefaultEngine.configurationBuilder;

/**
 *
 */
public class FSMBuilder<T extends StateBuilder> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FSMBuilder.class);

    final String packageName;
    private final CtClass stateMachine;

    private final Map<String, CtClass> stateMap = newHashMap();
    private final Set<T> stateBuilders = Sets.newHashSet();

    public FSMBuilder() {
        packageName = "jibe.tools.fsm.builder." + RandomStringUtils.randomAlphabetic(5);
        ClassPool classPool = ClassPool.getDefault();
        try {
            classPool.makePackage(classPool.getClassLoader(), packageName);
            this.stateMachine = makeClassWithAnnotation(packageName + ".StateMachine", StateMachine.class);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static TransitionBuilder transition(String name) {
        return new TransitionBuilder(name);
    }

    private Class<?> writeFile(CtClass ctClass) {
        try {
            ctClass.writeFile(System.getProperty("java.io.tmpdir"));
            return ctClass.toClass();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public Engine build() {
        try {
            // All states
            Set<StateBuilder.StateFacade> stateFacades = Sets.newHashSet();
            for (StateBuilder sb : stateBuilders) {
                StateBuilder.StateFacade stateFacade = sb.build();
                stateFacades.add(stateFacade);
                String className = packageName + "." + stateFacade.getName();
                stateMap.put(className, makeClassWithAnnotation(className, stateFacade.getAnnotationClass()));
            }
            // Transition States
            Set<TransitionBuilder.TransitionFacade> transitionFacades = Sets.newHashSet();
            for (StateBuilder.StateFacade sf : stateFacades) {
                for (TransitionBuilder.TransitionFacade tf : sf.getTransitions()) {
                    transitionFacades.add(tf);
                    String toState = tf.getToState();
                    String className = packageName + "." + toState;
                    stateMap.put(className, makeClassWithAnnotation(className, State.class));
                }
            }

            // Create the real States
            for (CtClass ctClass : stateMap.values()) {
                ctClass.writeFile(System.getProperty("java.io.tmpdir"));
            }

            return EngineFactory.newInstance()
                    .newEngine(writeFile(stateMachine).newInstance(), configurationBuilder()
                            .classLoader(newClassLoader()));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    CtClass makeClassWithAnnotation(String className, Class<? extends Annotation> annotationClass) {
        if (!className.startsWith(packageName)) {
            className = packageName + "." + className;
        }
        ClassPool cp = ClassPool.getDefault();
        try {
            return cp.get(className);
        } catch (javassist.NotFoundException e) {
        }
        CtClass ctClass = cp.makeClass(className);
        ClassFile classFile = ctClass.getClassFile();
        ConstPool constPool = classFile.getConstPool();
        AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(annotationClass.getName(), constPool);
        attr.setAnnotation(annotation);
        classFile.addAttribute(attr);
        return ctClass;
    }

    void makeTransitionMethod(CtClass declaringClass, CtClass toState, Object event) {
        String methodTemplate
                = "@jibe.tools.fsm.annotations.Transition"
                + "public %s %s(%s event) {"
                + "  %s;"
                + "}";

        //        String methodSrc = String.format(methodTemplate, toState.getName(), methodName, argType, methodBody);
        //        return CtMethod.make(methodSrc, declaringClass);
    }

    private ClassLoader newClassLoader() {
        List<URL> urLs = newArrayList(((URLClassLoader) FSMBuilder.class.getClassLoader()).getURLs());
        try {
            urLs.add(new File(System.getProperty("java.io.tmpdir")).toURI().toURL());

        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return new MyClassLoader(urLs.toArray(new URL[0]));
    }

    public T addStateBuilder(T stateBuilder) {
        stateBuilders.add(stateBuilder);
        return stateBuilder;
    }

    public class MyClassLoader extends URLClassLoader {
        public MyClassLoader(URL[] urls) {
            super(urls);
        }
    }
}
