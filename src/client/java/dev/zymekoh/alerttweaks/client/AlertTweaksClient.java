package dev.zymekoh.alerttweaks.client;

import dev.zymekoh.alerttweaks.AlertTweaks;
import dev.zymekoh.alerttweaks.client.alert.AlertCards;
import dev.zymekoh.alerttweaks.client.alert.AlertFeed;
import dev.zymekoh.alerttweaks.client.alert.AlertMonitor;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.Gfx;
import dev.zymekoh.alerttweaks.client.gui.Icons;
import dev.zymekoh.alerttweaks.client.hud.HeartbeatSound;
import dev.zymekoh.alerttweaks.client.hud.LowHealthVignette;
import dev.zymekoh.alerttweaks.client.threat.ThreatIndicatorRenderer;
import dev.zymekoh.alerttweaks.client.threat.ThreatTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public final class AlertTweaksClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AlertTweaksConfig.load();
		// Last, so they sit on top of the rest of the HUD; the vignette first so arrows and cards draw over it.
		HudElementRegistry.addLast(AlertTweaks.id("low_health_vignette"), (graphics, delta) -> LowHealthVignette.render(Gfx.of(graphics), delta));
		HudElementRegistry.addLast(AlertTweaks.id("threat_indicator"), (graphics, delta) -> ThreatIndicatorRenderer.render(Gfx.of(graphics), delta));
		HudElementRegistry.addLast(AlertTweaks.id("alert_cards"), (graphics, delta) -> AlertCards.renderHud(Gfx.of(graphics), delta));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			HeartbeatSound.tick(client);
			ThreatTracker.tick(client);
			AlertMonitor.tick(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AlertMonitor.reset();
			AlertFeed.HUD.clear();
			Icons.clearCache();
		});
	}
}
