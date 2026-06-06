package com.magmaxy;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;

public class Paintbrush implements ModInitializer {

	public static final String MOD_ID = "paintbrush";

	public static final ResourceKey<Item> PAINTBRUSH_KEY = ResourceKey.create(
			Registries.ITEM,
			Identifier.fromNamespaceAndPath(MOD_ID, "paintbrush")
	);

	public static final Item PAINTBRUSH = Registry.register(
			BuiltInRegistries.ITEM,
			PAINTBRUSH_KEY,
			new PaintbrushItem(new Item.Properties().setId(PAINTBRUSH_KEY).stacksTo(1))
	);

	@Override
	public void onInitialize() {

	}
}