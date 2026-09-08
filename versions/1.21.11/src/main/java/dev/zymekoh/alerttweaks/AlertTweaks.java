package dev.zymekoh.alerttweaks;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AlertTweaks implements ModInitializer {
	public static final String MOD_ID = "alert_tweaks";
	public static final Logger LOGGER = LoggerFactory.getLogger("Alert Tweaks KoHs");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// Sounds live in the main entrypoint because the registry is still open there.
		AlertTweaksSounds.register();
		LOGGER.info("Alert Tweaks KoHs loaded.");
	}
}
