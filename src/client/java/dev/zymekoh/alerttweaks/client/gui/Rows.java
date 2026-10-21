package dev.zymekoh.alerttweaks.client.gui;

import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** The rows of the option tree. Each one draws itself in KoHs metal and answers its own clicks. */
public final class Rows {
	/** Milliseconds since the last frame, set by the screen before drawing rows. */
	public static float frameDt = 16.0F;
	/** Mouse y of the current frame, for rows with several targets (the style grid). */
	public static double mouseY;

	private Rows() {
	}

	public abstract static class Row {
		public final Component label;
		public final Component desc;
		public final Identifier icon;
		/** Visible while every ancestor branch is open. */
		public BooleanSupplier open = () -> true;
		public int depth;
		/** Animated state: y inside the list, how far it has come out (0..1), hover glow. */
		public float y = Float.NaN;
		public float appear;
		public float hover;
		/** Delay of the cascade when a page opens. */
		public long appearAfterMs;

		Row(Component label, Component desc, Identifier icon) {
			this.label = label;
			this.desc = desc;
			this.icon = icon;
		}

		public abstract int height();

		public abstract void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX);

		public boolean click(double mx, double my, int x, int y, int w) {
			return false;
		}

		public void drag(double mx, int x, int w) {
		}

		public void release() {
		}

		/** Row background: dark glass, a metal top rim, the hover glow from the left. */
		protected void plate(Gfx g, int x, int y, int w, int h, float alpha) {
			g.fill(x, y, x + w, y + h, Theme.alpha(Theme.mix(Theme.ROW, Theme.ROW_HOVER, this.hover), alpha));
			g.fill(x, y, x + w, y + 1, Theme.alpha(Theme.METAL, 0.8F * alpha));
			if (this.hover > 0.02F) {
				g.hGradient(x, y + 1, x + Math.min(w, 70), y + h, Theme.alpha(Theme.PURPLE, 0.28F * this.hover * alpha), 0);
				g.fill(x, y + 1, x + 1, y + h, Theme.alpha(Theme.MAGENTA, this.hover * alpha));
			}
		}

		/** Icon (if any) and label; returns the x where the label ends. */
		protected int caption(Gfx g, int x, int y, int maxW, float alpha) {
			int tx = x + 5;
			if (this.icon != null) {
				g.icon(this.icon, x + 12, y + 9, 12 + this.hover * 2.0F, 0.0F, Theme.alpha(0xFFFFFFFF, alpha));
				tx = x + 22;
			}
			String text = Gfx.font().plainSubstrByWidth(this.label.getString(), Math.max(10, maxW - (tx - x)));
			g.text(text, tx, y + 5, Theme.alpha(Theme.TEXT, alpha), true);
			return tx + Gfx.font().width(text);
		}
	}

	public static final class Section extends Row {
		public Section(Component label) {
			super(label, Component.empty(), null);
		}

		@Override
		public int height() {
			return 15;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			String text = this.label.getString().toUpperCase(Locale.ROOT);
			g.text(text, x + 2, y + 5, Theme.alpha(Theme.MAGENTA, alpha), false);
			int lx = x + 6 + Gfx.font().width(text);
			g.hGradient(lx, y + 9, x + w, y + 10, Theme.alpha(Theme.METAL_LIGHT, 0.6F * alpha), 0);
		}
	}

	public static final class Toggle extends Row {
		private final BooleanSupplier get;
		private final Consumer<Boolean> set;
		private float knob = Float.NaN;

		public Toggle(Component label, Component desc, Identifier icon, BooleanSupplier get, Consumer<Boolean> set) {
			super(label, desc, icon);
			this.get = get;
			this.set = set;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			boolean on = this.get.getAsBoolean();
			float target = on ? 1.0F : 0.0F;
			this.knob = Float.isNaN(this.knob) ? target : this.knob + (target - this.knob) * Theme.approach(frameDt, 45.0F);
			this.plate(g, x, y, w, 18, alpha);
			this.caption(g, x, y, w - 34, alpha);
			int sx = x + w - 28;
			int sy = y + 5;
			g.fill(sx, sy, sx + 22, sy + 9, Theme.alpha(Theme.METAL_DARK, alpha));
			g.outline(sx, sy, 22, 9, Theme.alpha(Theme.mix(Theme.METAL, Theme.MAGENTA, this.knob), alpha));
			int fillW = Math.round(20 * this.knob);
			if (fillW > 0) {
				g.hGradient(sx + 1, sy + 1, sx + 1 + fillW, sy + 8, Theme.alpha(Theme.PURPLE_DEEP, alpha), Theme.alpha(Theme.PURPLE, alpha));
			}
			int kx = sx + 1 + Math.round(13 * this.knob);
			g.fill(kx, sy + 1, kx + 8, sy + 8, Theme.alpha(Theme.mix(Theme.METAL, Theme.METAL_LIGHT, 0.4F + 0.6F * this.knob), alpha));
			g.fill(kx, sy + 1, kx + 8, sy + 2, Theme.alpha(0xFFFFFFFF, 0.45F * alpha));
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			this.set.accept(!this.get.getAsBoolean());
			AlertTweaksConfig.save();
			UiSound.click();
			return true;
		}
	}

	public static final class Slider extends Row {
		private final double min;
		private final double max;
		private final double step;
		private final DoubleSupplier get;
		private final Consumer<Double> set;
		private final DoubleFunction<String> format;
		private boolean dragging;

		public Slider(Component label, Component desc, double min, double max, double step, DoubleSupplier get, Consumer<Double> set,
			DoubleFunction<String> format) {
			super(label, desc, null);
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
		}

		@Override
		public int height() {
			return 26;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			this.plate(g, x, y, w, 26, alpha);
			String value = this.format.apply(this.get.getAsDouble());
			int valueW = Gfx.font().width(value);
			this.caption(g, x, y - 1, w - valueW - 12, alpha);
			g.text(value, x + w - 6 - valueW, y + 4, Theme.alpha(Theme.MAGENTA, alpha), true);
			int tx = x + 6;
			int tw = w - 12;
			int ty = y + 17;
			float t = (float)((this.get.getAsDouble() - this.min) / (this.max - this.min));
			t = Theme.clamp01(t);
			g.fill(tx, ty, tx + tw, ty + 3, Theme.alpha(Theme.METAL_DARK, alpha));
			g.hGradient(tx, ty, tx + Math.round(tw * t), ty + 3, Theme.alpha(Theme.PURPLE_DEEP, alpha), Theme.alpha(Theme.MAGENTA, alpha));
			int kx = tx + Math.round(tw * t);
			int glow = Math.round(2 + 2 * this.hover);
			g.fill(kx - 2 - glow, ty - 2, kx + 2 + glow, ty + 5, Theme.alpha(Theme.PURPLE, 0.25F * this.hover * alpha));
			g.fill(kx - 2, ty - 3, kx + 2, ty + 6, Theme.alpha(Theme.METAL_LIGHT, alpha));
			g.fill(kx - 2, ty - 3, kx + 2, ty - 2, Theme.alpha(0xFFFFFFFF, 0.6F * alpha));
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			this.dragging = true;
			this.drag(mx, x, w);
			return true;
		}

		@Override
		public void drag(double mx, int x, int w) {
			if (!this.dragging) {
				return;
			}
			double t = Mth.clamp((mx - (x + 6)) / Math.max(1, w - 12), 0.0, 1.0);
			double raw = this.min + t * (this.max - this.min);
			double snapped = Mth.clamp(Math.round((raw - this.min) / this.step) * this.step + this.min, this.min, this.max);
			if (snapped != this.get.getAsDouble()) {
				this.set.accept(snapped);
			}
		}

		@Override
		public void release() {
			if (this.dragging) {
				this.dragging = false;
				AlertTweaksConfig.save();
			}
		}
	}

	/** A short list of choices stepped with a click (right click steps back is not needed for ≤ 7 choices). */
	public static final class Cycle extends Row {
		private final Supplier<Component> value;
		private final Runnable next;

		public Cycle(Component label, Component desc, Identifier icon, Supplier<Component> value, Runnable next) {
			super(label, desc, icon);
			this.value = value;
			this.next = next;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			this.plate(g, x, y, w, 18, alpha);
			String value = this.value.get().getString();
			int vw = Gfx.font().width(value);
			this.caption(g, x, y, w - vw - 24, alpha);
			int vx = x + w - 10 - vw;
			g.text(value, vx, y + 5, Theme.alpha(Theme.MAGENTA, alpha), true);
			g.text("▸", x + w - 8, y + 5, Theme.alpha(Theme.mix(Theme.DIM, Theme.TEXT, this.hover), alpha), false);
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			this.next.run();
			AlertTweaksConfig.save();
			UiSound.click();
			return true;
		}
	}

	/** A metal button inside the list. */
	public static final class Action extends Row {
		private final Runnable action;
		private long pressedMs = -1L;

		public Action(Component label, Component desc, Identifier icon, Runnable action) {
			super(label, desc, icon);
			this.action = action;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			long since = this.pressedMs < 0L ? 9999L : net.minecraft.util.Util.getMillis() - this.pressedMs;
			float press = Theme.clamp01(1.0F - since / 260.0F);
			int bw = Math.min(w, Math.max(90, Gfx.font().width(this.label) + 40));
			int bx = x + 2;
			g.fill(bx, y + 1, bx + bw, y + 17, Theme.alpha(Theme.mix(Theme.METAL_DARK, Theme.PURPLE_DEEP, this.hover * 0.8F + press), alpha));
			g.outline(bx, y + 1, bw, 16, Theme.alpha(Theme.mix(Theme.METAL, Theme.MAGENTA, this.hover), alpha));
			g.fill(bx + 1, y + 2, bx + bw - 1, y + 3, Theme.alpha(Theme.METAL_LIGHT, 0.5F * alpha));
			if (press > 0.0F) {
				int r = Math.round(bw * 0.5F * (1.0F - press));
				g.outline(bx + bw / 2 - r, y + 1, r * 2, 16, Theme.alpha(Theme.MAGENTA, press * alpha));
			}
			int tx = bx + 8;
			if (this.icon != null) {
				g.icon(this.icon, bx + 12, y + 9, 11, 0.0F, Theme.alpha(0xFFFFFFFF, alpha));
				tx = bx + 22;
			}
			g.text(this.label, tx, y + 5, Theme.alpha(Theme.TEXT, alpha), true);
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			this.pressedMs = net.minecraft.util.Util.getMillis();
			this.action.run();
			UiSound.click();
			return true;
		}
	}

	/** Hue across, brightness down; drag to pick. */
	public static final class Palette extends Row {
		private boolean dragging;

		public Palette(Component label, Component desc) {
			super(label, desc, null);
		}

		@Override
		public int height() {
			return 46;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			AlertTweaksConfig config = AlertTweaksConfig.get();
			this.plate(g, x, y, w, 46, alpha);
			String hex = String.format(Locale.ROOT, "#%02X%02X%02X", config.vignetteRed, config.vignetteGreen, config.vignetteBlue);
			this.caption(g, x, y - 1, w - 60, alpha);
			g.text(hex, x + w - 30 - Gfx.font().width(hex), y + 4, Theme.alpha(Theme.MUTED, alpha), false);
			g.fill(x + w - 24, y + 3, x + w - 6, y + 13, Theme.alpha(config.getVignetteColor(255), alpha));
			g.outline(x + w - 25, y + 2, 20, 12, Theme.alpha(Theme.METAL_LIGHT, alpha));
			int px = x + 6;
			int py = y + 17;
			int pw = w - 12;
			int ph = 24;
			for (int i = 0; i < pw; i += 2) {
				int top = 0xFF000000 | Mth.hsvToRgb(i / (float)Math.max(1, pw - 1), 1.0F, 1.0F);
				g.gradient(px + i, py, px + Math.min(pw, i + 2), py + ph, Theme.alpha(top, alpha), Theme.alpha(0xFF000000, alpha));
			}
			g.outline(px - 1, py - 1, pw + 2, ph + 2, Theme.alpha(Theme.METAL, alpha));
			float[] hsv = hsv(config.vignetteRed, config.vignetteGreen, config.vignetteBlue);
			int mx = px + Math.round(hsv[0] * (pw - 1));
			int my = py + Math.round((1.0F - hsv[2]) * (ph - 1));
			g.outline(mx - 3, my - 3, 7, 7, Theme.alpha(0xFFFFFFFF, alpha));
			g.outline(mx - 2, my - 2, 5, 5, Theme.alpha(0xFF14071F, alpha));
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			if (my < y + 15) {
				return true;
			}
			this.dragging = true;
			this.pick(mx, my, x, y, w);
			return true;
		}

		private void pick(double mx, double my, int x, int y, int w) {
			float hue = (float)Mth.clamp((mx - (x + 6)) / Math.max(1, w - 13), 0.0, 1.0);
			float value = 1.0F - (float)Mth.clamp((my - (y + 17)) / 23.0, 0.0, 1.0);
			int rgb = Mth.hsvToRgb(hue, 1.0F, value);
			AlertTweaksConfig config = AlertTweaksConfig.get();
			config.vignetteRed = rgb >> 16 & 0xFF;
			config.vignetteGreen = rgb >> 8 & 0xFF;
			config.vignetteBlue = rgb & 0xFF;
		}

		/** Drag only knows x; the screen passes the row's y through {@link #dragAt}. */
		public void dragAt(double mx, double my, int x, int y, int w) {
			if (this.dragging) {
				this.pick(mx, my, x, y, w);
			}
		}

		@Override
		public void release() {
			if (this.dragging) {
				this.dragging = false;
				AlertTweaksConfig.save();
			}
		}

		private static float[] hsv(int red, int green, int blue) {
			float r = red / 255.0F;
			float g = green / 255.0F;
			float b = blue / 255.0F;
			float max = Math.max(r, Math.max(g, b));
			float min = Math.min(r, Math.min(g, b));
			float d = max - min;
			float hue;
			if (d == 0.0F) {
				hue = 0.0F;
			} else if (max == r) {
				hue = (g - b) / d % 6.0F;
			} else if (max == g) {
				hue = (b - r) / d + 2.0F;
			} else {
				hue = (r - g) / d + 4.0F;
			}
			hue /= 6.0F;
			return new float[]{hue < 0.0F ? hue + 1.0F : hue, max == 0.0F ? 0.0F : d / max, max};
		}
	}

	/** One of the owner's links (KoHs tab). */
	public static final class Link extends Row {
		private final String url;

		public Link(Component label, Component desc, Identifier icon, String url) {
			super(label, desc, icon);
			this.url = url;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			this.plate(g, x, y, w, 18, alpha);
			int end = this.caption(g, x, y, w - 8, alpha);
			int room = x + w - end - 18;
			if (room >= 40) {
				// The address is the least important part: it gives way to the label.
				String shown = Gfx.font().plainSubstrByWidth(this.url.replaceFirst("^https://", ""), room);
				g.text(shown, x + w - 8 - Gfx.font().width(shown), y + 5, Theme.alpha(Theme.mix(Theme.DIM, Theme.MAGENTA, this.hover), alpha), false);
			}
		}

		public String url() {
			return this.url;
		}
	}

	/** A paragraph of text, wrapped to the row width (it learns the width on its first frame). */
	public static final class Note extends Row {
		private int width = 200;
		private java.util.List<net.minecraft.util.FormattedCharSequence> lines;

		public Note(Component label) {
			super(label, Component.empty(), null);
		}

		private java.util.List<net.minecraft.util.FormattedCharSequence> lines(int w) {
			if (this.lines == null || w != this.width) {
				this.width = w;
				this.lines = Gfx.font().split(this.label, Math.max(40, w - 4));
			}
			return this.lines;
		}

		@Override
		public int height() {
			return 2 + 10 * this.lines(this.width).size();
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			var lines = this.lines(w);
			for (int i = 0; i < lines.size(); i++) {
				g.text(lines.get(i), x + 2, y + 2 + i * 10, Theme.alpha(Theme.MUTED, alpha), false);
			}
		}
	}

	/** A one-line text field with an add button (player names). */
	public static final class TextInput extends Row {
		public final StringBuilder text = new StringBuilder();
		private final java.util.function.Predicate<String> submit;
		public boolean focused;

		public TextInput(Component placeholder, Component desc, java.util.function.Predicate<String> submit) {
			super(placeholder, desc, null);
			this.submit = submit;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			this.plate(g, x, y, w, 18, alpha);
			int fx = x + 4;
			int fw = w - 30;
			g.fill(fx, y + 3, fx + fw, y + 15, Theme.alpha(0xFF050208, alpha));
			g.outline(fx, y + 3, fw, 12, Theme.alpha(this.focused ? Theme.MAGENTA : Theme.METAL, alpha));
			boolean empty = this.text.length() == 0;
			String shown = empty ? this.label.getString() : this.text.toString();
			shown = Gfx.font().plainSubstrByWidth(shown, fw - 8);
			g.text(shown, fx + 4, y + 5, Theme.alpha(empty ? Theme.DIM : Theme.TEXT, alpha), false);
			if (this.focused && (net.minecraft.util.Util.getMillis() / 500L) % 2L == 0L) {
				int cx = fx + 4 + (empty ? 0 : Gfx.font().width(shown));
				g.fill(cx, y + 4, cx + 1, y + 14, Theme.alpha(Theme.MAGENTA, alpha));
			}
			int bx = x + w - 22;
			boolean overButton = hovered && mouseX >= bx;
			g.fill(bx, y + 2, bx + 18, y + 16, Theme.alpha(overButton ? Theme.PURPLE_DEEP : Theme.METAL_DARK, alpha));
			g.outline(bx, y + 2, 18, 14, Theme.alpha(overButton ? Theme.MAGENTA : Theme.METAL, alpha));
			g.text("+", bx + 6, y + 5, Theme.alpha(Theme.TEXT, alpha), true);
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			if (mx >= x + w - 22) {
				this.submitNow();
			} else {
				this.focused = true;
			}
			UiSound.click();
			return true;
		}

		public void submitNow() {
			String value = this.text.toString().trim();
			if (!value.isEmpty() && this.submit.test(value)) {
				this.text.setLength(0);
			}
		}

		/** Java names (letters, digits, underscore) with the dot or star a Bedrock bridge puts in front. */
		public void type(String chars) {
			for (int i = 0; i < chars.length() && this.text.length() < 17; i++) {
				char c = chars.charAt(i);
				boolean prefix = (c == '.' || c == '*') && this.text.length() == 0;
				if (prefix || c == '_' || Character.isLetterOrDigit(c) && c < 128) {
					this.text.append(c);
				}
			}
		}

		public void backspace() {
			if (this.text.length() > 0) {
				this.text.setLength(this.text.length() - 1);
			}
		}
	}

	/** A watched player: face, name, online mark, a switch and a remove button. */
	public static final class PlayerRow extends Row {
		private final AlertTweaksConfig.WatchedPlayer player;
		private final Runnable remove;
		private float knob = Float.NaN;

		public PlayerRow(AlertTweaksConfig.WatchedPlayer player, Component desc, Runnable remove) {
			super(Component.literal(player.name), desc, null);
			this.player = player;
			this.remove = remove;
		}

		@Override
		public int height() {
			return 20;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			this.plate(g, x, y, w, 20, alpha);
			g.fill(x + 3, y + 2, x + 19, y + 18, Theme.alpha(0xFF050208, alpha));
			g.face(dev.zymekoh.alerttweaks.client.Faces.byName(this.player.name).get(), x + 3, y + 2, 16,
				this.player.enabled ? alpha : alpha * 0.45F);
			g.text(this.player.name, x + 24, y + 6, Theme.alpha(this.player.enabled ? Theme.TEXT : Theme.DIM, alpha), true);
			if (dev.zymekoh.alerttweaks.client.alert.AlertMonitor.isOnline(this.player.name)) {
				int dx = x + 28 + Gfx.font().width(this.player.name);
				g.fill(dx, y + 8, dx + 4, y + 12, Theme.alpha(Theme.MINT, alpha));
			}
			float target = this.player.enabled ? 1.0F : 0.0F;
			this.knob = Float.isNaN(this.knob) ? target : this.knob + (target - this.knob) * Theme.approach(frameDt, 45.0F);
			int sx = x + w - 44;
			int sy = y + 6;
			g.fill(sx, sy, sx + 22, sy + 9, Theme.alpha(Theme.METAL_DARK, alpha));
			g.outline(sx, sy, 22, 9, Theme.alpha(Theme.mix(Theme.METAL, Theme.MAGENTA, this.knob), alpha));
			int kx = sx + 1 + Math.round(13 * this.knob);
			g.fill(kx, sy + 1, kx + 8, sy + 8, Theme.alpha(Theme.mix(Theme.METAL, Theme.METAL_LIGHT, 0.4F + 0.6F * this.knob), alpha));
			boolean overRemove = hovered && mouseX >= x + w - 16;
			g.text("×", x + w - 12, y + 6, Theme.alpha(overRemove ? Theme.CRIMSON : Theme.DIM, alpha), false);
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			if (mx >= x + w - 16) {
				this.remove.run();
			} else {
				this.player.enabled = !this.player.enabled;
				AlertTweaksConfig.save();
			}
			UiSound.click();
			return true;
		}
	}

	/** Every threat indicator style playing live; click one to use it. */
	public static final class StyleGrid extends Row {
		private static final int COLUMNS = 4;
		private static final int TILE_H = 44;

		public StyleGrid(Component label, Component desc) {
			super(label, desc, null);
		}

		@Override
		public int height() {
			int rows = (dev.zymekoh.alerttweaks.client.threat.ThreatStyle.values().length + COLUMNS - 1) / COLUMNS;
			return rows * (TILE_H + 14) + 2;
		}

		private static int tileW(int w) {
			return (w - 4 - (COLUMNS - 1) * 4) / COLUMNS;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			var styles = dev.zymekoh.alerttweaks.client.threat.ThreatStyle.values();
			var chosen = dev.zymekoh.alerttweaks.client.threat.ThreatStyle.parse(AlertTweaksConfig.get().threatStyle);
			long now = net.minecraft.util.Util.getMillis();
			int tw = tileW(w);
			for (int i = 0; i < styles.length; i++) {
				int tx = x + 2 + (i % COLUMNS) * (tw + 4);
				int ty = y + 2 + (i / COLUMNS) * (TILE_H + 14);
				boolean over = hovered && mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + TILE_H + 12;
				boolean on = styles[i] == chosen;
				g.fill(tx, ty, tx + tw, ty + TILE_H, Theme.alpha(0xFF07030C, alpha));
				g.outline(tx, ty, tw, TILE_H, Theme.alpha(on ? Theme.MAGENTA : over ? Theme.METAL_LIGHT : Theme.METAL, alpha));
				float cx = tx + tw / 2.0F;
				float cy = ty + TILE_H / 2.0F;
				g.fill(Math.round(cx) - 2, Math.round(cy), Math.round(cx) + 3, Math.round(cy) + 1, Theme.alpha(0xFFFFFFFF, 0.6F * alpha));
				g.fill(Math.round(cx), Math.round(cy) - 2, Math.round(cx) + 1, Math.round(cy) + 3, Theme.alpha(0xFFFFFFFF, 0.6F * alpha));
				long loop = (now + i * 420L) % 3000L;
				long age = loop < 2000L ? loop : 3800L + (loop - 2000L) * 6L / 5L;
				float angle = (now / 25L % 360L) + i * 45.0F;
				float scale = 0.6F;
				float radius = Math.min(tw, TILE_H) * 0.3F / scale;
				g.scissor(tx + 1, ty + 1, tx + tw - 1, ty + TILE_H - 1);
				styles[i].draw(g, tx, ty, tw, TILE_H, cx, cy, angle, scale, radius, age, Icons.MACE);
				g.endScissor();
				String name = Gfx.font().plainSubstrByWidth(styles[i].title().getString(), tw);
				g.text(name, tx + (tw - Gfx.font().width(name)) / 2, ty + TILE_H + 3, Theme.alpha(on ? Theme.MAGENTA : Theme.MUTED, alpha), on);
			}
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			var styles = dev.zymekoh.alerttweaks.client.threat.ThreatStyle.values();
			int tw = tileW(w);
			int col = (int)((mx - x - 2) / (tw + 4));
			int row = (int)((my - y - 2) / (TILE_H + 14));
			int index = row * COLUMNS + col;
			if (col >= 0 && col < COLUMNS && index >= 0 && index < styles.length) {
				AlertTweaksConfig.get().threatStyle = styles[index].name();
				AlertTweaksConfig.save();
				UiSound.click();
			}
			return true;
		}
	}
}
