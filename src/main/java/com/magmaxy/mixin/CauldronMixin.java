package com.magmaxy.mixin;

import com.magmaxy.PaintbrushItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractCauldronBlock.class)
public abstract class CauldronMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onCauldronUse(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {

        ItemStack stack = player.getItemInHand(hand);

        if (stack.getItem() instanceof PaintbrushItem) {
            if (state.getBlock() instanceof LayeredCauldronBlock && state.getValue(LayeredCauldronBlock.LEVEL) > 0) {

                    ItemStack vanillaBrush = new ItemStack(Items.BRUSH);
                    if (stack.has(DataComponents.DAMAGE)) {
                        vanillaBrush.set(DataComponents.DAMAGE, stack.get(DataComponents.DAMAGE));
                    }
                    if (stack.has(DataComponents.ENCHANTMENTS)) {
                        vanillaBrush.set(DataComponents.ENCHANTMENTS, stack.get(DataComponents.ENCHANTMENTS));
                    }

                    player.setItemInHand(hand, vanillaBrush);

                    LayeredCauldronBlock.lowerFillLevel(state, level, pos);

                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }