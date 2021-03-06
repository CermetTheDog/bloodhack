package dev.lors.bloodhack.module.BloodModules.combat;

import dev.lors.bloodhack.managers.Value;
import dev.lors.bloodhack.module.Category;
import dev.lors.bloodhack.module.Module;
import dev.lors.bloodhack.utils.EntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AutoCrystal extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean togglePitch = false;
    private static double yaw;
    private static double pitch;
    private static boolean isSpoofingAngles;
    public boolean isActive = false;
    Value<Boolean> place = new Value<Boolean>("Place", true);
    Value<Integer> range = new Value<Integer>("Range", 5, 0, 10);
    Value<Integer> delay = new Value<Integer>("Delay", 2, 0, 20);
    private BlockPos render;
    private Entity renderEnt;
    private final long systemTime = -1;
    private boolean switchCooldown = false;
    private final boolean isAttacking = false;
    private final int oldSlot = -1;
    private int newSlot;
    private long placeSystemTime;
    private long breakSystemTime;
    private long chatSystemTime;
    private long multiPlaceSystemTime;
    private long antiStuckSystemTime;
    private int placements;
    public AutoCrystal() {
        super("AutoCrystal", Category.COMBAT);
    }

    public static BlockPos getPlayerPos() {
        return new BlockPos(Math.floor(AutoCrystal.mc.player.posX), Math.floor(AutoCrystal.mc.player.posY), Math.floor(AutoCrystal.mc.player.posZ));
    }

    public static float calculateDamage(final double posX, final double posY, final double posZ, final Entity entity) {
        final float doubleExplosionSize = 12.0f;
        final double distancedsize = entity.getDistance(posX, posY, posZ) / doubleExplosionSize;
        final Vec3d vec3d = new Vec3d(posX, posY, posZ);
        final double blockDensity = entity.world.getBlockDensity(vec3d, entity.getEntityBoundingBox());
        final double v = (1.0 - distancedsize) * blockDensity;
        final float damage = (float) (int) ((v * v + v) / 2.0 * 7.0 * doubleExplosionSize + 1.0);
        double finald = 1.0;
        if (entity instanceof EntityLivingBase) {
            finald = getBlastReduction((EntityLivingBase) entity, getDamageMultiplied(damage), new Explosion(AutoCrystal.mc.world, null, posX, posY, posZ, 6.0f, false, true));
        }
        return (float) finald;
    }

    public static float getBlastReduction(final EntityLivingBase entity, float damage, final Explosion explosion) {
        if (entity instanceof EntityPlayer) {
            final EntityPlayer ep = (EntityPlayer) entity;
            final DamageSource ds = DamageSource.causeExplosionDamage(explosion);
            damage = CombatRules.getDamageAfterAbsorb(damage, (float) ep.getTotalArmorValue(), (float) ep.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
            final int k = EnchantmentHelper.getEnchantmentModifierDamage(ep.getArmorInventoryList(), ds);
            final float f = MathHelper.clamp((float) k, 0.0f, 20.0f);
            damage *= 1.0f - f / 25.0f;
            if (entity.isPotionActive(Potion.getPotionById(11))) {
                damage -= damage / 4.0f;
            }
            return damage;
        }
        damage = CombatRules.getDamageAfterAbsorb(damage, (float) entity.getTotalArmorValue(), (float) entity.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        return damage;
    }

    private static float getDamageMultiplied(final float damage) {
        final int diff = AutoCrystal.mc.world.getDifficulty().getId();
        return damage * ((diff == 0) ? 0.0f : ((diff == 2) ? 1.0f : ((diff == 1) ? 0.5f : 1.5f)));
    }

    public static float calculateDamage(final EntityEnderCrystal crystal, final Entity entity) {
        return calculateDamage(crystal.posX, crystal.posY, crystal.posZ, entity);
    }

    public static boolean canBlockBeSeen(final BlockPos blockPos) {
        return AutoCrystal.mc.world.rayTraceBlocks(new Vec3d(AutoCrystal.mc.player.posX, AutoCrystal.mc.player.posY + AutoCrystal.mc.player.getEyeHeight(), AutoCrystal.mc.player.posZ), new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()), false, true, false) == null;
    }

    private static void setYawAndPitch(final float yaw1, final float pitch1) {
        AutoCrystal.yaw = yaw1;
        AutoCrystal.pitch = pitch1;
    }

    private static void resetRotation() {
        AutoCrystal.yaw = AutoCrystal.mc.player.rotationYaw;
        AutoCrystal.pitch = AutoCrystal.mc.player.rotationPitch;
    }

    @Override
    public void setup() {
        this.placeSystemTime = -1L;
        this.breakSystemTime = -1L;
        this.chatSystemTime = -1L;
        this.multiPlaceSystemTime = -1L;
        this.antiStuckSystemTime = -1L;
        this.switchCooldown = false;
        this.placements = 0;
    }

    @Override
    public void onUpdate() {
        final EntityEnderCrystal crystal = (EntityEnderCrystal) AutoCrystal.mc.world.loadedEntityList.stream().filter(entity -> entity instanceof EntityEnderCrystal).map(entity -> entity).min(Comparator.comparing(c -> AutoCrystal.mc.player.getDistance(c))).orElse(null);
        if (crystal != null && AutoCrystal.mc.player.getDistance(crystal) <= 5) {
            if (System.nanoTime() / 1000000L - this.breakSystemTime >= 420 - 5 * 20) {
                this.lookAtPacket(crystal.posX, crystal.posY, crystal.posZ, AutoCrystal.mc.player);
                AutoCrystal.mc.playerController.attackEntity(AutoCrystal.mc.player, crystal);
                AutoCrystal.mc.player.swingArm(EnumHand.MAIN_HAND);
                this.breakSystemTime = System.nanoTime() / 1000000L;
            }
            if (System.nanoTime() / 1000000L - this.antiStuckSystemTime <= 400 + (400 - 5 * 20)) {
                return;
            }
        } else {
            resetRotation();
        }
        int crystalSlot = (AutoCrystal.mc.player.getHeldItemMainhand().getItem() == Items.END_CRYSTAL) ? AutoCrystal.mc.player.inventory.currentItem : -1;
        if (crystalSlot == -1) {
            for (int l = 0; l < 9; ++l) {
                if (AutoCrystal.mc.player.inventory.getStackInSlot(l).getItem() == Items.END_CRYSTAL) {
                    crystalSlot = l;
                    break;
                }
            }
        }
        boolean offhand = false;
        if (mc.player.getHeldItemOffhand().getItem() == Items.END_CRYSTAL) {
            offhand = true;
        } else if (crystalSlot == -1) {
            return;
        }
        Entity ent = null;
        Entity lastTarget = null;

        BlockPos finalPos = null;
        final List<BlockPos> blocks = this.findCrystalBlocks();
        final List<Entity> entities = new ArrayList<Entity>();
        entities.addAll(AutoCrystal.mc.world.playerEntities.stream().filter(entityPlayer -> true).collect(Collectors.toList()));
        double damage = 0.5;
        for (final Entity entity2 : entities) {
            if (entity2 != AutoCrystal.mc.player) {
                if (((EntityLivingBase) entity2).getHealth() <= 0.0f) {
                    continue;
                }
                if (AutoCrystal.mc.player.getDistanceSq(entity2) > 5 * 5) {
                    continue;
                }
                for (final BlockPos blockPos : blocks) {
                    if (!canBlockBeSeen(blockPos) && AutoCrystal.mc.player.getDistanceSq(blockPos) > 25.0) {
                        continue;
                    }
                    final double b = entity2.getDistanceSq(blockPos);
                    if (b > 56.2) {
                        continue;
                    }
                    final double d = calculateDamage(blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5, entity2);
                    if (d < 7 && ((EntityLivingBase) entity2).getHealth() + ((EntityLivingBase) entity2).getAbsorptionAmount() > 20) {
                        continue;
                    }
                    if (d <= damage) {
                        continue;
                    }
                    final double self = calculateDamage(blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5, AutoCrystal.mc.player);
                    if (AutoCrystal.mc.player.getHealth() + AutoCrystal.mc.player.getAbsorptionAmount() - self <= 7.0) {
                        continue;
                    }
                    if (self > d) {
                        continue;
                    }
                    damage = d;
                    finalPos = blockPos;
                    ent = entity2;
                    lastTarget = entity2;

                }
            }
        }
        if (damage == 0.5) {
            this.render = null;
            this.renderEnt = null;
            resetRotation();
            return;
        }
        this.render = finalPos;
        this.renderEnt = ent;

        if (true) {
            if (!offhand && AutoCrystal.mc.player.inventory.currentItem != crystalSlot) {
                AutoCrystal.mc.player.inventory.currentItem = crystalSlot;
                resetRotation();
                this.switchCooldown = true;
                return;
            }
            this.lookAtPacket(finalPos.getX() + 0.5, finalPos.getY() - 0.5, finalPos.getZ() + 0.5, AutoCrystal.mc.player);
            final RayTraceResult result = AutoCrystal.mc.world.rayTraceBlocks(new Vec3d(AutoCrystal.mc.player.posX, AutoCrystal.mc.player.posY + AutoCrystal.mc.player.getEyeHeight(), AutoCrystal.mc.player.posZ), new Vec3d(finalPos.getX() + 0.5, finalPos.getY() - 0.5, finalPos.getZ() + 0.5));
            EnumFacing f;
            if (result == null || result.sideHit == null) {
                f = EnumFacing.UP;
            } else {
                f = result.sideHit;
            }
            if (this.switchCooldown) {
                this.switchCooldown = false;
                return;
            }
            if (System.nanoTime() / 1000000L - this.placeSystemTime >= delay.value) {
                AutoCrystal.mc.player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(finalPos, f, offhand ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND, 0.0f, 0.0f, 0.0f));
                ++this.placements;
                this.antiStuckSystemTime = System.nanoTime() / 1000000L;
                this.placeSystemTime = System.nanoTime() / 1000000L;
            }
        }
        if (AutoCrystal.togglePitch) {
            final EntityPlayerSP player = AutoCrystal.mc.player;
            player.rotationPitch += (float) 4.0E-4;
            AutoCrystal.togglePitch = false;
        } else {
            final EntityPlayerSP player2 = AutoCrystal.mc.player;
            player2.rotationPitch -= (float) 4.0E-4;
            AutoCrystal.togglePitch = true;
        }
    }

    private void lookAtPacket(final double px, final double py, final double pz, final EntityPlayer me) {
        final double[] v = EntityUtil.calculateLookAt(px, py, pz, me);
        setYawAndPitch((float) v[0], (float) v[1]);
    }

    private boolean canPlaceCrystal(final BlockPos blockPos) {
        final BlockPos boost = blockPos.add(0, 1, 0);
        final BlockPos boost2 = blockPos.add(0, 2, 0);
        return (AutoCrystal.mc.world.getBlockState(blockPos).getBlock() == Blocks.BEDROCK || AutoCrystal.mc.world.getBlockState(blockPos).getBlock() == Blocks.OBSIDIAN) && AutoCrystal.mc.world.getBlockState(boost).getBlock() == Blocks.AIR && AutoCrystal.mc.world.getBlockState(boost2).getBlock() == Blocks.AIR && AutoCrystal.mc.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(boost)).isEmpty() && AutoCrystal.mc.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(boost2)).isEmpty();
    }

    private List<BlockPos> findCrystalBlocks() {
        NonNullList positions = NonNullList.create();
        positions.addAll(this.getSphere(AutoCrystal.getPlayerPos(), range.value, range.value, false, true, 0).stream().filter(this::canPlaceCrystal).collect(Collectors.toList()));
        return (List<BlockPos>) positions;
    }

    public List<BlockPos> getSphere(final BlockPos loc, final float r, final int h, final boolean hollow, final boolean sphere, final int plus_y) {
        final List<BlockPos> circleblocks = new ArrayList<BlockPos>();
        final int cx = loc.getX();
        final int cy = loc.getY();
        final int cz = loc.getZ();
        for (int x = cx - (int) r; x <= cx + r; ++x) {
            for (int z = cz - (int) r; z <= cz + r; ++z) {
                for (int y = sphere ? (cy - (int) r) : cy; y < (sphere ? (cy + r) : ((float) (cy + h))); ++y) {
                    final double dist = (cx - x) * (cx - x) + (cz - z) * (cz - z) + (sphere ? ((cy - y) * (cy - y)) : 0);
                    if (dist < r * r && (!hollow || dist >= (r - 1.0f) * (r - 1.0f))) {
                        final BlockPos l = new BlockPos(x, y + plus_y, z);
                        circleblocks.add(l);
                    }
                }
            }
        }
        return circleblocks;
    }
}