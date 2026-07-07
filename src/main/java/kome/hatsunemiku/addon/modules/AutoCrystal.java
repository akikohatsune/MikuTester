package kome.hatsunemiku.addon.modules;

import kome.hatsunemiku.addon.MikuTester;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AutoCrystal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgTarget = settings.createGroup("Target");

    // General
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switches to crystal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates to crystals and blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range to find targets.")
        .defaultValue(10.0)
        .min(1.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    // Place settings
    private final Setting<Boolean> place = sgPlace.add(new BoolSetting.Builder()
        .name("place")
        .description("Places crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range to place crystals.")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .sliderMax(6.0)
        .visible(place::get)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay between placing crystals (ticks).")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(place::get)
        .build()
    );

    private final Setting<Boolean> onlyObsidian = sgPlace.add(new BoolSetting.Builder()
        .name("only-obsidian")
        .description("Only places on obsidian/bedrock.")
        .defaultValue(true)
        .visible(place::get)
        .build()
    );

    // Break settings
    private final Setting<Boolean> breakCrystal = sgBreak.add(new BoolSetting.Builder()
        .name("break")
        .description("Breaks crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Range to break crystals.")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .sliderMax(6.0)
        .visible(breakCrystal::get)
        .build()
    );

    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Delay between breaking crystals (ticks).")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(breakCrystal::get)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgBreak.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Prevents breaking crystals that would kill you.")
        .defaultValue(true)
        .visible(breakCrystal::get)
        .build()
    );

    private final Setting<Double> minHealth = sgBreak.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health to break crystals.")
        .defaultValue(8.0)
        .min(1.0)
        .max(36.0)
        .sliderMax(36.0)
        .visible(() -> breakCrystal.get() && antiSuicide.get())
        .build()
    );

    // Target settings
    private final Setting<Boolean> targetPlayers = sgTarget.add(new BoolSetting.Builder()
        .name("target-players")
        .description("Targets players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Ignores friends.")
        .defaultValue(true)
        .visible(targetPlayers::get)
        .build()
    );

    private int placeTimer = 0;
    private int breakTimer = 0;
    private int previousSlot = -1;

    public AutoCrystal() {
        super(MikuTester.CATEGORY, "auto-crystal", "Automatically places and breaks end crystals.");
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
        breakTimer = 0;
        previousSlot = -1;
    }

    @Override
    public void onDeactivate() {
        if (previousSlot != -1 && mc.player != null) {
            InvUtils.swap(previousSlot, true);
            previousSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // Update timers
        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;

        // Find target
        Player target = findTarget();

        // Break crystals first (higher priority)
        if (breakCrystal.get() && breakTimer == 0) {
            EndCrystal crystal = findCrystal();
            if (crystal != null) {
                breakCrystalEntity(crystal);
                breakTimer = breakDelay.get();
                return;
            }
        }

        // Place crystals
        if (place.get() && placeTimer == 0 && target != null) {
            BlockPos placePos = findPlacePosition(target);
            if (placePos != null) {
                placeCrystalAt(placePos);
                placeTimer = placeDelay.get();
            }
        }
    }

    private Player findTarget() {
        if (!targetPlayers.get()) return null;

        Player target = null;
        double closestDist = targetRange.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player)) continue;
            if (entity == mc.player) continue;

            Player player = (Player) entity;

            // Check if friend
            if (ignoreFriends.get() && player.getName().getString().equals("Friend")) {
                // TODO: Check actual friend system
                continue;
            }

            double dist = mc.player.distanceTo(player);
            if (dist < closestDist) {
                target = player;
                closestDist = dist;
            }
        }

        return target;
    }

    private EndCrystal findCrystal() {
        EndCrystal closest = null;
        double closestDist = breakRange.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal)) continue;

            EndCrystal crystal = (EndCrystal) entity;
            double dist = mc.player.distanceTo(crystal);

            if (dist < closestDist) {
                // Check anti-suicide
                if (antiSuicide.get()) {
                    Vec3 crystalPos = new Vec3(crystal.getX(), crystal.getY(), crystal.getZ());
                    double damage = calculateDamage(crystalPos);
                    if (mc.player.getHealth() - damage < minHealth.get()) {
                        continue;
                    }
                }

                closest = crystal;
                closestDist = dist;
            }
        }

        return closest;
    }

    private BlockPos findPlacePosition(Player target) {
        if (target == null) return null;

        List<BlockPos> positions = new ArrayList<>();
        BlockPos targetPos = target.blockPosition();

        // Check positions around target
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos pos = targetPos.offset(x, y, z);

                    if (canPlaceCrystal(pos)) {
                        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        double dist = playerPos.distanceTo(Vec3.atCenterOf(pos));
                        if (dist <= placeRange.get()) {
                            positions.add(pos);
                        }
                    }
                }
            }
        }

        // Return best position (closest to target)
        BlockPos best = null;
        double bestScore = 0;

        for (BlockPos pos : positions) {
            Vec3 targetPos2 = new Vec3(target.getX(), target.getY(), target.getZ());
            double targetDist = targetPos2.distanceTo(Vec3.atCenterOf(pos.above()));
            double score = 1.0 / (targetDist + 1);

            if (score > bestScore) {
                best = pos;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        if (mc.level == null) return false;

        // Check if block is obsidian or bedrock
        if (onlyObsidian.get()) {
            if (mc.level.getBlockState(pos).getBlock() != Blocks.OBSIDIAN &&
                mc.level.getBlockState(pos).getBlock() != Blocks.BEDROCK) {
                return false;
            }
        }

        // Check if space above is clear
        BlockPos above = pos.above();
        BlockPos above2 = pos.above(2);

        if (!mc.level.getBlockState(above).isAir() || !mc.level.getBlockState(above2).isAir()) {
            return false;
        }

        // Check if there's already a crystal
        AABB box = new AABB(above);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal && entity.getBoundingBox().intersects(box)) {
                return false;
            }
        }

        return true;
    }

    private void placeCrystalAt(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return;

        // Switch to crystal
        if (autoSwitch.get()) {
            FindItemResult crystal = InvUtils.find(Items.END_CRYSTAL);
            if (!crystal.found()) return;

            if (previousSlot == -1) {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i) == mc.player.getMainHandItem()) {
                        previousSlot = i;
                        break;
                    }
                }
            }

            InvUtils.swap(crystal.slot(), true);
        }

        // Rotate if needed
        if (rotate.get()) {
            Vec3 hitVec = Vec3.atCenterOf(pos).add(0, 1, 0);
            Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec));
        }

        // Place crystal
        Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
    }

    private void breakCrystalEntity(EndCrystal crystal) {
        if (mc.player == null || mc.gameMode == null) return;

        // Rotate if needed
        if (rotate.get()) {
            Vec3 pos = new Vec3(crystal.getX(), crystal.getY(), crystal.getZ());
            Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos));
        }

        // Attack crystal
        mc.gameMode.attack(mc.player, crystal);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private double calculateDamage(Vec3 pos) {
        // Simple damage calculation (can be improved)
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double distance = playerPos.distanceTo(pos);
        if (distance > 12) return 0;

        double damage = (1 - (distance / 12)) * 12;
        return Math.max(0, damage);
    }
}
