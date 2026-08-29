package dev.letsgoaway.geyserextras.core.parity.java.combat;

import dev.letsgoaway.geyserextras.core.utils.MathUtils;
import dev.letsgoaway.geyserextras.core.ExtrasPlayer;
import dev.letsgoaway.geyserextras.core.utils.TickMath;
import dev.letsgoaway.geyserextras.core.utils.GUIElements;
import lombok.Getter;
import lombok.Setter;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;
import dev.letsgoaway.geyserextras.core.parity.java.combat.CooldownType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;

import java.util.List;

import static dev.letsgoaway.geyserextras.core.GeyserExtras.SERVER;

public class CooldownHandler {
    private static final List<Item> readyToAttackIndicatorItems = List.of(
            Items.NETHERITE_AXE,
            Items.DIAMOND_AXE,
            Items.GOLDEN_AXE,
            Items.IRON_AXE,
            Items.STONE_AXE,
            Items.WOODEN_AXE,
            Items.NETHERITE_PICKAXE,
            Items.DIAMOND_PICKAXE,
            Items.GOLDEN_PICKAXE,
            Items.IRON_PICKAXE,
            Items.STONE_PICKAXE,
            Items.WOODEN_PICKAXE,
            Items.NETHERITE_SHOVEL,
            Items.DIAMOND_SHOVEL,
            Items.GOLDEN_SHOVEL,
            Items.IRON_SHOVEL,
            Items.STONE_SHOVEL,
            Items.WOODEN_SHOVEL,
            Items.NETHERITE_SWORD,
            Items.DIAMOND_SWORD,
            Items.GOLDEN_SWORD,
            Items.IRON_SWORD,
            Items.STONE_SWORD,
            Items.WOODEN_SWORD,
            Items.TRIDENT,
            Items.MACE
    );
    private static final String[] crosshair = {"\uF821", "\uF810", "\uF811", "\uF812", "\uF813", "\uF814", "\uF815", "\uF816", "\uF817", "\uF818", "\uF819", "\uF81A", "\uF81B", "\uF81C", "\uF81D", "\uF81E", "\uF81F"};
    private static final String[] hotbar = {"\uF800", "\uF801", "\uF802", "\uF803", "\uF804", "\uF805", "\uF806", "\uF807", "\uF808", "\uF809", "\uF80A", "\uF80B", "\uF80C", "\uF80D", "\uF80E", "\uF80F"};
    private static final String crosshairAttackReady = "\uF820";
    private final ExtrasPlayer player;
    @Getter
    @Setter
    public double attackSpeed = 4.0;
    /**
     * -1 means the player is not digging
     */
    @Setter
    @Getter
    public int digTicks = -1;
    public boolean readyToAttack = false;
    private GeyserSession session;
    @Setter
    private long lastSwingTime;
    private long lastHotbarTime = 0;
    @Setter
    private long lastMouseoverID = 0;
    // Shield stuff
    @Setter
    @Getter
    private boolean skipNextItemUse1 = false;
    @Setter
    @Getter
    private long lastBlockRightClickTime = 0;
    @Setter
    @Getter
    private boolean lastClickWasAirClick = false;
    private String lastCharSent = "";
    @Getter
    private double averagePing = 0.0f;
    @Getter
    private long pingSample = 0;
    @Getter
    private long pingSampleSize = 0;
    @Getter
    private int lastPing = -1;

    public CooldownHandler(ExtrasPlayer player) {
        this.player = player;
        lastSwingTime = System.currentTimeMillis();
        session = player.getSession();
    }

    public boolean isTool() {
        return readyToAttackIndicatorItems.contains(session.getPlayerInventory().getItemInHand().asItem());
    }

    public void tick() {
        calculateAveragePing();
        if (lastMouseoverID != 0 && session.getMouseoverEntity() != null && isTool()) {
            readyToAttack = session.getMouseoverEntity().isAlive();
        } else {
            readyToAttack = false;
        }
        double time = (System.currentTimeMillis() + (player.getPreferences().isAdjustCooldownWithPing() && TickMath.toMillis((float) getCooldownPeriod()) > averagePing ? averagePing : 0)) - lastSwingTime;
        double cooldown = MathUtils.restrain((time) * attackSpeed / 1000.0, 1);
        sendCooldown(cooldown);
    }

    // ============================================================
    // 完全禁用扩展冷却，让 Geyser 原生接管
    // 确保 config.yml 中 cooldown-type: crosshair 或 hotbar
    // ============================================================
    private void sendCooldown(double progress) {
        // 完全禁用，让 Geyser 原生处理
        return;
    }

    public double getCooldownPeriod() {
        return 1.0D / attackSpeed * 20.0;
    }

    private void calculateAveragePing() {
        int ping = session.ping();
        if (ping != lastPing) {
            pingSample += ping;
            pingSampleSize++;
            lastPing = ping;
        }
        averagePing = (double) pingSample / pingSampleSize;
    }

    public void setLastHotbarTime(long time) {
        lastHotbarTime = time;
        setLastSwingTime(time);
    }

    private double getHBStayTime() {
        double textTime = 3.5;
        GeyserItemStack item = session.getPlayerInventory().getItemInHand();
        ItemEnchantments enchantments = item.getComponent(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            for (int enchID : enchantments.getEnchantments().keySet()) {
                if (enchID != 22) {
                    textTime += 0.75;
                }
            }
        }
        if (player.getPreferences().isAdjustCooldownWithPing()) {
            if (lastPing >= 40) {
                if (textTime - (averagePing / 1000) > 0.0) {
                    textTime -= (averagePing / 1000);
                }
            }
        }
        return textTime * 1000;
    }
}
