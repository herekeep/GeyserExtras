package dev.letsgoaway.geyserextras.core;

import dev.letsgoaway.geyserextras.InitializeLogger;
import dev.letsgoaway.geyserextras.Server;
import dev.letsgoaway.geyserextras.ServerType;
import dev.letsgoaway.geyserextras.core.cache.Cache;
import dev.letsgoaway.geyserextras.core.cache.PackCacheUtils;
import dev.letsgoaway.geyserextras.core.config.ConfigLoader;
import dev.letsgoaway.geyserextras.core.config.GeyserExtrasConfig;
import dev.letsgoaway.geyserextras.core.injectors.GeyserHandler;
import dev.letsgoaway.geyserextras.core.parity.bedrock.EmoteUtils;
import dev.letsgoaway.geyserextras.core.preferences.JavaPreferencesData;
import dev.letsgoaway.geyserextras.core.preferences.PreferencesData;
import dev.letsgoaway.geyserextras.core.protocol.CapeLoader;
import dev.letsgoaway.geyserextras.core.utils.IdUtils;
import dev.letsgoaway.geyserextras.core.utils.IsAvailable;
import dev.letsgoaway.geyserextras.core.version.PluginVersion;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.*;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreReloadEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GeyserExtras implements EventRegistrar {
    public static GeyserExtras GE;
    public static Server SERVER;
    public GeyserApi geyserApi;
    public ConcurrentHashMap<String, ExtrasPlayer> connections;
    public ConcurrentHashMap<UUID, JavaPreferencesData> javaConnections;
    @Getter
    @Setter
    private GeyserExtrasConfig config;

    private ScheduledExecutorService tickExecutor;

    public GeyserExtras(Server server) {
        GE = this;
        SERVER = server;
        ServerType.platformType = GeyserImpl.getInstance().platformType();

        IsAvailable.preload();

        InitializeLogger.start();

        if (!IsAvailable.cloudburst()) {
            SERVER.warn("!ERROR! GeyserExtras currently does not support running as an extension on modded platforms. !ERROR!");
            SERVER.warn("Please use Geyser-Standalone!");
            InitializeLogger.endNoDone();
            return;
        }

        geyserApi = GeyserApi.api();

        SERVER.log("Loading config...");
        ConfigLoader.load();
        SERVER.onConfigLoad();

        if (ServerType.isExtension()) {
            GeyserHandler.register();

            SERVER.log("Initializing cache...");
            Cache.initialize();
            SERVER.log("Loading Emote Data...");
            EmoteUtils.initialize();
        }

        PreferencesData.init();

        SERVER.log("Registering events...");
        geyserApi.eventBus().register(this, this);
        geyserApi.eventBus().subscribe(this, GeyserPostInitializeEvent.class, this::onGeyserInitialize);

        // ExtrasPlayer handlers
        geyserApi.eventBus().subscribe(this, SessionLoginEvent.class, this::onSessionLogin);
        geyserApi.eventBus().subscribe(this, SessionJoinEvent.class, this::onSessionJoin);
        geyserApi.eventBus().subscribe(this, SessionDisconnectEvent.class, this::onSessionRemove);

        // Emote bindings
        geyserApi.eventBus().subscribe(this, ClientEmoteEvent.class, this::onEmoteEvent);

        // Auto reconnect
        geyserApi.eventBus().subscribe(this, GeyserPreReloadEvent.class, this::onGeyserReload);
        geyserApi.eventBus().subscribe(this, GeyserShutdownEvent.class, this::onGeyserShutdown);

        // Packs
        geyserApi.eventBus().subscribe(this, SessionLoadResourcePacksEvent.class, this::onLoadPacks);

        connections = new ConcurrentHashMap<>();
        javaConnections = new ConcurrentHashMap<>();

        // ========== 启动定时任务，每 50ms 执行一次 serverTick ==========
        tickExecutor = Executors.newSingleThreadScheduledExecutor();
        tickExecutor.scheduleAtFixedRate(this::serverTick, 0, 50, TimeUnit.MILLISECONDS);

        if (ServerType.isExtension()) {
            InitializeLogger.end();
        }

        PluginVersion.checkForUpdatesAndPrintToLog();
    }

    /**
     * 每 tick 调用一次，更新所有玩家的状态（包括冷却指示器）
     */
    public void serverTick() {
        for (ExtrasPlayer player : connections.values()) {
            if (player.isLoggedIn()) {
                player.tick();
            }
        }
    }

    public void onGeyserInitialize(GeyserPostInitializeEvent init) {
        if (!ServerType.isExtension()) {
            GeyserHandler.register();
            SERVER.log("Initializing cache...");
            Cache.initialize();
            SERVER.log("Loading Emote Data...");
            EmoteUtils.initialize();
            InitializeLogger.end();
        }
    }

    // ========== 修复：在登录时创建 ExtrasPlayer ==========
    public void onSessionLogin(SessionLoginEvent ev) {
        GeyserConnection connection = ev.connection();
        String xuid = connection.xuid();
        if (connections.containsKey(xuid)) {
            connections.remove(xuid);
        }
        ExtrasPlayer player = SERVER.createPlayer(connection);
        connections.put(xuid, player);
        SERVER.log("[DEBUG] Created ExtrasPlayer for " + connection.bedrockUsername() + " (XUID: " + xuid + ")");
    }

    public void onSessionJoin(SessionJoinEvent ev) {
        String xuid = ev.connection().xuid();
        ExtrasPlayer player = connections.get(xuid);
        if (player == null) {
            player = SERVER.createPlayer(ev.connection());
            connections.put(xuid, player);
            SERVER.warn("[DEBUG] ExtrasPlayer was null on SessionJoin, created one now for " + ev.connection().bedrockUsername());
        }
        player.startGame();
    }

    public void onSessionRemove(SessionDisconnectEvent ev) {
        GeyserConnection connection = ev.connection();
        for (ExtrasPlayer player : connections.values()) {
            GeyserSession session = player.getSession();
            if (session.bedrockUsername().equals(connection.bedrockUsername())) {
                if (session.getAuthData() == null && session.getClientData() == null) {
                    player.getPreferences().getSelectedPacks().clear();
                    player.getPreferences().save();
                }
                if (GE.getConfig().isAutoReconnect()) {
                    player.reconnect();
                }
                connections.get(player.getBedrockXUID()).onDisconnect();
                connections.remove(player.getBedrockXUID());
                return;
            }
        }
        if (connections.containsKey(ev.connection().xuid())) {
            connections.get(ev.connection().xuid()).onDisconnect();
        }
        if (connections.remove(ev.connection().xuid()) == null) {
            SERVER.warn("Could not remove user.");
        }
    }

    public void onEmoteEvent(ClientEmoteEvent ev) {
        ExtrasPlayer player = connections.get(ev.connection().xuid());
        if (player != null) {
            player.onEmoteEvent(ev);
        } else {
            SERVER.warn("Received emote event for unknown player: " + ev.connection().bedrockUsername());
        }
    }

    public void onGeyserReload(GeyserPreReloadEvent ignored) {
        autoReconnectAll();
    }

    public void onGeyserShutdown(GeyserShutdownEvent ignored) {
        if (tickExecutor != null) {
            tickExecutor.shutdown();
        }
        autoReconnectAll();
    }

    public void onLoadPacks(SessionLoadResourcePacksEvent ev) {
        String xuid = ev.connection().xuid();
        if (!connections.containsKey(xuid)) {
            ExtrasPlayer player = SERVER.createPlayer(ev.connection());
            connections.put(xuid, player);
        }
        ExtrasPlayer player = connections.get(xuid);
        PackCacheUtils.onPackLoadEvent(player, ev);
    }

    public JavaPreferencesData getJavaPreferencesData(UUID javaUUID) {
        return javaConnections.get(javaUUID);
    }

    public void autoReconnectAll() {
        if (getConfig().isAutoReconnect()) {
            for (ExtrasPlayer player : connections.values()) {
                player.getPreferences().save();
                player.reconnect();
            }
        }
    }

    public void onJavaPlayerJoin(UUID javaUUID) {
        if (!IdUtils.isBedrockPlayer(javaUUID)) {
            javaConnections.put(javaUUID, JavaPreferencesData.load(javaUUID));
        }
    }

    public void onJavaPlayerLeave(UUID javaUUID) {
        if (javaConnections.containsKey(javaUUID)) {
            javaConnections.get(javaUUID).save();
            javaConnections.remove(javaUUID);
        }
    }
}
