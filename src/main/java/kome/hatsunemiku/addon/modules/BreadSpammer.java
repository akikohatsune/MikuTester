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

    private final Setting<Integer> amountPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("amount-per-tick")
        .description("How many stacks of bread to drop per tick.")
        .defaultValue(5)
        .min(1)
        .max(64)
        .sliderMax(64)
        .build()
    );

    private final Setting<Integer> stackSize = sgGeneral.add(new IntSetting.Builder()
        .name("stack-size")
        .description("How many bread per stack.")
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
        if (mc.player == null || mc.level == null) return;
        if (onlyCreative.get() && !mc.player.getAbilities().instabuild) return;

        ItemStack bread = new ItemStack(Items.BREAD, stackSize.get());

        for (int i = 0; i < amountPerTick.get(); i++) {
            // Slot -1 = drop item directly, no secondary packet needed
            mc.player.connection.send(
                new ServerboundSetCreativeModeSlotPacket(-1, bread)
            );
        }
    }
}