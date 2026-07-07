package kome.hatsunemiku.addon.modules;

import kome.hatsunemiku.addon.MikuTester;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class BreadSpammer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount-per-tick")
        .description("How many bread to drop per tick.")
        .defaultValue(5)
        .min(1)
        .max(64)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> stackSize = sgGeneral.add(new IntSetting.Builder()
        .name("stack-size")
        .description("How many bread per stack dropped.")
        .defaultValue(64)
        .min(1)
        .max(64)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> onlyCreative = sgGeneral.add(new BoolSetting.Builder()
        .name("only-creative")
        .description("Only works in creative mode.")
        .defaultValue(true)
        .build()
    );

    public BreadSpammer() {
        super(MikuTester.CATEGORY, "bread-spammer",
            "Drops bread at extreme speed in creative mode.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Check creative mode
        if (onlyCreative.get() && !mc.player.getAbilities().creativeMode) return;

        int slot = 36; // Hotbar slot 0 (off screen, won't affect player's visible items)

        for (int i = 0; i < amount.get(); i++) {
            // Set slot to bread stack via creative packet
            mc.player.networkHandler.sendPacket(
                new CreativeInventoryActionC2SPacket(slot, new ItemStack(Items.BREAD, stackSize.get()))
            );

            // Drop the item from that slot
            mc.player.networkHandler.sendPacket(
                new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.DROP_ALL_ITEMS,
                    BlockPos.ORIGIN,
                    Direction.DOWN
                )
            );
        }
    }
}