package dev.zymekoh.alerttweaks.client.alert;

import dev.zymekoh.alerttweaks.client.Mc;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.AlertPositionScreen;
import dev.zymekoh.alerttweaks.client.gui.Gfx;
import dev.zymekoh.alerttweaks.client.gui.Theme;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix3x2fStack;

/**
 * Draws the alerts: each at its own place (or with the unplaced ones at the default place), in its
 * own look (card, compact, icon, text or banner), stacked per place, animated in and out.
 */
public final class AlertCards {
	/** Height of the CARD look; other looks have their own ({@link AlertLook#height()}). */
	public static final int CARD_HEIGHT = 26;
	public static final int GAP = 3;
	/** The hotbar and offhand slot, half width, and the height kept clear above the screen bottom. */
	public static final float HOTBAR_HALF = 122.0F;
	public static final float RESERVE_BOTTOM = 58.0F;
	private static final float ENTER_MS = 300.0F;
	private static final float EXIT_MS = 360.0F;
	private static final float PUNCH_MS = 240.0F;
	private static final boolean[] DONE = new boolean[AlertFeed.MAX_CARDS];

	private AlertCards() {
	}

	public static int tone(Alert.Tone tone) {
		return switch (tone) {
			case DANGER -> Theme.CRIMSON;
			case WARN -> Theme.MAGENTA;
			case INFO -> Theme.PURPLE;
			case GOOD -> 0xFF5CD6E8;
		};
	}

	/** HUD element. */
	public static void renderHud(Gfx g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || Mc.hudHidden(mc) || Mc.screen(mc) instanceof AlertPositionScreen) {
			return;
		}
		AlertFeed feed = AlertFeed.HUD;
		drawFlash(g, feed, 0, 0, g.width(), g.height());
		draw(g, feed, 0, 0, g.width(), g.height(), AlertTweaksConfig.get().toastScale, 1.0F);
	}

	public static void drawFlash(Gfx g, AlertFeed feed, int x, int y, int w, int h) {
		float flash = feed.flash();
		if (flash <= 0.01F) {
			return;
		}
		int edge = Math.max(8, Math.min(w, h) / 6);
		int color = Theme.alpha(0xFF000000 | feed.flashColor(), 0.55F * flash);
		int clear = color & 0x00FFFFFF;
		g.gradient(x, y, x + w, y + edge, color, clear);
		g.gradient(x, y + h - edge, x + w, y + h, clear, color);
		g.hGradient(x, y, x + edge, y + h, color, clear);
		g.hGradient(x + w - edge, y, x + w, y + h, clear, color);
	}

	/** Stored place of an alert: its own, or the default one. */
	public static float placeX(Alert alert) {
		AlertTweaksConfig.AlertSetting s = AlertTweaksConfig.get().alert(alert);
		return s.placed() ? s.x : AlertTweaksConfig.get().defaultX;
	}

	public static float placeY(Alert alert) {
		AlertTweaksConfig.AlertSetting s = AlertTweaksConfig.get().alert(alert);
		return s.placed() ? s.y : AlertTweaksConfig.get().defaultY;
	}

	/** Centre of the next card in a stack, given the centre and height of the previous one. */
	public static float nextCentre(float previousCentre, int dir, float previousH, float h, float scale) {
		return previousCentre + dir * (previousH / 2.0F + GAP * scale + h / 2.0F);
	}

	/** Whether a card centred at {@code centre} still fits between the stack's limits. */
	public static boolean fits(float centre, float h, int dir, float ceiling, float floor) {
		return dir > 0 ? centre + h / 2.0F <= floor : centre - h / 2.0F >= ceiling;
	}

	/**
	 * Every live alert of {@code feed} inside the area. {@code unit} is area pixels per screen GUI
	 * pixel: 1 on the HUD, smaller in a miniature preview.
	 */
	public static void draw(Gfx g, AlertFeed feed, float areaX, float areaY, float areaW, float areaH, float scale, float unit) {
		List<AlertFeed.Card> cards = feed.cards();
		long nanos = System.nanoTime();
		float dt = feed.lastDrawNanos == 0L ? 16.0F : Math.min(100.0F, (nanos - feed.lastDrawNanos) / 1_000_000.0F);
		feed.lastDrawNanos = nanos;
		int n = cards.size();
		if (n == 0) {
			return;
		}
		AlertTweaksConfig config = AlertTweaksConfig.get();
		long now = Util.getMillis();
		long life = AlertFeed.lifeMs();
		Arrays.fill(DONE, false);
		for (int i = 0; i < n; i++) {
			if (DONE[i]) {
				continue;
			}
			float px = placeX(cards.get(i).alert);
			float py = placeY(cards.get(i).alert);
			float ax = areaX + px * areaW;
			float ay = areaY + py * areaH;
			int dir = AlertPlacement.direction(py);
			int align = AlertPlacement.align(px);
			boolean overHotbar = Math.abs(ax - (areaX + areaW / 2.0F)) < HOTBAR_HALF * unit + 60.0F * scale;
			float floor = areaY + areaH - (overHotbar ? RESERVE_BOTTOM * unit : 2.0F);
			float ceiling = areaY + 2.0F;
			float centre = ay;
			float previousH = 0.0F;
			int drawn = 0;
			// Danger first, then newest first; what does not fit waits for a free slot.
			for (int pass = 0; pass < 2; pass++) {
				for (int j = n - 1; j >= i; j--) {
					AlertFeed.Card card = cards.get(j);
					if (DONE[j] || placeX(card.alert) != px || placeY(card.alert) != py
						|| (card.alert.tone == Alert.Tone.DANGER) != (pass == 0)) {
						continue;
					}
					DONE[j] = true;
					AlertLook look = config.look(card.alert);
					float h = look.height() * scale;
					float c = drawn == 0 ? ay : nextCentre(centre, dir, previousH, h, scale);
					if (drawn > 0 && !fits(c, h, dir, ceiling, floor)) {
						continue;
					}
					centre = c;
					previousH = h;
					drawn++;
					float top = c - h / 2.0F;
					card.shownY = Float.isNaN(card.shownY) ? top : card.shownY + (top - card.shownY) * Theme.approach(dt, 70.0F);
					drawLive(g, card, look, ax, align, dir, card.shownY, scale, areaX, areaW, now, life);
				}
			}
		}
	}

	/** Unscaled width of a CARD showing these texts (kept for callers that only know cards). */
	public static int width(Component title, Component detail, String badge) {
		return AlertLook.CARD.width(title, detail, badge);
	}

	public static float left(float anchorX, int align, int w, float scale, float areaX, float areaW) {
		float left = AlertPlacement.cardLeft(anchorX, w * scale, align);
		return Math.max(areaX + 2.0F, Math.min(left, areaX + areaW - 2.0F - w * scale));
	}

	private static void drawLive(Gfx g, AlertFeed.Card card, AlertLook look, float anchorX, int align, int dir, float top, float scale,
		float areaX, float areaW, long now, long life) {
		float age = now - card.bornMs;
		float enter = Theme.clamp01(age / ENTER_MS);
		float exit = Theme.clamp01((age - (life - EXIT_MS)) / EXIT_MS);
		float alpha = Theme.clamp01(enter * 2.2F) * (1.0F - exit);
		if (alpha <= 0.01F) {
			return;
		}
		float punch = Theme.clamp01(1.0F - (now - card.refreshedMs) / PUNCH_MS);
		String badge = card.count > 1 ? "x" + card.count : null;
		int w = look.width(card.title, card.detail, badge);
		int h = look.height();
		float pop = Theme.easeOutBack(enter);
		float x = left(anchorX, align, w, scale, areaX, areaW);
		float y = top;
		float slide = (1.0F - pop) * 26.0F + exit * exit * 34.0F;
		switch (look) {
			case CARD, COMPACT -> {
				if (align < 0) {
					x -= slide * scale;
				} else if (align > 0) {
					x += slide * scale;
				} else {
					y += dir * slide * 0.6F * scale;
				}
			}
			case TEXT -> y -= exit * 8.0F * scale;
			default -> {
			}
		}
		float bump = 1.0F + 0.07F * punch * punch;
		g.nextStratum();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w * scale / 2.0F, y + h * scale / 2.0F);
		pose.scale(scale * bump, scale * bump);
		pose.translate(-w / 2.0F, -h / 2.0F);
		paint(g, look, w, tone(card.alert.tone), alpha, punch, pop, Theme.easeOutCubic(enter), card.icon, card.face, card.title, card.detail,
			badge, 1.0F - Theme.clamp01(age / life), age, card.alert.tone == Alert.Tone.DANGER);
		pose.popMatrix();
	}

	/** An alert at rest in its look (position editor). Returns its unscaled width. */
	public static int drawStatic(Gfx g, Alert alert, AlertSamples.Content content, float left, float top, float scale, float alpha,
		boolean highlight) {
		AlertLook look = AlertTweaksConfig.get().look(alert);
		int w = look.width(content.title(), content.detail(), null);
		g.nextStratum();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(left, top);
		pose.scale(scale, scale);
		paint(g, look, w, tone(alert.tone), alpha, highlight ? 0.6F : 0.0F, 1.0F, 1.0F, content.icon(), content.face(), content.title(),
			content.detail(), null, 1.0F, 9999.0F, alert.tone == Alert.Tone.DANGER);
		if (highlight) {
			g.outline(-1, -1, w + 2, look.height() + 2, Theme.alpha(Theme.MAGENTA, alpha));
		}
		pose.popMatrix();
		return w;
	}

	/** Width of an alert in its current look, for layout and hit tests. */
	public static int widthOf(Alert alert, AlertSamples.Content content) {
		return AlertTweaksConfig.get().look(alert).width(content.title(), content.detail(), null);
	}

	// ------------------------------------------------------------------ looks

	private static void paint(Gfx g, AlertLook look, int w, int accent, float alpha, float punch, float pop, float enterEase, Identifier icon,
		Supplier<PlayerSkin> face, Component title, Component detail, String badge, float lifeLeft, float age, boolean danger) {
		switch (look) {
			case CARD -> card(g, w, accent, alpha, punch, pop, enterEase, icon, face, title, detail, badge, lifeLeft, age);
			case COMPACT -> compact(g, w, accent, alpha, punch, pop, enterEase, icon, face, title, badge, lifeLeft, age);
			case ICON -> icon(g, accent, alpha, punch, pop, enterEase, icon, face, badge, lifeLeft, age, danger);
			case TEXT -> text(g, w, accent, alpha, icon, face, title, detail, badge, age);
			case BANNER -> banner(g, w, accent, alpha, punch, pop, icon, face, title, detail, badge, lifeLeft, age);
		}
	}

	private static void iconOrFace(Gfx g, Identifier icon, Supplier<PlayerSkin> face, float cx, float cy, float size, float spin, float alpha) {
		if (face != null) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(cx, cy);
			pose.scale(size / 16.0F, size / 16.0F);
			g.face(face.get(), -8, -8, 16, alpha);
			pose.popMatrix();
		} else {
			g.icon(icon, cx, cy, size, spin, Theme.alpha(0xFFFFFFFF, alpha));
		}
	}

	/** Rectangle with float precision in the current pose. */
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

	private static void diamond(Gfx g, float cx, float cy, float size, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(Mth.PI / 4.0F);
		rect(g, -size / 2.0F, -size / 2.0F, size, size, color);
		pose.popMatrix();
	}

	private static void diamondOutline(Gfx g, float cx, float cy, float size, float thick, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.rotate(Mth.PI / 4.0F);
		float s = size / 2.0F;
		rect(g, -s, -s, size, thick, color);
		rect(g, -s, s - thick, size, thick, color);
		rect(g, -s, -s + thick, thick, size - 2.0F * thick, color);
		rect(g, s - thick, -s + thick, thick, size - 2.0F * thick, color);
		pose.popMatrix();
	}

	/** Black glass, metal rims, the tone's energy from the left, icon socket, two lines and a life line. */
	private static void card(Gfx g, int w, int accent, float alpha, float punch, float pop, float enterEase, Identifier icon,
		Supplier<PlayerSkin> face, Component title, Component detail, String badge, float lifeLeft, float age) {
		int h = CARD_HEIGHT;
		g.fill(0, 0, w, h, Theme.alpha(Theme.PANEL, alpha));
		g.hGradient(0, 0, Math.min(w, 46), h, Theme.alpha(accent, 0.30F * alpha + 0.25F * punch), Theme.alpha(accent, 0.0F));
		g.fill(0, 0, w, 1, Theme.alpha(Theme.METAL_LIGHT, 0.55F * alpha));
		g.fill(0, h - 1, w, h, Theme.alpha(Theme.METAL_DARK, alpha));
		g.fill(w - 1, 0, w, h, Theme.alpha(Theme.METAL, alpha));
		g.fill(0, 0, 2, h, Theme.alpha(accent, alpha));
		g.fill(5, 3, 25, 23, Theme.alpha(0xFF050208, 0.9F * alpha));
		g.outline(5, 3, 20, 20, Theme.alpha(Theme.mix(Theme.METAL, accent, 0.5F + 0.5F * punch), alpha));
		float size = 16.0F * (1.0F + 0.6F * (1.0F - pop) + 0.18F * punch);
		iconOrFace(g, icon, face, 15.0F, 13.0F, size, (1.0F - enterEase) * -1.2F, alpha);
		if (age < 420.0F) {
			float ring = age / 420.0F;
			int r = Math.round(10 + 22 * Theme.easeOutCubic(ring));
			g.outline(15 - r, 13 - r, r * 2, r * 2, Theme.alpha(accent, (1.0F - ring) * 0.8F * alpha));
		}
		g.text(title, 30, 4, Theme.alpha(Theme.TEXT, alpha), true);
		g.text(detail, 30, 14, Theme.alpha(Theme.MUTED, alpha), false);
		if (badge != null) {
			g.text(badge, w - 6 - Gfx.font().width(badge), 4, Theme.alpha(accent, alpha), true);
		}
		g.fill(2, h - 2, 2 + Math.round((w - 3) * lifeLeft), h - 1, Theme.alpha(accent, 0.75F * alpha));
	}

	/** A one-line pill that unrolls from its icon. */
	private static void compact(Gfx g, int w, int accent, float alpha, float punch, float pop, float enterEase, Identifier icon,
		Supplier<PlayerSkin> face, Component title, String badge, float lifeLeft, float age) {
		int h = 16;
		float reveal = Theme.easeOutCubic(Theme.clamp01(age / 220.0F));
		int shown = 16 + Math.round((w - 16) * reveal);
		g.fill(0, 0, shown, h, Theme.alpha(Theme.PANEL, alpha));
		g.hGradient(0, 0, Math.min(shown, 34), h, Theme.alpha(accent, 0.35F * alpha + 0.25F * punch), Theme.alpha(accent, 0.0F));
		g.fill(0, 0, shown, 1, Theme.alpha(Theme.METAL_LIGHT, 0.5F * alpha));
		g.fill(0, h - 1, shown, h, Theme.alpha(Theme.METAL_DARK, alpha));
		g.fill(0, 0, 2, h, Theme.alpha(accent, alpha));
		g.fill(shown - 1, 0, shown, h, Theme.alpha(Theme.mix(Theme.METAL, accent, reveal < 1.0F ? 1.0F : 0.3F), alpha));
		iconOrFace(g, icon, face, 10.0F, 8.0F, 12.0F * (1.0F + 0.5F * (1.0F - pop) + 0.15F * punch), (1.0F - enterEase) * -1.4F, alpha);
		g.scissor(0, 0, shown, h);
		g.text(title, 19, 4, Theme.alpha(Theme.TEXT, alpha), true);
		if (badge != null) {
			g.text(badge, w - 5 - Gfx.font().width(badge), 4, Theme.alpha(accent, alpha), true);
		}
		g.endScissor();
		g.fill(2, h - 2, 2 + Math.round((shown - 3) * lifeLeft), h - 1, Theme.alpha(accent, 0.7F * alpha));
	}

	/** Just the icon in a diamond: it spins in, breathes (faster for danger) and fades as its time runs out. */
	private static void icon(Gfx g, int accent, float alpha, float punch, float pop, float enterEase, Identifier icon,
		Supplier<PlayerSkin> face, String badge, float lifeLeft, float age, boolean danger) {
		float c = 13.0F;
		float breath = 0.5F + 0.5F * Mth.sin(age / (danger ? 110.0F : 230.0F));
		for (int k = 0; k < 3; k++) {
			diamond(g, c, c, 22.0F + 5.0F * k + 3.0F * breath, Theme.alpha(accent, (0.07F + 0.04F * breath) * alpha));
		}
		diamond(g, c, c, 22.0F, Theme.alpha(Theme.mix(Theme.METAL, accent, 0.4F + 0.6F * punch), alpha));
		diamond(g, c, c, 19.0F, Theme.alpha(0xFF050208, 0.95F * alpha));
		if (age < 480.0F) {
			float wave = Theme.easeOutCubic(age / 480.0F);
			diamondOutline(g, c, c, 22.0F + 24.0F * wave, 1.2F, Theme.alpha(accent, (1.0F - wave) * 0.9F * alpha));
		}
		float size = 15.0F * (1.0F + 0.7F * (1.0F - pop) + 0.2F * punch);
		iconOrFace(g, icon, face, c, c, size, (1.0F - enterEase) * -2.6F, alpha);
		// Time left: a bright tip running round the rim.
		float t = 1.0F - lifeLeft;
		float edge = 15.5F;
		float px = c + (t < 0.25F ? t * 4.0F : t < 0.5F ? 1.0F - (t - 0.25F) * 4.0F : t < 0.75F ? -(t - 0.5F) * 4.0F : -1.0F + (t - 0.75F) * 4.0F) * edge;
		float py = c + (t < 0.25F ? -1.0F + t * 4.0F : t < 0.5F ? (t - 0.25F) * 4.0F : t < 0.75F ? 1.0F - (t - 0.5F) * 4.0F : -(t - 0.75F) * 4.0F) * edge;
		if (lifeLeft < 1.0F) {
			diamond(g, px, py, 3.0F, Theme.alpha(Theme.TEXT, alpha));
		}
		if (badge != null) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(c + 6.0F, c + 6.0F);
			pose.scale(0.75F, 0.75F);
			g.text(badge, 0, 0, Theme.alpha(accent, alpha), true);
			pose.popMatrix();
		}
	}

	/** No panel: a small icon and the words typing themselves in, tinted by the alert's tone. */
	private static void text(Gfx g, int w, int accent, float alpha, Identifier icon, Supplier<PlayerSkin> face, Component title,
		Component detail, String badge, float age) {
		float typed = Theme.clamp01(age / 260.0F);
		iconOrFace(g, icon, face, 6.0F, 6.0F, 11.0F, 0.0F, alpha);
		int color = Theme.alpha(Theme.mix(accent, Theme.TEXT, 0.35F), alpha);
		if (typed < 1.0F) {
			String full = title.getString();
			g.text(full.substring(0, Math.min(full.length(), (int)Math.ceil(full.length() * typed))), 14, 1, color, true);
		} else {
			g.text(title, 14, 1, color, true);
		}
		if (age > 120.0F) {
			g.text(detail, 14, 11, Theme.alpha(Theme.MUTED, alpha * Theme.clamp01((age - 120.0F) / 200.0F)), true);
		}
		if (badge != null) {
			g.text(badge, w - Gfx.font().width(badge), 1, Theme.alpha(accent, alpha), true);
		}
		if (age < 520.0F) {
			float e = Theme.easeOutCubic(age / 520.0F);
			rect(g, 14.0F, 10.0F, (w - 14) * e, 1.0F, Theme.alpha(accent, (1.0F - e) * alpha));
		}
	}

	/** A wide strip that opens from its centre with a flash: for what must not be missed. */
	private static void banner(Gfx g, int w, int accent, float alpha, float punch, float pop, Identifier icon, Supplier<PlayerSkin> face,
		Component title, Component detail, String badge, float lifeLeft, float age) {
		int h = 24;
		float open = Theme.easeOutCubic(Theme.clamp01(age / 240.0F));
		float half = w / 2.0F;
		float reach = half * open;
		int panel = Theme.alpha(Theme.PANEL, 0.92F * alpha);
		int line = Theme.alpha(accent, alpha);
		int l = Math.round(half - reach);
		int m = Math.round(half);
		int r = Math.round(half + reach);
		g.hGradient(l, 0, m, h, panel & 0x00FFFFFF, panel);
		g.hGradient(m, 0, r, h, panel, panel & 0x00FFFFFF);
		g.hGradient(l, 0, m, 1, line & 0x00FFFFFF, line);
		g.hGradient(m, 0, r, 1, line, line & 0x00FFFFFF);
		g.hGradient(l, h - 1, m, h, line & 0x00FFFFFF, line);
		g.hGradient(m, h - 1, r, h, line, line & 0x00FFFFFF);
		if (age < 280.0F) {
			float flash = 1.0F - age / 280.0F;
			g.fill(l, 0, l + 2, h, Theme.alpha(0xFFFFFFFF, flash * alpha));
			g.fill(r - 2, 0, r, h, Theme.alpha(0xFFFFFFFF, flash * alpha));
		}
		float textAlpha = alpha * Theme.clamp01((open - 0.5F) * 2.0F);
		if (textAlpha <= 0.01F) {
			return;
		}
		iconOrFace(g, icon, face, 16.0F, 12.0F, 16.0F * (1.0F + 0.5F * (1.0F - pop) + 0.2F * punch), 0.0F, textAlpha);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(30.0F, 4.0F);
		pose.scale(AlertLook.BANNER_TITLE, AlertLook.BANNER_TITLE);
		g.text(title, 0, 0, Theme.alpha(Theme.mix(Theme.TEXT, accent, 0.25F), textAlpha), true);
		pose.popMatrix();
		int dx = 30 + Math.round(Gfx.font().width(title) * AlertLook.BANNER_TITLE) + 10;
		g.text(detail, dx, 9, Theme.alpha(Theme.MUTED, textAlpha), true);
		if (badge != null) {
			g.text(badge, w - 10 - Gfx.font().width(badge), 9, Theme.alpha(accent, textAlpha), true);
		}
		float lifeHalf = (half - 8.0F) * lifeLeft;
		rect(g, half - lifeHalf, h - 3.0F, lifeHalf * 2.0F, 1.0F, Theme.alpha(accent, 0.8F * textAlpha));
	}
}
