package net.minecraft.gametest.framework;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.structures.NbtToSnbt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

public class TestCommand {
    public static final int STRUCTURE_BLOCK_NEARBY_SEARCH_RADIUS = 15;
    public static final int STRUCTURE_BLOCK_FULL_SEARCH_RADIUS = 200;
    public static final int VERIFY_TEST_GRID_AXIS_SIZE = 10;
    public static final int VERIFY_TEST_BATCH_SIZE = 100;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_CLEAR_RADIUS = 200;
    private static final int MAX_CLEAR_RADIUS = 1024;
    private static final int TEST_POS_Z_OFFSET_FROM_PLAYER = 3;
    private static final int SHOW_POS_DURATION_MS = 10000;
    private static final int DEFAULT_X_SIZE = 5;
    private static final int DEFAULT_Y_SIZE = 5;
    private static final int DEFAULT_Z_SIZE = 5;
    private static final String STRUCTURE_BLOCK_ENTITY_COULD_NOT_BE_FOUND = "Structure block entity could not be found";
    private static final TestFinder.Builder<TestCommand.Runner> testFinder = new TestFinder.Builder<>(TestCommand.Runner::new);

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptions(
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
        Function<CommandContext<CommandSourceStack>, TestCommand.Runner> runnerGetter,
        Function<ArgumentBuilder<CommandSourceStack, ?>, ArgumentBuilder<CommandSourceStack, ?>> modifier
    ) {
        return argumentBuilder.executes(context -> runnerGetter.apply(context).run())
            .then(
                Commands.argument("numberOfTimes", IntegerArgumentType.integer(0))
                    .executes(context -> runnerGetter.apply(context).run(new RetryOptions(IntegerArgumentType.getInteger(context, "numberOfTimes"), false)))
                    .then(
                        modifier.apply(
                            Commands.argument("untilFailed", BoolArgumentType.bool())
                                .executes(
                                    context -> runnerGetter.apply(context)
                                        .run(
                                            new RetryOptions(
                                                IntegerArgumentType.getInteger(context, "numberOfTimes"), BoolArgumentType.getBool(context, "untilFailed")
                                            )
                                        )
                                )
                        )
                    )
            );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptions(
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, Function<CommandContext<CommandSourceStack>, TestCommand.Runner> runnerGetter
    ) {
        return runWithRetryOptions(argumentBuilder, runnerGetter, builder -> builder);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> runWithRetryOptionsAndBuildInfo(
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, Function<CommandContext<CommandSourceStack>, TestCommand.Runner> runnerGetter
    ) {
        return runWithRetryOptions(
            argumentBuilder,
            runnerGetter,
            argumentBuilder1 -> argumentBuilder1.then(
                Commands.argument("rotationSteps", IntegerArgumentType.integer())
                    .executes(
                        context -> runnerGetter.apply(context)
                            .run(
                                new RetryOptions(IntegerArgumentType.getInteger(context, "numberOfTimes"), BoolArgumentType.getBool(context, "untilFailed")),
                                IntegerArgumentType.getInteger(context, "rotationSteps")
                            )
                    )
                    .then(
                        Commands.argument("testsPerRow", IntegerArgumentType.integer())
                            .executes(
                                context -> runnerGetter.apply(context)
                                    .run(
                                        new RetryOptions(
                                            IntegerArgumentType.getInteger(context, "numberOfTimes"), BoolArgumentType.getBool(context, "untilFailed")
                                        ),
                                        IntegerArgumentType.getInteger(context, "rotationSteps"),
                                        IntegerArgumentType.getInteger(context, "testsPerRow")
                                    )
                            )
                    )
            )
        );
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder = runWithRetryOptionsAndBuildInfo(
            Commands.argument("onlyRequiredTests", BoolArgumentType.bool()),
            context -> testFinder.failedTests(context, BoolArgumentType.getBool(context, "onlyRequiredTests"))
        );
        ArgumentBuilder<CommandSourceStack, ?> argumentBuilder1 = runWithRetryOptionsAndBuildInfo(
            Commands.argument("testClassName", TestClassNameArgument.testClassName()),
            context -> testFinder.allTestsInClass(context, TestClassNameArgument.getTestClassName(context, "testClassName"))
        );
        dispatcher.register(
            Commands.literal("test")
                .then(
                    Commands.literal("run")
                        .then(
                            runWithRetryOptionsAndBuildInfo(
                                Commands.argument("testName", TestFunctionArgument.testFunctionArgument()),
                                context -> testFinder.byArgument(context, "testName")
                            )
                        )
                )
                .then(
                    Commands.literal("runmultiple")
                        .then(
                            Commands.argument("testName", TestFunctionArgument.testFunctionArgument())
                                .executes(context -> testFinder.byArgument(context, "testName").run())
                                .then(
                                    Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(
                                            context -> testFinder.createMultipleCopies(IntegerArgumentType.getInteger(context, "amount"))
                                                .byArgument(context, "testName")
                                                .run()
                                        )
                                )
                        )
                )
                .then(runWithRetryOptionsAndBuildInfo(Commands.literal("runall").then(argumentBuilder1), testFinder::allTests))
                .then(runWithRetryOptions(Commands.literal("runthese"), testFinder::allNearby))
                .then(runWithRetryOptions(Commands.literal("runclosest"), testFinder::nearest))
                .then(runWithRetryOptions(Commands.literal("runthat"), testFinder::lookedAt))
                .then(runWithRetryOptionsAndBuildInfo(Commands.literal("runfailed").then(argumentBuilder), testFinder::failedTests))
                .then(
                    Commands.literal("verify")
                        .then(
                            Commands.argument("testName", TestFunctionArgument.testFunctionArgument())
                                .executes(context -> testFinder.byArgument(context, "testName").verify())
                        )
                )
                .then(
                    Commands.literal("verifyclass")
                        .then(
                            Commands.argument("testClassName", TestClassNameArgument.testClassName())
                                .executes(
                                    context -> testFinder.allTestsInClass(context, TestClassNameArgument.getTestClassName(context, "testClassName")).verify()
                                )
                        )
                )
                .then(
                    Commands.literal("locate")
                        .then(
                            Commands.argument("testName", TestFunctionArgument.testFunctionArgument())
                                .executes(
                                    context -> testFinder.locateByName(
                                            context, "minecraft:" + TestFunctionArgument.getTestFunction(context, "testName").structureName()
                                        )
                                        .locate()
                                )
                        )
                )
                .then(Commands.literal("resetclosest").executes(context -> testFinder.nearest(context).reset()))
                .then(Commands.literal("resetthese").executes(context -> testFinder.allNearby(context).reset()))
                .then(Commands.literal("resetthat").executes(context -> testFinder.lookedAt(context).reset()))
                .then(
                    Commands.literal("export")
                        .then(
                            Commands.argument("testName", StringArgumentType.word())
                                .executes(context -> exportTestStructure(context.getSource(), "minecraft:" + StringArgumentType.getString(context, "testName")))
                        )
                )
                .then(Commands.literal("exportclosest").executes(context -> testFinder.nearest(context).export()))
                .then(Commands.literal("exportthese").executes(context -> testFinder.allNearby(context).export()))
                .then(Commands.literal("exportthat").executes(context -> testFinder.lookedAt(context).export()))
                .then(Commands.literal("clearthat").executes(context -> testFinder.lookedAt(context).clear()))
                .then(Commands.literal("clearthese").executes(context -> testFinder.allNearby(context).clear()))
                .then(
                    Commands.literal("clearall")
                        .executes(context -> testFinder.radius(context, 200).clear())
                        .then(
                            Commands.argument("radius", IntegerArgumentType.integer())
                                .executes(context -> testFinder.radius(context, Mth.clamp(IntegerArgumentType.getInteger(context, "radius"), 0, 1024)).clear())
                        )
                )
                .then(
                    Commands.literal("import")
                        .then(
                            Commands.argument("testName", StringArgumentType.word())
                                .executes(context -> importTestStructure(context.getSource(), StringArgumentType.getString(context, "testName")))
                        )
                )
                .then(Commands.literal("stop").executes(context -> stopTests()))
                .then(
                    Commands.literal("pos")
                        .executes(context -> showPos(context.getSource(), "pos"))
                        .then(
                            Commands.argument("var", StringArgumentType.word())
                                .executes(context -> showPos(context.getSource(), StringArgumentType.getString(context, "var")))
                        )
                )
                .then(
                    Commands.literal("create")
                        .then(
                            Commands.argument("testName", StringArgumentType.word())
                                .suggests(TestFunctionArgument::suggestTestFunction)
                                .executes(context -> createNewStructure(context.getSource(), StringArgumentType.getString(context, "testName"), 5, 5, 5))
                                .then(
                                    Commands.argument("width", IntegerArgumentType.integer())
                                        .executes(
                                            context -> createNewStructure(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "testName"),
                                                IntegerArgumentType.getInteger(context, "width"),
                                                IntegerArgumentType.getInteger(context, "width"),
                                                IntegerArgumentType.getInteger(context, "width")
                                            )
                                        )
                                        .then(
                                            Commands.argument("height", IntegerArgumentType.integer())
                                                .then(
                                                    Commands.argument("depth", IntegerArgumentType.integer())
                                                        .executes(
                                                            context -> createNewStructure(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "testName"),
                                                                IntegerArgumentType.getInteger(context, "width"),
                                                                IntegerArgumentType.getInteger(context, "height"),
                                                                IntegerArgumentType.getInteger(context, "depth")
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int resetGameTestInfo(GameTestInfo gameTestInfo) {
        gameTestInfo.getLevel().getEntities(null, gameTestInfo.getStructureBounds()).stream().forEach(entity -> entity.remove(Entity.RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD)); // Paper
        gameTestInfo.getStructureBlockEntity().placeStructure(gameTestInfo.getLevel());
        StructureUtils.removeBarriers(gameTestInfo.getStructureBounds(), gameTestInfo.getLevel());
        say(gameTestInfo.getLevel(), "Reset succeded for: " + gameTestInfo.getTestName(), ChatFormatting.GREEN);
        return 1;
    }

    static Stream<GameTestInfo> toGameTestInfos(CommandSourceStack source, RetryOptions retryOptions, StructureBlockPosFinder structureBlockPosFinder) {
        return structureBlockPosFinder.findStructureBlockPos()
            .map(blockPos -> createGameTestInfo(blockPos, source.getLevel(), retryOptions))
            .flatMap(Optional::stream);
    }

    static Stream<GameTestInfo> toGameTestInfo(CommandSourceStack source, RetryOptions retryOptions, TestFunctionFinder testFunctionFinder, int rotationSteps) {
        return testFunctionFinder.findTestFunctions()
            .filter(function -> verifyStructureExists(source.getLevel(), function.structureName()))
            .map(function -> new GameTestInfo(function, StructureUtils.getRotationForRotationSteps(rotationSteps), source.getLevel(), retryOptions));
    }

    private static Optional<GameTestInfo> createGameTestInfo(BlockPos pos, ServerLevel level, RetryOptions retryOptions) {
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)level.getBlockEntity(pos);
        if (structureBlockEntity == null) {
            say(level, "Structure block entity could not be found", ChatFormatting.RED);
            return Optional.empty();
        } else {
            String metaData = structureBlockEntity.getMetaData();
            Optional<TestFunction> optional = GameTestRegistry.findTestFunction(metaData);
            if (optional.isEmpty()) {
                say(level, "Test function for test " + metaData + " could not be found", ChatFormatting.RED);
                return Optional.empty();
            } else {
                TestFunction testFunction = optional.get();
                GameTestInfo gameTestInfo = new GameTestInfo(testFunction, structureBlockEntity.getRotation(), level, retryOptions);
                gameTestInfo.setStructureBlockPos(pos);
                return !verifyStructureExists(level, gameTestInfo.getStructureName()) ? Optional.empty() : Optional.of(gameTestInfo);
            }
        }
    }

    private static int createNewStructure(CommandSourceStack source, String structureName, int x, int y, int z) {
        if (x <= 48 && y <= 48 && z <= 48) {
            ServerLevel level = source.getLevel();
            BlockPos blockPos = createTestPositionAround(source).below();
            StructureUtils.createNewEmptyStructureBlock(structureName.toLowerCase(), blockPos, new Vec3i(x, y, z), Rotation.NONE, level);
            BlockPos blockPos1 = blockPos.above();
            BlockPos blockPos2 = blockPos1.offset(x - 1, 0, z - 1);
            BlockPos.betweenClosedStream(blockPos1, blockPos2).forEach(pos -> level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState()));
            StructureUtils.addCommandBlockAndButtonToStartTest(blockPos, new BlockPos(1, 0, -1), Rotation.NONE, level);
            return 0;
        } else {
            throw new IllegalArgumentException("The structure must be less than 48 blocks big in each axis");
        }
    }

    private static int showPos(CommandSourceStack source, String variableName) throws CommandSyntaxException {
        BlockHitResult blockHitResult = (BlockHitResult)source.getPlayerOrException().pick(10.0, 1.0F, false);
        BlockPos blockPos = blockHitResult.getBlockPos();
        ServerLevel level = source.getLevel();
        Optional<BlockPos> optional = StructureUtils.findStructureBlockContainingPos(blockPos, 15, level);
        if (optional.isEmpty()) {
            optional = StructureUtils.findStructureBlockContainingPos(blockPos, 200, level);
        }

        if (optional.isEmpty()) {
            source.sendFailure(Component.literal("Can't find a structure block that contains the targeted pos " + blockPos));
            return 0;
        } else {
            StructureBlockEntity structureBlockEntity = (StructureBlockEntity)level.getBlockEntity(optional.get());
            if (structureBlockEntity == null) {
                say(level, "Structure block entity could not be found", ChatFormatting.RED);
                return 0;
            } else {
                BlockPos blockPos1 = blockPos.subtract(optional.get());
                String string = blockPos1.getX() + ", " + blockPos1.getY() + ", " + blockPos1.getZ();
                String metaData = structureBlockEntity.getMetaData();
                Component component = Component.literal(string)
                    .setStyle(
                        Style.EMPTY
                            .withBold(true)
                            .withColor(ChatFormatting.GREEN)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy to clipboard")))
                            .withClickEvent(
                                new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "final BlockPos " + variableName + " = new BlockPos(" + string + ");")
                            )
                    );
                source.sendSuccess(() -> Component.literal("Position relative to " + metaData + ": ").append(component), false);
                DebugPackets.sendGameTestAddMarker(level, new BlockPos(blockPos), string, -2147418368, 10000);
                return 1;
            }
        }
    }

    static int stopTests() {
        GameTestTicker.SINGLETON.clear();
        return 1;
    }

    static int trackAndStartRunner(CommandSourceStack source, ServerLevel level, GameTestRunner runner) {
        runner.addListener(new TestCommand.TestBatchSummaryDisplayer(source));
        MultipleTestTracker multipleTestTracker = new MultipleTestTracker(runner.getTestInfos());
        multipleTestTracker.addListener(new TestCommand.TestSummaryDisplayer(level, multipleTestTracker));
        multipleTestTracker.addFailureListener(testInfo -> GameTestRegistry.rememberFailedTest(testInfo.getTestFunction()));
        runner.start();
        return 1;
    }

    static int saveAndExportTestStructure(CommandSourceStack source, StructureBlockEntity structureBlockEntity) {
        String structureName = structureBlockEntity.getStructureName();
        if (!structureBlockEntity.saveStructure(true)) {
            say(source, "Failed to save structure " + structureName);
        }

        return exportTestStructure(source, structureName);
    }

    private static int exportTestStructure(CommandSourceStack source, String structurePath) {
        Path path = Paths.get(StructureUtils.testStructuresDir);
        ResourceLocation resourceLocation = ResourceLocation.parse(structurePath);
        Path path1 = source.getLevel().getStructureManager().createAndValidatePathToGeneratedStructure(resourceLocation, ".nbt");
        Path path2 = NbtToSnbt.convertStructure(CachedOutput.NO_CACHE, path1, resourceLocation.getPath(), path);
        if (path2 == null) {
            say(source, "Failed to export " + path1);
            return 1;
        } else {
            try {
                FileUtil.createDirectoriesSafe(path2.getParent());
            } catch (IOException var7) {
                say(source, "Could not create folder " + path2.getParent());
                LOGGER.error("Could not create export folder", (Throwable)var7);
                return 1;
            }

            say(source, "Exported " + structurePath + " to " + path2.toAbsolutePath());
            return 0;
        }
    }

    private static boolean verifyStructureExists(ServerLevel level, String structure) {
        if (level.getStructureManager().get(ResourceLocation.parse(structure)).isEmpty()) {
            say(level, "Test structure " + structure + " could not be found", ChatFormatting.RED);
            return false;
        } else {
            return true;
        }
    }

    static BlockPos createTestPositionAround(CommandSourceStack source) {
        BlockPos blockPos = BlockPos.containing(source.getPosition());
        int y = source.getLevel().getHeightmapPos(Heightmap.Types.WORLD_SURFACE, blockPos).getY();
        return new BlockPos(blockPos.getX(), y + 1, blockPos.getZ() + 3);
    }

    static void say(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    private static int importTestStructure(CommandSourceStack source, String structurePath) {
        Path path = Paths.get(StructureUtils.testStructuresDir, structurePath + ".snbt");
        ResourceLocation resourceLocation = ResourceLocation.withDefaultNamespace(structurePath);
        Path path1 = source.getLevel().getStructureManager().createAndValidatePathToGeneratedStructure(resourceLocation, ".nbt");

        try {
            BufferedReader bufferedReader = Files.newBufferedReader(path);
            String string = IOUtils.toString(bufferedReader);
            Files.createDirectories(path1.getParent());

            try (OutputStream outputStream = Files.newOutputStream(path1)) {
                NbtIo.writeCompressed(NbtUtils.snbtToStructure(string), outputStream);
            }

            source.getLevel().getStructureManager().remove(resourceLocation);
            say(source, "Imported to " + path1.toAbsolutePath());
            return 0;
        } catch (CommandSyntaxException | IOException var12) {
            LOGGER.error("Failed to load structure {}", structurePath, var12);
            return 1;
        }
    }

    static void say(ServerLevel serverLevel, String message, ChatFormatting formatting) {
        serverLevel.getPlayers(player -> true).forEach(player -> player.sendSystemMessage(Component.literal(message).withStyle(formatting)));
    }

    public static class Runner {
        private final TestFinder<TestCommand.Runner> finder;

        public Runner(TestFinder<TestCommand.Runner> finder) {
            this.finder = finder;
        }

        public int reset() {
            TestCommand.stopTests();
            return TestCommand.toGameTestInfos(this.finder.source(), RetryOptions.noRetries(), this.finder)
                    .map(TestCommand::resetGameTestInfo)
                    .toList()
                    .isEmpty()
                ? 0
                : 1;
        }

        private <T> void logAndRun(Stream<T> structureBlockPos, ToIntFunction<T> testCounter, Runnable onFail, Consumer<Integer> onSuccess) {
            int i = structureBlockPos.mapToInt(testCounter).sum();
            if (i == 0) {
                onFail.run();
            } else {
                onSuccess.accept(i);
            }
        }

        public int clear() {
            TestCommand.stopTests();
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel level = commandSourceStack.getLevel();
            GameTestRunner.clearMarkers(level);
            this.logAndRun(
                this.finder.findStructureBlockPos(),
                pos -> {
                    StructureBlockEntity structureBlockEntity = (StructureBlockEntity)level.getBlockEntity(pos);
                    if (structureBlockEntity == null) {
                        return 0;
                    } else {
                        BoundingBox structureBoundingBox = StructureUtils.getStructureBoundingBox(structureBlockEntity);
                        StructureUtils.clearSpaceForStructure(structureBoundingBox, level);
                        return 1;
                    }
                },
                () -> TestCommand.say(level, "Could not find any structures to clear", ChatFormatting.RED),
                integer -> TestCommand.say(commandSourceStack, "Cleared " + integer + " structures")
            );
            return 1;
        }

        public int export() {
            MutableBoolean mutableBoolean = new MutableBoolean(true);
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel level = commandSourceStack.getLevel();
            this.logAndRun(
                this.finder.findStructureBlockPos(),
                pos -> {
                    StructureBlockEntity structureBlockEntity = (StructureBlockEntity)level.getBlockEntity(pos);
                    if (structureBlockEntity == null) {
                        TestCommand.say(level, "Structure block entity could not be found", ChatFormatting.RED);
                        mutableBoolean.setFalse();
                        return 0;
                    } else {
                        if (TestCommand.saveAndExportTestStructure(commandSourceStack, structureBlockEntity) != 0) {
                            mutableBoolean.setFalse();
                        }

                        return 1;
                    }
                },
                () -> TestCommand.say(level, "Could not find any structures to export", ChatFormatting.RED),
                integer -> TestCommand.say(commandSourceStack, "Exported " + integer + " structures")
            );
            return mutableBoolean.getValue() ? 0 : 1;
        }

        int verify() {
            TestCommand.stopTests();
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel level = commandSourceStack.getLevel();
            BlockPos blockPos = TestCommand.createTestPositionAround(commandSourceStack);
            Collection<GameTestInfo> collection = Stream.concat(
                    TestCommand.toGameTestInfos(commandSourceStack, RetryOptions.noRetries(), this.finder),
                    TestCommand.toGameTestInfo(commandSourceStack, RetryOptions.noRetries(), this.finder, 0)
                )
                .toList();
            GameTestRunner.clearMarkers(level);
            GameTestRegistry.forgetFailedTests();
            Collection<GameTestBatch> collection1 = new ArrayList<>();

            for (GameTestInfo gameTestInfo : collection) {
                for (Rotation rotation : Rotation.values()) {
                    Collection<GameTestInfo> collection2 = new ArrayList<>();

                    for (int i = 0; i < 100; i++) {
                        GameTestInfo gameTestInfo1 = new GameTestInfo(gameTestInfo.getTestFunction(), rotation, level, new RetryOptions(1, true));
                        collection2.add(gameTestInfo1);
                    }

                    GameTestBatch gameTestBatch = GameTestBatchFactory.toGameTestBatch(
                        collection2, gameTestInfo.getTestFunction().batchName(), rotation.ordinal()
                    );
                    collection1.add(gameTestBatch);
                }
            }

            StructureGridSpawner structureGridSpawner = new StructureGridSpawner(blockPos, 10, true);
            GameTestRunner gameTestRunner = GameTestRunner.Builder.fromBatches(collection1, level)
                .batcher(GameTestBatchFactory.fromGameTestInfo(100))
                .newStructureSpawner(structureGridSpawner)
                .existingStructureSpawner(structureGridSpawner)
                .haltOnError(true)
                .build();
            return TestCommand.trackAndStartRunner(commandSourceStack, level, gameTestRunner);
        }

        public int run(RetryOptions retryOptions, int rotationSteps, int testsPerRow) {
            TestCommand.stopTests();
            CommandSourceStack commandSourceStack = this.finder.source();
            ServerLevel level = commandSourceStack.getLevel();
            BlockPos blockPos = TestCommand.createTestPositionAround(commandSourceStack);
            Collection<GameTestInfo> collection = Stream.concat(
                    TestCommand.toGameTestInfos(commandSourceStack, retryOptions, this.finder),
                    TestCommand.toGameTestInfo(commandSourceStack, retryOptions, this.finder, rotationSteps)
                )
                .toList();
            if (collection.isEmpty()) {
                TestCommand.say(commandSourceStack, "No tests found");
                return 0;
            } else {
                GameTestRunner.clearMarkers(level);
                GameTestRegistry.forgetFailedTests();
                TestCommand.say(commandSourceStack, "Running " + collection.size() + " tests...");
                GameTestRunner gameTestRunner = GameTestRunner.Builder.fromInfo(collection, level)
                    .newStructureSpawner(new StructureGridSpawner(blockPos, testsPerRow, false))
                    .build();
                return TestCommand.trackAndStartRunner(commandSourceStack, level, gameTestRunner);
            }
        }

        public int run(int rotationSteps, int testsPerRow) {
            return this.run(RetryOptions.noRetries(), rotationSteps, testsPerRow);
        }

        public int run(int rotationSteps) {
            return this.run(RetryOptions.noRetries(), rotationSteps, 8);
        }

        public int run(RetryOptions retryOptions, int rotationSteps) {
            return this.run(retryOptions, rotationSteps, 8);
        }

        public int run(RetryOptions retryOptions) {
            return this.run(retryOptions, 0, 8);
        }

        public int run() {
            return this.run(RetryOptions.noRetries());
        }

        public int locate() {
            TestCommand.say(this.finder.source(), "Started locating test structures, this might take a while..");
            MutableInt mutableInt = new MutableInt(0);
            BlockPos blockPos = BlockPos.containing(this.finder.source().getPosition());
            this.finder
                .findStructureBlockPos()
                .forEach(
                    pos -> {
                        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)this.finder.source().getLevel().getBlockEntity(pos);
                        if (structureBlockEntity != null) {
                            Direction direction = structureBlockEntity.getRotation().rotate(Direction.NORTH);
                            BlockPos blockPos1 = structureBlockEntity.getBlockPos().relative(direction, 2);
                            int i1 = (int)direction.getOpposite().toYRot();
                            String string = String.format("/tp @s %d %d %d %d 0", blockPos1.getX(), blockPos1.getY(), blockPos1.getZ(), i1);
                            int i2 = blockPos.getX() - pos.getX();
                            int i3 = blockPos.getZ() - pos.getZ();
                            int floor = Mth.floor(Mth.sqrt(i2 * i2 + i3 * i3));
                            Component component = ComponentUtils.wrapInSquareBrackets(
                                    Component.translatable("chat.coordinates", pos.getX(), pos.getY(), pos.getZ())
                                )
                                .withStyle(
                                    style -> style.withColor(ChatFormatting.GREEN)
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, string))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")))
                                );
                            Component component1 = Component.literal("Found structure at: ").append(component).append(" (distance: " + floor + ")");
                            this.finder.source().sendSuccess(() -> component1, false);
                            mutableInt.increment();
                        }
                    }
                );
            int i = mutableInt.intValue();
            if (i == 0) {
                TestCommand.say(this.finder.source().getLevel(), "No such test structure found", ChatFormatting.RED);
                return 0;
            } else {
                TestCommand.say(this.finder.source().getLevel(), "Finished locating, found " + i + " structure(s)", ChatFormatting.GREEN);
                return 1;
            }
        }
    }

    record TestBatchSummaryDisplayer(CommandSourceStack source) implements GameTestBatchListener {
        @Override
        public void testBatchStarting(GameTestBatch batch) {
            TestCommand.say(this.source, "Starting batch: " + batch.name());
        }

        @Override
        public void testBatchFinished(GameTestBatch batch) {
        }
    }

    public record TestSummaryDisplayer(ServerLevel level, MultipleTestTracker tracker) implements GameTestListener {
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) {
        }

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            showTestSummaryIfAllDone(this.level, this.tracker);
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            showTestSummaryIfAllDone(this.level, this.tracker);
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            this.tracker.addTestToTrack(newTest);
        }

        private static void showTestSummaryIfAllDone(ServerLevel level, MultipleTestTracker tracker) {
            if (tracker.isDone()) {
                TestCommand.say(level, "GameTest done! " + tracker.getTotalCount() + " tests were run", ChatFormatting.WHITE);
                if (tracker.hasFailedRequired()) {
                    TestCommand.say(level, tracker.getFailedRequiredCount() + " required tests failed :(", ChatFormatting.RED);
                } else {
                    TestCommand.say(level, "All required tests passed :)", ChatFormatting.GREEN);
                }

                if (tracker.hasFailedOptional()) {
                    TestCommand.say(level, tracker.getFailedOptionalCount() + " optional tests failed", ChatFormatting.GRAY);
                }
            }
        }
    }
}
