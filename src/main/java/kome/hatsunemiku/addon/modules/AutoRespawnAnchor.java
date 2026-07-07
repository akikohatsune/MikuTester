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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

public class AutoRespawnAnchor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgCharge = settings.createGroup("Charge");
    private final SettingGroup sgDetonate = settings.createGroup("Detonate");
    private final SettingGroup sgTarget = settings.createGroup("Target");

    public enum Mode {
        Auto,
        Manual
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Auto: targets players. Manual: auto charge/detonate anchors you place.")
        .defaultValue(Mode.Manual)
        .build()
    );

    // General
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically switches to anchor/glowstone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates to anchor.")
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
        .description("Places respawn anchors.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range to place anchors.")
        .defaultValue(4.5)
        .min(1.0)
        .max(6.0)
        .sliderMax(6.0)
        .visible(place::get)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Delay between placing anchors (ticks).")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(place::get)
        .build()
    );

    // Charge settings
    private final Setting<Boolean> charge = sgCharge.add(new BoolSetting.Builder()
        .name("charge")
        .description("Charges respawn anchors with glowstone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> chargeLevel = sgCharge.add(new IntSetting.Builder()
        .name("charge-level")
        .description("Charge level (1-4).")
        .defaultValue(4)
        .min(1)
        .max(4)
        .sliderMax(4)
        .visible(charge::get)
        .build()
    );

    private final Setting<Integer> chargeDelay = sgCharge.add(new IntSetting.Builder()
        .name("charge-delay")
        .description("Delay between charging (ticks).")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(charge::get)
        .build()
    );

    // Detonate settings
    private final Setting<Boolean> detonate = sgDetonate.add(new BoolSetting.Builder()
        .name("detonate")
        .description("Detonates charged anchors.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> detonateDelay = sgDetonate.add(new IntSetting.Builder()
        .name("detonate-delay")
        .description("Delay before detonating (ticks).")
        .defaultValue(1)
        .min(0)
        .max(20)
        .sliderMax(20)
        .visible(detonate::get)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgDetonate.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Prevents detonating if it would kill you.")
        .defaultValue(true)
        .visible(detonate::get)
        .build()
    );

    private final Setting<Double> minHealth = sgDetonate.add(new DoubleSetting.Builder()
        .name("min-health")
        .description("Minimum health to detonate.")
        .defaultValue(8.0)
        .min(1.0)
        .max(36.0)
        .sliderMax(36.0)
        .visible(() -> detonate.get() && antiSuicide.get())
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
    private int chargeTimer = 0;
    private int detonateTimer = 0;
    private int previousSlot = -1;

    public AutoRespawnAnchor() {
        super(MikuTester.CATEGORY, "auto-respawn-anchor", "Automatically places, charges, and detonates respawn anchors.");
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
        chargeTimer = 0;
        detonateTimer = 0;
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
        if (chargeTimer > 0) chargeTimer--;
        if (detonateTimer > 0) detonateTimer--;

        if (mode.get() == Mode.Manual) {
            // Manual mode: auto charge and detonate nearby anchors
            manualMode();
        } else {
            // Auto mode: find target and place/charge/detonate
            autoMode();
        }
    }

    private void manualMode() {
        // Find nearby anchors and auto charge/detonate them
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        // Priority 1: Detonate charged anchors
        if (detonate.get() && detonateTimer == 0) {
            BlockPos anchorPos = findNearbyChargedAnchor(playerPos);
            if (anchorPos != null) {
                detonateAnchor(anchorPos);
                detonateTimer = detonateDelay.get();
                return;
            }
        }

        // Priority 2: Charge existing anchors
        if (charge.get() && chargeTimer == 0) {
            BlockPos anchorPos = findNearbyUnchargedAnchor(playerPos);
            if (anchorPos != null) {
                chargeAnchor(anchorPos);
                chargeTimer = chargeDelay.get();
                return;
            }
        }
    }

    private void autoMode() {
        // Find target
        Player target = findTarget();
        if (target == null) return;

        // Priority 1: Detonate charged anchors
        if (detonate.get() && detonateTimer == 0) {
            BlockPos anchorPos = findChargedAnchor(target);
            if (anchorPos != null) {
                detonateAnchor(anchorPos);
                detonateTimer = detonateDelay.get();
                return;
            }
        }

        // Priority 2: Charge existing anchors
        if (charge.get() && chargeTimer == 0) {
            BlockPos anchorPos = findUnchargedAnchor(target);
            if (anchorPos != null) {
                chargeAnchor(anchorPos);
                chargeTimer = chargeDelay.get();
                return;
            }
        }

        // Priority 3: Place new anchors
        if (place.get() && placeTimer == 0) {
            BlockPos placePos = findPlacePosition(target);
            if (placePos != null) {
                placeAnchor(placePos);
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

            if (ignoreFriends.get() && player.getName().getString().equals("Friend")) {
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

    private BlockPos findPlacePosition(Player target) {
        if (target == null) return null;

        BlockPos targetPos = target.blockPosition();
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        BlockPos best = null;
        double bestScore = 0;

        for (int x = -4; x <= 4; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.offset(x, y, z);

                    if (canPlaceAnchor(pos)) {
                        double dist = playerPos.distanceTo(Vec3.atCenterOf(pos));
                        if (dist <= placeRange.get()) {
                            Vec3 targetVec = new Vec3(target.getX(), target.getY(), target.getZ());
                            double targetDist = targetVec.distanceTo(Vec3.atCenterOf(pos));
                            double score = 1.0 / (targetDist + 1);

                            if (score > bestScore) {
                                best = pos;
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findUnchargedAnchor(Player target) {
        if (target == null || mc.level == null) return null;

        BlockPos targetPos = target.blockPosition();
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = targetPos.offset(x, y, z);
                    double dist = playerPos.distanceTo(Vec3.atCenterOf(pos));

                    if (dist <= placeRange.get()) {
                        Block block = mc.level.getBlockState(pos).getBlock();
                        if (block == Blocks.RESPAWN_ANCHOR) {
                            int charges = mc.level.getBlockState(pos).getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                            if (charges < chargeLevel.get()) {
                                return pos;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private BlockPos findChargedAnchor(Player target) {
        if (target == null || mc.level == null) return null;

        BlockPos targetPos = target.blockPosition();
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = targetPos.offset(x, y, z);
                    double dist = playerPos.distanceTo(Vec3.atCenterOf(pos));

                    if (dist <= placeRange.get()) {
                        Block block = mc.level.getBlockState(pos).getBlock();
                        if (block == Blocks.RESPAWN_ANCHOR) {
                            int charges = mc.level.getBlockState(pos).getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                            if (charges >= chargeLevel.get()) {
                                // Check anti-suicide
                                if (antiSuicide.get()) {
                                    double damage = calculateAnchorDamage(pos);
                                    if (mc.player.getHealth() - damage < minHealth.get()) {
                                        continue;
                                    }
                                }
                                return pos;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean canPlaceAnchor(BlockPos pos) {
        if (mc.level == null) return false;

        // Check if block is replaceable
        if (!mc.level.getBlockState(pos).canBeReplaced()) {
            return false;
        }

        // Check if there's a solid block below
        if (!mc.level.getBlockState(pos.below()).isSolidRender()) {
            return false;
        }

        return true;
    }

    private void placeAnchor(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return;

        // Switch to anchor
        if (autoSwitch.get()) {
            FindItemResult anchor = InvUtils.find(Items.RESPAWN_ANCHOR);
            if (!anchor.found()) return;

            if (previousSlot == -1) {
                for (int i = 0; i < 9; i++) {
                    if (mc.player.getInventory().getItem(i) == mc.player.getMainHandItem()) {
                        previousSlot = i;
                        break;
                    }
                }
            }

            InvUtils.swap(anchor.slot(), true);
        }

        // Rotate if needed
        if (rotate.get()) {
            Vec3 hitVec = Vec3.atCenterOf(pos);
            Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec));
        }

        // Place anchor
        Vec3 hitVec = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
    }

    private void chargeAnchor(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return;

        // Switch to glowstone
        if (autoSwitch.get()) {
            FindItemResult glowstone = InvUtils.find(Items.GLOWSTONE);
            if (!glowstone.found()) return;

            InvUtils.swap(glowstone.slot(), true);
        }

        // Rotate if needed
        if (rotate.get()) {
            Vec3 hitVec = Vec3.atCenterOf(pos);
            Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec));
        }

        // Charge anchor
        Vec3 hitVec = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
    }

    private void detonateAnchor(BlockPos pos) {
        if (mc.player == null || mc.gameMode == null) return;

        // Rotate if needed
        if (rotate.get()) {
            Vec3 hitVec = Vec3.atCenterOf(pos);
            Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec));
        }

        // Detonate anchor (right click with empty hand or any item)
        Vec3 hitVec = Vec3.atCenterOf(pos);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
    }

    private double calculateAnchorDamage(BlockPos pos) {
        // Simple damage calculation for respawn anchor
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double distance = playerPos.distanceTo(Vec3.atCenterOf(pos));
        if (distance > 10) return 0;

        // Respawn anchor does significant damage
        double damage = (1 - (distance / 10)) * 20;
        return Math.max(0, damage);
    }

    private BlockPos findNearbyUnchargedAnchor(Vec3 playerPos) {
        if (mc.level == null) return null;

        BlockPos playerBlockPos = new BlockPos((int)playerPos.x, (int)playerPos.y, (int)playerPos.z);

        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = playerBlockPos.offset(x, y, z);
                    double dist = playerPos.distanceTo(Vec3.atCenterOf(pos));

                    if (dist <= placeRange.get()) {
                        Block block = mc.level.getBlockState(pos).getBlock();
                        if (block == Blocks.RESPAWN_ANCHOR) {
                            int charges = mc.level.getBlockState(pos).getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                            if (charges < chargeLevel.get()) {
                                return pos;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private BlockPos findNearbyChargedAnchor(Vec3 playerPos) {
        if (mc.level == null) return null;

        BlockPos playerBlockPos = new BlockPos((int)playerPos.x, (int)playerPos.y, (int)playerPos.z);

        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = playerBlockPos.offset(x, y, z);
                    double dist = playerPos.distanceTo(Vec3.atCenterOf(pos));

                    if (dist <= placeRange.get()) {
                        Block block = mc.level.getBlockState(pos).getBlock();
                        if (block == Blocks.RESPAWN_ANCHOR) {
                            int charges = mc.level.getBlockState(pos).getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES);
                            if (charges >= chargeLevel.get()) {
                                // Check anti-suicide
                                if (antiSuicide.get()) {
                                    double damage = calculateAnchorDamage(pos);
                                    if (mc.player.getHealth() - damage < minHealth.get()) {
                                        continue;
                                    }
                                }
                                return pos;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }
}
