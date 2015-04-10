package jibe.tools.fsm.builder;

import com.google.common.base.Throwables;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
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

import static com.google.common.collect.Lists.newArrayList;
import static jibe.tools.fsm.core.DefaultEngine.configurationBuilder;

/**
 *
 */
public class FSMBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(FSMBuilder.class);

    final String packageName;
    private final CtClass stateMachine;
    private final List<StateBuilder> stateBuilders = newArrayList();

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

    public static Class<?> writeFile(CtClass ctClass) {
        try {
            ctClass.writeFile(System.getProperty("java.io.tmpdir"));
            return ctClass.toClass();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public StateBuilder state(String name) {
        return add(new StateBuilder(this, name));
    }

    public StartStateBuilder startState(String name) {
        return add(new StartStateBuilder(this, name));
    }

    private <T extends StateBuilder> T add(T stateBuilder) {
        stateBuilders.add(stateBuilder);
        return stateBuilder;
    }

    public Engine build() {
        try {
            for (StateBuilder sb : stateBuilders) {
                sb.build();
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

    public class MyClassLoader extends URLClassLoader {
        public MyClassLoader(URL[] urls) {
            super(urls);
        }
    }
}
