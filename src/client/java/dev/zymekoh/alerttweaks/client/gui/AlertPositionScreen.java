package dev.zymekoh.alerttweaks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.alerttweaks.client.Mc;
import dev.zymekoh.alerttweaks.client.alert.Alert;
import dev.zymekoh.alerttweaks.client.alert.AlertCards;
import dev.zymekoh.alerttweaks.client.alert.AlertFeed;
import dev.zymekoh.alerttweaks.client.alert.AlertPlacement;
import dev.zymekoh.alerttweaks.client.alert.AlertSamples;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.Rows.Row;
import dev.zymekoh.alerttweaks.client.hud.MiniHud;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * Where each alert appears, edited on the real screen at real size. In a world the player's own
 * screen stays visible behind; elsewhere the menu panorama and a PvP hotbar stand in for it.
 *
 * <p>Cards are dragged where they should appear; dropped on top of each other they share a place
 * and stack. Alerts never placed appear together at the default place, which moves with its grip.
 * A floating list holds every alert (placed ones dimmed, switched-off ones marked) to drag onto the
 * screen, plus the card settings that used to be the Style page.
 */
public final class AlertPositionScreen extends Screen {
	private static final Alert[] ALERTS = Alert.values();
	private static final int N = ALERTS.length;
	private static final int TITLE_BAR = 14;
	private static final int BOTTOM_BAR = 22;
	private static final float SNAP_PX = 6.0F;

	private final Screen parent;
	private final AlertSamples.Content[] contents = new AlertSamples.Content[N];
	private final int[] cardW = new int[N];
	/** Unscaled height of each alert in its current look. */
	private final int[] cardH = new int[N];
	private final float[] left = new float[N];
	private final float[] top = new float[N];
	private final boolean[] shown = new boolean[N];
	private final boolean[] grouped = new boolean[N];
	/** For each alert, the first alert of its stack; for each stack lead, how many did not fit. */
	private final int[] lead = new int[N];
	private final int[] overflow = new int[N];
	private final List<Row> rows = new ArrayList<>();

	// Snapshot for Cancel.
	private final float[] savedX = new float[N];
	private final float[] savedY = new float[N];
	private final boolean[] savedOn = new boolean[N];
	private final float savedDefaultX;
	private final float savedDefaultY;
	private final float savedScale;
	private final float savedSeconds;
	private final float savedVolume;
	private final boolean savedFlash;

	private float winX = Float.NaN;
	private float winY;
	private int winW;
	private boolean collapsed;
	private boolean movingWindow;
	/** The title bar was pressed; it becomes a move once the mouse travels, otherwise a fold. */
	private boolean titleMoved;
	private double titlePressX;
	private double titlePressY;
	private double grabX;
	private double grabY;
	private float scroll;
	private float scrollTarget;
	private Row pressedRow;
	private Row hoveredRow;

	/** Dragging: one alert (dragIndex) or a whole stack (dragLead, -2 for the default stack). */
	private int dragIndex = -1;
	private int dragLead = -1;
	/** The card whose width and grab point lead the drag (the first card of a dragged stack). */
	private int dragRef = -1;
	private boolean dragStarted;
	/** A drag from the list only counts once it has left the list. */
	private boolean dragLeftWindow;
	private boolean dragFromList;
	private double pressX;
	private double pressY;
	private float grabOffX;
	private float grabOffY;
	private float guideX = -1.0F;
	private float guideY = -1.0F;
	private int hoverCard = -1;
	private int highlight = -1;
	private long highlightUntil;
	private long testUntil = -1L;
	/** When the tutorial started (-1: not playing). It plays by itself the first time. */
	private long tutorialStart = -1L;
	private static final long TUTORIAL_MS = 9600L;
	private long lastFrameNanos;
	private final long openedMs = Util.getMillis();

	public AlertPositionScreen(Screen parent) {
		super(Component.translatable("alert_tweaks.editor.title"));
		this.parent = parent;
		AlertTweaksConfig c = AlertTweaksConfig.get();
		for (int i = 0; i < N; i++) {
			AlertTweaksConfig.AlertSetting s = c.alert(ALERTS[i]);
			this.savedX[i] = s.x;
			this.savedY[i] = s.y;
			this.savedOn[i] = s.enabled;
		}
		this.savedDefaultX = c.defaultX;
		this.savedDefaultY = c.defaultY;
		this.savedScale = c.toastScale;
		this.savedSeconds = c.toastSeconds;
		this.savedVolume = c.alertVolume;
		this.savedFlash = c.edgeFlash;
		MiniHud.shuffle();
	}

	@Override
	protected void init() {
		for (int i = 0; i < N; i++) {
			this.contents[i] = AlertSamples.content(ALERTS[i]);
			this.cardW[i] = AlertCards.widthOf(ALERTS[i], this.contents[i]);
			this.cardH[i] = AlertTweaksConfig.get().look(ALERTS[i]).height();
		}
		this.winW = Math.min(184, this.width - 16);
		if (Float.isNaN(this.winX)) {
			this.winX = this.width - this.winW - 8;
			this.winY = 8;
		}
		this.clampWindow();
		if (this.rows.isEmpty()) {
			this.buildRows();
			if (!AlertTweaksConfig.get().editorTutorialSeen) {
				this.tutorialStart = Util.getMillis() + 450L;
			}
		}
	}

	private static Component t(String key, Object... args) {
		return Component.translatable("alert_tweaks.editor." + key, args);
	}

	private void buildRows() {
		this.rows.clear();
		this.rows.add(new Rows.Section(Component.translatable("alert_tweaks.gui.section.cards")));
		this.rows.add(new Rows.Slider(Component.translatable("alert_tweaks.gui.card_size"), Component.translatable("alert_tweaks.gui.card_size.desc"),
			0.6, 1.6, 0.05, () -> AlertTweaksConfig.get().toastScale, v -> AlertTweaksConfig.get().toastScale = v.floatValue(),
			v -> Math.round(v * 100.0) + "%"));
		this.rows.add(new Rows.Slider(Component.translatable("alert_tweaks.gui.card_time"), Component.translatable("alert_tweaks.gui.card_time.desc"),
			1.5, 8, 0.5, () -> AlertTweaksConfig.get().toastSeconds, v -> AlertTweaksConfig.get().toastSeconds = v.floatValue(),
			v -> String.format(Locale.ROOT, "%.1f s", v)));
		this.rows.add(new Rows.Slider(Component.translatable("alert_tweaks.gui.alert_volume"),
			Component.translatable("alert_tweaks.gui.alert_volume.desc"), 0, 1, 0.05, () -> AlertTweaksConfig.get().alertVolume,
			v -> AlertTweaksConfig.get().alertVolume = v.floatValue(), v -> Math.round(v * 100.0) + "%"));
		this.rows.add(new Rows.Toggle(Component.translatable("alert_tweaks.gui.edge_flash"), Component.translatable("alert_tweaks.gui.edge_flash.desc"),
			null, () -> AlertTweaksConfig.get().edgeFlash, v -> AlertTweaksConfig.get().edgeFlash = v));
		this.rows.add(new Rows.Cycle(t("look_all"), t("look_all.desc"), null, () -> {
			var shared = sharedLook();
			return shared == null ? t("look_mixed") : shared.label();
		}, () -> {
			var shared = sharedLook();
			var next = shared == null ? dev.zymekoh.alerttweaks.client.alert.AlertLook.CARD : shared.next();
			for (Alert alert : ALERTS) {
				AlertTweaksConfig.get().alert(alert).look = next.name();
			}
		}));
		this.rows.add(new Rows.Action(t("test"), t("test.desc"), Icons.BELL, this::playAll));
		this.rows.add(new Rows.Action(t("reset"), t("reset.desc"), Icons.CLOCK, this::resetPositions));
		this.rows.add(new Rows.Section(t("alerts")));
		for (int i = 0; i < N; i++) {
			this.rows.add(new Chip(i));
		}
		this.rows.add(new Rows.Note(t("hint")));
		this.rows.add(new Rows.Note(t("hint2")));
		for (Row row : this.rows) {
			row.appear = 1.0F;
		}
	}

	/** Plays every switched-on alert at its place with its real animation. */
	public void playAll() {
		AlertFeed.PREVIEW.clear();
		boolean first = true;
		for (int i = 0; i < N; i++) {
			if (AlertTweaksConfig.get().enabled(ALERTS[i])) {
				AlertSamples.fire(AlertFeed.PREVIEW, ALERTS[i], first);
				first = false;
			}
		}
		this.testUntil = Util.getMillis() + AlertFeed.lifeMs() + 300L;
	}

	private void resetPositions() {
		AlertTweaksConfig c = AlertTweaksConfig.get();
		for (Alert alert : ALERTS) {
			c.alert(alert).x = -1.0F;
			c.alert(alert).y = -1.0F;
		}
		c.defaultX = 0.5F;
		c.defaultY = 0.58F;
		AlertTweaksConfig.save();
	}

	/** The look every alert has, or null when they differ. */
	private static dev.zymekoh.alerttweaks.client.alert.AlertLook sharedLook() {
		var first = AlertTweaksConfig.get().look(ALERTS[0]);
		for (Alert alert : ALERTS) {
			if (AlertTweaksConfig.get().look(alert) != first) {
				return null;
			}
		}
		return first;
	}

	@Override
	public void onClose() {
		AlertFeed.PREVIEW.clear();
		AlertTweaksConfig.save();
		Mc.setScreen(Minecraft.getInstance(), this.parent);
	}

	private void cancel() {
		AlertTweaksConfig c = AlertTweaksConfig.get();
		for (int i = 0; i < N; i++) {
			AlertTweaksConfig.AlertSetting s = c.alert(ALERTS[i]);
			s.x = this.savedX[i];
			s.y = this.savedY[i];
			s.enabled = this.savedOn[i];
		}
		c.defaultX = this.savedDefaultX;
		c.defaultY = this.savedDefaultY;
		c.toastScale = this.savedScale;
		c.toastSeconds = this.savedSeconds;
		c.alertVolume = this.savedVolume;
		c.edgeFlash = this.savedFlash;
		this.onClose();
	}

	// ------------------------------------------------------------------ layout of the cards

	private float place(int i, boolean x) {
		return x ? AlertCards.placeX(ALERTS[i]) : AlertCards.placeY(ALERTS[i]);
	}

	/** Lays every switched-on alert out at its place, stacked the way the HUD stacks them. */
	private void layoutCards() {
		AlertTweaksConfig c = AlertTweaksConfig.get();
		float s = c.toastScale;
		for (int i = 0; i < N; i++) {
			// Looks can change while the editor is open.
			this.cardW[i] = AlertCards.widthOf(ALERTS[i], this.contents[i]);
			this.cardH[i] = c.look(ALERTS[i]).height();
		}
		Arrays.fill(this.shown, false);
		Arrays.fill(this.grouped, false);
		Arrays.fill(this.lead, -1);
		Arrays.fill(this.overflow, 0);
		for (int i = 0; i < N; i++) {
			if (this.grouped[i] || !c.enabled(ALERTS[i])) {
				continue;
			}
			float px = this.place(i, true);
			float py = this.place(i, false);
			float ax = px * this.width;
			float ay = py * this.height;
			int dir = AlertPlacement.direction(py);
			int align = AlertPlacement.align(px);
			boolean overHotbar = Math.abs(ax - this.width / 2.0F) < AlertCards.HOTBAR_HALF + 60.0F * s;
			float floor = this.height - (overHotbar ? AlertCards.RESERVE_BOTTOM : 2.0F);
			float centre = ay;
			float previousH = 0.0F;
			int slot = 0;
			for (int pass = 0; pass < 2; pass++) {
				for (int j = i; j < N; j++) {
					if (this.grouped[j] || !c.enabled(ALERTS[j]) || this.place(j, true) != px || this.place(j, false) != py
						|| (ALERTS[j].tone == Alert.Tone.DANGER) != (pass == 0)) {
						continue;
					}
					this.grouped[j] = true;
					this.lead[j] = i;
					float h = this.cardH[j] * s;
					float c2 = slot == 0 ? ay : AlertCards.nextCentre(centre, dir, previousH, h, s);
					if (slot > 0 && !AlertCards.fits(c2, h, dir, 2.0F, floor)) {
						this.overflow[i]++;
						continue;
					}
					centre = c2;
					previousH = h;
					slot++;
					this.shown[j] = true;
					this.top[j] = c2 - h / 2.0F;
					this.left[j] = AlertCards.left(ax, align, this.cardW[j], s, 0.0F, this.width);
				}
			}
		}
	}

	private int cardAt(double mx, double my) {
		float s = AlertTweaksConfig.get().toastScale;
		for (int i = N - 1; i >= 0; i--) {
			if (this.shown[i] && i != this.dragIndex && mx >= this.left[i] && mx < this.left[i] + this.cardW[i] * s
				&& my >= this.top[i] && my < this.top[i] + this.cardH[i] * s) {
				return i;
			}
		}
		return -1;
	}

	/** The grip of a stack sits on the side the stack grows away from. */
	private float[] grip(int leadIndex) {
		float s = AlertTweaksConfig.get().toastScale;
		int first = -1;
		for (int i = 0; i < N; i++) {
			if (this.lead[i] == leadIndex && this.shown[i]) {
				first = i;
				break;
			}
		}
		if (first < 0) {
			return null;
		}
		int dir = AlertPlacement.direction(this.place(leadIndex, false));
		boolean isDefault = !AlertTweaksConfig.get().alert(ALERTS[leadIndex]).placed();
		float w = isDefault ? Math.max(44.0F, Gfx.font().width(t("default_zone")) + 12.0F) : 26.0F;
		float cx = this.left[first] + this.cardW[first] * s / 2.0F;
		float y = dir > 0 ? this.top[first] - 9.0F : this.top[first] + this.cardH[first] * s + 2.0F;
		return new float[]{cx - w / 2.0F, y, w, 7.0F};
	}

	// ------------------------------------------------------------------ window

	private float winH() {
		return this.collapsed ? TITLE_BAR : Math.min(this.height - 16, 330);
	}

	private void clampWindow() {
		this.winX = Mth.clamp(this.winX, 2.0F, this.width - this.winW - 2.0F);
		this.winY = Mth.clamp(this.winY, 2.0F, this.height - this.winH() - 2.0F);
	}

	private boolean overWindow(double mx, double my) {
		return mx >= this.winX && mx < this.winX + this.winW && my >= this.winY && my < this.winY + this.winH();
	}

	private int listTop() {
		return Math.round(this.winY) + TITLE_BAR + 2;
	}

	private int listBottom() {
		return Math.round(this.winY + this.winH()) - BOTTOM_BAR;
	}

	private int rowX(Row row) {
		return Math.round(this.winX) + 4;
	}

	private int rowW() {
		return this.winW - 10;
	}

	// ------------------------------------------------------------------ drawing

	//? if >=26.1 {
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (Minecraft.getInstance().level == null) {
			this.extractPanorama(graphics, delta);
		}
		this.draw(Gfx.of(graphics), mouseX, mouseY);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}
	//?} else {
	/*@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (Minecraft.getInstance().level == null) {
			this.renderPanorama(graphics, delta);
		}
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
		float in = Theme.easeOutCubic(Theme.clamp01((now - this.openedMs) / 350.0F));
		boolean world = Minecraft.getInstance().level != null;

		if (world) {
			g.fill(0, 0, this.width, this.height, Theme.alpha(0x30000000, in));
		} else {
			g.fill(0, 0, this.width, this.height, Theme.alpha(0x58000000, in));
			MiniHud.draw(g, this.width, this.height);
			MiniHud.crosshair(g, this.width, this.height);
		}
		boolean dragging = this.dragStarted && (this.dragIndex >= 0 || this.dragLead != -1);
		if (dragging) {
			this.grid(g);
		}

		this.layoutCards();
		this.hoverCard = this.overWindow(mouseX, mouseY) || dragging ? -1 : this.cardAt(mouseX, mouseY);
		float s = AlertTweaksConfig.get().toastScale;
		if (this.tutorialPlaying()) {
			// Only the example cards of the tutorial: the real ones would look like part of it.
			this.drawTutorial(g, now);
			return;
		}
		if (now < this.testUntil) {
			AlertCards.drawFlash(g, AlertFeed.PREVIEW, 0, 0, this.width, this.height);
			AlertCards.draw(g, AlertFeed.PREVIEW, 0.0F, 0.0F, this.width, this.height, s, 1.0F);
		} else {
			for (int i = 0; i < N; i++) {
				if (!this.shown[i]) {
					continue;
				}
				boolean lit = i == this.hoverCard || i == this.dragIndex || (i == this.highlight && now < this.highlightUntil);
				float alpha = in * (i == this.dragIndex ? 1.0F : this.dragIndex >= 0 ? 0.7F : 1.0F);
				AlertCards.drawStatic(g, ALERTS[i], this.contents[i], this.left[i], this.top[i], s, alpha, lit);
			}
			this.grips(g, mouseX, mouseY, in);
		}
		if (dragging) {
			this.guides(g);
		}
		this.window(g, mouseX, mouseY, dt, in);
		if (this.dragIndex >= 0 && this.dragStarted && this.overWindow(mouseX, mouseY) && (!this.dragFromList || this.dragLeftWindow)) {
			// Over the list: the card goes back to the default place on release.
			float x = mouseX - this.grabOffX;
			float y = mouseY - this.grabOffY;
			AlertCards.drawStatic(g, ALERTS[this.dragIndex], this.contents[this.dragIndex], x, y, s, 0.75F, true);
			g.text(t("drop_back"), Math.round(x), Math.round(y + this.cardH[this.dragIndex] * s + 3), Theme.MAGENTA, true);
		}
	}

	private void grid(Gfx g) {
		for (int k = 1; k < 8; k++) {
			int x = this.width * k / 8;
			int y = this.height * k / 8;
			g.fill(x, 0, x + 1, this.height, 0x12D45CFF);
			g.fill(0, y, this.width, y + 1, 0x12D45CFF);
		}
		g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x30D45CFF);
		g.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x30D45CFF);
	}

	private void guides(Gfx g) {
		if (this.guideX >= 0.0F) {
			int x = Math.round(this.guideX * this.width);
			g.fill(x, 0, x + 1, this.height, 0xC0D45CFF);
		}
		if (this.guideY >= 0.0F) {
			int y = Math.round(this.guideY * this.height);
			g.fill(0, y, this.width, y + 1, 0xC0D45CFF);
		}
	}

	private void grips(Gfx g, int mouseX, int mouseY, float in) {
		for (int i = 0; i < N; i++) {
			if (this.lead[i] != i) {
				continue;
			}
			float[] r = this.grip(i);
			if (r == null) {
				continue;
			}
			boolean over = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] - 2 && mouseY < r[1] + r[3] + 2;
			boolean isDefault = !AlertTweaksConfig.get().alert(ALERTS[i]).placed();
			this.drawGrip(g, r[0], r[1], r[2], isDefault, over, in);
			if (this.overflow[i] > 0) {
				String more = "+" + this.overflow[i];
				g.text(more, Math.round(r[0] + r[2] + 3), Math.round(r[1]) - 1, Theme.alpha(Theme.MAGENTA, in), true);
			}
		}
	}

	/** The handle of a stack: three dots, or DEFAULT for the stack of alerts without a place. */
	private void drawGrip(Gfx g, float gx, float gy, float gw, boolean isDefault, boolean over, float in) {
		int x = Math.round(gx);
		int y = Math.round(gy);
		g.fill(x, y, x + Math.round(gw), y + 7, Theme.alpha(over ? Theme.PURPLE : Theme.METAL_DARK, 0.95F * in));
		g.outline(x, y, Math.round(gw), 7, Theme.alpha(over ? Theme.MAGENTA : Theme.METAL_LIGHT, in));
		if (isDefault) {
			String label = t("default_zone").getString();
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x + gw / 2.0F, y + 1.5F);
			pose.scale(0.5F, 0.5F);
			g.text(label, -Gfx.font().width(label) / 2, 0, Theme.alpha(Theme.TEXT, in), false);
			pose.popMatrix();
		} else {
			for (int d = 0; d < 3; d++) {
				g.fill(x + Math.round(gw / 2.0F) - 5 + d * 4, y + 3, x + Math.round(gw / 2.0F) - 4 + d * 4, y + 4, Theme.alpha(Theme.TEXT, in));
			}
		}
	}

	// ------------------------------------------------------------------ tutorial

	private boolean tutorialPlaying() {
		return this.tutorialStart >= 0L;
	}

	private void endTutorial() {
		this.tutorialStart = -1L;
		if (!AlertTweaksConfig.get().editorTutorialSeen) {
			AlertTweaksConfig.get().editorTutorialSeen = true;
			AlertTweaksConfig.save();
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Progress of a move between two moments, eased, 0 before and 1 after. */
	private static float move(float t, float from, float to) {
		return Theme.easeInOut((t - from) / (to - from));
	}

	/**
	 * Under ten seconds, a hand shows the four moves on two example cards: place a card, drop a
	 * second one on it to stack them, move the stack by its grip, right-click a card to send it back.
	 */
	private void drawTutorial(Gfx g, long now) {
		float t = now - this.tutorialStart;
		if (t < 0.0F) {
			return;
		}
		if (t > TUTORIAL_MS) {
			this.endTutorial();
			return;
		}
		float a = Math.min(Theme.clamp01(t / 300.0F), Theme.clamp01((TUTORIAL_MS - t) / 400.0F));
		g.nextStratum();
		g.fill(0, 0, this.width, this.height, Theme.alpha(0x9C050208, a));

		AlertTweaksConfig c = AlertTweaksConfig.get();
		float s = c.toastScale;
		int ia = Alert.TOTEM_OFFHAND.ordinal();
		int ib = Alert.EFFECT_HARMFUL.ordinal();
		float wa = this.cardW[ia] * s;
		float ha = this.cardH[ia] * s;
		float wb = this.cardW[ib] * s;
		float hb = this.cardH[ib] * s;
		float gap = AlertCards.GAP * s;
		// Centres: the default stack, the first place, the second place.
		float homeX = 0.5F * this.width;
		float homeAY = 0.6F * this.height;
		float homeBY = homeAY + ha / 2.0F + gap + hb / 2.0F;
		float p1X = 0.27F * this.width;
		float p1Y = 0.36F * this.height;
		float p2X = 0.73F * this.width;
		float p2Y = 0.34F * this.height;
		float underA = ha / 2.0F + gap + hb / 2.0F;

		// Card A: dragged to the first place, later moved with the stack.
		float ax = homeX;
		float ay = homeAY;
		if (t >= 800.0F) {
			float m = move(t, 800.0F, 2100.0F);
			ax = lerp(homeX, p1X, m);
			ay = lerp(homeAY, p1Y, m);
		}
		if (t >= 5100.0F) {
			float m = move(t, 5100.0F, 6600.0F);
			ax = lerp(p1X, p2X, m);
			ay = lerp(p1Y, p2Y, m);
		}
		// Card B: dropped under A, rides with the stack, then sent back by a right click.
		float bx = homeX;
		float by = homeBY;
		if (t >= 3000.0F && t < 4200.0F) {
			float m = move(t, 3000.0F, 4200.0F);
			bx = lerp(homeX, p1X, m);
			by = lerp(homeBY, p1Y + underA, m);
		} else if (t >= 4200.0F && t < 7900.0F) {
			bx = ax;
			by = ay + underA;
		} else if (t >= 7900.0F) {
			float m = Theme.easeOutCubic(Theme.clamp01((t - 7900.0F) / 500.0F));
			bx = lerp(p2X, homeX, m);
			by = lerp(p2Y + underA, homeBY, m);
		}
		boolean stacked = t >= 4200.0F && t < 7900.0F;
		// Grip of the stack, above card A.
		float gripW = 26.0F;
		float gripX = ax - gripW / 2.0F;
		float gripY = ay - ha / 2.0F - 9.0F;

		// The hand's path.
		float startX = 0.86F * this.width;
		float startY = 0.86F * this.height;
		float hx;
		float hy;
		if (t < 800.0F) {
			float m = move(t, 200.0F, 800.0F);
			hx = lerp(startX, homeX, m);
			hy = lerp(startY, homeAY, m);
		} else if (t < 2200.0F) {
			hx = ax;
			hy = ay;
		} else if (t < 3000.0F) {
			float m = move(t, 2300.0F, 3000.0F);
			hx = lerp(p1X, homeX, m);
			hy = lerp(p1Y, homeBY, m);
		} else if (t < 4400.0F) {
			hx = bx;
			hy = by;
		} else if (t < 5100.0F) {
			float m = move(t, 4500.0F, 5100.0F);
			hx = lerp(p1X, ax, m);
			hy = lerp(p1Y + underA, gripY + 3.0F, m);
		} else if (t < 7000.0F) {
			hx = ax;
			hy = gripY + 3.0F;
		} else if (t < 7800.0F) {
			float m = move(t, 7100.0F, 7700.0F);
			hx = lerp(p2X, p2X, m);
			hy = lerp(gripY + 3.0F, p2Y + underA, m);
		} else {
			hx = p2X;
			hy = p2Y + underA;
		}
		boolean leftDown = (t >= 800.0F && t < 2100.0F) || (t >= 3000.0F && t < 4200.0F) || (t >= 5100.0F && t < 6600.0F);
		boolean rightDown = t >= 7800.0F && t < 8050.0F;

		float cardsAlpha = a;
		AlertCards.drawStatic(g, Alert.EFFECT_HARMFUL, this.contents[ib], bx - wb / 2.0F, by - hb / 2.0F, s, cardsAlpha, t >= 3000.0F && t < 4200.0F);
		AlertCards.drawStatic(g, Alert.TOTEM_OFFHAND, this.contents[ia], ax - wa / 2.0F, ay - ha / 2.0F, s, cardsAlpha,
			(t >= 800.0F && t < 2100.0F) || (t >= 5100.0F && t < 6600.0F));
		if (t < 800.0F || (t >= 7900.0F)) {
			this.drawGrip(g, homeX - 22.0F, homeAY - ha / 2.0F - 9.0F, 44.0F, true, false, a);
		}
		if (stacked) {
			boolean show = t >= 4400.0F && t < 5100.0F;
			if (show) {
				float pulse = 0.5F + 0.5F * Mth.sin(t / 70.0F);
				g.fill(Math.round(gripX) - 3, Math.round(gripY) - 3, Math.round(gripX + gripW) + 3, Math.round(gripY) + 10,
					Theme.alpha(Theme.MAGENTA, 0.35F * pulse * a));
			}
			this.drawGrip(g, gripX, gripY, gripW, false, t >= 5100.0F && t < 6600.0F, a);
		}
		this.drawHand(g, hx, hy, leftDown, rightDown, t, a);

		// Caption.
		int step = t < 2200.0F ? 1 : t < 4400.0F ? 2 : t < 7000.0F ? 3 : t < 8700.0F ? 4 : 5;
		Component caption = t("tut" + step);
		float scale = this.width >= 400 ? 1.3F : 1.0F;
		int tw = Gfx.font().width(caption);
		float boxW = tw * scale + 34.0F;
		float boxX = (this.width - boxW) / 2.0F;
		float boxY = 14.0F;
		int bx0 = Math.round(boxX);
		int by0 = Math.round(boxY);
		int bw0 = Math.round(boxW);
		int bh0 = Math.round(12 * scale + 12);
		g.fill(bx0, by0, bx0 + bw0, by0 + bh0, Theme.alpha(Theme.PANEL_HI, a));
		g.outline(bx0, by0, bw0, bh0, Theme.alpha(Theme.MAGENTA, a));
		if (step <= 4) {
			g.fill(bx0 + 5, by0 + bh0 / 2 - 6, bx0 + 17, by0 + bh0 / 2 + 6, Theme.alpha(Theme.MAGENTA, a));
			g.text(String.valueOf(step), bx0 + 9, by0 + bh0 / 2 - 4, Theme.alpha(0xFF14071F, a), false);
		}
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(bx0 + 24.0F, by0 + 6.0F);
		pose.scale(scale, scale);
		g.text(caption, 0, 0, Theme.alpha(Theme.TEXT, a), true);
		pose.popMatrix();
		float progress = t / TUTORIAL_MS;
		g.fill(bx0, by0 + bh0, bx0 + Math.round(bw0 * progress), by0 + bh0 + 1, Theme.alpha(Theme.MAGENTA, a));
		Component skip = t("tut_skip");
		g.text(skip, (this.width - Gfx.font().width(skip)) / 2, by0 + bh0 + 5, Theme.alpha(Theme.MUTED, a), true);
	}

	/** A pointer with a little mouse showing which button is held. */
	private void drawHand(Gfx g, float x, float y, boolean left, boolean right, float t, float a) {
		int px = Math.round(x);
		int py = Math.round(y);
		if (left || right) {
			float ring = (t % 500.0F) / 500.0F;
			int r = Math.round(4 + 10 * ring);
			g.outline(px - r, py - r, r * 2, r * 2, Theme.alpha(right ? Theme.CRIMSON : Theme.MAGENTA, (1.0F - ring) * a));
		}
		// Arrow pointer, drawn row by row: black edge, white body.
		int[] widths = {1, 2, 3, 4, 5, 6, 7, 8, 5, 3, 2};
		for (int row = 0; row < widths.length; row++) {
			g.fill(px - 1, py + row - 1, px + widths[row] + 1, py + row + 1, Theme.alpha(0xFF000000, a));
		}
		for (int row = 0; row < widths.length; row++) {
			g.fill(px, py + row, px + widths[row], py + row + 1, Theme.alpha(0xFFFFFFFF, a));
		}
		// The mouse: two buttons on top, the pressed one lit.
		int mx = px + 10;
		int my = py + 10;
		g.fill(mx, my, mx + 9, my + 12, Theme.alpha(0xFF14071F, a));
		g.outline(mx, my, 9, 12, Theme.alpha(Theme.METAL_LIGHT, a));
		g.fill(mx + 4, my, mx + 5, my + 5, Theme.alpha(Theme.METAL_LIGHT, a));
		if (left) {
			g.fill(mx + 1, my + 1, mx + 4, my + 5, Theme.alpha(Theme.MAGENTA, a));
		}
		if (right) {
			g.fill(mx + 5, my + 1, mx + 8, my + 5, Theme.alpha(Theme.CRIMSON, a));
		}
	}

	private void window(Gfx g, int mouseX, int mouseY, float dt, float in) {
		int x = Math.round(this.winX);
		int y = Math.round(this.winY);
		int w = this.winW;
		int h = Math.round(this.winH());
		g.nextStratum();
		g.fill(x, y, x + w, y + h, Theme.alpha(Theme.PANEL, 0.97F * in));
		g.outline(x - 1, y - 1, w + 2, h + 2, Theme.alpha(Theme.METAL_DARK, in));
		g.outline(x, y, w, h, Theme.alpha(Theme.METAL, in));
		g.hGradient(x + 1, y, x + w - 1, y + 1, Theme.alpha(Theme.METAL_LIGHT, 0.2F * in), Theme.alpha(Theme.METAL_LIGHT, 0.9F * in));
		// Title bar: grab to move, arrow to fold.
		g.fill(x + 1, y + 1, x + w - 1, y + TITLE_BAR, Theme.alpha(Theme.PANEL_HI, in));
		for (int d = 0; d < 3; d++) {
			g.fill(x + 5, y + 4 + d * 3, x + 9, y + 5 + d * 3, Theme.alpha(Theme.METAL_LIGHT, in));
		}
		g.text(this.title, x + 13, y + 3, Theme.alpha(Theme.TEXT, in), true);
		g.text(this.collapsed ? "▸" : "▾", x + w - 10, y + 3, Theme.alpha(Theme.MAGENTA, in), false);
		boolean overHelp = mouseX >= x + w - 26 && mouseX < x + w - 14 && mouseY >= y && mouseY < y + TITLE_BAR;
		g.fill(x + w - 25, y + 2, x + w - 15, y + 12, Theme.alpha(overHelp ? Theme.PURPLE : Theme.METAL_DARK, in));
		g.outline(x + w - 25, y + 2, 10, 10, Theme.alpha(overHelp ? Theme.MAGENTA : Theme.METAL_LIGHT, in));
		g.text("?", x + w - 22, y + 3, Theme.alpha(Theme.TEXT, in), false);
		if (this.collapsed) {
			return;
		}

		int top = this.listTop();
		int bottom = this.listBottom();
		float content = 2.0F;
		for (Row row : this.rows) {
			row.y = content;
			content += row.height() + 3;
		}
		float maxScroll = Math.max(0.0F, content - (bottom - top));
		this.scrollTarget = Mth.clamp(this.scrollTarget, 0.0F, maxScroll);
		this.scroll += (this.scrollTarget - this.scroll) * Theme.approach(dt, 60.0F);
		this.hoveredRow = null;
		g.scissor(x + 1, top, x + w - 1, bottom);
		for (Row row : this.rows) {
			int ry = top + Math.round(row.y - this.scroll);
			if (ry > bottom || ry + row.height() < top) {
				continue;
			}
			int rx = this.rowX(row);
			int rw = this.rowW();
			boolean hover = mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + row.height() && mouseY >= top && mouseY < bottom
				&& !(row instanceof Rows.Section) && !(row instanceof Rows.Note) && this.dragIndex < 0;
			if (hover) {
				this.hoveredRow = row;
			}
			row.hover += ((hover ? 1.0F : 0.0F) - row.hover) * Theme.approach(dt, 45.0F);
			row.draw(g, rx, ry, rw, in, hover, mouseX);
		}
		g.endScissor();
		if (maxScroll > 0.0F) {
			int trackX = x + w - 4;
			g.fill(trackX, top, trackX + 2, bottom, Theme.alpha(Theme.METAL_DARK, in));
			int thumbH = Math.max(10, Math.round((bottom - top) * (bottom - top) / content));
			int thumbY = top + Math.round((bottom - top - thumbH) * (this.scroll / maxScroll));
			g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Theme.alpha(Theme.PURPLE, in));
		}
		// Done and Cancel.
		int by = bottom + 3;
		int half = (w - 12) / 2;
		this.button(g, x + 4, by, half, t("done"), mouseX, mouseY, Theme.MAGENTA, in);
		this.button(g, x + 8 + half, by, half, t("cancel"), mouseX, mouseY, Theme.CRIMSON, in);
	}

	private void button(Gfx g, int x, int y, int w, Component label, int mouseX, int mouseY, int accent, float in) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
		g.fill(x, y, x + w, y + 16, Theme.alpha(hover ? Theme.PURPLE_DEEP : Theme.METAL_DARK, in));
		g.outline(x, y, w, 16, Theme.alpha(hover ? accent : Theme.METAL, in));
		String text = Gfx.font().plainSubstrByWidth(label.getString(), w - 4);
		g.text(text, x + (w - Gfx.font().width(text)) / 2, y + 4, Theme.alpha(hover ? Theme.TEXT : Theme.MUTED, in), true);
	}

	// ------------------------------------------------------------------ dragging

	/** Moves the dragged card or stack so its card sits under the mouse; snaps to centres, the crosshair and other stacks. */
	private void dragTo(double mx, double my) {
		AlertTweaksConfig c = AlertTweaksConfig.get();
		float s = c.toastScale;
		float h = this.cardH[Math.max(0, this.dragRef)] * s;
		float w = this.cardW[Math.max(0, this.dragRef)] * s;
		float cardLeft = (float)mx - this.grabOffX;
		float cardTop = (float)my - this.grabOffY;
		float nx = AlertPlacement.storeX(cardLeft, w, this.width);
		float ny = AlertPlacement.storeY(cardTop + h / 2.0F, this.height);
		this.guideX = -1.0F;
		this.guideY = -1.0F;
		float bestX = SNAP_PX;
		float bestY = SNAP_PX;
		float[] xs = {0.5F};
		float gapY = (14.0F * s + h / 2.0F) / this.height;
		float[] ys = {0.5F + gapY, 0.5F - gapY, (this.height - AlertCards.RESERVE_BOTTOM - h / 2.0F) / this.height, (6.0F + h / 2.0F) / this.height};
		for (float tx : xs) {
			float d = Math.abs(tx - nx) * this.width;
			if (d < bestX) {
				bestX = d;
				nx = tx;
				this.guideX = tx;
			}
		}
		for (float ty : ys) {
			float d = Math.abs(ty - ny) * this.height;
			if (d < bestY) {
				bestY = d;
				ny = ty;
				this.guideY = ty;
			}
		}
		// Other stacks: line up with them, or join one by dropping right on it.
		for (int i = 0; i < N; i++) {
			if (this.lead[i] != i || i == this.dragIndex || this.isDragged(i)) {
				continue;
			}
			float ox = this.place(i, true);
			float oy = this.place(i, false);
			if (Math.abs(ox - nx) * this.width < bestX) {
				bestX = Math.abs(ox - nx) * this.width;
				nx = ox;
				this.guideX = ox;
			}
			if (Math.abs(oy - ny) * this.height < bestY) {
				bestY = Math.abs(oy - ny) * this.height;
				ny = oy;
				this.guideY = oy;
			}
		}
		if (this.dragIndex >= 0) {
			AlertTweaksConfig.AlertSetting setting = c.alert(ALERTS[this.dragIndex]);
			setting.x = nx;
			setting.y = ny;
			setting.enabled = true;
		} else if (this.dragLead == -2) {
			c.defaultX = nx;
			c.defaultY = ny;
		} else if (this.dragLead >= 0) {
			float ox = this.place(this.dragLead, true);
			float oy = this.place(this.dragLead, false);
			for (Alert alert : ALERTS) {
				AlertTweaksConfig.AlertSetting setting = c.alert(alert);
				if (setting.placed() && setting.x == ox && setting.y == oy) {
					setting.x = nx;
					setting.y = ny;
				}
			}
		}
	}

	/** Whether the stack led by {@code leadIndex} is the one being dragged. */
	private boolean isDragged(int leadIndex) {
		if (this.dragLead == -2) {
			return !AlertTweaksConfig.get().alert(ALERTS[leadIndex]).placed();
		}
		return this.dragLead >= 0 && leadIndex == this.dragLead;
	}

	private void unplace(int i) {
		AlertTweaksConfig.AlertSetting setting = AlertTweaksConfig.get().alert(ALERTS[i]);
		setting.x = -1.0F;
		setting.y = -1.0F;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		if (this.tutorialPlaying()) {
			this.endTutorial();
			return true;
		}
		boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
		boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (this.overWindow(mx, my)) {
			if (!left) {
				return true;
			}
			if (my < this.winY + TITLE_BAR) {
				if (mx >= this.winX + this.winW - 26 && mx < this.winX + this.winW - 14) {
					this.tutorialStart = Util.getMillis();
					UiSound.sweep();
					return true;
				}
				this.movingWindow = true;
				this.titleMoved = false;
				this.titlePressX = mx;
				this.titlePressY = my;
				this.grabX = mx - this.winX;
				this.grabY = my - this.winY;
				return true;
			}
			int by = this.listBottom() + 3;
			if (my >= by && my < by + 16) {
				int half = (this.winW - 12) / 2;
				if (mx < this.winX + 4 + half) {
					UiSound.click();
					this.onClose();
				} else {
					UiSound.click();
					this.cancel();
				}
				return true;
			}
			Row target = this.rowAt(mx, my);
			if (target instanceof Chip chip && !chip.onSwitch(mx)) {
				// Start dragging this alert out of the list.
				this.dragIndex = chip.index;
				this.dragRef = chip.index;
				this.dragFromList = true;
				this.dragLeftWindow = false;
				this.dragStarted = false;
				this.pressX = mx;
				this.pressY = my;
				float s = AlertTweaksConfig.get().toastScale;
				this.grabOffX = this.cardW[chip.index] * s / 2.0F;
				this.grabOffY = this.cardH[chip.index] * s / 2.0F;
				this.highlight = chip.index;
				this.highlightUntil = Util.getMillis() + 1200L;
				return true;
			}
			if (target != null) {
				int ry = this.listTop() + Math.round(target.y - this.scroll);
				if (target.click(mx, my, this.rowX(target), ry, this.rowW())) {
					this.pressedRow = target;
				}
			}
			return true;
		}
		if (right) {
			int i = this.cardAt(mx, my);
			if (i >= 0 && AlertTweaksConfig.get().alert(ALERTS[i]).placed()) {
				this.unplace(i);
				AlertTweaksConfig.save();
				UiSound.click();
			}
			return true;
		}
		if (!left) {
			return true;
		}
		for (int i = 0; i < N; i++) {
			if (this.lead[i] != i) {
				continue;
			}
			float[] r = this.grip(i);
			if (r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] - 2 && my < r[1] + r[3] + 2) {
				boolean isDefault = !AlertTweaksConfig.get().alert(ALERTS[i]).placed();
				this.dragLead = isDefault ? -2 : i;
				int first = this.firstShown(i);
				this.dragRef = first;
				this.dragFromList = false;
				this.grabOffX = (float)mx - this.left[first];
				this.grabOffY = (float)my - this.top[first];
				this.dragStarted = true;
				UiSound.click();
				return true;
			}
		}
		int i = this.cardAt(mx, my);
		if (i >= 0) {
			this.dragIndex = i;
			this.dragRef = i;
			this.dragFromList = false;
			this.dragStarted = false;
			this.pressX = mx;
			this.pressY = my;
			this.grabOffX = (float)mx - this.left[i];
			this.grabOffY = (float)my - this.top[i];
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** The list row under a point inside the window. */
	private Row rowAt(double mx, double my) {
		int top = this.listTop();
		int bottom = this.listBottom();
		if (this.collapsed || my < top || my >= bottom) {
			return null;
		}
		for (Row row : this.rows) {
			if (row instanceof Rows.Section || row instanceof Rows.Note) {
				continue;
			}
			int ry = top + Math.round(row.y - this.scroll);
			int rx = this.rowX(row);
			if (mx >= rx && mx < rx + this.rowW() && my >= ry && my < ry + row.height()) {
				return row;
			}
		}
		return null;
	}

	private int firstShown(int leadIndex) {
		for (int i = 0; i < N; i++) {
			if (this.lead[i] == leadIndex && this.shown[i]) {
				return i;
			}
		}
		return leadIndex;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		double mx = event.x();
		double my = event.y();
		if (this.movingWindow) {
			if (!this.titleMoved && Math.abs(mx - this.titlePressX) + Math.abs(my - this.titlePressY) > 3.0) {
				this.titleMoved = true;
			}
			if (this.titleMoved) {
				this.winX = (float)(mx - this.grabX);
				this.winY = (float)(my - this.grabY);
				this.clampWindow();
			}
			return true;
		}
		if (this.pressedRow != null) {
			this.pressedRow.drag(mx, this.rowX(this.pressedRow), this.rowW());
			return true;
		}
		if (this.dragIndex >= 0 || this.dragLead != -1) {
			if (!this.dragStarted && Math.abs(mx - this.pressX) + Math.abs(my - this.pressY) > 3.0) {
				this.dragStarted = true;
			}
			boolean overList = this.dragIndex >= 0 && this.overWindow(mx, my);
			if (this.dragStarted && !overList) {
				this.dragLeftWindow = true;
				this.dragTo(mx, my);
			}
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.movingWindow) {
			this.movingWindow = false;
			if (!this.titleMoved) {
				this.collapsed = !this.collapsed;
				this.clampWindow();
				UiSound.click();
			}
			return true;
		}
		if (this.pressedRow != null) {
			this.pressedRow.release();
			this.pressedRow = null;
			return true;
		}
		if (this.dragIndex >= 0 || this.dragLead != -1) {
			boolean backToList = this.dragStarted && this.dragIndex >= 0 && this.overWindow(event.x(), event.y())
				&& (!this.dragFromList || this.dragLeftWindow);
			if (backToList) {
				this.unplace(this.dragIndex);
			}
			if (this.dragStarted) {
				UiSound.click();
			}
			AlertTweaksConfig.save();
			this.dragIndex = -1;
			this.dragLead = -1;
			this.dragRef = -1;
			this.dragStarted = false;
			this.dragFromList = false;
			this.dragLeftWindow = false;
			this.guideX = -1.0F;
			this.guideY = -1.0F;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.overWindow(mouseX, mouseY)) {
			this.scrollTarget -= (float)scrollY * 24.0F;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (this.tutorialPlaying()) {
			this.endTutorial();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	/** One alert in the list: drag it onto the screen; dimmed once placed, marked when switched off. */
	private final class Chip extends Row {
		final int index;
		private float knob = Float.NaN;

		Chip(int index) {
			super(ALERTS[index].title(), ALERTS[index].description(), ALERTS[index].icon);
			this.index = index;
		}

		boolean onSwitch(double mx) {
			return mx >= AlertPositionScreen.this.winX + 4 + AlertPositionScreen.this.rowW() - 26;
		}

		@Override
		public int height() {
			return 18;
		}

		@Override
		public void draw(Gfx g, int x, int y, int w, float alpha, boolean hovered, double mouseX) {
			AlertTweaksConfig.AlertSetting setting = AlertTweaksConfig.get().alert(ALERTS[this.index]);
			boolean on = setting.enabled;
			boolean placed = setting.placed();
			float dim = !on ? 0.4F : placed ? 0.6F : 1.0F;
			float a = alpha * dim;
			this.plate(g, x, y, w, 18, alpha);
			AlertSamples.Content content = AlertPositionScreen.this.contents[this.index];
			if (content.face() != null) {
				g.face(content.face().get(), x + 4, y + 1, 16, a);
			} else {
				g.icon(content.icon(), x + 12, y + 9, 13, 0.0F, Theme.alpha(0xFFFFFFFF, a));
			}
			Component status = !on ? t("off") : placed ? t("placed") : t("default");
			int statusW = Gfx.font().width(status);
			String name = Gfx.font().plainSubstrByWidth(this.label.getString(), w - 52 - statusW);
			g.text(name, x + 22, y + 5, Theme.alpha(Theme.TEXT, a), true);
			int sc = !on ? Theme.CRIMSON : placed ? Theme.MINT : Theme.MUTED;
			g.text(status, x + w - 30 - statusW, y + 5, Theme.alpha(sc, Math.max(a, alpha * 0.7F)), false);
			float target = on ? 1.0F : 0.0F;
			this.knob = Float.isNaN(this.knob) ? target : this.knob + (target - this.knob) * Theme.approach(Rows.frameDt, 45.0F);
			int sx = x + w - 25;
			int sy = y + 5;
			g.fill(sx, sy, sx + 20, sy + 8, Theme.alpha(Theme.METAL_DARK, alpha));
			g.outline(sx, sy, 20, 8, Theme.alpha(Theme.mix(Theme.METAL, Theme.MAGENTA, this.knob), alpha));
			int kx = sx + 1 + Math.round(12 * this.knob);
			g.fill(kx, sy + 1, kx + 7, sy + 7, Theme.alpha(Theme.mix(Theme.METAL, Theme.METAL_LIGHT, 0.4F + 0.6F * this.knob), alpha));
		}

		@Override
		public boolean click(double mx, double my, int x, int y, int w) {
			if (this.onSwitch(mx)) {
				AlertTweaksConfig.AlertSetting setting = AlertTweaksConfig.get().alert(ALERTS[this.index]);
				setting.enabled = !setting.enabled;
				AlertTweaksConfig.save();
				UiSound.click();
			}
			return false;
		}
	}
}
