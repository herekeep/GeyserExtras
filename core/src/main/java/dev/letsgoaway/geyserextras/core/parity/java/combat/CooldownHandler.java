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

    // this code is shit
    private void sendCooldown(double progress) {
        SERVER.log("[DEBUG] sendCooldown called, progress=" + progress);
        
        CooldownType position = CooldownType.CROSSHAIR;
        SERVER.log("[DEBUG] position=" + position);
        
        if (position.equals(CooldownType.OFF)) {
            SERVER.log("[DEBUG] position is OFF, returning");
            return;
        }

        SERVER.log("[DEBUG] digTicks=" + digTicks + ", progress=" + progress + ", readyToAttack=" + readyToAttack);
        
        if (digTicks != -1 || progress == 1.0) {
            SERVER.log("[DEBUG] Entering first switch (digTicks or progress==1.0)");
            switch (position) {
                case CROSSHAIR -> {
                    SERVER.log("[DEBUG] CROSSHAIR branch - readyToAttack=" + readyToAttack + ", lastCharSent=" + lastCharSent);
                    if (readyToAttack && !lastCharSent.equals(crosshairAttackReady)) {
                        lastCharSent = crosshairAttackReady;
                        player.sendTitle("", lastCharSent, 0, Integer.MAX_VALUE, 0);
                        SERVER.log("[DEBUG] Sent CROSSHAIR ready title");
                    } else if (!readyToAttack && !lastCharSent.isEmpty()) {
                        lastCharSent = "";
                        player.resetTitle();
                        SERVER.log("[DEBUG] Reset title");
                    }
                }
                case HOTBAR -> {
                    SERVER.log("[DEBUG] HOTBAR branch");
                    if (!lastCharSent.isEmpty()) {
                        lastCharSent = "";
                        player.sendActionbarTitle(" ");
                        SERVER.log("[DEBUG] Sent HOTBAR actionbar");
                    }
                }
            }
            return;
        }
        
        SERVER.log("[DEBUG] Entering second switch (normal cooldown progression)");
        switch (position) {
            case CROSSHAIR -> {
                int max = (crosshair.length - 1);
                int cooldown = Math.toIntExact(Math.round(progress * max + 0.475f));
                if (cooldown > max) {
                    cooldown = max;
                }
                String curChar = crosshair[cooldown];
                SERVER.log("[DEBUG] CROSSHAIR cooldown=" + cooldown + ", curChar=" + curChar);
                if (lastCharSent.equals(curChar)) {
                    SERVER.log("[DEBUG] curChar same as last, skipping");
                    return;
                }
                lastCharSent = curChar;
                player.sendTitle("", lastCharSent, 0, MathUtils.ceil((float) getCooldownPeriod()), 0);
                SERVER.log("[DEBUG] Sent CROSSHAIR cooldown title");
            }
            case HOTBAR -> {
                int max = (hotbar.length - 1);
                int cooldown = Math.toIntExact(Math.round(progress * max + 0.475f));
                if (cooldown > max) {
                    cooldown = max;
                }
                StringBuilder curChar = new StringBuilder(" " + hotbar[cooldown]);
                if (!GUIElements.ITEM_TEXT_POPUP.isHidden(session) && System.currentTimeMillis() / (lastHotbarTime + getHBStayTime()) < 1.0) {
                    if (session.getGameMode().equals(GameMode.SURVIVAL) || session.getGameMode().equals(GameMode.ADVENTURE)) {
                        curChar.append("\n\n\n");
                    }
                    GeyserItemStack heldItem = session.getPlayerInventory().getItemInHand();
                    if (heldItem.asItem().equals(Items.DEBUG_STICK)) {
                        curChar.append("\n\n");
                    }
                    ItemEnchantments enchantments = heldItem.getComponent(DataComponentTypes.ENCHANTMENTS);
                    if (enchantments != null) {
                        for (int enchID : enchantments.getEnchantments().keySet()) {
                            if (enchID != 22) {
                                curChar.append("\n\n");
                            }
                        }
                    }
                }
                curChar.append(" ");
                if (lastCharSent.contentEquals(curChar)) {
                    SERVER.log("[DEBUG] HOTBAR curChar same as last, skipping");
                    return;
                }
                lastCharSent = curChar.toString();
                player.sendActionbarTitle(lastCharSent);
                SERVER.log("[DEBUG] Sent HOTBAR cooldown actionbar");
            }
        }
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
