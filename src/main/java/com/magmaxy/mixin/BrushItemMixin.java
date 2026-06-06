package com.magmaxy.mixin;

import com.magmaxy.Paintbrush;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.UnknownNullability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Optional;

@Mixin(BrushItem.class)
public abstract class BrushItemMixin {

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    public void onUseTickTransform(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        InteractionHand otherHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack offhandStack = player.getItemInHand(otherHand);
        HitResult hitresult = player.pick(player.blockInteractionRange(), 0.0F, false);

        if (hitresult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult blockHit = (BlockHitResult) hitresult;

        boolean isDye = offhandStack.getItem() instanceof DyeItem;
        boolean isWater = offhandStack.is(Items.POTION) && offhandStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);

        if (isDye || isWater) {

            int effLevel = EnchantmentHelper.getItemEnchantmentLevel(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), stack);
            float multiplier = 1.0f;

            multiplier *= switch (effLevel) {
                case 1 -> 1.30f;
                case 2 -> 1.69f;
                case 3 -> 2.20f;
                case 4 -> 2.89f;
                case 5 -> 3.71f;
                default -> 1.0f;
            };

            if (player.hasEffect(MobEffects.HASTE)) {
                multiplier *= (1.0f + ((Objects.requireNonNull(player.getEffect(MobEffects.HASTE)).getAmplifier() + 1) * 0.2f));
            }

            if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
                int fatigueLevel = Objects.requireNonNull(player.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier() + 1;
                multiplier *= Math.max(0.1f, 1.0f - (fatigueLevel * 0.3f));
            }

            int targetTicks = Math.max(1, (int) (40.0f / multiplier));
            int tickProgress = stack.getUseDuration(player) - remainingUseDuration + 1;

            if (isDye) {
                handleBrushTransformation(level, player, stack, offhandStack, blockHit, tickProgress, targetTicks);
            } else {
                handleWashing(level, player, stack, offhandStack, blockHit, tickProgress, targetTicks);
            }

            ci.cancel();
        }
    }

    @Unique
    private void handleBrushTransformation(Level level, Player player, ItemStack stack, ItemStack offhandStack, BlockHitResult hitResult, int tickProgress, int targetTicks) {

        if (tickProgress % 10 == 5 && tickProgress < targetTicks) {
            level.playSound(player, hitResult.getBlockPos(), SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (tickProgress >= targetTicks) {
            if (!level.isClientSide()) {
                DyeColor dyeColor = offhandStack.get(DataComponents.DYE);
                ItemStack newBrush = new ItemStack(Paintbrush.PAINTBRUSH);

                if (stack.has(DataComponents.DAMAGE)) newBrush.set(DataComponents.DAMAGE, stack.get(DataComponents.DAMAGE));
                if (stack.has(DataComponents.ENCHANTMENTS)) newBrush.set(DataComponents.ENCHANTMENTS, stack.get(DataComponents.ENCHANTMENTS));
                if (stack.has(DataComponents.REPAIRABLE)) newBrush.set(DataComponents.REPAIRABLE, stack.get(DataComponents.REPAIRABLE));

                newBrush.set(DataComponents.BASE_COLOR, dyeColor);
                player.setItemInHand(player.getUsedItemHand(), newBrush);

                if (!player.isCreative()) {
                    offhandStack.shrink(1);
                    newBrush.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.GLOW_INK_SAC_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            player.stopUsingItem();
        }
    }

    @Unique
    private void handleWashing(Level level, Player player, ItemStack brushStack, ItemStack offhandStack, BlockHitResult hitResult, int tickProgress, int targetTicks) {
        if (tickProgress % 10 == 5 && tickProgress < targetTicks) {
            level.playSound(player, hitResult.getBlockPos(), SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (tickProgress >= targetTicks) {
            if (!level.isClientSide()) {
                Optional<Holder.Reference<Block>> uncoloredBlock = getUncoloredBlock(level.getBlockState(hitResult.getBlockPos()).getBlock());

                if (uncoloredBlock.isPresent()) {
                    washBlockAt(level, hitResult.getBlockPos(), uncoloredBlock.get().value());
                    level.playSound(null, hitResult.getBlockPos(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);

                    if (!player.isCreative()) {
                        offhandStack.shrink(1);
                        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
                        brushStack.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    }
                }
            }
            player.stopUsingItem();
        }
    }

    @Unique
    private BlockState copyProperties(BlockState oldState, BlockState newState) {
        for (Property<?> property : oldState.getProperties()) {
            if (newState.hasProperty(property)) {
                newState = applyProperty(newState, oldState, property);
            }
        }
        return newState;
    }

    @Unique
    private <T extends Comparable<T>> BlockState applyProperty(BlockState newState, BlockState oldState, Property<T> property) {
        return newState.setValue(property, oldState.getValue(property));
    }

    @Unique
    private void washBlockAt(Level level, BlockPos pos, @UnknownNullability Block newBlockType) {
        BlockState oldState = level.getBlockState(pos);

        BlockState newState = newBlockType.defaultBlockState();

        newState = copyProperties(oldState, newState);

        BlockEntity oldBE = level.getBlockEntity(pos);
        net.minecraft.nbt.CompoundTag beTag = null;
        if (oldBE != null) beTag = oldBE.saveWithoutMetadata(level.registryAccess());

        level.setBlock(pos, newState, 3);

        if (beTag != null) {
            BlockEntity newBE = level.getBlockEntity(pos);
            if (newBE != null) {
                try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(newBE.problemPath(), com.mojang.logging.LogUtils.getLogger())) {
                    newBE.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(reporter, level.registryAccess(), beTag));
                }
                newBE.setChanged();
            }
        }
    }

    @Unique
    private Optional<Holder.Reference<Block>> getUncoloredBlock(Block original) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(original);
        String path = id.getPath();

        if (path.equals("tinted_glass")) {
            return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath(id.getNamespace(), "glass"));
        }

        for (DyeColor color : DyeColor.values()) {
            String colorName = color.getName();
            if (path.startsWith(colorName + "_")) {
                String suffix = path.substring(colorName.length() + 1);

                String baseName = switch (suffix) {
                    case "stained_glass" -> "glass";
                    case "stained_glass_pane" -> "glass_pane";
                    case "terracotta" -> "terracotta";
                    case "candle" -> "candle";
                    case "shulker_box" -> "shulker_box";
                    default -> null;
                };

                if (baseName != null) {
                    return BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath(id.getNamespace(), baseName));
                }
            }
        }
        return Optional.empty();
    }
}