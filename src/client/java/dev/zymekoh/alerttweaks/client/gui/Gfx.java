package dev.zymekoh.alerttweaks.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * The only place that knows the GUI drawing API of each Minecraft version. Everything else draws
 * through this, so screens and HUD code stay the same in every version.
 *
 * <p>One shared instance on the render thread: {@link #of} swaps the wrapped object, nothing is
 * allocated per frame.
 */
public final class Gfx {
	//? if >=26.1 {
	private GuiGraphicsExtractor g;

	public static Gfx of(GuiGraphicsExtractor graphics) {
		SHARED.g = graphics;
		return SHARED;
	}

	public GuiGraphicsExtractor raw() {
		return this.g;
	}
	//?} else {
	/*private GuiGraphics g;

	public static Gfx of(GuiGraphics graphics) {
		SHARED.g = graphics;
		return SHARED;
	}

	public GuiGraphics raw() {
		return this.g;
	}
	*///?}

	private static final Gfx SHARED = new Gfx();

	private Gfx() {
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public int width() {
		return this.g.guiWidth();
	}

	public int height() {
		return this.g.guiHeight();
	}

	public Matrix3x2fStack pose() {
		return this.g.pose();
	}

	public void nextStratum() {
		this.g.nextStratum();
	}

	public void scissor(int x1, int y1, int x2, int y2) {
		this.g.enableScissor(x1, y1, x2, y2);
	}

	public void endScissor() {
		this.g.disableScissor();
	}

	public void fill(int x1, int y1, int x2, int y2, int color) {
		if ((color >>> 24) != 0) {
			this.g.fill(x1, y1, x2, y2, color);
		}
	}

	/** Top to bottom. */
	public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
		this.g.fillGradient(x1, y1, x2, y2, top, bottom);
	}

	/** Left to right, painted as vertical strips (the game only has vertical gradients). */
	public void hGradient(int x1, int y1, int x2, int y2, int left, int right) {
		int w = x2 - x1;
		if (w <= 0) {
			return;
		}
		int steps = Math.min(w, 24);
		for (int i = 0; i < steps; i++) {
			int a = x1 + w * i / steps;
			int b = x1 + w * (i + 1) / steps;
			this.fill(a, y1, b, y2, Theme.mix(left, right, (i + 0.5F) / steps));
		}
	}

	public void outline(int x, int y, int w, int h, int color) {
		//? if >=26.1 {
		this.g.outline(x, y, w, h, color);
		//?} else {
		/*this.g.renderOutline(x, y, w, h, color);
		*///?}
	}

	public void text(Component text, int x, int y, int color, boolean shadow) {
		//? if >=26.1 {
		this.g.text(font(), text, x, y, color, shadow);
		//?} else {
		/*this.g.drawString(font(), text, x, y, color, shadow);
		*///?}
	}

	public void text(FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		//? if >=26.1 {
		this.g.text(font(), text, x, y, color, shadow);
		//?} else {
		/*this.g.drawString(font(), text, x, y, color, shadow);
		*///?}
	}

	public void text(String text, int x, int y, int color, boolean shadow) {
		//? if >=26.1 {
		this.g.text(font(), text, x, y, color, shadow);
		//?} else {
		/*this.g.drawString(font(), text, x, y, color, shadow);
		*///?}
	}

	public void centered(Component text, int centerX, int y, int color) {
		this.text(text, centerX - font().width(text) / 2, y, color, true);
	}

	/** A whole texture stretched over a rectangle, tinted (ARGB). */
	public void blit(Identifier texture, int x, int y, int w, int h, int texW, int texH, int color) {
		this.g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, w, h, texW, texH, texW, texH, color);
	}

	/** Part of a texture. */
	public void blit(Identifier texture, int x, int y, float u, float v, int w, int h, int texW, int texH, int color) {
		this.g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, texW, texH, color);
	}

	/** A region of a texture stretched over a rectangle. */
	public void blitRegion(Identifier texture, int x, int y, int w, int h, float u, float v, int regionW, int regionH, int texW, int texH,
		int color) {
		this.g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, regionW, regionH, texW, texH, color);
	}

	/** A GUI atlas sprite (hud/hotbar, hud/heart/full...): follows the player's resource pack. */
	public void sprite(Identifier sprite, int x, int y, int w, int h) {
		this.g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
	}

	/** The left {@code part} pixels of a sprite of size spriteW x spriteH (experience bar progress). */
	public void spritePart(Identifier sprite, int spriteW, int spriteH, int x, int y, int part, int h) {
		this.g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, spriteW, spriteH, 0, 0, x, y, part, h);
	}

	/** A real item stack, only in a world (components are not bound on the title screen). */
	public void item(net.minecraft.world.item.ItemStack stack, int x, int y) {
		//? if >=26.1 {
		this.g.item(stack, x, y);
		//?} else {
		/*this.g.renderItem(stack, x, y);
		*///?}
	}

	public void itemDecorations(net.minecraft.world.item.ItemStack stack, int x, int y) {
		//? if >=26.1 {
		this.g.itemDecorations(font(), stack, x, y);
		//?} else {
		/*this.g.renderItemDecorations(font(), stack, x, y);
		*///?}
	}

	/** A player's face (head and hat layer) from their skin. */
	public void face(net.minecraft.world.entity.player.PlayerSkin skin, int x, int y, int size, float alpha) {
		//? if >=26.1 {
		net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(this.g, skin, x, y, size,
			Theme.alpha(0xFFFFFFFF, alpha));
		//?} else {
		/*net.minecraft.client.gui.components.PlayerFaceRenderer.draw(this.g, skin, x, y, size, Theme.alpha(0xFFFFFFFF, alpha));
		*///?}
	}

	private static final org.joml.Matrix3x2f SHEAR = new org.joml.Matrix3x2f();

	/**
	 * A block drawn like an inventory item: three faces of an isometric cube from raw block textures,
	 * lit from the top left, so blocks look right where no ItemStack may be rendered.
	 */
	public void cube(Identifier top, Identifier side, float x, float y, float size, float alpha) {
		float s = size;
		this.face3(top, x, y, 0.0F, s / 4.0F, s / 2.0F, -s / 4.0F, s / 2.0F, s / 4.0F, Theme.alpha(0xFFFFFFFF, alpha));
		this.face3(side, x, y, 0.0F, s / 4.0F, s / 2.0F, s / 4.0F, 0.0F, s / 2.0F, Theme.alpha(0xFFCFCFCF, alpha));
		this.face3(side, x, y, s / 2.0F, s / 2.0F, s / 2.0F, -s / 4.0F, 0.0F, s / 2.0F, Theme.alpha(0xFF9A9A9A, alpha));
	}

	/** One 16x16 texture mapped onto the parallelogram origin + u*U + v*V. */
	private void face3(Identifier texture, float x, float y, float ox, float oy, float ux, float uy, float vx, float vy, int color) {
		Matrix3x2fStack pose = this.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		SHEAR.set(ux / 16.0F, uy / 16.0F, vx / 16.0F, vy / 16.0F, ox, oy);
		pose.mul(SHEAR);
		this.blit(texture, 0, 0, 16, 16, 16, 16, color);
		pose.popMatrix();
	}

	/**
	 * A 16x16 item or effect texture drawn centred on (cx, cy) at any size. Uses the raw PNG, never an
	 * ItemStack, so it is safe on screens opened from the title screen.
	 */
	public void icon(Identifier texture, float cx, float cy, float size, float angle, int color) {
		if ((color >>> 24) == 0 || size <= 0.5F) {
			return;
		}
		Matrix3x2fStack pose = this.pose();
		pose.pushMatrix();
		pose.translate(cx, cy);
		if (angle != 0.0F) {
			pose.rotate(angle);
		}
		float unit = size / 16.0F;
		pose.scale(unit, unit);
		this.blit(texture, -8, -8, 16, 16, Icons.sizeOf(texture), Icons.sizeOf(texture), color);
		pose.popMatrix();
	}
}
