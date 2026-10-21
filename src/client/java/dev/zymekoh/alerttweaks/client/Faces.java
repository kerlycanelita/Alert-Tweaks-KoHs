package dev.zymekoh.alerttweaks.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Player faces by name. The skin comes from Mojang through the game's own profile cache (the one
 * player heads use), so it is the same skin NameMC shows for premium names; names without a Mojang
 * account fall back to the default skins. Lookups are asynchronous and cached by the game.
 */
public final class Faces {
	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();
	private static final Map<String, Supplier<PlayerSkin>> SUPPLIERS = new HashMap<>();

	private Faces() {
	}

	public static Supplier<PlayerSkin> byName(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		return SUPPLIERS.computeIfAbsent(key, k -> {
			ResolvableProfile profile = PROFILES.computeIfAbsent(k, n -> ResolvableProfile.createUnresolved(name));
			return () -> Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin();
		});
	}
}
