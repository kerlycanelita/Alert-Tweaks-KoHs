package dev.zymekoh.alerttweaks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.alerttweaks.AlertTweaks;
import dev.zymekoh.alerttweaks.client.Mc;
import dev.zymekoh.alerttweaks.client.alert.Alert;
import dev.zymekoh.alerttweaks.client.alert.AlertCards;
import dev.zymekoh.alerttweaks.client.alert.AlertFeed;
import dev.zymekoh.alerttweaks.client.alert.AlertSamples;
import dev.zymekoh.alerttweaks.client.alert.AlertSound;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.AlertLayout.Rect;
import dev.zymekoh.alerttweaks.client.gui.Rows.Row;
import dev.zymekoh.alerttweaks.client.hud.HeartbeatSound;
import dev.zymekoh.alerttweaks.client.hud.LowHealthVignette;
import dev.zymekoh.alerttweaks.client.hud.MiniHud;
import dev.zymekoh.alerttweaks.client.threat.ThreatIndicatorRenderer;
import dev.zymekoh.alerttweaks.client.threat.ThreatStyle;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Settings: five pages in a rail (Health, Threat, Alerts, Player alerts, KoHs), an option tree in the
 * middle and a live miniature of the player's own screen on the right. The header carries the way
 * into the position editor. A totem spins in the first time it opens in a session and sweeps across
 * the list on every page change.
 */
public final class AlertTweaksScreen extends Screen {
	private static final Identifier[] PREVIEW_FRAMES = new Identifier[48];
	private static final long ENTRANCE_MS = 950L;
	private static final long SWEEP_MS = 420L;
	private static final int ROW_GAP = 3;
	private static boolean enteredThisSession;
	private static Page lastPage = Page.HEALTH;

	static {
		for (int i = 0; i < PREVIEW_FRAMES.length; i++) {
			PREVIEW_FRAMES[i] = AlertTweaks.id(String.format(Locale.ROOT, "textures/gui/preview/frame_%02d.png", i));
		}
	}

	enum Page {
		HEALTH(Icons.HEART), THREAT(Icons.SWORD), ALERTS(Icons.BELL), PLAYERS(Icons.NAME_TAG), KOHS(Icons.AMETHYST);

		final Identifier icon;

		Page(Identifier icon) {
			this.icon = icon;
		}

		Component title() {
			return Component.translatable("alert_tweaks.page." + this.name().toLowerCase(Locale.ROOT));
		}

		Component desc() {
			return Component.translatable("alert_tweaks.page." + this.name().toLowerCase(Locale.ROOT) + ".desc");
		}
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private AlertLayout layout;
	private Page page = lastPage;
	private final long openedMs = Util.getMillis();
	private final boolean playEntrance;
	private long pageChangedMs = -1L;
	private long lastFrameNanos;
	private float scroll;
	private float scrollTarget;
	private float tabMarker = Float.NaN;
	private Row pressed;
	private Row hovered;
	private Rows.TextInput input;
	private long resetArmedMs = -1L;
	private long demoNextMs;
	private int demoIndex;
	private float threatAngle = 30.0F;
	private long threatStartMs = Util.getMillis();
	private boolean editHover;

	public AlertTweaksScreen(Screen parent) {
		super(Component.translatable("alert_tweaks.title"));
		this.parent = parent;
		this.playEntrance = !enteredThisSession;
		enteredThisSession = true;
		MiniHud.shuffle();
	}

	@Override
	protected void init() {
		this.layout = AlertLayout.fit(this.width, this.height);
		if (this.rows.isEmpty()) {
			this.buildRows(false);
		}
		if (this.playEntrance && Util.getMillis() - this.openedMs < 50L) {
			UiSound.entrance();
		}
	}

	@Override
	public void onClose() {
		HeartbeatSound.stopPreview();
		AlertFeed.PREVIEW.clear();
		AlertTweaksConfig.save();
		Mc.setScreen(Minecraft.getInstance(), this.parent);
	}

	// ------------------------------------------------------------------ pages

	private void buildRows(boolean cascade) {
		this.rows.clear();
		this.input = null;
		AlertTweaksConfig c = AlertTweaksConfig.get();
		switch (this.page) {
			case HEALTH -> this.healthRows(c);
			case THREAT -> this.threatRows(c);
			case ALERTS -> this.alertRows();
			case PLAYERS -> this.playerRows(c);
			case KOHS -> this.kohsRows();
		}
		long start = Util.getMillis() + (cascade ? 140L : this.playEntrance ? 520L : 0L);
		for (int i = 0; i < this.rows.size(); i++) {
			Row row = this.rows.get(i);
			row.appearAfterMs = start + Math.min(i, 14) * 22L;
			row.appear = 0.0F;
			row.y = Float.NaN;
		}
		this.scroll = 0.0F;
		this.scrollTarget = 0.0F;
	}

	/** Rebuilds the page in place (a player added or removed): no cascade, no jump to the top. */
	private void rebuildInPlace() {
		float keepScroll = this.scrollTarget;
		List<Row> before = new ArrayList<>(this.rows);
		this.buildRows(false);
		for (int i = 0; i < this.rows.size(); i++) {
			Row row = this.rows.get(i);
			row.appearAfterMs = 0L;
			if (i < before.size() && before.get(i).getClass() == row.getClass()) {
				row.appear = 1.0F;
				row.y = before.get(i).y;
			}
		}
		this.scroll = keepScroll;
		this.scrollTarget = keepScroll;
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("alert_tweaks.gui." + key, args);
	}

	private <R extends Row> R add(R row, int depth, BooleanSupplier open) {
		row.depth = depth;
		row.open = open;
		this.rows.add(row);
		return row;
	}

	private <R extends Row> R add(R row) {
		return this.add(row, 0, () -> true);
	}

	private void healthRows(AlertTweaksConfig c0) {
		this.add(new Rows.Section(t("section.low_health")));
		BooleanSupplier vignette = () -> AlertTweaksConfig.get().vignetteEnabled;
		this.add(new Rows.Toggle(t("vignette"), t("vignette.desc"), Icons.HEART, vignette, v -> AlertTweaksConfig.get().vignetteEnabled = v));
		this.add(new Rows.Slider(t("health"), t("health.desc"), 1, 20, 1, () -> AlertTweaksConfig.get().thresholdHealthPoints,
			v -> AlertTweaksConfig.get().thresholdHealthPoints = v.floatValue(), v -> String.format(Locale.ROOT, "%.1f ❤", v / 2.0)), 1, vignette);
		this.add(new Rows.Palette(t("color"), t("color.desc")), 1, vignette);
		this.add(new Rows.Slider(t("opacity"), t("opacity.desc"), 0, 255, 5, () -> AlertTweaksConfig.get().vignetteAlpha,
			v -> AlertTweaksConfig.get().vignetteAlpha = (int)Math.round(v), v -> Math.round(v / 2.55) + "%"), 1, vignette);
		this.add(new Rows.Slider(t("size"), t("size.desc"), 0.45, 1.75, 0.05, () -> AlertTweaksConfig.get().vignetteSize,
			v -> AlertTweaksConfig.get().vignetteSize = v.floatValue(), AlertTweaksScreen::percent), 1, vignette);
		this.add(new Rows.Toggle(t("smooth"), t("smooth.desc"), null, () -> AlertTweaksConfig.get().smoothVignetteEnabled,
			v -> AlertTweaksConfig.get().smoothVignetteEnabled = v), 1, vignette);
		BooleanSupplier fixed = () -> vignette.getAsBoolean() && AlertTweaksConfig.get().staticColorEnabled;
		this.add(new Rows.Toggle(t("static"), t("static.desc"), null, () -> AlertTweaksConfig.get().staticColorEnabled,
			v -> AlertTweaksConfig.get().staticColorEnabled = v), 1, vignette);
		this.add(new Rows.Slider(t("intensity"), t("intensity.desc"), 0.05, 1, 0.05, () -> AlertTweaksConfig.get().staticColorIntensity,
			v -> AlertTweaksConfig.get().staticColorIntensity = v.floatValue(), AlertTweaksScreen::percent), 2, fixed);
		BooleanSupplier pulse = () -> vignette.getAsBoolean() && AlertTweaksConfig.get().heartbeatEnabled;
		this.add(new Rows.Toggle(t("pulse"), t("pulse.desc"), null, () -> AlertTweaksConfig.get().heartbeatEnabled,
			v -> AlertTweaksConfig.get().heartbeatEnabled = v), 1, vignette);
		this.add(new Rows.Toggle(t("pulse_link"), t("pulse_link.desc"), null, () -> AlertTweaksConfig.get().heartbeatScaleWithLowHealth,
			v -> AlertTweaksConfig.get().heartbeatScaleWithLowHealth = v), 2, pulse);

		this.add(new Rows.Section(t("section.heartbeat")));
		BooleanSupplier beat = () -> AlertTweaksConfig.get().heartbeatSoundEnabled;
		this.add(new Rows.Toggle(t("heartbeat"), t("heartbeat.desc"), Icons.GOAT_HORN, beat, v -> AlertTweaksConfig.get().heartbeatSoundEnabled = v));
		this.add(new Rows.Slider(t("volume"), t("volume.desc"), 0.05, 1, 0.05, () -> AlertTweaksConfig.get().heartbeatSoundVolume,
			v -> AlertTweaksConfig.get().heartbeatSoundVolume = v.floatValue(), AlertTweaksScreen::percent), 1, beat);
		this.add(new Rows.Slider(t("tempo"), t("tempo.desc"), 0.5, 2, 0.05, () -> AlertTweaksConfig.get().heartbeatSoundIntensity,
			v -> AlertTweaksConfig.get().heartbeatSoundIntensity = v.floatValue(), AlertTweaksScreen::percent), 1, beat);
		this.add(new Rows.Toggle(t("beat_link"), t("beat_link.desc"), null, () -> AlertTweaksConfig.get().heartbeatSoundScaleWithLowHealth,
			v -> AlertTweaksConfig.get().heartbeatSoundScaleWithLowHealth = v), 1, beat);
		this.add(new Rows.Action(t("play"), t("play.desc"), Icons.GOAT_HORN, HeartbeatSound::startPreview), 1, beat);
	}

	private void threatRows(AlertTweaksConfig c0) {
		this.add(new Rows.Section(t("section.threat")));
		BooleanSupplier on = () -> AlertTweaksConfig.get().threatIndicatorEnabled;
		this.add(new Rows.Toggle(t("threat"), t("threat.desc"), Icons.SWORD, on, v -> AlertTweaksConfig.get().threatIndicatorEnabled = v));
		this.add(new Rows.StyleGrid(t("threat_style"), t("threat_style.desc")), 1, on);
		this.add(new Rows.Toggle(t("adaptive"), t("adaptive.desc"), null, () -> AlertTweaksConfig.get().threatAdaptiveScale,
			v -> AlertTweaksConfig.get().threatAdaptiveScale = v), 1, on);
		this.add(new Rows.Slider(t("scale"), t("scale.desc"), 0.5, 2.5, 0.05, () -> AlertTweaksConfig.get().threatScale, v -> {
			AlertTweaksConfig.get().threatScale = v.floatValue();
			// Touching the slider is what turns the adaptive default off.
			AlertTweaksConfig.get().threatAdaptiveScale = false;
		}, AlertTweaksScreen::percent), 1, on);
		this.add(new Rows.Slider(t("distance"), t("distance.desc"), 0, 1, 0.05, () -> AlertTweaksConfig.get().threatDistance,
			v -> AlertTweaksConfig.get().threatDistance = v.floatValue(), AlertTweaksScreen::percent), 1, on);
		this.add(new Rows.Action(t("simulate_hit"), t("simulate_hit.desc"), Icons.SWORD, () -> {
			this.threatAngle = (float)(Math.random() * 360.0);
			this.threatStartMs = Util.getMillis();
		}), 1, on);
	}

	/** Switch, sound, threshold and test for one alert. */
	private void alertBlock(Alert alert) {
		BooleanSupplier on = () -> AlertTweaksConfig.get().enabled(alert);
		this.add(new Rows.Toggle(alert.title(), alert.description(), alert.icon, on, v -> AlertTweaksConfig.get().alert(alert).enabled = v));
		this.add(new Rows.Cycle(t("sound"), t("sound.desc"), null, () -> AlertTweaksConfig.get().sound(alert).label(), () -> {
			AlertTweaksConfig.AlertSetting setting = AlertTweaksConfig.get().alert(alert);
			AlertSound next = AlertSound.parse(setting.sound, alert.defaultSound).next();
			setting.sound = next.name();
			next.play(AlertTweaksConfig.get().alertVolume);
		}), 1, on);
		this.add(new Rows.Cycle(t("look"), t("look.desc"), null, () -> AlertTweaksConfig.get().look(alert).label(), () -> {
			AlertTweaksConfig.AlertSetting setting = AlertTweaksConfig.get().alert(alert);
			setting.look = AlertTweaksConfig.get().look(alert).next().name();
			AlertSamples.fire(AlertFeed.PREVIEW, alert, false);
		}), 1, on);
		if (alert.threshold != null) {
			Alert.Threshold th = alert.threshold;
			this.add(new Rows.Slider(alert.thresholdLabel(), alert.description(), th.min(), th.max(), th.step(),
				() -> AlertTweaksConfig.get().threshold(alert), v -> AlertTweaksConfig.get().alert(alert).threshold = v.floatValue(),
				v -> format(th.unit(), v)), 1, on);
		}
		this.add(new Rows.Action(t("test"), t("test.desc"), null, () -> AlertSamples.fire(AlertFeed.PREVIEW, alert, true)), 1, on);
	}

	private void alertRows() {
		Alert.Group group = null;
		for (Alert alert : Alert.values()) {
			if (alert.group == Alert.Group.PLAYERS) {
				continue;
			}
			if (alert.group != group) {
				group = alert.group;
				this.add(new Rows.Section(group.title()));
			}
			this.alertBlock(alert);
		}
	}

	private void playerRows(AlertTweaksConfig c0) {
		this.add(new Rows.Section(t("section.watch")));
		this.input = this.add(new Rows.TextInput(t("player_name"), t("player_name.desc"), this::addPlayer));
		for (AlertTweaksConfig.WatchedPlayer player : AlertTweaksConfig.get().players) {
			this.add(new Rows.PlayerRow(player, t("player_row.desc"), () -> {
				AlertTweaksConfig.get().players.remove(player);
				AlertTweaksConfig.save();
				this.rebuildInPlace();
			}));
		}
		this.add(new Rows.Note(t("players.note")));
		this.add(new Rows.Section(t("section.player_alerts")));
		this.alertBlock(Alert.PLAYER_JOIN);
		this.alertBlock(Alert.PLAYER_LEAVE);
		this.alertBlock(Alert.PLAYER_ONLINE);
	}

	private boolean addPlayer(String name) {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		if (name.length() < 2 || config.watched(name) != null || config.players.size() >= 50) {
			UiSound.click();
			return false;
		}
		config.players.add(new AlertTweaksConfig.WatchedPlayer(name));
		AlertTweaksConfig.save();
		this.rebuildInPlace();
		if (this.input != null) {
			this.input.focused = true;
		}
		return true;
	}

	private void kohsRows() {
		this.add(new Rows.Section(t("section.kohs")));
		this.add(new Rows.Note(t("kohs.note")));
		this.add(new Rows.Link(t("discord"), t("discord.desc"), Icons.ECHO, "https://discord.gg/9t2VxEF7UU"));
		this.add(new Rows.Link(t("website"), t("website.desc"), Icons.NETHER_STAR, "https://kerlycanelita.github.io/KoHs-Mod-Suite/"));
		this.add(new Rows.Link(t("github"), t("github.desc"), Icons.BOOK, "https://github.com/kerlycanelita/Alert-Tweaks-KoHs"));
		this.add(new Rows.Link(t("coffee"), t("coffee.desc"), Icons.NAME_TAG, "https://buymeacoffee.com/zymekohh"));
	}

	private static String percent(double value) {
		return Math.round(value * 100.0) + "%";
	}

	private static String format(Alert.Unit unit, double value) {
		return switch (unit) {
			case COUNT -> String.valueOf(Math.round(value));
			case PERCENT -> Math.round(value) + "%";
			case SECONDS -> String.format(Locale.ROOT, "%.1f s", value);
		};
	}

	private void switchPage(Page next) {
		if (next == this.page) {
			return;
		}
		this.page = next;
		lastPage = next;
		this.pageChangedMs = Util.getMillis();
		AlertFeed.PREVIEW.clear();
		this.demoNextMs = Util.getMillis() + 500L;
		this.buildRows(true);
		UiSound.sweep();
	}

	// ------------------------------------------------------------------ drawing

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.draw(Gfx.of(graphics), mouseX, mouseY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// The screen paints its own backdrop in draw(); no vanilla blur or menu texture.
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.draw(Gfx.of(graphics), mouseX, mouseY);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
	}
	*///?}

	private void draw(Gfx g, int mouseX, int mouseY) {
		long nanos = System.nanoTime();
		float dt = this.lastFrameNanos == 0L ? 16.0F : Math.min(100.0F, (nanos - this.lastFrameNanos) / 1_000_000.0F);
		this.lastFrameNanos = nanos;
		Rows.frameDt = dt;
		Rows.mouseY = mouseY;
		long now = Util.getMillis();
		AlertLayout l = this.layout;
		float entrance = this.playEntrance ? Theme.clamp01((now - this.openedMs) / (float)ENTRANCE_MS) : 1.0F;
		float frameIn = Theme.easeOutCubic(Theme.clamp01((entrance - 0.3F) / 0.7F));

		this.backdrop(g, now, entrance);
		if (frameIn > 0.0F) {
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			float s = 0.9F + 0.1F * frameIn;
			pose.translate(this.width / 2.0F, this.height / 2.0F);
			pose.scale(s, s);
			pose.translate(-this.width / 2.0F, -this.height / 2.0F);
			this.frame(g, now, frameIn, mouseX, mouseY);
			this.rail(g, mouseX, mouseY, dt, frameIn);
			this.list(g, mouseX, mouseY, dt, now, frameIn);
			if (l.preview.w() > 0) {
				this.preview(g, now, frameIn);
			}
			this.footer(g, mouseX, mouseY, now, frameIn);
			this.sweep(g, now);
			pose.popMatrix();
		}
		if (entrance < 1.0F) {
			this.entranceTotem(g, now, entrance);
		}
	}

	/** Dark purple over the world; over the title screen a void with drifting embers. */
	private void backdrop(Gfx g, long now, float entrance) {
		float a = Theme.easeOutCubic(Theme.clamp01(entrance * 2.0F));
		if (Minecraft.getInstance().level != null) {
			g.gradient(0, 0, this.width, this.height, Theme.alpha(0xE0100620, a), Theme.alpha(0xF0040108, a));
		} else {
			g.fill(0, 0, this.width, this.height, Theme.alpha(Theme.VOID, a));
			g.gradient(0, 0, this.width, this.height / 2, Theme.alpha(0x60301060, a), 0);
		}
		// Embers: positions from a hash of the index and time; nothing stored, nothing allocated.
		for (int i = 0; i < 46; i++) {
			int seed = i * 7919 + 104729;
			float speed = 0.012F + (seed % 97) / 97.0F * 0.03F;
			float x = (seed % 1000) / 1000.0F * this.width + Mth.sin(now / 1700.0F + i) * 12.0F;
			float y = this.height - ((now * speed + seed % this.height) % (this.height + 20));
			float life = Theme.clamp01((y / this.height));
			int size = 1 + (seed >> 3) % 2;
			int color = (seed & 1) == 0 ? Theme.PURPLE : Theme.MAGENTA;
			g.fill(Math.round(x), Math.round(y), Math.round(x) + size, Math.round(y) + size, Theme.alpha(color, 0.55F * life * a));
		}
	}

	private void frame(Gfx g, long now, float in, int mouseX, int mouseY) {
		Rect f = this.layout.frame;
		g.nextStratum();
		g.fill(f.x(), f.y(), f.right(), f.bottom(), Theme.alpha(Theme.PANEL, in));
		g.gradient(f.x(), f.y(), f.right(), f.y() + 60, Theme.alpha(0x40501880, in), 0);
		// Black metal rim, lit from above.
		g.outline(f.x() - 1, f.y() - 1, f.w() + 2, f.h() + 2, Theme.alpha(Theme.METAL_DARK, in));
		g.outline(f.x(), f.y(), f.w(), f.h(), Theme.alpha(Theme.METAL, in));
		g.hGradient(f.x() + 1, f.y(), f.right() - 1, f.y() + 1, Theme.alpha(Theme.METAL_LIGHT, 0.2F * in), Theme.alpha(Theme.METAL_LIGHT, 0.9F * in));
		int[][] corners = {{f.x() + 2, f.y() + 2}, {f.right() - 5, f.y() + 2}, {f.x() + 2, f.bottom() - 5}, {f.right() - 5, f.bottom() - 5}};
		for (int[] c : corners) {
			g.fill(c[0], c[1], c[0] + 3, c[1] + 3, Theme.alpha(Theme.METAL_LIGHT, in));
			g.fill(c[0] + 1, c[1] + 1, c[0] + 2, c[1] + 2, Theme.alpha(Theme.METAL_DARK, in));
		}

		Rect h = this.layout.header;
		int tx = h.x() + 8;
		g.icon(Icons.TOTEM, tx + 6, h.y() + 10, 14, Mth.sin(now / 900.0F) * 0.12F, Theme.alpha(0xFFFFFFFF, in));
		g.text(this.title, tx + 16, h.y() + 6, Theme.alpha(Theme.TEXT, in), true);
		g.text("KoHs", tx + 20 + Gfx.font().width(this.title), h.y() + 6, Theme.alpha(Theme.MAGENTA, in), true);
		this.editButton(g, now, in, mouseX, mouseY);
		// Energy line: a bright pulse runs along the header's lower rim.
		int ly = h.bottom() + 1;
		g.fill(f.x() + 4, ly, f.right() - 4, ly + 1, Theme.alpha(Theme.PURPLE_DEEP, in));
		float run = (now % 2400L) / 2400.0F;
		int px = f.x() + 4 + Math.round((f.w() - 8) * run);
		g.hGradient(Math.max(f.x() + 4, px - 60), ly, px, ly + 1, 0, Theme.alpha(Theme.MAGENTA, in));
		g.hGradient(px, ly, Math.min(f.right() - 4, px + 20), ly + 1, Theme.alpha(Theme.MAGENTA, in), 0);
	}

	/** The way into the position editor: it should be impossible to miss. */
	private void editButton(Gfx g, long now, float in, int mouseX, int mouseY) {
		Rect r = this.layout.edit;
		this.editHover = r.contains(mouseX, mouseY);
		float pulse = 0.5F + 0.5F * Mth.sin(now / 260.0F);
		g.nextStratum();
		int glow = Math.round(2 + 2 * pulse);
		g.fill(r.x() - glow, r.y() - glow, r.right() + glow, r.bottom() + glow, Theme.alpha(Theme.MAGENTA, (0.10F + 0.12F * pulse) * in));
		g.hGradient(r.x(), r.y(), r.right(), r.bottom(), Theme.alpha(this.editHover ? 0xFF8A2BE2 : Theme.PURPLE_DEEP, in),
			Theme.alpha(this.editHover ? 0xFFD45CFF : 0xFF6A1FB0, in));
		// A bright sheen sweeps across every two seconds.
		float sweep = (now % 2000L) / 700.0F;
		if (sweep < 1.0F) {
			int sx = r.x() + Math.round((r.w() + 20) * sweep) - 10;
			g.hGradient(Math.max(r.x(), sx - 10), r.y() + 1, Math.min(r.right(), sx), r.bottom() - 1, 0, Theme.alpha(0xFFFFFFFF, 0.35F * in));
			g.hGradient(Math.max(r.x(), sx), r.y() + 1, Math.min(r.right(), sx + 10), r.bottom() - 1, Theme.alpha(0xFFFFFFFF, 0.35F * in), 0);
		}
		g.outline(r.x(), r.y(), r.w(), r.h(), Theme.alpha(Theme.mix(Theme.MAGENTA, 0xFFFFFFFF, this.editHover ? 0.5F : 0.2F * pulse), in));
		float spin = now / 1400.0F;
		g.icon(Icons.SPYGLASS, r.x() + 10, r.y() + 8, 12 + (this.editHover ? 2 : 0), Mth.sin(spin) * 0.3F, Theme.alpha(0xFFFFFFFF, in));
		if (this.layout.editLabel) {
			Component label = t("edit_positions");
			String text = Gfx.font().plainSubstrByWidth(label.getString().toUpperCase(Locale.ROOT), r.w() - 24);
			g.text(text, r.x() + 20, r.y() + 4, Theme.alpha(Theme.TEXT, in), true);
		}
	}

	private void rail(Gfx g, int mouseX, int mouseY, float dt, float in) {
		AlertLayout l = this.layout;
		Page[] pages = Page.values();
		Rect selected = l.tab(this.page.ordinal());
		float target = l.tabsOnTop ? selected.x() : selected.y();
		this.tabMarker = Float.isNaN(this.tabMarker) ? target : this.tabMarker + (target - this.tabMarker) * Theme.approach(dt, 60.0F);
		g.nextStratum();
		int slideX = Math.round((1.0F - in) * -30.0F);
		for (int i = 0; i < pages.length; i++) {
			Rect r = l.tab(i);
			int x = r.x() + slideX;
			boolean hover = r.contains(mouseX, mouseY);
			boolean on = pages[i] == this.page;
			g.fill(x, r.y(), x + r.w(), r.bottom(), Theme.alpha(hover ? Theme.ROW_HOVER : Theme.ROW, in));
			g.fill(x, r.y(), x + r.w(), r.y() + 1, Theme.alpha(Theme.METAL, in));
			int ix = x + (l.tabLabels ? 12 : r.w() / 2);
			float lift = hover ? -1.0F : 0.0F;
			g.icon(pages[i].icon, ix, r.y() + r.h() / 2.0F + lift, on ? 15 : 13, 0.0F, Theme.alpha(on || hover ? 0xFFFFFFFF : 0xFFB0A8C0, in));
			if (l.tabLabels) {
				String label = Gfx.font().plainSubstrByWidth(pages[i].title().getString(), r.w() - 28);
				g.text(label, x + 24, r.y() + 7, Theme.alpha(on ? Theme.TEXT : Theme.MUTED, in), on);
			}
		}
		// Selection marker glides between tabs.
		int m = Math.round(this.tabMarker);
		if (l.tabsOnTop) {
			g.fill(m + slideX, selected.bottom() - 2, m + slideX + selected.w(), selected.bottom(), Theme.alpha(Theme.MAGENTA, in));
		} else {
			g.fill(selected.x() + slideX, m, selected.x() + slideX + 2, m + selected.h(), Theme.alpha(Theme.MAGENTA, in));
			g.hGradient(selected.x() + slideX + 2, m, selected.x() + slideX + Math.min(selected.w(), 50), m + selected.h(),
				Theme.alpha(Theme.PURPLE, 0.4F * in), 0);
		}
	}

	private int rowX(Row row) {
		return this.layout.list.x() + 2 + row.depth * 10;
	}

	private int rowW(Row row) {
		return this.layout.list.w() - 8 - row.depth * 10;
	}

	private void list(Gfx g, int mouseX, int mouseY, float dt, long now, float in) {
		Rect list = this.layout.list;
		// Relayout every frame: open rows ease to their place, closed branches fold under their parent.
		float response = Theme.approach(dt, 75.0F);
		float cursor = 2.0F;
		float parentY = 0.0F;
		for (Row row : this.rows) {
			boolean show = row.open.getAsBoolean() && now >= row.appearAfterMs;
			float target = show ? cursor : parentY;
			row.y = Float.isNaN(row.y) ? target : row.y + (target - row.y) * response;
			row.appear += ((show ? 1.0F : 0.0F) - row.appear) * Theme.approach(dt, show ? 90.0F : 50.0F);
			if (row.open.getAsBoolean()) {
				cursor += row.height() + ROW_GAP;
				if (row.depth == 0) {
					parentY = cursor - row.height() - ROW_GAP;
				}
			}
		}
		float content = cursor;
		float maxScroll = Math.max(0.0F, content - list.h());
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, maxScroll);
		this.scroll += (this.scrollTarget - this.scroll) * Theme.approach(dt, 60.0F);

		this.hovered = null;
		g.nextStratum();
		g.scissor(list.x(), list.y(), list.right(), list.bottom());
		for (Row row : this.rows) {
			if (row.appear < 0.02F) {
				continue;
			}
			int y = list.y() + Math.round(row.y - this.scroll);
			if (y > list.bottom() || y + row.height() < list.y()) {
				continue;
			}
			int x = this.rowX(row) + Math.round((1.0F - row.appear) * 14.0F);
			int w = this.rowW(row);
			boolean hover = list.contains(mouseX, mouseY) && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + row.height()
				&& row.appear > 0.9F && !(row instanceof Rows.Section) && !(row instanceof Rows.Note);
			if (hover) {
				this.hovered = row;
			}
			row.hover += ((hover ? 1.0F : 0.0F) - row.hover) * Theme.approach(dt, 45.0F);
			if (row.depth > 0) {
				int bx = this.layout.list.x() + 2 + (row.depth - 1) * 10 + 4;
				g.fill(bx, y - ROW_GAP, bx + 1, y + row.height() / 2, Theme.alpha(Theme.METAL_LIGHT, 0.45F * row.appear * in));
				g.fill(bx, y + row.height() / 2, x - 1, y + row.height() / 2 + 1, Theme.alpha(Theme.METAL_LIGHT, 0.45F * row.appear * in));
				g.fill(x - 3, y + row.height() / 2 - 1, x - 1, y + row.height() / 2 + 2, Theme.alpha(Theme.MAGENTA, row.appear * in));
			}
			row.draw(g, x, y, w, row.appear * in, hover, mouseX);
		}
		g.endScissor();

		if (maxScroll > 0.0F) {
			int trackX = list.right() - 3;
			g.fill(trackX, list.y(), trackX + 2, list.bottom(), Theme.alpha(Theme.METAL_DARK, in));
			int thumbH = Math.max(12, Math.round(list.h() * list.h() / content));
			int thumbY = list.y() + Math.round((list.h() - thumbH) * (this.scroll / maxScroll));
			g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Theme.alpha(Theme.PURPLE, in));
		}
	}

	/**
	 * A miniature of the player's own screen: a clip of a fight behind, their HUD in front (the
	 * real one in a world, a PvP kit elsewhere), and whatever the page is about at the scale and
	 * place it will have in game.
	 */
	private void preview(Gfx g, long now, float in) {
		Rect p = this.layout.preview;
		int slide = Math.round((1.0F - in) * 40.0F);
		int x = p.x() + slide;
		int sceneH = Math.min(Math.round(p.w() * 9.0F / 16.0F), p.h() - 34);
		g.nextStratum();
		g.fill(x - 1, p.y() - 1, x + p.w() + 1, p.y() + sceneH + 1, Theme.alpha(Theme.METAL, in));
		int frame = (int)(now / 50L % PREVIEW_FRAMES.length);
		// Recorded without a HUD (tools/make_preview_frames.py), so the player's own HUD goes on top.
		g.blit(PREVIEW_FRAMES[frame], x, p.y(), p.w(), sceneH, 512, 288, Theme.alpha(0xFFFFFFFF, in));
		g.scissor(x, p.y(), x + p.w(), p.y() + sceneH);

		AlertTweaksConfig c = AlertTweaksConfig.get();
		float f = p.w() / (float)this.width;
		int vw = this.width;
		int vh = Math.round(sceneH / f);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, p.y());
		pose.scale(f, f);
		if (this.page == Page.HEALTH && c.vignetteEnabled) {
			this.previewVignette(g, c, vw, vh);
		}
		MiniHud.draw(g, vw, vh);
		MiniHud.crosshair(g, vw, vh);
		if (this.page == Page.THREAT && c.threatIndicatorEnabled) {
			long elapsed = now - this.threatStartMs;
			long age = elapsed % 5600L;
			float angle = this.threatAngle + elapsed / 5600L * 137.0F;
			if (age < 5000L) {
				ThreatStyle.parse(c.threatStyle).draw(g, 0.0F, 0.0F, vw, vh, vw / 2.0F, vh / 2.0F, angle,
					ThreatIndicatorRenderer.effectiveScale(g, c), ThreatIndicatorRenderer.radius(), age, Icons.MACE);
			}
		}
		if (this.page == Page.ALERTS || this.page == Page.PLAYERS || this.page == Page.KOHS) {
			this.demo(now);
			AlertCards.drawFlash(g, AlertFeed.PREVIEW, 0, 0, vw, vh);
			AlertCards.draw(g, AlertFeed.PREVIEW, 0.0F, 0.0F, vw, vh, c.toastScale, 1.0F);
		}
		pose.popMatrix();
		g.endScissor();
		g.outline(x - 1, p.y() - 1, p.w() + 2, sceneH + 2, Theme.alpha(Theme.METAL, in));
		g.fill(x, p.y() - 1, x + p.w(), p.y(), Theme.alpha(Theme.METAL_LIGHT, in));

		// Explanation under the picture: the row under the mouse, or the page.
		int iy = p.y() + sceneH + 6;
		int ih = p.bottom() - iy;
		if (ih < 20) {
			return;
		}
		g.fill(x, iy, x + p.w(), p.bottom(), Theme.alpha(Theme.PANEL_HI, in));
		g.outline(x, iy, p.w(), ih, Theme.alpha(Theme.METAL, in));
		Component head = this.hovered != null ? this.hovered.label : this.page.title();
		Component body = this.hovered != null ? this.hovered.desc : this.page.desc();
		g.text(Gfx.font().plainSubstrByWidth(head.getString(), p.w() - 10), x + 5, iy + 5, Theme.alpha(Theme.MAGENTA, in), true);
		List<FormattedCharSequence> lines = Gfx.font().split(body, p.w() - 10);
		int maxLines = Math.max(0, (ih - 18) / 10);
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			g.text(lines.get(i), x + 5, iy + 17 + i * 10, Theme.alpha(Theme.MUTED, in), false);
		}
	}

	/** Keeps the alert preview alive with a different card every couple of seconds, without sound. */
	private void demo(long now) {
		if (now < this.demoNextMs) {
			return;
		}
		this.demoNextMs = now + 2300L;
		Alert[] all = Alert.values();
		for (int tries = 0; tries < all.length; tries++) {
			Alert alert = all[this.demoIndex++ % all.length];
			boolean onPage = this.page == Page.PLAYERS ? alert.group == Alert.Group.PLAYERS : alert.group != Alert.Group.PLAYERS;
			if (onPage && AlertTweaksConfig.get().enabled(alert)) {
				AlertSamples.fire(AlertFeed.PREVIEW, alert, false);
				return;
			}
		}
	}

	/** The preview shows the player close to death, where the vignette is strongest. */
	private void previewVignette(Gfx g, AlertTweaksConfig c, int w, int h) {
		float strength = c.staticColorEnabled ? c.staticColorIntensity : 0.9F;
		float size = 1.0F;
		if (c.heartbeatEnabled) {
			float low = c.heartbeatScaleWithLowHealth ? 0.82F : 0.45F;
			float pulse = LowHealthVignette.heartbeatPulse(c, low);
			float amount = LowHealthVignette.heartbeatAmount(c, low);
			strength = Mth.clamp(strength * (1.0F + pulse * amount), 0.0F, 1.0F);
			size = 1.0F + pulse * amount * 0.26F;
		}
		LowHealthVignette.drawEdges(g, c, 0, 0, w, h, Mth.clamp(Math.round(c.vignetteAlpha * strength), 0, 255), size);
	}

	private void footer(Gfx g, int mouseX, int mouseY, long now, float in) {
		AlertLayout l = this.layout;
		boolean armed = this.resetArmedMs >= 0L && now - this.resetArmedMs < 3000L;
		this.button(g, l.reset, armed ? t("reset_confirm") : t("reset"), l.reset.contains(mouseX, mouseY), armed ? Theme.CRIMSON : Theme.MAGENTA, in);
		this.button(g, l.done, t("done"), l.done.contains(mouseX, mouseY), Theme.MAGENTA, in);
		if (l.hint.w() > 30) {
			Component hint = this.hovered != null && this.layout.preview.w() == 0 ? this.hovered.desc : Component.empty();
			String text = Gfx.font().plainSubstrByWidth(hint.getString(), l.hint.w());
			g.text(text, l.hint.x() + (l.hint.w() - Gfx.font().width(text)) / 2, l.hint.y() + 4, Theme.alpha(Theme.MUTED, in), false);
		}
	}

	private void button(Gfx g, Rect r, Component label, boolean hover, int accent, float in) {
		g.fill(r.x(), r.y(), r.right(), r.bottom(), Theme.alpha(hover ? Theme.PURPLE_DEEP : Theme.METAL_DARK, in));
		g.outline(r.x(), r.y(), r.w(), r.h(), Theme.alpha(hover ? accent : Theme.METAL, in));
		g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.y() + 2, Theme.alpha(Theme.METAL_LIGHT, 0.5F * in));
		String text = Gfx.font().plainSubstrByWidth(label.getString(), r.w() - 4);
		g.text(text, r.x() + (r.w() - Gfx.font().width(text)) / 2, r.y() + 4, Theme.alpha(hover ? Theme.TEXT : Theme.MUTED, in), true);
	}

	/** Page change: a totem sweeps across the list leaving a purple wake. */
	private void sweep(Gfx g, long now) {
		if (this.pageChangedMs < 0L) {
			return;
		}
		float t = (now - this.pageChangedMs) / (float)SWEEP_MS;
		if (t >= 1.0F) {
			this.pageChangedMs = -1L;
			return;
		}
		Rect list = this.layout.list;
		float e = Theme.easeInOut(t);
		int x = list.x() + Math.round((list.w() + 40) * e) - 20;
		int cy = list.y() + Math.min(list.h(), 120) / 2;
		g.nextStratum();
		g.hGradient(Math.max(list.x(), x - 90), list.y(), Math.min(list.right(), x), list.bottom(),
			0, Theme.alpha(Theme.PURPLE, 0.35F * (1.0F - t)));
		g.fill(Mth.clamp(x, list.x(), list.right()), list.y(), Mth.clamp(x + 1, list.x(), list.right()), list.bottom(),
			Theme.alpha(Theme.MAGENTA, 0.9F * (1.0F - t)));
		g.icon(Icons.TOTEM, x, cy, 22, t * 6.28F, Theme.alpha(0xFFFFFFFF, 1.0F - t * t));
	}

	/** First opening of the session: a totem spins in, bursts in rings, and the panel opens behind it. */
	private void entranceTotem(Gfx g, long now, float t) {
		float cx = this.width / 2.0F;
		float cy = this.height / 2.0F;
		float spin = Theme.easeOutCubic(Theme.clamp01(t / 0.45F));
		float out = Theme.clamp01((t - 0.45F) / 0.55F);
		float size = 20.0F + 60.0F * Theme.easeOutBack(Theme.clamp01(t / 0.4F)) + 40.0F * out;
		g.nextStratum();
		for (int i = 0; i < 3; i++) {
			float ring = Theme.clamp01((t - 0.25F - i * 0.08F) / 0.5F);
			if (ring > 0.0F && ring < 1.0F) {
				int r = Math.round(10 + Math.max(this.width, this.height) * 0.45F * Theme.easeOutCubic(ring));
				g.outline(Math.round(cx) - r, Math.round(cy) - r, r * 2, r * 2, Theme.alpha(i == 1 ? Theme.MAGENTA : Theme.PURPLE, (1.0F - ring) * 0.9F));
			}
		}
		float flash = Theme.clamp01(1.0F - Math.abs(t - 0.42F) / 0.12F);
		if (flash > 0.0F) {
			g.fill(0, 0, this.width, this.height, Theme.alpha(0xFFD9B8FF, 0.25F * flash));
		}
		g.icon(Icons.TOTEM, cx, cy, size, (1.0F - spin) * 9.42F, Theme.alpha(0xFFFFFFFF, 1.0F - out * out));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double mx = event.x();
		double my = event.y();
		AlertLayout l = this.layout;
		Row target = this.rowAt(mx, my);
		if (this.input != null && target != this.input) {
			this.input.focused = false;
		}
		if (l.edit.contains(mx, my)) {
			UiSound.sweep();
			Mc.setScreen(Minecraft.getInstance(), new AlertPositionScreen(this));
			return true;
		}
		for (int i = 0; i < AlertLayout.TABS; i++) {
			if (l.tab(i).contains(mx, my)) {
				this.switchPage(Page.values()[i]);
				UiSound.click();
				return true;
			}
		}
		if (l.done.contains(mx, my)) {
			UiSound.click();
			this.onClose();
			return true;
		}
		if (l.reset.contains(mx, my)) {
			UiSound.click();
			long now = Util.getMillis();
			if (this.resetArmedMs >= 0L && now - this.resetArmedMs < 3000L) {
				AlertTweaksConfig.reset();
				this.resetArmedMs = -1L;
				this.buildRows(true);
				this.pageChangedMs = now;
			} else {
				this.resetArmedMs = now;
			}
			return true;
		}
		if (target != null) {
			Row row = target;
			int y = l.list.y() + Math.round(row.y - this.scroll);
			if (row instanceof Rows.Link link) {
				UiSound.click();
				ConfirmLinkScreen.confirmLinkNow(this, URI.create(link.url()));
				return true;
			}
			if (row.click(mx, my, this.rowX(row), y, this.rowW(row))) {
				this.pressed = row;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The row under a point, from the same geometry the list is drawn with. */
	private Row rowAt(double mx, double my) {
		Rect list = this.layout.list;
		if (!list.contains(mx, my)) {
			return null;
		}
		for (Row row : this.rows) {
			if (row.appear < 0.9F || row instanceof Rows.Section || row instanceof Rows.Note || Float.isNaN(row.y)) {
				continue;
			}
			int y = list.y() + Math.round(row.y - this.scroll);
			int x = this.rowX(row);
			if (mx >= x && mx < x + this.rowW(row) && my >= y && my < y + row.height()) {
				return row;
			}
		}
		return null;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.pressed != null) {
			int y = this.layout.list.y() + Math.round(this.pressed.y - this.scroll);
			if (this.pressed instanceof Rows.Palette palette) {
				palette.dragAt(event.x(), event.y(), this.rowX(this.pressed), y, this.rowW(this.pressed));
			} else {
				this.pressed.drag(event.x(), this.rowX(this.pressed), this.rowW(this.pressed));
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.pressed != null) {
			this.pressed.release();
			this.pressed = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.layout.list.contains(mouseX, mouseY)) {
			this.scrollTarget -= (float)scrollY * 24.0F;
			return true;
		}
		if (this.layout.rail.contains(mouseX, mouseY)) {
			Page[] pages = Page.values();
			int next = Math.floorMod(this.page.ordinal() - (int)Math.signum(scrollY), pages.length);
			this.switchPage(pages[next]);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.input != null && this.input.focused) {
			if (event.isEscape()) {
				this.input.focused = false;
				return true;
			}
			if (event.isConfirmation()) {
				this.input.submitNow();
				return true;
			}
			if (event.key() == InputConstants.KEY_BACKSPACE) {
				this.input.backspace();
				return true;
			}
			if (event.isPaste()) {
				this.input.type(Minecraft.getInstance().keyboardHandler.getClipboard());
				return true;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (this.input != null && this.input.focused) {
			this.input.type(event.codepointAsString());
			return true;
		}
		return super.charTyped(event);
	}
}
