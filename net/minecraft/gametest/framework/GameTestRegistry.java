package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

public class GameTestRegistry {
    private static final Collection<TestFunction> TEST_FUNCTIONS = Lists.newArrayList();
    private static final Set<String> TEST_CLASS_NAMES = Sets.newHashSet();
    private static final Map<String, Consumer<ServerLevel>> BEFORE_BATCH_FUNCTIONS = Maps.newHashMap();
    private static final Map<String, Consumer<ServerLevel>> AFTER_BATCH_FUNCTIONS = Maps.newHashMap();
    private static final Set<TestFunction> LAST_FAILED_TESTS = Sets.newHashSet();

    public static void register(Class<?> testClass) {
        Arrays.stream(testClass.getDeclaredMethods()).sorted(Comparator.comparing(Method::getName)).forEach(GameTestRegistry::register);
    }

    public static void register(Method testMethod) {
        String simpleName = testMethod.getDeclaringClass().getSimpleName();
        GameTest gameTest = testMethod.getAnnotation(GameTest.class);
        if (gameTest != null) {
            TEST_FUNCTIONS.add(turnMethodIntoTestFunction(testMethod));
            TEST_CLASS_NAMES.add(simpleName);
        }

        GameTestGenerator gameTestGenerator = testMethod.getAnnotation(GameTestGenerator.class);
        if (gameTestGenerator != null) {
            TEST_FUNCTIONS.addAll(useTestGeneratorMethod(testMethod));
            TEST_CLASS_NAMES.add(simpleName);
        }

        registerBatchFunction(testMethod, BeforeBatch.class, BeforeBatch::batch, BEFORE_BATCH_FUNCTIONS);
        registerBatchFunction(testMethod, AfterBatch.class, AfterBatch::batch, AFTER_BATCH_FUNCTIONS);
    }

    private static <T extends Annotation> void registerBatchFunction(
        Method testMethod, Class<T> annotationType, Function<T, String> valueGetter, Map<String, Consumer<ServerLevel>> positioning
    ) {
        T annotation = testMethod.getAnnotation(annotationType);
        if (annotation != null) {
            String string = valueGetter.apply(annotation);
            Consumer<ServerLevel> consumer = positioning.putIfAbsent(string, (Consumer<ServerLevel>)turnMethodIntoConsumer(testMethod));
            if (consumer != null) {
                throw new RuntimeException("Hey, there should only be one " + annotationType + " method per batch. Batch '" + string + "' has more than one!");
            }
        }
    }

    public static Stream<TestFunction> getTestFunctionsForClassName(String className) {
        return TEST_FUNCTIONS.stream().filter(testFunction -> isTestFunctionPartOfClass(testFunction, className));
    }

    public static Collection<TestFunction> getAllTestFunctions() {
        return TEST_FUNCTIONS;
    }

    public static Collection<String> getAllTestClassNames() {
        return TEST_CLASS_NAMES;
    }

    public static boolean isTestClass(String className) {
        return TEST_CLASS_NAMES.contains(className);
    }

    public static Consumer<ServerLevel> getBeforeBatchFunction(String functionName) {
        return BEFORE_BATCH_FUNCTIONS.getOrDefault(functionName, serverLevel -> {});
    }

    public static Consumer<ServerLevel> getAfterBatchFunction(String functionName) {
        return AFTER_BATCH_FUNCTIONS.getOrDefault(functionName, serverLevel -> {});
    }

    public static Optional<TestFunction> findTestFunction(String testName) {
        return getAllTestFunctions().stream().filter(testFunction -> testFunction.testName().equalsIgnoreCase(testName)).findFirst();
    }

    public static TestFunction getTestFunction(String testName) {
        Optional<TestFunction> optional = findTestFunction(testName);
        if (optional.isEmpty()) {
            throw new IllegalArgumentException("Can't find the test function for " + testName);
        } else {
            return optional.get();
        }
    }

    private static Collection<TestFunction> useTestGeneratorMethod(Method testMethod) {
        try {
            Object instance = testMethod.getDeclaringClass().newInstance();
            return (Collection<TestFunction>)testMethod.invoke(instance);
        } catch (ReflectiveOperationException var2) {
            throw new RuntimeException(var2);
        }
    }

    private static TestFunction turnMethodIntoTestFunction(Method testMethod) {
        GameTest gameTest = testMethod.getAnnotation(GameTest.class);
        String simpleName = testMethod.getDeclaringClass().getSimpleName();
        String string = simpleName.toLowerCase();
        String string1 = string + "." + testMethod.getName().toLowerCase();
        String string2 = gameTest.template().isEmpty() ? string1 : string + "." + gameTest.template();
        String string3 = gameTest.batch();
        Rotation rotationForRotationSteps = StructureUtils.getRotationForRotationSteps(gameTest.rotationSteps());
        return new TestFunction(
            string3,
            string1,
            string2,
            rotationForRotationSteps,
            gameTest.timeoutTicks(),
            gameTest.setupTicks(),
            gameTest.required(),
            gameTest.manualOnly(),
            gameTest.requiredSuccesses(),
            gameTest.attempts(),
            gameTest.skyAccess(),
            (Consumer<GameTestHelper>)turnMethodIntoConsumer(testMethod)
        );
    }

    private static Consumer<?> turnMethodIntoConsumer(Method testMethod) {
        return object -> {
            try {
                Object instance = testMethod.getDeclaringClass().newInstance();
                testMethod.invoke(instance, object);
            } catch (InvocationTargetException var3) {
                if (var3.getCause() instanceof RuntimeException) {
                    throw (RuntimeException)var3.getCause();
                } else {
                    throw new RuntimeException(var3.getCause());
                }
            } catch (ReflectiveOperationException var4) {
                throw new RuntimeException(var4);
            }
        };
    }

    private static boolean isTestFunctionPartOfClass(TestFunction testFunction, String className) {
        return testFunction.testName().toLowerCase().startsWith(className.toLowerCase() + ".");
    }

    public static Stream<TestFunction> getLastFailedTests() {
        return LAST_FAILED_TESTS.stream();
    }

    public static void rememberFailedTest(TestFunction testFunction) {
        LAST_FAILED_TESTS.add(testFunction);
    }

    public static void forgetFailedTests() {
        LAST_FAILED_TESTS.clear();
    }
}
