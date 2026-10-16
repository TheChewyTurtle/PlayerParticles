package dev.esophose.playerparticles.manager;

import dev.esophose.playerparticles.PlayerParticles;
import dev.esophose.playerparticles.command.CommandModule;
import dev.esophose.playerparticles.command.OtherCommandModule;
import dev.esophose.playerparticles.particles.PPlayer;
import dev.esophose.playerparticles.util.CommandDispatch;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.scheduler.RoseScheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

/** Real command dispatch with queued scheduler/data boundaries, without a running server. */
class CommandContextTest {
    private RosePlugin plugin;
    private RoseScheduler scheduler;
    private DataManager data;
    private LocaleManager locale;
    private PermissionManager permissions;
    private CommandManager manager;
    private Player player;
    private PPlayer pplayer;
    private Command command;
    private UUID uuid;
    private CommandModule module;
    private final List<Runnable> entityTasks = new ArrayList<>();
    private final List<Runnable> globalTasks = new ArrayList<>();
    private final List<Runnable> databaseTasks = new ArrayList<>();
    private final AtomicReference<String> context = new AtomicReference<>("caller");
    private final AtomicReference<Consumer<PPlayer>> loaded = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        plugin = mock(RosePlugin.class);
        scheduler = mock(RoseScheduler.class);
        when(plugin.getScheduler()).thenReturn(scheduler);
        when(plugin.getCommand("pp")).thenReturn(mock(PluginCommand.class));
        when(plugin.getCommand("ppo")).thenReturn(mock(PluginCommand.class));
        data = mock(DataManager.class);
        locale = mock(LocaleManager.class);
        permissions = mock(PermissionManager.class);
        when(plugin.getManager(DataManager.class)).thenReturn(data);
        when(plugin.getManager(LocaleManager.class)).thenReturn(locale);
        when(plugin.getManager(PermissionManager.class)).thenReturn(permissions);
        manager = spy(new CommandManager(plugin));
        module = mock(CommandModule.class);
        player = mock(Player.class);
        uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        pplayer = mock(PPlayer.class);
        command = mock(Command.class);
        when(command.getName()).thenReturn("pp");
        doAnswer(call -> {
            assertEquals("database", context.get(), "a cold lookup must never perform SQL on the player thread");
            loaded.set(call.getArgument(1));
            return null;
        }).when(data).getPPlayer(eq(uuid), any());
        doAnswer(call -> {
            entityTasks.add(call.getArgument(1));
            return null;
        }).when(scheduler).runTaskAtEntity(eq(player), any());
        doAnswer(call -> {
            globalTasks.add(call.getArgument(0));
            return null;
        }).when(scheduler).runTask(any());
        doAnswer(call -> {
            databaseTasks.add(call.getArgument(0));
            return null;
        }).when(scheduler).runTaskAsync(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fixed", "add", "gui", "reset", "group"})
    void everyPlayerModuleWaitsForOwningSchedulerAfterDelayedDatabaseCallback(String name) {
        doReturn(module).when(manager).findMatchingCommand(name);
        doAnswer(call -> {
            assertEquals("player", context.get(), "module parsing/execution must own the player");
            return null;
        }).when(module).onCommandExecute(any(), any());
        assertTrue(manager.onCommand(player, command, "pp", new String[] {name, "looking", "flame", "normal"}));
        verify(module, never()).onCommandExecute(any(), any());
        assertEquals(1, databaseTasks.size());
        verify(data, never()).getPPlayer(eq(uuid), any());
        runDatabaseTasks();
        loaded.get().accept(pplayer);
        verify(module, never()).onCommandExecute(any(), any());
        assertEquals(1, entityTasks.size());
        runEntityTasks();
        verify(module).onCommandExecute(eq(pplayer), aryEq(new String[] {"looking", "flame", "normal"}));
        verify(scheduler, times(1)).runTaskAsync(any());
        assertTrue(globalTasks.isEmpty());
    }

    @Test
    void inlineCachedDataCallbackStillQueuesBeforeModuleExecution() {
        doReturn(module).when(manager).findMatchingCommand("fixed");
        doAnswer(call -> {
            ((Consumer<PPlayer>) call.getArgument(1)).accept(pplayer);
            return null;
        }).when(data).getPPlayer(eq(uuid), any());
        manager.onCommand(player, command, "pp", new String[] {"fixed"});
        runDatabaseTasks();
        verify(module, never()).onCommandExecute(any(), any());
        assertEquals(1, entityTasks.size());
        runEntityTasks();
        verify(module).onCommandExecute(eq(pplayer), any());
    }

    @Test
    void permissionChecksAndRejectionRunOnPlayerContextToo() {
        doReturn(module).when(manager).findMatchingCommand("fixed");
        when(module.requiresEffectsAndStyles()).thenReturn(true);
        when(permissions.getEffectsUserHasPermissionFor(pplayer)).thenAnswer(call -> {
            assertEquals("player", context.get());
            return Collections.emptyList();
        });
        manager.onCommand(player, command, "pp", new String[] {"fixed"});
        runDatabaseTasks();
        loaded.get().accept(pplayer);
        verifyNoInteractions(permissions);
        runEntityTasks();
        verify(locale).sendMessage(pplayer, "command-error-missing-effects-or-styles");
        verify(module, never()).onCommandExecute(any(), any());
    }

    @Test
    void playerWhoDisconnectsDuringLookupDoesNotExecuteStaleCommand() {
        doReturn(module).when(manager).findMatchingCommand("fixed");
        manager.onCommand(player, command, "pp", new String[] {"fixed"});
        runDatabaseTasks();
        loaded.get().accept(pplayer);
        when(player.isOnline()).thenReturn(false);
        runEntityTasks();
        verify(module, never()).onCommandExecute(any(), any());
    }

    @Test
    void consoleWorkUsesGlobalContextWithoutAnAsyncWorker() {
        ConsoleCommandSender sender = mock(ConsoleCommandSender.class);
        Runnable work = mock(Runnable.class);
        CommandDispatch.atSender(scheduler, sender, work);
        verify(work, never()).run();
        assertEquals(1, globalTasks.size());
        globalTasks.get(0).run();
        verify(work).run();
        verify(scheduler, never()).runTaskAsync(any());
    }

    @Test
    void commandBlockUsesItsBlockRegion() {
        BlockCommandSender sender = mock(BlockCommandSender.class);
        Block block = mock(Block.class);
        Location location = new Location(mock(World.class), 1, 64, 2);
        when(sender.getBlock()).thenReturn(block);
        when(block.getLocation()).thenReturn(location);
        Runnable work = mock(Runnable.class);
        CommandDispatch.atSender(scheduler, sender, work);
        verify(scheduler).runTaskAtLocation(location, work);
        verify(scheduler, never()).runTask(any());
    }

    @Test
    void commandMinecartUsesItsEntityContext() {
        CommandMinecart sender = mock(CommandMinecart.class);
        Runnable work = mock(Runnable.class);
        CommandDispatch.atSender(scheduler, sender, work);
        verify(scheduler).runTaskAtEntity(eq(sender), any());
        verify(scheduler, never()).runTask(any());
    }

    @Test
    void overrideCommandRunsTargetModuleOnTargetEntityAfterDataCallback() {
        PlayerParticles actualPlugin = mock(PlayerParticles.class);
        CommandManager commandManager = mock(CommandManager.class);
        ConsoleCommandSender sender = mock(ConsoleCommandSender.class);
        when(player.getName()).thenReturn("Target");
        when(pplayer.getUniqueId()).thenReturn(uuid);
        when(actualPlugin.getScheduler()).thenReturn(scheduler);
        when(actualPlugin.getManager(LocaleManager.class)).thenReturn(locale);
        when(actualPlugin.getManager(PermissionManager.class)).thenReturn(permissions);
        when(actualPlugin.getManager(CommandManager.class)).thenReturn(commandManager);
        when(actualPlugin.getManager(DataManager.class)).thenReturn(data);
        when(permissions.canOverride(sender)).thenReturn(true);
        when(commandManager.findMatchingCommand("fixed")).thenReturn(module);
        doAnswer(call -> {
            assertEquals("player", context.get());
            assertEquals(uuid, ((PPlayer) call.getArgument(0)).getUniqueId());
            return null;
        }).when(module).onCommandExecute(any(), any());
        try (MockedStatic<PlayerParticles> pp = mockStatic(PlayerParticles.class);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            pp.when(PlayerParticles::getInstance).thenReturn(actualPlugin);
            bukkit.when(() -> Bukkit.getPlayer("Target")).thenReturn(player);
            new OtherCommandModule().onCommandExecute(sender, new String[] {"Target", "fixed", "list"});
            runDatabaseTasks();
            loaded.get().accept(pplayer);
            verify(module, never()).onCommandExecute(any(), any());
            runEntityTasks();
            verify(module).onCommandExecute(any(), aryEq(new String[] {"list"}));
        }
    }

    private void runEntityTasks() {
        context.set("player");
        for (Runnable task : entityTasks) task.run();
    }

    private void runDatabaseTasks() {
        context.set("database");
        for (Runnable task : databaseTasks) task.run();
    }
}
