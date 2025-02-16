package net.minecraft.gametest.framework;

import com.mojang.brigadier.context.CommandContext;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

public class TestFinder<T> implements StructureBlockPosFinder, TestFunctionFinder {
    static final TestFunctionFinder NO_FUNCTIONS = Stream::empty;
    static final StructureBlockPosFinder NO_STRUCTURES = Stream::empty;
    private final TestFunctionFinder testFunctionFinder;
    private final StructureBlockPosFinder structureBlockPosFinder;
    private final CommandSourceStack source;
    private final Function<TestFinder<T>, T> contextProvider;

    @Override
    public Stream<BlockPos> findStructureBlockPos() {
        return this.structureBlockPosFinder.findStructureBlockPos();
    }

    TestFinder(
        CommandSourceStack source,
        Function<TestFinder<T>, T> contextProvider,
        TestFunctionFinder testFunctionFinder,
        StructureBlockPosFinder structureBlockPosFinder
    ) {
        this.source = source;
        this.contextProvider = contextProvider;
        this.testFunctionFinder = testFunctionFinder;
        this.structureBlockPosFinder = structureBlockPosFinder;
    }

    T get() {
        return this.contextProvider.apply(this);
    }

    public CommandSourceStack source() {
        return this.source;
    }

    @Override
    public Stream<TestFunction> findTestFunctions() {
        return this.testFunctionFinder.findTestFunctions();
    }

    public static class Builder<T> {
        private final Function<TestFinder<T>, T> contextProvider;
        private final UnaryOperator<Supplier<Stream<TestFunction>>> testFunctionFinderWrapper;
        private final UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper;

        public Builder(Function<TestFinder<T>, T> contextProvider) {
            this.contextProvider = contextProvider;
            this.testFunctionFinderWrapper = supplier -> supplier;
            this.structureBlockPosFinderWrapper = supplier -> supplier;
        }

        private Builder(
            Function<TestFinder<T>, T> contextProvider,
            UnaryOperator<Supplier<Stream<TestFunction>>> testFunctionFinderWrapper,
            UnaryOperator<Supplier<Stream<BlockPos>>> structureBlockPosFinderWrapper
        ) {
            this.contextProvider = contextProvider;
            this.testFunctionFinderWrapper = testFunctionFinderWrapper;
            this.structureBlockPosFinderWrapper = structureBlockPosFinderWrapper;
        }

        public TestFinder.Builder<T> createMultipleCopies(int count) {
            return new TestFinder.Builder<>(this.contextProvider, createCopies(count), createCopies(count));
        }

        private static <Q> UnaryOperator<Supplier<Stream<Q>>> createCopies(int count) {
            return supplier -> {
                List<Q> list = new LinkedList<>();
                List<Q> list1 = ((Stream)supplier.get()).toList();

                for (int i = 0; i < count; i++) {
                    list.addAll(list1);
                }

                return list::stream;
            };
        }

        private T build(CommandSourceStack source, TestFunctionFinder testFunctionFinder, StructureBlockPosFinder structureBlockPosFinder) {
            return new TestFinder<>(
                    source,
                    this.contextProvider,
                    this.testFunctionFinderWrapper.apply(testFunctionFinder::findTestFunctions)::get,
                    this.structureBlockPosFinderWrapper.apply(structureBlockPosFinder::findStructureBlockPos)::get
                )
                .get();
        }

        public T radius(CommandContext<CommandSourceStack> context, int radius) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findStructureBlocks(blockPos, radius, commandSourceStack.getLevel())
            );
        }

        public T nearest(CommandContext<CommandSourceStack> context) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.findNearestStructureBlock(blockPos, 15, commandSourceStack.getLevel()).stream()
            );
        }

        public T allNearby(CommandContext<CommandSourceStack> context) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack, TestFinder.NO_FUNCTIONS, () -> StructureUtils.findStructureBlocks(blockPos, 200, commandSourceStack.getLevel())
            );
        }

        public T lookedAt(CommandContext<CommandSourceStack> context) {
            CommandSourceStack commandSourceStack = context.getSource();
            return this.build(
                commandSourceStack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.lookedAtStructureBlockPos(
                    BlockPos.containing(commandSourceStack.getPosition()), commandSourceStack.getPlayer().getCamera(), commandSourceStack.getLevel()
                )
            );
        }

        public T allTests(CommandContext<CommandSourceStack> context) {
            return this.build(
                context.getSource(),
                () -> GameTestRegistry.getAllTestFunctions().stream().filter(testFunction -> !testFunction.manualOnly()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T allTestsInClass(CommandContext<CommandSourceStack> context, String className) {
            return this.build(
                context.getSource(),
                () -> GameTestRegistry.getTestFunctionsForClassName(className).filter(testFunction -> !testFunction.manualOnly()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T failedTests(CommandContext<CommandSourceStack> context, boolean onlyRequired) {
            return this.build(
                context.getSource(),
                () -> GameTestRegistry.getLastFailedTests().filter(testFunction -> !onlyRequired || testFunction.required()),
                TestFinder.NO_STRUCTURES
            );
        }

        public T byArgument(CommandContext<CommandSourceStack> context, String argumentName) {
            return this.build(context.getSource(), () -> Stream.of(TestFunctionArgument.getTestFunction(context, argumentName)), TestFinder.NO_STRUCTURES);
        }

        public T locateByName(CommandContext<CommandSourceStack> context, String name) {
            CommandSourceStack commandSourceStack = context.getSource();
            BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
            return this.build(
                commandSourceStack,
                TestFinder.NO_FUNCTIONS,
                () -> StructureUtils.findStructureByTestFunction(blockPos, 1024, commandSourceStack.getLevel(), name)
            );
        }

        public T failedTests(CommandContext<CommandSourceStack> context) {
            return this.failedTests(context, false);
        }
    }
}
