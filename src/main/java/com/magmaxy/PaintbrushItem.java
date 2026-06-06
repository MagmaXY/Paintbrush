package com.magmaxy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;

import static net.minecraft.world.item.Items.STRING;

public class PaintbrushItem extends BrushItem {

    @SuppressWarnings("deprecation")
    public PaintbrushItem(Item.Properties properties) {
        super(properties
                .durability(64)
                .enchantable(14)
                .component(DataComponents.REPAIRABLE, new Repairable(HolderSet.direct(STRING.builtInRegistryHolder())))
                .component(DataComponents.BASE_COLOR, DyeColor.WHITE)
        );
    }

    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && this.calculateHitResult(context.getLevel(), player).getType() == HitResult.Type.BLOCK) {
            player.startUsingItem(context.getHand());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player user, @NonNull InteractionHand hand) {
        if (this.calculateHitResult(level, user).getType() == HitResult.Type.BLOCK) {
            user.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public int getUseDuration(@NonNull ItemStack stack, LivingEntity entity) {
        float baseTicks = 40.0f;
        float multiplier = 1.0f;

        int effLevel = EnchantmentHelper.getItemEnchantmentLevel(entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), stack);
        if (effLevel > 0) {
            multiplier *= switch (effLevel) {
                case 1 -> 1.30f;
                case 2 -> 1.69f;
                case 3 -> 2.20f;
                case 4 -> 289f / 100f;
                case 5 -> 3.71f;
                default -> 1.0f + (effLevel * 0.3f);
            };
        }

        if (entity.hasEffect(MobEffects.HASTE)) {
            int hasteLevel = Objects.requireNonNull(entity.getEffect(MobEffects.HASTE)).getAmplifier() + 1;
            multiplier *= (1.0f + (hasteLevel * 0.2f));
        }

        if (entity.hasEffect(MobEffects.MINING_FATIGUE)) {
            int fatigueLevel = Objects.requireNonNull(entity.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier() + 1;
            multiplier *= Math.max(0.1f, 1.0f - (fatigueLevel * 0.3f));
        }

        return Math.max(1, (int) (baseTicks / multiplier));
    }

    @Override
    public void onUseTick(@NonNull Level level, @NonNull LivingEntity entity, @NonNull ItemStack stack, int remainingUseDuration) {
        if (remainingUseDuration >= 0 && entity instanceof Player player) {
            HitResult hitresult = this.calculateHitResult(level, player);
            if (hitresult instanceof BlockHitResult blockHitResult && hitresult.getType() == HitResult.Type.BLOCK) {

                int tickProgress = this.getUseDuration(stack, entity) - remainingUseDuration + 1;

                if (tickProgress % 10 == 5) {
                    BlockPos pos = blockHitResult.getBlockPos();
                    BlockState state = level.getBlockState(pos);
                    level.playSound(player, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 1.0F, 1.0F);

                    if (state.shouldSpawnTerrainParticles() && state.getRenderShape() != RenderShape.INVISIBLE) {
                        HumanoidArm arm = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();

                        BlockState particleState = state;
                        DyeColor brushColor = stack.get(DataComponents.BASE_COLOR);

                        if (brushColor != null) {
                            InteractionHand otherHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                            ItemStack offhandStack = player.getItemInHand(otherHand);
                            boolean isDyeing = offhandStack.getItem() instanceof DyeItem && offhandStack.get(DataComponents.DYE) == brushColor;

                            if (isDyeing) {
                                Optional<Holder.Reference<Block>> coloredBlockHolder = getColoredBlock(state.getBlock(), brushColor);
                                if (coloredBlockHolder.isPresent()) {
                                    Block newBlock = coloredBlockHolder.get().value();
                                    if (newBlock != state.getBlock()) {
                                        particleState = copyProperties(state, newBlock.defaultBlockState());
                                    }
                                }
                            }
                        }
                        this.spawnDustParticles(level, blockHitResult, particleState, player.getViewVector(0.0F), arm);
                    }
                }

                if (tickProgress >= this.getUseDuration(stack, entity)) {
                    if (!level.isClientSide()) {
                        InteractionHand otherHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                        ItemStack offhandStack = player.getItemInHand(otherHand);

                        boolean isWater = offhandStack.is(Items.POTION) && offhandStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
                        boolean isAmethyst = offhandStack.is(Items.AMETHYST_SHARD);
                        DyeColor brushColor = stack.get(DataComponents.BASE_COLOR);

                        if (isWater) {
                            performWash(level, player, stack, offhandStack);
                        } else if (isAmethyst && brushColor != null) {
                            performTinting(level, player, stack, offhandStack, blockHitResult);
                        } else {
                            performPaint(level, player, stack, blockHitResult);
                        }
                    }
                    player.stopUsingItem();
                }
            } else {
                player.stopUsingItem();
            }
        }
    }

    private void performTinting(Level level, Player player, ItemStack brushStack, ItemStack offhandStack, BlockHitResult hitResult) {
        if (level.isClientSide()) return;

        BlockState state = level.getBlockState(hitResult.getBlockPos());
        Block block = state.getBlock();

        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String path = id.getPath();

        boolean isStainedGlass = path.contains("glass")   && !path.contains("pane") && !path.contains("tinted");

        if (isStainedGlass && block != Blocks.TINTED_GLASS) {

            paintBlockAt(level, hitResult.getBlockPos(), Blocks.TINTED_GLASS, 3);

            level.playSound(null, hitResult.getBlockPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5F, 1.2F);

            if (!player.isCreative()) {
                offhandStack.shrink(1);
                brushStack.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
    }

    private void performPaint(Level level, Player player, ItemStack brushStack, BlockHitResult hitResult) {
        DyeColor brushColor = brushStack.get(DataComponents.BASE_COLOR);
        if (brushColor == null) return;
        InteractionHand otherHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack offhandStack = player.getItemInHand(otherHand);
        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Block originalBlock = state.getBlock();

        boolean isDyeing = offhandStack.getItem() instanceof DyeItem && offhandStack.get(DataComponents.DYE) == brushColor;
        if (!isDyeing) return;

        Optional<Holder.Reference<Block>> coloredBlockHolder = getColoredBlock(originalBlock, brushColor);
        if (coloredBlockHolder.isPresent()) {
            Block newBlock = coloredBlockHolder.get().value();

            if (originalBlock == newBlock) return;

            if (originalBlock instanceof AbstractBannerBlock) {
                paintBlockAt(level, pos, newBlock, 3);
            } else if (originalBlock instanceof BedBlock) {
                BedPart part = state.getValue(BedBlock.PART);
                Direction facing = state.getValue(BedBlock.FACING);
                BlockPos otherPos = part == BedPart.HEAD ? pos.relative(facing.getOpposite()) : pos.relative(facing);
                paintBlockAt(level, otherPos, newBlock, 50);
                paintBlockAt(level, pos, newBlock, 50);
            } else {
                paintBlockAt(level, pos, newBlock, 3);
            }
            level.playSound(null, pos, SoundEvents.GLOW_INK_SAC_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.isCreative()) {
                offhandStack.shrink(1);
                brushStack.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
    }

    private void paintBlockAt(Level level, BlockPos pos, Block newBlockType, int updateFlags) {
        BlockState oldState = level.getBlockState(pos);
        BlockEntity oldBE = level.getBlockEntity(pos);
        net.minecraft.nbt.CompoundTag beTag = (oldBE != null) ? oldBE.saveWithoutMetadata(level.registryAccess()) : null;
        level.setBlock(pos, copyProperties(oldState, newBlockType.defaultBlockState()), updateFlags);
        if (beTag != null) {
            BlockEntity newBE = level.getBlockEntity(pos);
            if (newBE != null) {
                newBE.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(new net.minecraft.util.ProblemReporter.Collector(), level.registryAccess(), beTag));
                newBE.setChanged();
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private BlockState copyProperties(BlockState oldState, BlockState newState) {
        for (Property property : oldState.getProperties()) {
            if (newState.hasProperty(property)) {
                newState = applyProperty(newState, property, oldState.getValue(property));
            }
        }
        return newState;
    }

    private <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> property, Comparable<?> value) {
        //noinspection unchecked
        return state.setValue(property, (T) value);
    }

    private Optional<Holder.Reference<Block>> getColoredBlock(Block original, DyeColor targetColor) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(original);
        String path = id.getPath();
        if (path.contains("_banner")) {
            String suffix = path.contains("wall_banner") ? "_wall_banner" : "_banner";
            return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath(id.getNamespace(), targetColor.getName() + suffix));
        }
        for (DyeColor color : DyeColor.values()) {
            String prefix = color.getName() + "_";
            if (path.startsWith(prefix)) {
                String suffix = path.substring(prefix.length());
                return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath(id.getNamespace(), targetColor.getName() + "_" + suffix));
            }
        }
        String newPath = switch (path) {
            case "glass" -> targetColor.getName() + "_stained_glass";
            case "glass_pane" -> targetColor.getName() + "_stained_glass_pane";
            case "terracotta" -> targetColor.getName() + "_terracotta";
            case "shulker_box" -> targetColor.getName() + "_shulker_box";
            case "candle" -> targetColor.getName() + "_candle";
            default -> null;
        };
        return newPath != null ? BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath(id.getNamespace(), newPath)) : Optional.empty();
    }

    private void performWash(Level level, Player player, ItemStack brushStack, ItemStack offhandStack) {
        ItemStack vanillaBrush = new ItemStack(Items.BRUSH);
        if (brushStack.has(DataComponents.DAMAGE)) vanillaBrush.set(DataComponents.DAMAGE, brushStack.get(DataComponents.DAMAGE));
        if (brushStack.has(DataComponents.ENCHANTMENTS)) vanillaBrush.set(DataComponents.ENCHANTMENTS, brushStack.get(DataComponents.ENCHANTMENTS));
        player.setItemInHand(player.getUsedItemHand(), vanillaBrush);
        if (!player.isCreative()) {
            offhandStack.shrink(1);
            player.setItemInHand(player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
            vanillaBrush.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private void spawnDustParticles(Level level, BlockHitResult hitResult, BlockState state, Vec3 viewVector, HumanoidArm arm) {
        int flip = arm == HumanoidArm.RIGHT ? 1 : -1;
        int particleCount = level.getRandom().nextInt(7) + 8;
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Direction direction = hitResult.getDirection();
        DustParticlesDelta delta = DustParticlesDelta.fromDirection(viewVector, direction);
        Vec3 loc = hitResult.getLocation();

        for (int i = 0; i < particleCount; ++i) {
            level.addParticle(
                    particle,
                    loc.x - (direction == Direction.WEST ? 1.0E-6F : 0.0),
                    loc.y,
                    loc.z - (direction == Direction.NORTH ? 1.0E-6F : 0.0),
                    delta.xd() * (double) flip * 3.0 * level.getRandom().nextDouble(),
                    0.0,
                    delta.zd() * (double) flip * 3.0 * level.getRandom().nextDouble()
            );
        }
    }

    private HitResult calculateHitResult(Level level, Player player) {
        return getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
    }

    @Override
    public @NonNull ItemUseAnimation getUseAnimation(@NonNull ItemStack stack) { return ItemUseAnimation.BRUSH; }

    @Override
    public @NonNull Component getName(ItemStack stack) {
        DyeColor color = stack.get(DataComponents.BASE_COLOR);
        return color == null ? super.getName(stack) : Component.translatable("item.paintbrush." + color.getName()).withStyle(style -> style.withItalic(false));
    }

    private record DustParticlesDelta(double xd, double yd, double zd) {
        private static final double ALONG_SIDE_DELTA = 1.0;
        private static final double OUT_FROM_SIDE_DELTA = 0.1;

        public static DustParticlesDelta fromDirection(Vec3 viewVector, Direction hitDirection) {
            return switch (hitDirection) {
                case DOWN, UP -> new DustParticlesDelta(viewVector.z(), 0.0, -viewVector.x());
                case NORTH -> new DustParticlesDelta(ALONG_SIDE_DELTA, 0.0, -OUT_FROM_SIDE_DELTA);
                case SOUTH -> new DustParticlesDelta(-ALONG_SIDE_DELTA, 0.0, OUT_FROM_SIDE_DELTA);
                case WEST -> new DustParticlesDelta(-OUT_FROM_SIDE_DELTA, 0.0, -ALONG_SIDE_DELTA);
                case EAST -> new DustParticlesDelta(OUT_FROM_SIDE_DELTA, 0.0, ALONG_SIDE_DELTA);
            };
        }
    }
}