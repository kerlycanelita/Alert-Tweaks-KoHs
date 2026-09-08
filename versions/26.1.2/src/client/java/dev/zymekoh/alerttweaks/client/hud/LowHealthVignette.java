package dev.zymekoh.alerttweaks.client.hud;

import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class LowHealthVignette {
	private static final float SMOOTHING_FACTOR = 0.065F;
	private static final float FIXED_HEARTBEAT_PRESSURE = 0.45F;
	private static final int SIDE_FADE_STEPS = 32;

	private static float displayedIntensity;

	private LowHealthVignette() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		AlertTweaksConfig config = AlertTweaksConfig.get();
		if (!canRender(minecraft, config)) {
			displayedIntensity = 0.0F;
			return;
		}

		float targetIntensity = getTargetIntensity(minecraft, config);
		if (config.smoothVignetteEnabled) {
			displayedIntensity = Mth.lerp(SMOOTHING_FACTOR, displayedIntensity, targetIntensity);
			if (targetIntensity <= 0.0F && displayedIntensity < 0.004F) {
				displayedIntensity = 0.0F;
			}
		} else {
			displayedIntensity = targetIntensity;
		}
		if (displayedIntensity < 0.01F) {
			return;
		}

		float renderIntensity = displayedIntensity;
		float sizeMultiplier = 1.0F;
		if (config.heartbeatEnabled) {
			float lowHealthFactor = getLowHealthFactor(minecraft, config);
			float pulse = heartbeatPulse(config, lowHealthFactor);
			float amount = heartbeatAmount(config, lowHealthFactor);
			renderIntensity = Mth.clamp(renderIntensity * (1.0F + pulse * amount), 0.0F, 1.0F);
			sizeMultiplier = 1.0F + pulse * amount * 0.26F;
		}

		int alpha = Mth.clamp(Math.round(config.vignetteAlpha * renderIntensity), 0, 255);
		if (alpha > 0) {
			draw(graphics, config, alpha, sizeMultiplier);
		}
	}

	private static boolean canRender(Minecraft minecraft, AlertTweaksConfig config) {
		LocalPlayer player = minecraft.player;
		if (!config.vignetteEnabled || player == null || minecraft.options.hideGui) {
			return false;
		}
		return !player.isCreative() && !player.isSpectator() && player.isAlive();
	}

	/** 0 above the threshold, ramping to 1 at zero health. */
	private static float getTargetIntensity(Minecraft minecraft, AlertTweaksConfig config) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return 0.0F;
		}
		float maxHealth = Math.max(1.0F, player.getMaxHealth());
		float threshold = Math.min(config.thresholdHealthPoints, maxHealth);
		float health = Mth.clamp(player.getHealth(), 0.0F, maxHealth);
		if (health > threshold) {
			return 0.0F;
		}
		if (config.staticColorEnabled) {
			return Mth.clamp(config.staticColorIntensity, 0.05F, 1.0F);
		}
		float progress = (threshold - health) / threshold;
		return Mth.clamp(0.12F + progress * 0.88F, 0.0F, 1.0F);
	}

	private static float getLowHealthFactor(Minecraft minecraft, AlertTweaksConfig config) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return 0.0F;
		}
		float maxHealth = Math.max(1.0F, player.getMaxHealth());
		float threshold = Math.min(config.thresholdHealthPoints, maxHealth);
		float health = Mth.clamp(player.getHealth(), 0.0F, maxHealth);
		return health > threshold ? 0.0F : Mth.clamp((threshold - health) / threshold, 0.0F, 1.0F);
	}

	public static float heartbeatPulse(AlertTweaksConfig config, float lowHealthFactor) {
		float pressure = config.heartbeatScaleWithLowHealth ? lowHealthFactor : FIXED_HEARTBEAT_PRESSURE;
		float speed = config.heartbeatScaleWithLowHealth ? Mth.lerp(pressure, 1.15F, 3.2F) : 1.45F;
		double phase = System.currentTimeMillis() / 1000.0 * Math.PI * 2.0 * speed;
		float wave = Math.max(0.0F, (float)Math.sin(phase));
		return wave * wave * wave * wave;
	}

	public static float heartbeatAmount(AlertTweaksConfig config, float lowHealthFactor) {
		return config.heartbeatScaleWithLowHealth ? Mth.lerp(lowHealthFactor, 0.08F, 0.3F) : 0.16F;
	}

	private static void draw(GuiGraphicsExtractor graphics, AlertTweaksConfig config, int alpha, float sizeMultiplier) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int baseEdgeSize = Mth.clamp(Math.min(width, height) / 4, 36, 118);
		int maxEdgeSize = Math.max(18, Math.min(width, height) / 2);
		int edgeSize = Mth.clamp(Math.round(baseEdgeSize * config.vignetteSize * sizeMultiplier), 18, maxEdgeSize);
		int color = config.getVignetteColor(alpha);
		int clear = config.getVignetteColor(0);
		graphics.fillGradient(0, 0, width, edgeSize, color, clear);
		graphics.fillGradient(0, height - edgeSize, width, height, clear, color);
		drawSideFade(graphics, config, alpha, width, height, edgeSize);
	}

	/** fillGradient only goes top to bottom, so the sides are painted as vertical strips. */
	private static void drawSideFade(GuiGraphicsExtractor graphics, AlertTweaksConfig config, int alpha, int width, int height, int edgeSize) {
		for (int i = 0; i < SIDE_FADE_STEPS; i++) {
			float progress = (float)i / SIDE_FADE_STEPS;
			int stripAlpha = Math.round(alpha * (float)Math.pow(1.0F - progress, 1.65F));
			int stripColor = config.getVignetteColor(stripAlpha);
			int x1 = Math.round(edgeSize * progress);
			int x2 = Math.round(edgeSize * ((float)(i + 1) / SIDE_FADE_STEPS));
			graphics.fill(x1, 0, x2, height, stripColor);
			graphics.fill(width - x2, 0, width - x1, height, stripColor);
		}
	}
}
