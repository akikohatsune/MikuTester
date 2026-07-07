package kome.hatsunemiku.addon.modules;

import kome.hatsunemiku.addon.MikuTester;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;

public class BreadSpammer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> packetsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("packets-per-tick")
        .description("Packets per tick. Server only accepts 1, keep at 1 to avoid being ignored.")
        .defaultValue(1)
        .min(1)
        .max(10)
        .sliderMax(10)
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
            "Drops bread at max allowed speed in creative mode.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (onlyCreative.get() && !mc.player.getAbilities().instabuild) return;

        // Stack size 64 = max per packet, 1 packet/tick = max server allows
        ItemStack bread = new ItemStack(Items.BREAD, 64);

        for (int i = 0; i < packetsPerTick.get(); i++) {
            mc.player.connection.send(
                new ServerboundSetCreativeModeSlotPacket(-1, bread)
            );
        }
    }
}