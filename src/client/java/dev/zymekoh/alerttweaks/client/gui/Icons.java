package dev.zymekoh.alerttweaks.client.gui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;

/**
 * Icons straight from Minecraft's own PNGs (items, blocks, effects). Never an ItemStack: item
 * components are not bound on the title screen and rendering one there crashes the game.
 */
public final class Icons {
	public static final Identifier TOTEM = item("totem_of_undying");
	public static final Identifier HEART = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
	public static final Identifier SWORD = item("iron_sword");
	public static final Identifier AXE = item("iron_axe");
	public static final Identifier BELL = item("bell");
	public static final Identifier SPYGLASS = item("spyglass");
	public static final Identifier AMETHYST = item("amethyst_shard");
	public static final Identifier CHESTPLATE = item("iron_chestplate");
	public static final Identifier BEEF = item("cooked_beef");
	public static final Identifier FEATHER = item("feather");
	public static final Identifier BUNDLE = item("bundle");
	public static final Identifier PEARL = item("ender_pearl");
	public static final Identifier GAPPLE = item("golden_apple");
	public static final Identifier CLOCK = item("clock_00");
	public static final Identifier POTION = item("potion");
	public static final Identifier POISON = Identifier.withDefaultNamespace("textures/mob_effect/poison.png");
	public static final Identifier ECHO = item("echo_shard");
	public static final Identifier BOOK = item("writable_book");
	public static final Identifier NETHER_STAR = item("nether_star");
	public static final Identifier GOAT_HORN = item("goat_horn");
	public static final Identifier NAME_TAG = item("name_tag");
	public static final Identifier MACE = item("mace");

	private static final Map<Identifier, Integer> SIZES = new HashMap<>();
	private static final Map<Item, Identifier> ITEM_CACHE = new HashMap<>();
	private static final Map<MobEffect, Identifier> EFFECT_CACHE = new HashMap<>();

	static {
		SIZES.put(HEART, 9);
		SIZES.put(POISON, 18);
	}

	private Icons() {
	}

	public static Identifier item(String path) {
		return Identifier.withDefaultNamespace("textures/item/" + path + ".png");
	}

	/** Pixel size of a whole icon texture (items 16, effects 18, the HUD heart 9). */
	public static int sizeOf(Identifier texture) {
		return SIZES.getOrDefault(texture, 16);
	}

	/** The item's own texture, its block's top texture for block items, or the fallback. */
	public static Identifier of(Item item, Identifier fallback) {
		return ITEM_CACHE.computeIfAbsent(item, key -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(key);
			Identifier asItem = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/item/" + id.getPath() + ".png");
			if (exists(asItem)) {
				return asItem;
			}
			Identifier asBlock = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/block/" + id.getPath() + ".png");
			return exists(asBlock) ? asBlock : fallback;
		});
	}

	public static Identifier of(Holder<MobEffect> effect) {
		return EFFECT_CACHE.computeIfAbsent(effect.value(), key -> {
			Identifier id = effect.unwrapKey().map(k -> k.identifier()).orElse(null);
			if (id == null) {
				return POTION;
			}
			Identifier texture = Identifier.fromNamespaceAndPath(id.getNamespace(), "textures/mob_effect/" + id.getPath() + ".png");
			if (!exists(texture)) {
				return POTION;
			}
			SIZES.put(texture, 18);
			return texture;
		});
	}

	private static boolean exists(Identifier texture) {
		return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
	}

	/** Resource packs can change what exists. */
	public static void clearCache() {
		ITEM_CACHE.clear();
		EFFECT_CACHE.clear();
	}
}
