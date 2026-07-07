package kome.hatsunemiku.addon.modules;

import kome.hatsunemiku.addon.MikuTester;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class CrosshairAttack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum HandMode {
        MainHand,
        OffHand,
        Both
    }

    private final Setting<Boolean> onlyWhenSword = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-sword")
        .description("Only auto-attack when holding a sword.")
        .defaultValue(true)
        .build()
    );

    private final Setting<HandMode> handMode = sgGeneral.add(new EnumSetting.Builder<HandMode>()
        .name("hand")
        .description("Which hand to attack with.")
        .defaultValue(HandMode.MainHand)
        .build()
    );

    private final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
        .name("reach")
        .description("Max reach distance. Keep 3.0 for vanilla.")
        .defaultValue(3.0)
        .min(1.0)
        .max(6.0)
        .sliderMax(6.0)
        .build()
    );

    private final Setting<Double> minDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-delay")
        .description("Minimum delay between attacks (ticks). Supports decimals.")
        .defaultValue(4.0)
        .min(0.0)
        .max(40.0)
        .sliderMin(0.0)
        .sliderMax(40.0)
        .build()
    );

    private final Setting<Double> maxDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-delay")
        .description("Maximum delay between attacks (ticks). Supports decimals.")
        .defaultValue(8.0)
        .min(0.0)
        .max(40.0)
        .sliderMin(0.0)
        .sliderMax(40.0)
        .build()
    );

    // Bypass: chỉ attack khi attack cooldown gần đầy
    private final Setting<Boolean> waitCooldown = sgGeneral.add(new BoolSetting.Builder()
        .name("wait-cooldown")
        .description("Only attack when attack cooldown is above threshold. Bypass AC.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cooldownThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("cooldown-threshold")
        .description("Attack cooldown required before hitting (0.0 - 1.0). 0.9 = 90% charged.")
        .defaultValue(0.9)
        .min(0.0)
        .max(1.0)
        .sliderMin(0.0)
        .sliderMax(1.0)
        .visible(waitCooldown::get)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Does not attack friends.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("Swings hand animation when attacking.")
        .defaultValue(true)
        .build()
    );

    private double timer = 0;
    private final Random random = new Random();

    public CrosshairAttack() {
        super(MikuTester.CATEGORY, "crosshair-attack",
            "Automatically attacks players you are looking at. Does not attack mobs or blocks.");
    }

    @Override
    public void onActivate() {
        timer = nextDelay();
    }

    private double nextDelay() {
        double min = minDelay.get();
        double max = maxDelay.get();
        if (min >= max) return min;
        return min + random.nextDouble() * (max - min);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (onlyWhenSword.get()) {
            if (!mc.player.getMainHandItem().is(ItemTags.SWORDS)) return;
        }

        if (timer > 0) {
            timer--;
            return;
        }

        // Check attack cooldown
        if (waitCooldown.get()) {
            float cooldown = mc.player.getAttackStrengthScale(0f);
            if (cooldown < cooldownThreshold.get()) return;
        }

        // Check crosshair target
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;

        EntityHitResult entityHit = (EntityHitResult) hit;
        if (!(entityHit.getEntity() instanceof Player target)) return;
        if (target == mc.player) return;
        if (ignoreFriends.get() && Friends.get().isFriend(target)) return;

        // Reach + raycast check
        Vec3 eyePos  = mc.player.getEyePosition();
        AABB targetBox = target.getBoundingBox().inflate(0.1);
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 endVec  = eyePos.add(lookVec.scale(reach.get()));

        if (targetBox.clip(eyePos, endVec).isEmpty()) return;

        double dist = eyePos.distanceTo(new Vec3(target.getX(), target.getEyeY(), target.getZ()));
        if (dist > reach.get()) return;

        // Attack
        mc.gameMode.attack(mc.player, target);
        if (swingHand.get()) {
            switch (handMode.get()) {
                case MainHand -> mc.player.swing(InteractionHand.MAIN_HAND);
                case OffHand  -> mc.player.swing(InteractionHand.OFF_HAND);
                case Both -> {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                    mc.player.swing(InteractionHand.OFF_HAND);
                }
            }
        }

        // Random delay cho lần tiếp theo
        timer = nextDelay();
    }
}