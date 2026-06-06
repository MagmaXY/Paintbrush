package com.magmaxy.mixin;

import com.magmaxy.Paintbrush;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {

	@Shadow private Collection<ItemStack> displayItems;
	@Shadow private Set<ItemStack> displayItemsSearchTab;

	@Inject(method = "buildContents", at = @At("TAIL"))
	private void addCustomItemsToTab(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
		CreativeModeTab currentTab = (CreativeModeTab) (Object) this;
		Optional<Holder.Reference<CreativeModeTab>> toolsTab = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.TOOLS_AND_UTILITIES);

		if (currentTab != null && toolsTab.isPresent() && currentTab == toolsTab.get().value()) {
			ItemStack paintbrushStack = Paintbrush.PAINTBRUSH.getDefaultInstance();

			this.displayItems.add(paintbrushStack);
			this.displayItemsSearchTab.add(paintbrushStack);
		}
	}
}