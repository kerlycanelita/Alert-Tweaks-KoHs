package dev.zymekoh.alerttweaks.client.threat;

import dev.zymekoh.alerttweaks.AlertTweaks;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.threat.ThreatTracker.Threat;
import java.util.List;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/** Draws the arrows that point at whoever just hit the player. */
public final class ThreatIndicatorRenderer {
	private static final Identifier ARROW = AlertTweaks.id("textures/gui/threat_arrow.png");
	private static final Identifier BURST = AlertTweaks.id("textures/gui/threat_burst.png");

	/** Both sprites are square; the matrix does the scaling so this stays a plain 1:1 blit. */
	private static final int SPRITE = 32;

	private static final float BASE_ARROW_SIZE = 26.0F;
	/** Distance slider ends: 0 sits just clear of the crosshair, 1 pushes the arrows well out. */
	private static final float MIN_RADIUS = 22.0F;
	private static final float MAX_RADIUS = 100.0F;

	private static final float SPAWN_MS = 190.0F;
	private static final float BURST_MS = 340.0F;
	private static final float FADE_START_MS = 3800.0F;
	private static final float ECHO_START_MS = 4500.0F;

	/** Reference GUI height the adaptive size is calibrated against (1080p at GUI scale 4). */
	private static final float ADAPTIVE_REFERENCE_HEIGHT = 270.0F;

	private ThreatIndicatorRenderer() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		AlertTweaksConfig config = AlertTweaksConfig.get();
		LocalPlayer player = minecraft.player;
		if (!config.threatIndicatorEnabled || player == null || minecraft.options.hideGui || player.isSpectator()) {
			return;
		}
		List<Threat> threats = ThreatTracker.active();
		if (threats.isEmpty()) {
			return;
		}

		float viewYaw = player.getViewYRot(deltaTracker.getGameTimeDeltaPartialTick(false));
		float centerX = graphics.guiWidth() / 2.0F;
		float centerY = graphics.guiHeight() / 2.0F;
		float scale = effectiveScale(graphics, config);
		long now = Util.getMillis();

		for (Threat threat : threats) {
			long age = now - threat.startedAtMillis();
			if (age < 0L || age > ThreatTracker.LIFETIME_MS) {
				continue;
			}
			double dx = threat.x() - player.getX();
			double dz = threat.z() - player.getZ();
			if (dx * dx + dz * dz < 4.0E-4) {
				continue;
			}
			float relative = Mth.wrapDegrees(ThreatTracker.angleTo(player, threat.x(), threat.z()) - viewYaw);
			draw(graphics, centerX, centerY, relative, scale, age);
		}
	}

	/**
	 * Adaptive keeps the indicator the same physical size on screen: GUI pixels get bigger as the GUI
	 * scale goes up, so a constant fraction of the GUI height cancels the scale out.
	 */
	public static float effectiveScale(GuiGraphicsExtractor graphics, AlertTweaksConfig config) {
		if (!config.threatAdaptiveScale) {
			return config.threatScale;
		}
		return Mth.clamp(graphics.guiHeight() / ADAPTIVE_REFERENCE_HEIGHT, 0.55F, 2.5F);
	}

	/**
	 * One indicator, animated from the age of the hit.
	 *
	 * <p>It punches out of the crosshair with a shockwave, breathes while it holds, and on the way out it
	 * drifts outwards while a second ring expands behind it.
	 */
	public static void draw(GuiGraphicsExtractor graphics, float centerX, float centerY, float relativeDegrees,
		float scale, long ageMillis) {
		float spawn = Mth.clamp(ageMillis / SPAWN_MS, 0.0F, 1.0F);
		float ease = easeOutBack(spawn);
		float fade = ageMillis <= FADE_START_MS
			? 1.0F
			: 1.0F - Mth.clamp((ageMillis - FADE_START_MS) / (ThreatTracker.LIFETIME_MS - FADE_START_MS), 0.0F, 1.0F);
		float exit = 1.0F - fade;
		float breath = 1.0F + 0.045F * Mth.sin(ageMillis / 210.0F);

		float alpha = fade * fade * Mth.clamp(spawn * 1.8F, 0.0F, 1.0F);
		if (alpha <= 0.004F) {
			return;
		}

		float baseRadius = Mth.lerp(AlertTweaksConfig.get().threatDistance, MIN_RADIUS, MAX_RADIUS);
		float radius = baseRadius * scale * (0.70F + 0.30F * ease + 0.22F * exit);
		float arrowSize = BASE_ARROW_SIZE * scale * (0.55F + 0.45F * ease) * breath * (1.0F + 0.30F * exit);
		float radians = relativeDegrees * Mth.DEG_TO_RAD;
		float x = centerX + Mth.sin(radians) * radius;
		float y = centerY - Mth.cos(radians) * radius;

		Matrix3x2fStack pose = graphics.pose();

		// Impact shockwave: a quick flash right where the arrow lands.
		if (ageMillis < BURST_MS) {
			float burst = Mth.clamp(ageMillis / BURST_MS, 0.0F, 1.0F);
			float burstSize = arrowSize * (0.60F + 1.55F * burst);
			int burstAlpha = alphaOf((1.0F - burst) * (1.0F - burst) * 0.85F * alpha);
			blit(graphics, pose, BURST, x, y, radians, burstSize, burstAlpha);
		}

		// Exit echo: the ring keeps expanding after the arrow starts dissolving.
		if (ageMillis > ECHO_START_MS) {
			float echo = Mth.clamp((ageMillis - ECHO_START_MS) / (ThreatTracker.LIFETIME_MS - ECHO_START_MS), 0.0F, 1.0F);
			float echoSize = arrowSize * (1.0F + 1.30F * echo);
			int echoAlpha = alphaOf((1.0F - echo) * 0.45F);
			blit(graphics, pose, BURST, x, y, radians, echoSize, echoAlpha);
		}

		blit(graphics, pose, ARROW, x, y, radians, arrowSize, alphaOf(alpha));
	}

	private static void blit(GuiGraphicsExtractor graphics, Matrix3x2fStack pose, Identifier texture,
		float x, float y, float radians, float size, int alpha) {
		if (alpha <= 0) {
			return;
		}
		float unit = size / SPRITE;
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate(radians);
		pose.scale(unit, unit);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -SPRITE / 2, -SPRITE / 2, 0.0F, 0.0F,
			SPRITE, SPRITE, SPRITE, SPRITE, 0x00FFFFFF | alpha << 24);
		pose.popMatrix();
	}

	private static int alphaOf(float value) {
		return Mth.clamp(Math.round(value * 255.0F), 0, 255);
	}

	/** Overshoots slightly past the target so the arrow snaps into place instead of sliding. */
	private static float easeOutBack(float t) {
		float inverted = t - 1.0F;
		return 1.0F + 2.70158F * inverted * inverted * inverted + 1.70158F * inverted * inverted;
	}
}
