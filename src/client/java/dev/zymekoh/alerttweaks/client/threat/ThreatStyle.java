package dev.zymekoh.alerttweaks.client.threat;

import dev.zymekoh.alerttweaks.client.gui.Gfx;
import dev.zymekoh.alerttweaks.client.gui.Icons;
import dev.zymekoh.alerttweaks.client.gui.Theme;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * The looks of the threat indicator. All of them show the same thing (the direction of whoever hit
 * you, for five seconds); they only differ in how it is drawn. Crimson because it is an enemy.
 */
public enum ThreatStyle {
	CLASSIC, ARC, CHEVRONS, SLASH, RADAR, WEAPON, EDGE, SHARD;

	private static final int CORE = 0xFFFF5470;
	private static final int GLOW = Theme.CRIMSON;
	private static final int HOT = 0xFFFFE6EC;
	private static final float LIFE = ThreatTracker.LIFETIME_MS;

	public Component title() {
		return Component.translatable("alert_tweaks.threat_style." + this.name().toLowerCase(Locale.ROOT));
	}

	public static ThreatStyle parse(String name) {
		for (ThreatStyle style : values()) {
			if (style.name().equalsIgnoreCase(name)) {
				return style;
			}
		}
		return CLASSIC;
	}

	/**
	 * One indicator. {@code relativeDegrees}: direction of the attacker, clockwise from straight
	 * ahead; {@code area}: the screen (or a preview tile) for the edge style; {@code weapon}: what hit.
	 */
	public void draw(Gfx g, float areaX, float areaY, float areaW, float areaH, float cx, float cy, float relativeDegrees, float scale,
		float radius, long age, Identifier weapon) {
		if (age < 0L || age > LIFE) {
			return;
		}
		float spawn = Theme.clamp01(age / 190.0F);
		float ease = Theme.easeOutBack(spawn);
		float fade = age <= 3800L ? 1.0F : 1.0F - Theme.clamp01((age - 3800.0F) / (LIFE - 3800.0F));
		float exit = 1.0F - fade;
		float alpha = fade * fade * Theme.clamp01(spawn * 1.8F);
		if (alpha <= 0.004F) {
			return;
		}
		float theta = relativeDegrees * Mth.DEG_TO_RAD;
		g.nextStratum();
		switch (this) {
			case CLASSIC -> ThreatIndicatorRenderer.draw(g, cx, cy, relativeDegrees, scale, age, radius);
			case ARC -> arc(g, cx, cy, theta, scale, radius, age, ease, exit, alpha);
			case CHEVRONS -> chevrons(g, cx, cy, theta, scale, radius, age, ease, exit, alpha);
			case SLASH -> slash(g, cx, cy, theta, scale, radius, age, exit, alpha);
			case RADAR -> radar(g, cx, cy, theta, scale, radius, age, ease, exit, alpha);
			case WEAPON -> weapon(g, cx, cy, theta, scale, radius, age, ease, exit, alpha, weapon);
			case EDGE -> edge(g, areaX, areaY, areaW, areaH, cx, cy, theta, scale, age, ease, alpha);
			case SHARD -> shard(g, cx, cy, theta, scale, radius, age, ease, exit, alpha);
		}
	}

	// ------------------------------------------------------------------ primitives

	/** Axis-aligned rectangle with float precision in the current pose. */
	private static void rect(Gfx g, float x, float y, float w, float h, int color) {
		if ((color >>> 24) == 0 || w <= 0.0F || h <= 0.0F) {
			return;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w, h);
		g.fill(0, 0, 1, 1, color);
		pose.popMatrix();
	}

	/** Vertical gradient rectangle with float precision. */
	private static void rectGradient(Gfx g, float x, float y, float w, float h, int top, int bottom) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(w, h);
		g.gradient(0, 0, 1, 1, top, bottom);
		pose.popMatrix();
	}

	/** Enters the frame of the indicator: origin on the crosshair, local up pointing at the attacker. */
	private static void polar(Gfx g, float cx, float cy, float theta) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(theta);
	}

	private static void leave(Gfx g) {
		g.pose().popMatrix();
	}

	/** A ring of tangent segments around the origin of the current frame (a full circle or an arc). */
	private static void ring(Gfx g, float radius, float span, int segments, float thickness, int color, boolean taper) {
		Matrix3x2fStack pose = g.pose();
		float segLength = radius * span / segments * 1.18F;
		for (int i = 0; i < segments; i++) {
			float t = (i + 0.5F) / segments;
			float a = -span / 2.0F + span * t;
			float k = taper ? 1.0F - (float)Math.pow(Math.abs(2.0F * t - 1.0F), 2.2F) : 1.0F;
			pose.pushMatrix();
			pose.rotate(a);
			rect(g, -segLength / 2.0F, -radius - thickness / 2.0F, segLength, thickness, Theme.alpha(color, k));
			pose.popMatrix();
		}
	}

	private static void diamond(Gfx g, float x, float y, float size, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.rotate(Mth.PI / 4.0F);
		rect(g, -size / 2.0F, -size / 2.0F, size, size, color);
		pose.popMatrix();
	}

	// ------------------------------------------------------------------ styles

	/** A curved blade of light on a ring around the crosshair, like a shooter's damage arc. */
	private static void arc(Gfx g, float cx, float cy, float theta, float s, float radius, long age, float ease, float exit, float alpha) {
		float r = radius * s * (0.85F + 0.15F * ease + 0.25F * exit);
		float span = 0.95F * (0.35F + 0.65F * Theme.clamp01(ease));
		float breath = 1.0F + 0.25F * Mth.sin(age / 140.0F);
		float flash = Theme.clamp01(1.0F - age / 160.0F);
		polar(g, cx, cy, theta);
		if (age < 420L) {
			float wave = age / 420.0F;
			ring(g, r * (0.6F + 0.7F * wave), Mth.TWO_PI, 40, 1.2F * s, Theme.alpha(GLOW, (1.0F - wave) * 0.45F * alpha), false);
		}
		ring(g, r, span * 1.15F, 22, 9.0F * s * breath * (1.0F - 0.5F * exit), Theme.alpha(GLOW, 0.22F * alpha), true);
		ring(g, r, span, 22, 4.0F * s * (1.0F - 0.6F * exit), Theme.alpha(Theme.mix(CORE, HOT, flash), alpha), true);
		ring(g, r, span * 0.55F, 14, 1.4F * s, Theme.alpha(HOT, 0.8F * alpha), true);
		leave(g);
	}

	/** Three chevrons pulsing outwards, towards the attacker. */
	private static void chevrons(Gfx g, float cx, float cy, float theta, float s, float radius, long age, float ease, float exit,
		float alpha) {
		float base = radius * s * (0.5F + 0.5F * Theme.clamp01(ease)) + exit * 18.0F * s;
		polar(g, cx, cy, theta);
		for (int i = 0; i < 3; i++) {
			float r = base + i * 7.5F * s;
			float wave = 0.5F + 0.5F * Mth.sin(age / 95.0F - i * 1.1F);
			float a = alpha * (0.35F + 0.65F * wave) * (1.0F - Theme.clamp01(exit * 1.6F - i * 0.25F));
			float len = 7.0F * s;
			for (int side = -1; side <= 1; side += 2) {
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(0.0F, -r);
				pose.rotate(side * 0.75F);
				rect(g, -2.2F * s, 0.0F, 4.4F * s, len, Theme.alpha(GLOW, 0.3F * a));
				rect(g, -0.9F * s, 0.0F, 1.8F * s, len, Theme.alpha(Theme.mix(CORE, HOT, wave * 0.5F), a));
				pose.popMatrix();
			}
		}
		leave(g);
	}

	/** A blade cut across the direction: it slices in, splits into two edges and bleeds sparks. */
	private static void slash(Gfx g, float cx, float cy, float theta, float s, float radius, long age, float exit, float alpha) {
		float r = radius * s;
		float grow = Theme.easeOutCubic(Theme.clamp01(age / 130.0F));
		float split = Theme.clamp01((age - 160.0F) / 900.0F);
		float len = 30.0F * s * (1.0F + 0.2F * exit);
		polar(g, cx, cy, theta);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(0.0F, -r);
		pose.rotate(-0.55F);
		float start = -len / 2.0F;
		rect(g, start, -3.5F * s, len * grow, 7.0F * s, Theme.alpha(GLOW, 0.22F * alpha));
		float gap = 1.2F * s + split * 2.4F * s;
		rect(g, start, -gap - 0.8F * s, len * grow, 1.6F * s, Theme.alpha(CORE, alpha));
		rect(g, start, gap - 0.8F * s, len * grow, 1.6F * s, Theme.alpha(CORE, alpha * (0.6F + 0.4F * (1.0F - split))));
		if (age < 220L) {
			float hot = 1.0F - age / 220.0F;
			rect(g, start, -0.6F * s, len * grow, 1.2F * s, Theme.alpha(HOT, hot * alpha));
		}
		pose.popMatrix();
		for (int i = 0; i < 4; i++) {
			float t = Theme.clamp01((age - 60.0F - i * 25.0F) / 520.0F);
			if (t <= 0.0F || t >= 1.0F) {
				continue;
			}
			float side = (i % 2 == 0 ? 1.0F : -1.0F) * (3.0F + i * 2.5F) * s;
			diamond(g, side * (0.4F + t), -r - (6.0F + 16.0F * t) * s, 2.2F * s * (1.0F - t), Theme.alpha(i == 0 ? HOT : CORE, (1.0F - t) * alpha));
		}
		leave(g);
	}

	/** Sonar: a sweep finds the attacker, rings pulse out from the crosshair and the blip keeps pinging. */
	private static void radar(Gfx g, float cx, float cy, float theta, float s, float radius, long age, float ease, float exit, float alpha) {
		float r = radius * s;
		polar(g, cx, cy, theta);
		float sweep = Theme.clamp01(age / 360.0F);
		if (sweep < 1.0F) {
			Matrix3x2fStack pose = g.pose();
			for (int k = 0; k < 6; k++) {
				float back = (1.0F - Theme.easeOutCubic(sweep)) * Mth.PI + k * 0.07F;
				pose.pushMatrix();
				pose.rotate(-back);
				rectGradient(g, -0.6F * s, -r * 1.25F, 1.2F * s, r * 1.25F, Theme.alpha(CORE, (1.0F - k / 6.0F) * alpha), 0);
				pose.popMatrix();
			}
		}
		for (int k = 0; k < 2; k++) {
			float wave = ((age + k * 450L) % 900L) / 900.0F;
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.rotate(-theta);
			ring(g, r * 1.3F * wave, Mth.TWO_PI, 36, 1.0F * s, Theme.alpha(GLOW, (1.0F - wave) * 0.5F * alpha * fadeOut(exit)), false);
			pose.popMatrix();
		}
		float ping = (age % 600L) / 600.0F;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(0.0F, -r);
		ring(g, 3.0F * s + 9.0F * s * ping, Mth.TWO_PI, 16, 1.0F * s, Theme.alpha(CORE, (1.0F - ping) * alpha), false);
		pose.popMatrix();
		float size = 6.0F * s * (0.6F + 0.4F * ease) * (1.0F + 0.2F * Mth.sin(age / 120.0F));
		diamond(g, 0.0F, -r, size + 4.0F * s, Theme.alpha(GLOW, 0.3F * alpha));
		diamond(g, 0.0F, -r, size, Theme.alpha(CORE, alpha));
		diamond(g, 0.0F, -r, size * 0.4F, Theme.alpha(HOT, alpha));
		leave(g);
	}

	/** A soft halo: stacked diamonds, faint at the rim and stronger towards the middle. */
	private static void glow(Gfx g, float x, float y, float size, int color, float alpha) {
		for (int k = 0; k < 4; k++) {
			diamond(g, x, y, size * (1.15F - k * 0.22F), Theme.alpha(color, 0.07F * alpha));
		}
	}

	private static float fadeOut(float exit) {
		return 1.0F - exit;
	}

	/** What hit you, flung into place along the ring and pointing at its owner. */
	private static void weapon(Gfx g, float cx, float cy, float theta, float s, float radius, long age, float ease, float exit, float alpha,
		Identifier weapon) {
		Identifier icon = weapon == null ? Icons.SWORD : weapon;
		float r = radius * s * (1.0F + 0.3F * exit);
		float size = 18.0F * s * (0.5F + 0.5F * ease) * (1.0F + 0.06F * Mth.sin(age / 180.0F));
		float spin = (1.0F - Theme.easeOutCubic(Theme.clamp01(age / 320.0F))) * Mth.TWO_PI * 1.5F;
		polar(g, cx, cy, theta);
		if (age < 360L) {
			Matrix3x2fStack pose = g.pose();
			for (int k = 4; k >= 1; k--) {
				float lag = k * 0.16F * (1.0F - age / 360.0F);
				pose.pushMatrix();
				pose.rotate(-lag);
				g.icon(icon, 0.0F, -r, size, -Mth.PI / 4.0F + spin + lag * 3.0F, Theme.alpha(0xFFFF8098, (0.4F - k * 0.08F) * alpha));
				pose.popMatrix();
			}
		}
		glow(g, 0.0F, -r, size, GLOW, alpha);
		g.icon(icon, 0.0F, -r, size, -Mth.PI / 4.0F + spin, Theme.alpha(0xFFFFFFFF, alpha));
		leave(g);
	}

	/** A glow on the screen border where the hit came from: readable from the corner of your eye. */
	private static void edge(Gfx g, float ax, float ay, float aw, float ah, float cx, float cy, float theta, float s, long age, float ease,
		float alpha) {
		float dx = Mth.sin(theta);
		float dy = -Mth.cos(theta);
		float tx = dx > 1.0E-4F ? (ax + aw - cx) / dx : dx < -1.0E-4F ? (ax - cx) / dx : Float.MAX_VALUE;
		float ty = dy > 1.0E-4F ? (ay + ah - cy) / dy : dy < -1.0E-4F ? (ay - cy) / dy : Float.MAX_VALUE;
		boolean vertical = tx < ty;
		float t = Math.min(tx, ty);
		float hx = cx + dx * t;
		float hy = cy + dy * t;
		float flicker = 0.85F + 0.15F * Mth.sin(age / 45.0F);
		float thick = 14.0F * s * (1.0F + 1.4F * (1.0F - Theme.clamp01(ease)));
		float length = (vertical ? ah : aw) * 0.34F;
		int segments = 14;
		for (int i = 0; i < segments; i++) {
			float u = (i + 0.5F) / segments;
			float k = 1.0F - (float)Math.pow(Math.abs(2.0F * u - 1.0F), 2.0F);
			int core = Theme.alpha(GLOW, 0.75F * k * alpha * flicker);
			int clear = core & 0x00FFFFFF;
			float seg = length / segments;
			if (vertical) {
				float y0 = Mth.clamp(hy - length / 2.0F + i * seg, ay, ay + ah - seg);
				boolean right = dx > 0.0F;
				int x0 = Math.round(right ? ax + aw - thick : ax);
				int x1 = Math.round(right ? ax + aw : ax + thick);
				g.hGradient(x0, Math.round(y0), x1, Math.round(y0 + seg), right ? clear : core, right ? core : clear);
			} else {
				float x0 = Mth.clamp(hx - length / 2.0F + i * seg, ax, ax + aw - seg);
				boolean bottom = dy > 0.0F;
				int y0 = Math.round(bottom ? ay + ah - thick : ay);
				int y1 = Math.round(bottom ? ay + ah : ay + thick);
				g.gradient(Math.round(x0), y0, Math.round(x0 + seg), y1, bottom ? clear : core, bottom ? core : clear);
			}
		}
		// A small chevron on the border pointing out at the attacker.
		polar(g, Mth.clamp(hx, ax + 8.0F * s, ax + aw - 8.0F * s) - dx * 8.0F * s, Mth.clamp(hy, ay + 8.0F * s, ay + ah - 8.0F * s) - dy * 8.0F * s,
			theta);
		for (int side = -1; side <= 1; side += 2) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.rotate(side * 0.75F);
			rect(g, -0.9F * s, 0.0F, 1.8F * s, 6.0F * s, Theme.alpha(HOT, alpha * flicker));
			pose.popMatrix();
		}
		leave(g);
	}

	/** An amethyst shard spins into place, glints, and shatters into four pieces when it goes. */
	private static void shard(Gfx g, float cx, float cy, float theta, float s, float radius, long age, float ease, float exit, float alpha) {
		float r = radius * s;
		float size = 18.0F * s * (0.5F + 0.5F * ease);
		float spin = (1.0F - Theme.easeOutCubic(Theme.clamp01(age / 300.0F))) * Mth.TWO_PI;
		polar(g, cx, cy, theta);
		glow(g, 0.0F, -r, size, Theme.PURPLE, alpha);
		if (exit <= 0.0F) {
			g.icon(Icons.AMETHYST, 0.0F, -r, size, -Mth.PI / 4.0F + spin, Theme.alpha(0xFFFFFFFF, alpha));
		} else {
			float burst = Theme.easeOutCubic(exit);
			Matrix3x2fStack pose = g.pose();
			for (int q = 0; q < 4; q++) {
				float qx = (q % 2 == 0 ? -1.0F : 1.0F);
				float qy = (q < 2 ? -1.0F : 1.0F);
				pose.pushMatrix();
				pose.translate(qx * burst * 12.0F * s, -r + qy * burst * 12.0F * s);
				pose.rotate(-Mth.PI / 4.0F + qx * qy * burst * 2.0F);
				pose.scale(size / 16.0F, size / 16.0F);
				g.blitRegion(Icons.AMETHYST, q % 2 == 0 ? -8 : 0, q < 2 ? -8 : 0, 8, 8, q % 2 == 0 ? 0.0F : 8.0F, q < 2 ? 0.0F : 8.0F, 8, 8,
					16, 16, Theme.alpha(0xFFFFFFFF, alpha));
				pose.popMatrix();
			}
		}
		for (int i = 0; i < 3; i++) {
			float orbit = age / 380.0F + i * Mth.TWO_PI / 3.0F;
			float glint = 0.5F + 0.5F * Mth.sin(age / 90.0F + i * 2.0F);
			diamond(g, Mth.cos(orbit) * size * 0.75F, -r + Mth.sin(orbit) * size * 0.75F, 2.0F * s * glint,
				Theme.alpha(HOT, glint * alpha * (1.0F - exit)));
		}
		leave(g);
	}
}
