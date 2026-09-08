package dev.zymekoh.alerttweaks.client;

import dev.zymekoh.alerttweaks.AlertTweaks;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.hud.HeartbeatSound;
import dev.zymekoh.alerttweaks.client.hud.LowHealthVignette;
import dev.zymekoh.alerttweaks.client.threat.ThreatIndicatorRenderer;
import dev.zymekoh.alerttweaks.client.threat.ThreatTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class AlertTweaksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AlertTweaksConfig.load();
		// Both elements go last so they sit on top of the rest of the HUD, the way the old
		// HudRenderCallback used to behave. The vignette is registered first so arrows draw over it.
		HudElementRegistry.addLast(AlertTweaks.id("low_health_vignette"), LowHealthVignette::render);
		HudElementRegistry.addLast(AlertTweaks.id("threat_indicator"), ThreatIndicatorRenderer::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			HeartbeatSound.tick(client);
			ThreatTracker.tick(client);
		});
	}
}
