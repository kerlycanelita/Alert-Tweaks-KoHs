package dev.zymekoh.alerttweaks.client.gui;

import dev.zymekoh.alerttweaks.AlertTweaks;
import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.hud.HeartbeatSound;
import dev.zymekoh.alerttweaks.client.threat.ThreatIndicatorRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class AlertTweaksConfigScreen extends Screen {
	static final int PANEL_BACKGROUND = argb(218, 17, 6, 30);
	static final int PANEL_BORDER = argb(190, 142, 75, 220);
	static final int PANEL_SOFT = argb(112, 66, 22, 104);
	static final int PANEL_STRONG = argb(160, 92, 35, 148);
	static final int FOOTER_BACKGROUND = argb(214, 20, 7, 34);
	static final int TEXT_MAIN = argb(255, 243, 236, 255);
	static final int TEXT_MUTED = argb(255, 199, 187, 217);
	static final int TEXT_ACCENT = argb(255, 231, 176, 255);

	private static final int CONTROL_HEIGHT = 22;
	private static final int SLIDER_HEIGHT = 30;
	private static final int PALETTE_HEIGHT = 52;
	private static final int CONTROL_STEP = 27;
	private static final int SLIDER_STEP = 35;
	private static final int PALETTE_STEP = 57;
	private static final int BRANCH_INDENT = 14;

	private static final int PREVIEW_FRAME_COUNT = 32;
	private static final int PREVIEW_TEXTURE_WIDTH = 256;
	private static final int PREVIEW_TEXTURE_HEIGHT = 144;
	private static final long PREVIEW_FRAME_MILLIS = 70L;
	private static final float PREVIEW_VIGNETTE_STRENGTH = 0.58F;

	private static Boolean previewFramesAvailable;

	private final Screen parent;
	private final List<DescribedControl> describedControls = new ArrayList<>();
	private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
	private final List<AbstractWidget> scrollableWidgets = new ArrayList<>();

	private Tab selectedTab = Tab.TWEAKS;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int tabWidth;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int previewX;
	private int previewY;
	private int previewWidth;
	private int previewHeight;
	private int previewNoteX;
	private int previewNoteY;
	private int previewNoteWidth;
	private int previewNoteHeight;
	private int descriptionFooterX;
	private int descriptionFooterY;
	private int descriptionFooterWidth;
	private int descriptionFooterHeight;
	private int contentScrollOffset;
	private int maxContentScroll;

	public AlertTweaksConfigScreen(Screen parent) {
		super(Component.literal("Alert Tweaks KoHs"));
		this.parent = parent;
	}

	static Font font() {
		return Minecraft.getInstance().font;
	}

	static int argb(int alpha, int red, int green, int blue) {
		return Mth.clamp(alpha, 0, 255) << 24
			| Mth.clamp(red, 0, 255) << 16
			| Mth.clamp(green, 0, 255) << 8
			| Mth.clamp(blue, 0, 255);
	}

	@Override
	protected void init() {
		this.describedControls.clear();
		this.fixedWidgets.clear();
		this.scrollableWidgets.clear();
		this.calculateLayout();
		this.addTabs();
		if (this.selectedTab == Tab.TWEAKS) {
			this.addTweaksTab();
		} else {
			this.addSoundTab();
			this.addSoundPreviewControls();
		}

		int buttonY = this.panelY + this.panelHeight - 24;
		this.addFixedChild(new ThemeButton(this.contentX, buttonY, 94, 20, Component.literal("Reset"),
			"Restores every option to its default value.", button -> {
				AlertTweaksConfig.get().reset();
				AlertTweaksConfig.save();
				this.rebuildWidgets();
			}));
		this.addFixedChild(new ThemeButton(this.panelX + this.panelWidth - 106, buttonY, 94, 20, Component.literal("Done"),
			"Saves the current settings and returns to the previous screen.", button -> this.onClose()));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.drawScreenBackground(graphics);
		this.drawPanel(graphics);
		this.drawHeader(graphics);
		this.drawPreview(graphics);

		graphics.enableScissor(this.contentX, this.contentY, this.contentX + this.contentWidth + 16, this.descriptionFooterY - 8);
		for (AbstractWidget widget : this.scrollableWidgets) {
			widget.render(graphics, mouseX, mouseY, delta);
		}
		graphics.disableScissor();

		for (AbstractWidget widget : this.fixedWidgets) {
			widget.render(graphics, mouseX, mouseY, delta);
		}

		this.drawScrollHints(graphics);
		this.drawDescriptionFooter(graphics, mouseX, mouseY);
	}

	private void drawScreenBackground(GuiGraphics graphics) {
		graphics.fill(0, 0, this.width, this.height, argb(92, 10, 4, 18));
		graphics.fillGradient(0, 0, this.width, this.height, argb(88, 52, 15, 82), argb(132, 12, 3, 25));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		boolean insidePanel = mouseX >= this.panelX && mouseX <= this.panelX + this.panelWidth
			&& mouseY >= this.panelY && mouseY <= this.descriptionFooterY;
		if (this.maxContentScroll > 0 && insidePanel) {
			int previous = this.contentScrollOffset;
			this.contentScrollOffset = Mth.clamp(this.contentScrollOffset - (int)Math.round(scrollY * 27.0), 0, this.maxContentScroll);
			if (this.contentScrollOffset != previous) {
				this.rebuildWidgets();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		HeartbeatSound.stopPreview();
		AlertTweaksConfig.save();
		Minecraft.getInstance().setScreen(this.parent);
	}

	// ---------------------------------------------------------------- layout

	private void calculateLayout() {
		this.panelWidth = Math.min(this.width - 8, 760);
		this.panelHeight = Math.min(this.height - 8, 360);
		if (this.panelWidth < 460) {
			this.panelWidth = Math.max(320, this.width - 8);
		}
		if (this.panelHeight < 260) {
			this.panelHeight = Math.max(220, this.height - 8);
		}
		this.panelX = (this.width - this.panelWidth) / 2;
		this.panelY = (this.height - this.panelHeight) / 2;
		this.tabWidth = 92;
		this.previewWidth = Mth.clamp(this.panelWidth / 3, 122, 220);
		this.previewX = this.panelX + this.panelWidth - this.previewWidth - 12;
		this.previewY = this.panelY + 42;
		this.descriptionFooterX = this.panelX + 12;
		this.descriptionFooterY = this.panelY + this.panelHeight - 58;
		this.descriptionFooterWidth = this.panelWidth - 24;
		this.descriptionFooterHeight = 28;
		this.previewHeight = Math.min(Mth.clamp(Math.round(this.previewWidth * 9.0F / 16.0F), 70, 124),
			this.descriptionFooterY - this.previewY - 8);
		this.previewNoteX = this.previewX;
		this.previewNoteY = this.previewY + this.previewHeight + 7;
		this.previewNoteWidth = this.previewWidth;
		this.previewNoteHeight = Math.max(0, this.descriptionFooterY - this.previewNoteY - 8);
		this.contentX = this.panelX + this.tabWidth + 16;
		this.contentY = this.panelY + 43;
		this.contentWidth = Math.max(136, this.previewX - this.contentX - 12);
		this.maxContentScroll = Math.max(0, this.getCurrentContentHeight() - Math.max(0, this.descriptionFooterY - this.contentY - 8));
		this.contentScrollOffset = Mth.clamp(this.contentScrollOffset, 0, this.maxContentScroll);
	}

	private void addTabs() {
		int tabX = this.panelX + 12;
		int tabY = this.panelY + 43;
		for (Tab tab : Tab.values()) {
			this.addFixedChild(new TabButton(tabX, tabY, this.tabWidth - 18, CONTROL_HEIGHT, tab, tab == this.selectedTab, button -> {
				this.selectedTab = tab;
				this.contentScrollOffset = 0;
				this.rebuildWidgets();
			}));
			tabY += 30;
		}
	}

	// ---------------------------------------------------------------- tweaks tab

	private void addTweaksTab() {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		int y = this.contentY - this.contentScrollOffset;

		this.addScrollableChild(new ToggleButton(this.contentX, y, this.contentWidth, CONTROL_HEIGHT,
			"Low Health Vignette",
			"Shows a colored screen fade when your health drops below the selected threshold.",
			() -> config.vignetteEnabled,
			value -> {
				config.vignetteEnabled = value;
				AlertTweaksConfig.save();
				this.rebuildWidgets();
			}));
		y += CONTROL_STEP;

		if (config.vignetteEnabled) {
			this.addScrollableChild(new ToggleButton(this.contentX, y, this.contentWidth, CONTROL_HEIGHT,
				"Smooth Fade",
				"Animates the vignette in and out instead of snapping instantly.",
				() -> config.smoothVignetteEnabled,
				value -> {
					config.smoothVignetteEnabled = value;
					AlertTweaksConfig.save();
				}));
			y += CONTROL_STEP;

			this.addScrollableChild(new ValueSlider(this.contentX, y, this.contentWidth, SLIDER_HEIGHT,
				"Health",
				"Health points where the vignette starts. One heart equals 2 health points.",
				1.0, 20.0, 1.0, config.thresholdHealthPoints,
				value -> config.thresholdHealthPoints = (float)value,
				ValueSlider::formatHealth));
			y += SLIDER_STEP;

			this.addScrollableChild(new ColorPaletteWidget(this.contentX, y, this.contentWidth, PALETTE_HEIGHT,
				"Color", "Pick the vignette color from the palette. Drag inside the bar to adjust it."));
			y += PALETTE_STEP;

			this.addScrollableChild(new ToggleButton(this.contentX, y, this.contentWidth, CONTROL_HEIGHT,
				"Static Color",
				"Keeps the vignette intensity fixed while your health is under the threshold.",
				() -> config.staticColorEnabled,
				value -> {
					config.staticColorEnabled = value;
					AlertTweaksConfig.save();
					this.rebuildWidgets();
				}));
			y += CONTROL_STEP;

			if (config.staticColorEnabled) {
				this.addScrollableChild(new BranchValueSlider(this.contentX + BRANCH_INDENT, y, this.contentWidth - BRANCH_INDENT,
					SLIDER_HEIGHT,
					"Intensity",
					"Fixed vignette intensity used while Static Color is enabled.",
					0.05, 1.0, 0.05, config.staticColorIntensity,
					value -> config.staticColorIntensity = (float)value,
					ValueSlider::formatPercent));
				y += SLIDER_STEP;
			}

			this.addScrollableChild(new ToggleButton(this.contentX, y, this.contentWidth, CONTROL_HEIGHT,
				"Heartbeat Pulse",
				"Makes the vignette pulse like a heartbeat. With low-health linking, the pulse gets faster and stronger.",
				() -> config.heartbeatEnabled,
				value -> {
					config.heartbeatEnabled = value;
					AlertTweaksConfig.save();
					this.rebuildWidgets();
				}));
			y += CONTROL_STEP;

			if (config.heartbeatEnabled) {
				this.addScrollableChild(new BranchToggleButton(this.contentX + BRANCH_INDENT, y, this.contentWidth - BRANCH_INDENT,
					CONTROL_HEIGHT,
					"Link to Low Health",
					"When enabled, the pulse follows your health level; when off, it uses a steady heartbeat.",
					() -> config.heartbeatScaleWithLowHealth,
					value -> {
						config.heartbeatScaleWithLowHealth = value;
						AlertTweaksConfig.save();
					}));
				y += CONTROL_STEP;
			}

			this.addScrollableChild(new ValueSlider(this.contentX, y, this.contentWidth, SLIDER_HEIGHT,
				"Opacity",
				"Maximum color strength. Low health decides how close the vignette gets to this value.",
				0.0, 255.0, 1.0, config.vignetteAlpha,
				value -> config.vignetteAlpha = (int)Math.round(value),
				ValueSlider::formatByte));
			y += SLIDER_STEP;

			this.addScrollableChild(new ValueSlider(this.contentX, y, this.contentWidth, SLIDER_HEIGHT,
				"Size",
				"Controls how far the vignette reaches toward the center of the screen.",
				0.45, 1.75, 0.05, config.vignetteSize,
				value -> config.vignetteSize = (float)value,
				ValueSlider::formatPercent));
			y += SLIDER_STEP;
		}

		this.addScrollableChild(new ToggleButton(this.contentX, y, this.contentWidth, CONTROL_HEIGHT,
			"Threat Indicator",
			"Points an arrow at whoever just hit you, and fades it out five seconds after the hit.",
			() -> config.threatIndicatorEnabled,
			value -> {
				config.threatIndicatorEnabled = value;
				AlertTweaksConfig.save();
				this.rebuildWidgets();
			}));
		y += CONTROL_STEP;

		if (config.threatIndicatorEnabled) {
			this.addScrollableChild(new BranchToggleButton(this.contentX + BRANCH_INDENT, y, this.contentWidth - BRANCH_INDENT,
				CONTROL_HEIGHT,
				"Adaptive Size",
				"Keeps the arrows the same size on screen whatever your GUI scale is. Moving Scale turns this off.",
				() -> config.threatAdaptiveScale,
				value -> {
					config.threatAdaptiveScale = value;
					AlertTweaksConfig.save();
				}));
			y += CONTROL_STEP;

			this.addScrollableChild(new BranchValueSlider(this.contentX + BRANCH_INDENT, y, this.contentWidth - BRANCH_INDENT,
				SLIDER_HEIGHT,
				"Scale",
				"Size of the arrows. Using this slider switches Adaptive Size off.",
				0.5, 2.5, 0.05, config.threatScale,
				value -> {
					config.threatScale = (float)value;
					// Touching the slider is what turns the adaptive default off, as asked.
					config.threatAdaptiveScale = false;
				},
				ValueSlider::formatPercent));
		}
	}

	// ---------------------------------------------------------------- sound tab

	private void addSoundTab() {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		int y = this.contentY - this.contentScrollOffset;

		this.addScrollableChild(new ToggleButton(this.contentX, y, this.contentWidth, CONTROL_HEIGHT,
			"Heartbeat Sound",
			"Plays the heartbeat sound when your health drops below the selected threshold.",
			() -> config.heartbeatSoundEnabled,
			value -> {
				config.heartbeatSoundEnabled = value;
				AlertTweaksConfig.save();
				this.rebuildWidgets();
			}));
		y += CONTROL_STEP;

		if (config.heartbeatSoundEnabled) {
			this.addScrollableChild(new BranchValueSlider(this.contentX + BRANCH_INDENT, y, this.contentWidth - BRANCH_INDENT,
				SLIDER_HEIGHT,
				"Volume", "Base volume for the heartbeat sound.",
				0.05, 1.0, 0.05, config.heartbeatSoundVolume,
				value -> config.heartbeatSoundVolume = (float)value,
				ValueSlider::formatPercent));
			y += SLIDER_STEP;

			this.addScrollableChild(new ValueSlider(this.contentX, y, this.contentWidth, SLIDER_HEIGHT,
				"Heartbeat Intensity", "Controls how fast the heartbeat sound repeats.",
				0.5, 2.0, 0.05, config.heartbeatSoundIntensity,
				value -> config.heartbeatSoundIntensity = (float)value,
				ValueSlider::formatPercent));
			y += SLIDER_STEP;

			this.addScrollableChild(new BranchToggleButton(this.contentX + BRANCH_INDENT, y, this.contentWidth - BRANCH_INDENT,
				CONTROL_HEIGHT,
				"Affect Low Health",
				"When enabled, very low health makes the heartbeat faster than the configured intensity.",
				() -> config.heartbeatSoundScaleWithLowHealth,
				value -> {
					config.heartbeatSoundScaleWithLowHealth = value;
					AlertTweaksConfig.save();
				}));
		}
	}

	private void addSoundPreviewControls() {
		this.addFixedChild(new ThemeButton(this.soundPreviewButtonX(), this.soundPreviewButtonY(), this.soundPreviewButtonWidth(),
			CONTROL_HEIGHT, Component.literal("Play"),
			"Plays a short heartbeat preview using the configured volume and intensity.",
			button -> HeartbeatSound.startPreview()));
	}

	private int soundPreviewButtonWidth() {
		return Math.min(96, Math.max(72, this.previewWidth - 28));
	}

	private int soundPreviewButtonX() {
		return this.previewX + (this.previewWidth - this.soundPreviewButtonWidth()) / 2;
	}

	private int soundPreviewButtonY() {
		return this.previewY + Math.max(36, this.previewHeight - 22 - 9);
	}

	// ---------------------------------------------------------------- chrome

	private void drawPanel(GuiGraphics graphics) {
		graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, PANEL_BACKGROUND);
		graphics.renderOutline(this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL_BORDER);
		graphics.fill(this.panelX + 8, this.panelY + 38, this.panelX + this.tabWidth + 5, this.panelY + this.panelHeight - 44,
			argb(76, 34, 10, 58));
	}

	private void drawHeader(GuiGraphics graphics) {
		graphics.drawCenteredString(font(), this.title, this.panelX + this.panelWidth / 2, this.panelY + 14, TEXT_MAIN);
		graphics.fill(this.panelX + 12, this.panelY + 34, this.panelX + this.panelWidth - 12, this.panelY + 35, PANEL_BORDER);
	}

	private void drawPreview(GuiGraphics graphics) {
		if (this.selectedTab == Tab.SOUND) {
			this.drawSoundPreview(graphics);
			return;
		}
		AlertTweaksConfig config = AlertTweaksConfig.get();
		if (!config.vignetteEnabled && !config.threatIndicatorEnabled) {
			return;
		}
		int x = this.previewX;
		int y = this.previewY;
		int width = this.previewWidth;
		int height = this.previewHeight;
		graphics.renderOutline(x - 2, y - 2, width + 4, height + 4, argb(150, 72, 26, 112));
		graphics.fill(x, y, x + width, y + height, argb(255, 0, 0, 0));
		this.drawPreviewBackground(graphics, x, y, width, height);
		if (config.vignetteEnabled) {
			this.drawPreviewVignette(graphics, config, x, y, width, height);
		}
		if (config.threatIndicatorEnabled) {
			this.drawPreviewThreat(graphics, config, x, y, width, height);
		}
		this.drawPreviewNote(graphics, config);
	}

	private void drawPreviewBackground(GuiGraphics graphics, int x, int y, int width, int height) {
		if (hasPreviewFrames()) {
			int frame = (int)(Util.getMillis() / PREVIEW_FRAME_MILLIS % PREVIEW_FRAME_COUNT);
			graphics.blit(RenderPipelines.GUI_TEXTURED, previewFrameId(frame), x, y, 0.0F, 0.0F, width, height,
				PREVIEW_TEXTURE_WIDTH, PREVIEW_TEXTURE_HEIGHT, PREVIEW_TEXTURE_WIDTH, PREVIEW_TEXTURE_HEIGHT);
			return;
		}
		this.drawFallbackPreviewBackground(graphics, x, y, width, height);
	}

	private static boolean hasPreviewFrames() {
		if (previewFramesAvailable == null) {
			Minecraft minecraft = Minecraft.getInstance();
			previewFramesAvailable = minecraft.getResourceManager().getResource(previewFrameId(0)).isPresent();
		}
		return Boolean.TRUE.equals(previewFramesAvailable);
	}

	private static Identifier previewFrameId(int frame) {
		return AlertTweaks.id(String.format(Locale.ROOT, "textures/gui/preview/frame_%02d.png", frame));
	}

	/** Stand-in scene for when the recorded frames are not shipped with the build. */
	private void drawFallbackPreviewBackground(GuiGraphics graphics, int x, int y, int width, int height) {
		long time = Util.getMillis();
		int horizon = y + Math.round(height * 0.48F);
		graphics.fillGradient(x, y, x + width, horizon, argb(255, 145, 190, 255), argb(255, 184, 216, 255));
		graphics.fillGradient(x, horizon, x + width, y + height, argb(255, 47, 121, 59), argb(255, 24, 72, 37));
		int offset = (int)(time / 50L % 28L);
		for (int i = -28; i < width + 28; i += 28) {
			int hillX = x + i - offset;
			graphics.fill(hillX, horizon - 10, hillX + 18, horizon, argb(255, 52, 108, 58));
			graphics.fill(hillX + 8, horizon - 16, hillX + 26, horizon, argb(255, 44, 92, 52));
		}
		int stripeOffset = (int)(time / 35L % 18L);
		for (int i = -18; i < width + 18; i += 18) {
			int stripeX = x + i - stripeOffset;
			graphics.fill(stripeX, horizon + 3, stripeX + 9, y + height, argb(55, 180, 230, 160));
		}
		int playerX = x + width / 2 - 4;
		int playerY = horizon + Math.max(10, (y + height - horizon) / 3);
		graphics.fill(playerX, playerY, playerX + 8, playerY + 15, argb(255, 234, 224, 240));
		graphics.fill(playerX + 1, playerY + 4, playerX + 7, playerY + 10, argb(255, 138, 172, 195));
	}

	private void drawPreviewVignette(GuiGraphics graphics, AlertTweaksConfig config, int x, int y, int width, int height) {
		float strength = config.staticColorEnabled ? config.staticColorIntensity : PREVIEW_VIGNETTE_STRENGTH;
		float sizeMultiplier = 1.0F;
		if (config.heartbeatEnabled) {
			float lowHealthFactor = config.heartbeatScaleWithLowHealth ? 0.82F : 0.45F;
			float pulse = LowHealthVignettePreview.pulse(config, lowHealthFactor);
			float amount = LowHealthVignettePreview.amount(config, lowHealthFactor);
			strength = Mth.clamp(strength * (1.0F + pulse * amount), 0.0F, 1.0F);
			sizeMultiplier = 1.0F + pulse * amount * 0.26F;
		}
		int alpha = Mth.clamp(Math.round(config.vignetteAlpha * strength), 0, 255);
		int baseEdgeSize = Mth.clamp(Math.min(width, height) / 4, 10, 42);
		int edgeSize = Mth.clamp(Math.round(baseEdgeSize * config.vignetteSize * sizeMultiplier), 6, Math.max(7, Math.min(width, height) / 2));
		int color = config.getVignetteColor(alpha);
		int clear = config.getVignetteColor(0);
		graphics.fillGradient(x, y, x + width, y + edgeSize, color, clear);
		graphics.fillGradient(x, y + height - edgeSize, x + width, y + height, clear, color);
		int steps = 16;
		for (int i = 0; i < steps; i++) {
			float progress = (float)i / steps;
			int stripAlpha = Math.round(alpha * (float)Math.pow(1.0F - progress, 1.65F));
			int stripColor = config.getVignetteColor(stripAlpha);
			int x1 = Math.round(edgeSize * progress);
			int x2 = Math.round(edgeSize * ((float)(i + 1) / steps));
			graphics.fill(x + x1, y, x + x2, y + height, stripColor);
			graphics.fill(x + width - x2, y, x + width - x1, y + height, stripColor);
		}
	}

	/** Live demo of the indicator so the scale options can be judged without taking a hit. */
	private void drawPreviewThreat(GuiGraphics graphics, AlertTweaksConfig config, int x, int y, int width, int height) {
		long cycle = Util.getMillis() % 5000L;
		float angle = (Util.getMillis() % 12000L) / 12000.0F * 360.0F;
		float scale = ThreatIndicatorRenderer.effectiveScale(graphics, config) * (height / 260.0F);
		graphics.enableScissor(x, y, x + width, y + height);
		ThreatIndicatorRenderer.draw(graphics, x + width / 2.0F, y + height / 2.0F, angle, Math.max(0.28F, scale), cycle);
		graphics.disableScissor();
	}

	private void drawSoundPreview(GuiGraphics graphics) {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		int x = this.previewX;
		int y = this.previewY;
		int width = this.previewWidth;
		int height = this.previewHeight;
		graphics.renderOutline(x - 2, y - 2, width + 4, height + 4, argb(150, 72, 26, 112));
		graphics.fill(x, y, x + width, y + height, argb(214, 17, 6, 30));
		graphics.fillGradient(x, y, x + width, y + height, argb(68, 92, 35, 148), argb(30, 0, 0, 0));
		int centerX = x + width / 2;
		int buttonY = this.soundPreviewButtonY();
		int titleY = y + 9;
		int iconY = y + 32;
		int iconColor = config.heartbeatSoundEnabled ? TEXT_ACCENT : TEXT_MUTED;
		graphics.drawCenteredString(font(), Component.literal("Sound Preview"), centerX, titleY, TEXT_MAIN);
		if (this.previewHeight >= 116) {
			graphics.fill(centerX - 40, iconY + 8, centerX - 33, iconY + 20, iconColor);
			graphics.fill(centerX - 33, iconY + 5, centerX - 26, iconY + 23, iconColor);
			graphics.fill(centerX + 19, iconY + 8, centerX + 22, iconY + 20, argb(180, 176, 93, 245));
			graphics.fill(centerX + 27, iconY + 5, centerX + 30, iconY + 23, argb(210, 176, 93, 245));
			graphics.fill(centerX + 35, iconY + 2, centerX + 38, iconY + 26, argb(240, 176, 93, 245));
			int textY = buttonY - 24;
			graphics.drawCenteredString(font(), Component.literal("Volume: " + Math.round(config.heartbeatSoundVolume * 100.0F) + "%"),
				centerX, textY, TEXT_MUTED);
			graphics.drawCenteredString(font(), Component.literal("Intensity: " + Math.round(config.heartbeatSoundIntensity * 100.0F) + "%"),
				centerX, textY + 10, TEXT_MUTED);
		} else {
			graphics.drawCenteredString(font(), Component.literal(Math.round(config.heartbeatSoundVolume * 100.0F) + "% volume"),
				centerX, Math.max(titleY + 14, buttonY - 13), TEXT_MUTED);
		}
		String mode = config.heartbeatSoundScaleWithLowHealth ? "Low health boost is ON." : "Static tempo is ON.";
		this.drawNote(graphics, Component.literal("Press Play to test the configured volume and heartbeat intensity. " + mode));
	}

	private void drawPreviewNote(GuiGraphics graphics, AlertTweaksConfig config) {
		Component text = config.threatIndicatorEnabled
			? Component.literal("The arrow demo loops the full five second animation. In game it only shows after a real hit.")
			: Component.literal("Preview does not simulate health-based intensity; heartbeat animation is shown.");
		this.drawNote(graphics, text);
	}

	private void drawNote(GuiGraphics graphics, Component text) {
		if (this.previewNoteHeight < 18) {
			return;
		}
		graphics.fill(this.previewNoteX, this.previewNoteY, this.previewNoteX + this.previewNoteWidth,
			this.previewNoteY + this.previewNoteHeight, argb(124, 18, 6, 30));
		graphics.renderOutline(this.previewNoteX, this.previewNoteY, this.previewNoteWidth, this.previewNoteHeight, argb(120, 72, 26, 112));
		List<FormattedCharSequence> lines = font().split(text, this.previewNoteWidth - 12);
		int maxLines = Math.min(lines.size(), Math.max(1, (this.previewNoteHeight - 8) / 10));
		for (int i = 0; i < maxLines; i++) {
			graphics.drawString(font(), lines.get(i), this.previewNoteX + 6, this.previewNoteY + 5 + i * 10, TEXT_MUTED);
		}
	}

	private void drawDescriptionFooter(GuiGraphics graphics, int mouseX, int mouseY) {
		String description = this.selectedTab.description;
		for (DescribedControl control : this.describedControls) {
			if (control.containsMouse(mouseX, mouseY)) {
				description = control.description();
				break;
			}
		}
		graphics.fill(this.descriptionFooterX, this.descriptionFooterY, this.descriptionFooterX + this.descriptionFooterWidth,
			this.descriptionFooterY + this.descriptionFooterHeight, FOOTER_BACKGROUND);
		graphics.renderOutline(this.descriptionFooterX, this.descriptionFooterY, this.descriptionFooterWidth, this.descriptionFooterHeight,
			argb(180, 72, 26, 112));
		List<FormattedCharSequence> lines = font().split(Component.literal(description), this.descriptionFooterWidth - 16);
		int maxLines = Math.min(2, lines.size());
		for (int i = 0; i < maxLines; i++) {
			graphics.drawString(font(), lines.get(i), this.descriptionFooterX + 8, this.descriptionFooterY + 6 + i * 10, TEXT_MUTED);
		}
	}

	private void drawScrollHints(GuiGraphics graphics) {
		if (this.maxContentScroll <= 0) {
			return;
		}
		int top = this.contentY;
		int bottom = this.descriptionFooterY - 8;
		int fadeHeight = Math.min(18, Math.max(8, (bottom - top) / 4));
		if (this.contentScrollOffset > 0) {
			graphics.fillGradient(this.contentX, top, this.contentX + this.contentWidth, top + fadeHeight,
				PANEL_BACKGROUND, argb(0, 17, 6, 30));
		}
		if (this.contentScrollOffset < this.maxContentScroll) {
			graphics.fillGradient(this.contentX, bottom - fadeHeight, this.contentX + this.contentWidth, bottom,
				argb(0, 17, 6, 30), PANEL_BACKGROUND);
		}
		int trackX = this.contentX + this.contentWidth + 4;
		int trackHeight = Math.max(20, bottom - top);
		int thumbHeight = Mth.clamp(Math.round(trackHeight * ((float)trackHeight / (trackHeight + this.maxContentScroll))), 18, trackHeight);
		int thumbY = top + Math.round((trackHeight - thumbHeight) * ((float)this.contentScrollOffset / this.maxContentScroll));
		graphics.fill(trackX, top, trackX + 2, top + trackHeight, argb(72, 142, 75, 220));
		graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, argb(210, 216, 176, 255));
	}

	private int getCurrentContentHeight() {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		if (this.selectedTab == Tab.SOUND) {
			int height = CONTROL_HEIGHT;
			if (config.heartbeatSoundEnabled) {
				height += SLIDER_STEP + SLIDER_STEP + CONTROL_STEP;
			}
			return height;
		}

		int height = CONTROL_HEIGHT;
		if (config.vignetteEnabled) {
			height += CONTROL_STEP + SLIDER_STEP + PALETTE_STEP + CONTROL_STEP;
			if (config.staticColorEnabled) {
				height += SLIDER_STEP;
			}
			height += CONTROL_STEP;
			if (config.heartbeatEnabled) {
				height += CONTROL_STEP;
			}
			height += SLIDER_STEP + SLIDER_STEP;
		}
		height += CONTROL_STEP;
		if (config.threatIndicatorEnabled) {
			height += CONTROL_STEP + SLIDER_STEP;
		}
		return height;
	}

	private <T extends AbstractWidget & DescribedControl> T addDescribedChild(T child) {
		this.describedControls.add(child);
		return this.addRenderableWidget(child);
	}

	private <T extends AbstractWidget & DescribedControl> T addFixedChild(T child) {
		this.fixedWidgets.add(child);
		return this.addDescribedChild(child);
	}

	private <T extends AbstractWidget & DescribedControl> T addScrollableChild(T child) {
		boolean visible = child.getY() + child.getHeight() > this.contentY && child.getY() < this.descriptionFooterY - 8;
		child.visible = visible;
		child.active = visible;
		this.scrollableWidgets.add(child);
		return this.addDescribedChild(child);
	}

	// ---------------------------------------------------------------- widgets

	/** Mirrors the heartbeat maths so the preview pulses exactly like the real vignette. */
	private static final class LowHealthVignettePreview {
		private LowHealthVignettePreview() {
		}

		static float pulse(AlertTweaksConfig config, float lowHealthFactor) {
			float speed = config.heartbeatScaleWithLowHealth ? Mth.lerp(lowHealthFactor, 1.15F, 3.2F) : 1.45F;
			double phase = System.currentTimeMillis() / 1000.0 * Math.PI * 2.0 * speed;
			float wave = Math.max(0.0F, (float)Math.sin(phase));
			return wave * wave * wave * wave;
		}

		static float amount(AlertTweaksConfig config, float lowHealthFactor) {
			return config.heartbeatScaleWithLowHealth ? Mth.lerp(lowHealthFactor, 0.08F, 0.3F) : 0.16F;
		}
	}

	private interface DescribedControl {
		String description();

		boolean containsMouse(double mouseX, double mouseY);
	}

	@FunctionalInterface
	private interface ButtonPressAction {
		void onPress(ThemeButton button);
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean get();
	}

	@FunctionalInterface
	private interface BooleanConsumer {
		void accept(boolean value);
	}

	@FunctionalInterface
	private interface SliderFormatter {
		String format(double value);
	}

	private static class ThemeButton extends AbstractWidget implements DescribedControl {
		private final String description;
		private final ButtonPressAction pressAction;

		ThemeButton(int x, int y, int width, int height, Component message, String description, ButtonPressAction pressAction) {
			super(x, y, width, height, message);
			this.description = description;
			this.pressAction = pressAction;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			this.pressAction.onPress(this);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			int color = this.isHovered() ? PANEL_STRONG : PANEL_SOFT;
			graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), color);
			graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.isHovered() ? TEXT_ACCENT : PANEL_BORDER);
			graphics.drawCenteredString(font(), this.getMessage(), this.getX() + this.getWidth() / 2,
				this.getY() + (this.getHeight() - 8) / 2, TEXT_MAIN);
			this.handleCursor(graphics);
		}

		@Override
		public String description() {
			return this.description;
		}

		@Override
		public boolean containsMouse(double mouseX, double mouseY) {
			return this.visible && this.isMouseOver(mouseX, mouseY);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {
			builder.add(NarratedElementType.TITLE, this.getMessage());
			builder.add(NarratedElementType.HINT, Component.literal(this.description));
		}
	}

	private static final class TabButton extends ThemeButton {
		private final Tab tab;
		private final boolean selected;

		TabButton(int x, int y, int width, int height, Tab tab, boolean selected, ButtonPressAction pressAction) {
			super(x, y, width, height, Component.literal(tab.title), tab.description, pressAction);
			this.tab = tab;
			this.selected = selected;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			int color = this.selected ? argb(178, 102, 34, 166) : (this.isHovered() ? PANEL_STRONG : PANEL_SOFT);
			graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), color);
			graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.selected ? TEXT_ACCENT : PANEL_BORDER);
			graphics.drawString(font(), this.tab.title, this.getX() + 8, this.getY() + 8, this.selected ? TEXT_MAIN : TEXT_MUTED);
			this.handleCursor(graphics);
		}
	}

	private static class ToggleButton extends AbstractWidget implements DescribedControl {
		private final String label;
		private final String description;
		private final BooleanSupplier getter;
		private final BooleanConsumer setter;

		ToggleButton(int x, int y, int width, int height, String label, String description,
			BooleanSupplier getter, BooleanConsumer setter) {
			super(x, y, width, height, Component.literal(label));
			this.label = label;
			this.description = description;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			this.setter.accept(!this.getter.get());
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			boolean on = this.getter.get();
			int color = this.isHovered() ? PANEL_STRONG : PANEL_SOFT;
			graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), color);
			graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.isHovered() ? TEXT_ACCENT : PANEL_BORDER);
			int toggleX = this.getX() + this.getWidth() - 34;
			int toggleY = this.getY() + (this.getHeight() - 12) / 2;
			int toggleColor = on ? argb(210, 142, 58, 224) : argb(140, 70, 55, 84);
			graphics.drawString(font(), font().plainSubstrByWidth(this.label, this.getWidth() - 48),
				this.getX() + 8, this.getY() + (this.getHeight() - 8) / 2, TEXT_MAIN);
			graphics.fill(toggleX, toggleY, toggleX + 24, toggleY + 12, argb(120, 0, 0, 0));
			graphics.fill(toggleX + (on ? 12 : 2), toggleY + 2, toggleX + (on ? 22 : 12), toggleY + 10, toggleColor);
			this.handleCursor(graphics);
		}

		@Override
		public String description() {
			return this.description;
		}

		@Override
		public boolean containsMouse(double mouseX, double mouseY) {
			return this.visible && this.isMouseOver(mouseX, mouseY);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {
			builder.add(NarratedElementType.TITLE, Component.literal(this.label + ": " + (this.getter.get() ? "ON" : "OFF")));
			builder.add(NarratedElementType.HINT, Component.literal(this.description));
		}
	}

	private static final class BranchToggleButton extends ToggleButton {
		BranchToggleButton(int x, int y, int width, int height, String label, String description,
			BooleanSupplier getter, BooleanConsumer setter) {
			super(x, y, width, height, label, description, getter, setter);
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			drawBranch(graphics, this.getX(), this.getY(), this.getHeight());
			super.renderWidget(graphics, mouseX, mouseY, delta);
		}
	}

	private static void drawBranch(GuiGraphics graphics, int x, int y, int height) {
		int branchX = x - 9;
		int midY = y + height / 2;
		graphics.fill(branchX, y - 8, branchX + 1, midY, argb(150, 142, 75, 220));
		graphics.fill(branchX, midY, x - 2, midY + 1, argb(150, 142, 75, 220));
	}

	private static class ValueSlider extends AbstractSliderButton implements DescribedControl {
		private final String label;
		private final String description;
		private final double min;
		private final double max;
		private final double step;
		private final DoubleConsumer consumer;
		private final SliderFormatter formatter;

		ValueSlider(int x, int y, int width, int height, String label, String description,
			double min, double max, double step, double initial, DoubleConsumer consumer, SliderFormatter formatter) {
			super(x, y, width, height, Component.empty(), valueToSlider(min, max, initial));
			this.label = label;
			this.description = description;
			this.min = min;
			this.max = max;
			this.step = step;
			this.consumer = consumer;
			this.formatter = formatter;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(Component.literal(this.label + ": " + this.formatter.format(this.getActualValue())));
		}

		@Override
		protected void applyValue() {
			double actual = this.getActualValue();
			this.consumer.accept(actual);
			this.value = valueToSlider(this.min, this.max, actual);
		}

		/** The config only reaches the disk once the drag is over, not on every pixel of movement. */
		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			AlertTweaksConfig.save();
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			int background = this.isHoveredOrFocused() ? PANEL_STRONG : PANEL_SOFT;
			graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), background);
			graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(),
				this.isHoveredOrFocused() ? TEXT_ACCENT : PANEL_BORDER);
			String text = font().plainSubstrByWidth(this.getMessage().getString(), this.getWidth() - 16);
			graphics.drawString(font(), text, this.getX() + 8, this.getY() + 5, TEXT_MAIN);
			int trackX = this.getX() + 8;
			int trackY = this.getY() + this.getHeight() - 9;
			int trackWidth = this.getWidth() - 16;
			graphics.fill(trackX, trackY, trackX + trackWidth, trackY + 2, argb(130, 0, 0, 0));
			graphics.fill(trackX, trackY, trackX + Math.round(trackWidth * (float)this.value), trackY + 2, argb(210, 176, 93, 245));
			int knobX = trackX + Math.round(trackWidth * (float)this.value);
			graphics.fill(knobX - 2, trackY - 3, knobX + 2, trackY + 5, TEXT_ACCENT);
			this.handleCursor(graphics);
		}

		@Override
		public String description() {
			return this.description;
		}

		@Override
		public boolean containsMouse(double mouseX, double mouseY) {
			return this.visible && this.isMouseOver(mouseX, mouseY);
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput builder) {
			builder.add(NarratedElementType.TITLE, this.getMessage());
			builder.add(NarratedElementType.HINT, Component.literal(this.description));
		}

		private double getActualValue() {
			double raw = this.min + (this.max - this.min) * this.value;
			double stepped = Math.round(raw / this.step) * this.step;
			return Mth.clamp(stepped, this.min, this.max);
		}

		private static double valueToSlider(double min, double max, double value) {
			return max <= min ? 0.0 : Mth.clamp((value - min) / (max - min), 0.0, 1.0);
		}

		static String formatByte(double value) {
			return Integer.toString((int)Math.round(value));
		}

		static String formatHealth(double value) {
			int points = (int)Math.round(value);
			return points + " pts (" + String.format(Locale.ROOT, "%.1f", points / 2.0) + " hearts)";
		}

		static String formatPercent(double value) {
			return Math.round(value * 100.0) + "%";
		}
	}

	private static final class BranchValueSlider extends ValueSlider {
		BranchValueSlider(int x, int y, int width, int height, String label, String description,
			double min, double max, double step, double initial, DoubleConsumer consumer, SliderFormatter formatter) {
			super(x, y, width, height, label, description, min, max, step, initial, consumer, formatter);
		}

		@Override
		public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			drawBranch(graphics, this.getX(), this.getY(), this.getHeight());
			super.renderWidget(graphics, mouseX, mouseY, delta);
		}
	}

	private static final class ColorPaletteWidget extends AbstractWidget implements DescribedControl {
		private final String label;
		private final String description;

		ColorPaletteWidget(int x, int y, int width, int height, String label, String description) {
			super(x, y, width, height, Component.empty());
			this.label = label;
			this.description = description;
			this.updateMessage();
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
			AlertTweaksConfig config = AlertTweaksConfig.get();
			graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(),
				this.isHovered() ? PANEL_STRONG : PANEL_SOFT);
			graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(),
				this.isHovered() ? TEXT_ACCENT : PANEL_BORDER);
			graphics.drawString(font(), this.getMessage(), this.getX() + 8, this.getY() + 6, TEXT_MAIN);
			int previewX = this.getX() + this.getWidth() - 28;
			int previewY = this.getY() + 5;
			graphics.fill(previewX - 1, previewY - 1, previewX + 19, previewY + 13, argb(150, 0, 0, 0));
			graphics.fill(previewX, previewY, previewX + 18, previewY + 12, config.getVignetteColor(255));
			graphics.renderOutline(previewX - 1, previewY - 1, 20, 14, argb(160, 230, 210, 255));
			this.drawPalette(graphics, config);
			this.handleCursor(graphics);
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			this.applyColor(event.x(), event.y());
		}

		@Override
		protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
			this.applyColor(event.x(), event.y());
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			AlertTweaksConfig.save();
		}

		@Override
		public String description() {
			return this.description;
		}

		@Override
		public boolean containsMouse(double mouseX, double mouseY) {
			return this.visible && this.isMouseOver(mouseX, mouseY);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput builder) {
			builder.add(NarratedElementType.TITLE, this.getMessage());
			builder.add(NarratedElementType.HINT, Component.literal(this.description));
		}

		private void drawPalette(GuiGraphics graphics, AlertTweaksConfig config) {
			int paletteX = this.paletteX();
			int paletteY = this.paletteY();
			int paletteWidth = this.paletteWidth();
			int paletteHeight = this.paletteHeight();
			for (int x = 0; x < paletteWidth; x++) {
				float hue = (float)x / Math.max(1, paletteWidth - 1);
				int topColor = 0xFF000000 | Mth.hsvToRgb(hue, 1.0F, 1.0F);
				graphics.fillGradient(paletteX + x, paletteY, paletteX + x + 1, paletteY + paletteHeight, topColor, argb(255, 0, 0, 0));
			}
			graphics.renderOutline(paletteX - 1, paletteY - 1, paletteWidth + 2, paletteHeight + 2, argb(170, 230, 210, 255));
			float[] hsv = rgbToHsv(config.vignetteRed, config.vignetteGreen, config.vignetteBlue);
			int markerX = paletteX + Math.round(hsv[0] * (paletteWidth - 1));
			int markerY = paletteY + Math.round((1.0F - hsv[2]) * (paletteHeight - 1));
			graphics.renderOutline(markerX - 3, markerY - 3, 7, 7, argb(255, 255, 255, 255));
			graphics.renderOutline(markerX - 2, markerY - 2, 5, 5, argb(255, 20, 7, 34));
		}

		private void applyColor(double mouseX, double mouseY) {
			int paletteX = this.paletteX();
			int paletteY = this.paletteY();
			int paletteWidth = this.paletteWidth();
			int paletteHeight = this.paletteHeight();
			if (mouseX < paletteX || mouseX > paletteX + paletteWidth || mouseY < paletteY || mouseY > paletteY + paletteHeight) {
				return;
			}
			float hue = (float)((mouseX - paletteX) / Math.max(1, paletteWidth - 1));
			float value = 1.0F - (float)((mouseY - paletteY) / Math.max(1, paletteHeight - 1));
			int rgb = Mth.hsvToRgb(Mth.clamp(hue, 0.0F, 1.0F), 1.0F, Mth.clamp(value, 0.0F, 1.0F));
			AlertTweaksConfig config = AlertTweaksConfig.get();
			config.vignetteRed = rgb >> 16 & 0xFF;
			config.vignetteGreen = rgb >> 8 & 0xFF;
			config.vignetteBlue = rgb & 0xFF;
			this.updateMessage();
		}

		private void updateMessage() {
			AlertTweaksConfig config = AlertTweaksConfig.get();
			this.setMessage(Component.literal(this.label + ": #"
				+ String.format(Locale.ROOT, "%02X%02X%02X", config.vignetteRed, config.vignetteGreen, config.vignetteBlue)));
		}

		private int paletteX() {
			return this.getX() + 8;
		}

		private int paletteY() {
			return this.getY() + 24;
		}

		private int paletteWidth() {
			return Math.max(32, this.getWidth() - 16);
		}

		private int paletteHeight() {
			return Math.max(16, this.getHeight() - 31);
		}

		private static float[] rgbToHsv(int red, int green, int blue) {
			float r = Mth.clamp(red, 0, 255) / 255.0F;
			float g = Mth.clamp(green, 0, 255) / 255.0F;
			float b = Mth.clamp(blue, 0, 255) / 255.0F;
			float max = Math.max(r, Math.max(g, b));
			float min = Math.min(r, Math.min(g, b));
			float delta = max - min;
			float hue;
			if (delta == 0.0F) {
				hue = 0.0F;
			} else if (max == r) {
				hue = (g - b) / delta % 6.0F;
			} else if (max == g) {
				hue = (b - r) / delta + 2.0F;
			} else {
				hue = (r - g) / delta + 4.0F;
			}
			hue /= 6.0F;
			if (hue < 0.0F) {
				hue++;
			}
			return new float[]{hue, max == 0.0F ? 0.0F : delta / max, max};
		}
	}

	private enum Tab {
		TWEAKS("Tweaks", "Visual and behavior settings for low-health and threat alerts."),
		SOUND("Sound", "Audio settings for low-health heartbeat alerts.");

		private final String title;
		private final String description;

		Tab(String title, String description) {
			this.title = title;
			this.description = description;
		}
	}
}
