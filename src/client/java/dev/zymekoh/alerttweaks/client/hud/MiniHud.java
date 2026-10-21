package dev.zymekoh.alerttweaks.client.hud;

import dev.zymekoh.alerttweaks.client.gui.Gfx;
import dev.zymekoh.alerttweaks.client.gui.Icons;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * The player's HUD drawn the way the game draws it (same sprites, same layout, so it follows their
 * resource pack): with their real hotbar, health, food, armour and experience in a world, and with a
 * PvP kit (crystal, mace or nethpot, picked at random) where there is no world.
 */
public final class MiniHud {
	private static final Identifier HOTBAR = sprite("hud/hotbar");
	private static final Identifier SELECTION = sprite("hud/hotbar_selection");
	private static final Identifier OFFHAND_LEFT = sprite("hud/hotbar_offhand_left");
	private static final Identifier OFFHAND_RIGHT = sprite("hud/hotbar_offhand_right");
	private static final Identifier ARMOR_EMPTY = sprite("hud/armor_empty");
	private static final Identifier ARMOR_HALF = sprite("hud/armor_half");
	private static final Identifier ARMOR_FULL = sprite("hud/armor_full");
	private static final Identifier FOOD_EMPTY = sprite("hud/food_empty");
	private static final Identifier FOOD_HALF = sprite("hud/food_half");
	private static final Identifier FOOD_FULL = sprite("hud/food_full");
	private static final Identifier XP_BACKGROUND = sprite("hud/experience_bar_background");
	private static final Identifier XP_PROGRESS = sprite("hud/experience_bar_progress");
	private static final Identifier CROSSHAIR = sprite("hud/crosshair");
	private static final Identifier POTION_OVERLAY = Icons.item("potion_overlay");
	private static final String[] HEART_TYPES = {"", "poisoned_", "withered_", "frozen_", "absorbing_"};
	/** [type][hardcore][half], built once so drawing allocates nothing. */
	private static final Identifier[][][] HEARTS = new Identifier[HEART_TYPES.length][2][2];
	private static final Identifier[] CONTAINER = {sprite("hud/heart/container"), sprite("hud/heart/container_hardcore")};

	static {
		for (int t = 0; t < HEART_TYPES.length; t++) {
			for (int hc = 0; hc < 2; hc++) {
				for (int half = 0; half < 2; half++) {
					HEARTS[t][hc][half] = sprite("hud/heart/" + HEART_TYPES[t] + (hc == 1 ? "hardcore_" : "") + (half == 1 ? "half" : "full"));
				}
			}
		}
	}

	/** One mock hotbar slot: an item texture, or a block's top and side for a cube; a potion tint; durability left. */
	private record Slot(Identifier texture, Identifier side, int count, int tint, float durability) {
	}

	private static Slot item(String id, int count) {
		return new Slot(Icons.item(id), null, count, 0, -1.0F);
	}

	private static Slot tool(String id, float durability) {
		return new Slot(Icons.item(id), null, 1, 0, durability);
	}

	private static Slot block(String top, String side, int count) {
		return new Slot(block(top), block(side), count, 0, -1.0F);
	}

	private static Slot potion(int tint, int count) {
		return new Slot(Icons.item("splash_potion"), null, count, tint, -1.0F);
	}

	private static Identifier block(String id) {
		return Identifier.withDefaultNamespace("textures/block/" + id + ".png");
	}

	private static Identifier sprite(String id) {
		return Identifier.withDefaultNamespace(id);
	}

	/** Crystal PvP, mace PvP and nethpot hotbars; index 9 is the offhand. */
	private static final Slot[][] KITS = {
		{tool("netherite_sword", 0.93F), item("end_crystal", 64), block("obsidian", "obsidian", 64),
			block("respawn_anchor_top_off", "respawn_anchor_side0", 32), block("glowstone", "glowstone", 64), item("golden_apple", 48),
			item("ender_pearl", 16), item("experience_bottle", 64), item("totem_of_undying", 1), item("totem_of_undying", 1)},
		{item("mace", 1), tool("netherite_sword", 0.88F), item("wind_charge", 64), item("ender_pearl", 16), item("golden_apple", 32),
			block("cobweb", "cobweb", 32), item("firework_rocket", 64), tool("elytra", 0.71F), item("totem_of_undying", 1),
			item("totem_of_undying", 1)},
		{tool("netherite_sword", 0.97F), tool("netherite_axe", 0.9F), potion(0xFFF82423, 8), potion(0xFFFFC700, 4),
			potion(0xFF33EBFF, 4), item("golden_apple", 64), item("ender_pearl", 16), item("experience_bottle", 64),
			item("totem_of_undying", 1), item("totem_of_undying", 1)}
	};

	private static int kit;
	private static int selected;

	private MiniHud() {
	}

	/** New kit and selected slot; called when a settings screen opens. */
	public static void shuffle() {
		Random random = new Random();
		kit = random.nextInt(KITS.length);
		selected = random.nextInt(9);
	}

	/** True when there is a real player to show. */
	public static boolean real() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.level != null;
	}

	/** Draws the HUD of a screen of size w x h with its top left at the pose origin. */
	public static void draw(Gfx g, int w, int h) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player != null && mc.level != null) {
			drawReal(g, mc, player, w, h);
		} else {
			drawMock(g, w, h);
		}
	}

	private static void drawReal(Gfx g, Minecraft mc, LocalPlayer player, int w, int h) {
		int centre = w / 2;
		boolean offLeft = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
		ItemStack offhand = player.getOffhandItem();
		bar(g, centre, h, player.getInventory().getSelectedSlot(), !offhand.isEmpty(), offLeft);
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				g.item(stack, centre - 90 + i * 20 + 2, h - 19);
				g.itemDecorations(stack, centre - 90 + i * 20 + 2, h - 19);
			}
		}
		if (!offhand.isEmpty()) {
			int x = offLeft ? centre - 91 - 26 : centre + 91 + 10;
			g.item(offhand, x, h - 19);
			g.itemDecorations(offhand, x, h - 19);
		}
		if (mc.gameMode != null && mc.gameMode.canHurtPlayer()) {
			boolean hardcore = mc.level.getLevelData().isHardcore();
			int type = player.hasEffect(MobEffects.POISON) ? 1 : player.hasEffect(MobEffects.WITHER) ? 2 : player.isFullyFrozen() ? 3 : 0;
			hearts(g, centre, h, player.getHealth(), player.getMaxHealth(), player.getAbsorptionAmount(), type, hardcore);
			armor(g, centre, h, player.getArmorValue(), player.getMaxHealth(), player.getAbsorptionAmount());
			food(g, centre, h, player.getFoodData().getFoodLevel());
		}
		if (mc.gameMode != null && mc.gameMode.hasExperience()) {
			experience(g, w, h, player.experienceLevel, player.experienceProgress);
		}
	}

	private static void drawMock(Gfx g, int w, int h) {
		int centre = w / 2;
		Slot[] slots = KITS[kit];
		bar(g, centre, h, selected, true, true);
		for (int i = 0; i < 9; i++) {
			mockSlot(g, slots[i], centre - 90 + i * 20 + 2, h - 19);
		}
		mockSlot(g, slots[9], centre - 91 - 26, h - 19);
		// Close to death, like the vignette the preview shows; two golden hearts from the apple.
		hearts(g, centre, h, 7.0F, 20.0F, 4.0F, 0, false);
		armor(g, centre, h, 20, 20.0F, 4.0F);
		food(g, centre, h, 17);
		experience(g, w, h, 30, 0.42F);
	}

	private static void bar(Gfx g, int centre, int h, int selected, boolean offhand, boolean offLeft) {
		g.sprite(HOTBAR, centre - 91, h - 22, 182, 22);
		g.sprite(SELECTION, centre - 91 - 1 + selected * 20, h - 23, 24, 23);
		if (offhand) {
			if (offLeft) {
				g.sprite(OFFHAND_LEFT, centre - 91 - 29, h - 23, 29, 24);
			} else {
				g.sprite(OFFHAND_RIGHT, centre + 91, h - 23, 29, 24);
			}
		}
	}

	private static void mockSlot(Gfx g, Slot slot, int x, int y) {
		if (slot.side() != null) {
			g.cube(slot.texture(), slot.side(), x + 1, y + 1, 14, 1.0F);
		} else {
			g.blit(slot.texture(), x, y, 16, 16, 16, 16, 0xFFFFFFFF);
			if (slot.tint() != 0) {
				g.blit(POTION_OVERLAY, x, y, 16, 16, 16, 16, slot.tint());
			}
		}
		if (slot.durability() >= 0.0F && slot.durability() < 1.0F) {
			int width = Math.round(13.0F * slot.durability());
			g.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
			g.fill(x + 2, y + 13, x + 2 + width, y + 14, 0xFF000000 | Mth.hsvToRgb(slot.durability() / 3.0F, 1.0F, 1.0F));
		}
		if (slot.count() > 1) {
			String count = String.valueOf(slot.count());
			g.text(count, x + 17 - Gfx.font().width(count), y + 9, 0xFFFFFFFF, true);
		}
	}

	private static void hearts(Gfx g, int centre, int h, float health, float maxHealth, float absorption, int type, boolean hardcore) {
		int current = Mth.ceil(health);
		int absorb = Mth.ceil(absorption);
		int containers = Mth.ceil(maxHealth / 2.0F);
		int absorbContainers = Mth.ceil(absorb / 2.0F);
		int rows = Mth.ceil((maxHealth + absorb) / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		int left = centre - 91;
		int base = h - 39;
		int hc = hardcore ? 1 : 0;
		long shakeSeed = Util.getMillis() / 50L;
		for (int i = containers + absorbContainers - 1; i >= 0; i--) {
			int x = left + (i % 10) * 8;
			int y = base - (i / 10) * rowHeight;
			if (current + absorb <= 4) {
				// The game shakes the hearts at two hearts or less.
				y += (int)((shakeSeed * 31L + i * 17L) % 2L);
			}
			g.sprite(CONTAINER[hc], x, y, 9, 9);
			int halves = i * 2;
			if (i >= containers) {
				int absorbHalves = halves - containers * 2;
				if (absorbHalves < absorb) {
					g.sprite(HEARTS[4][hc][absorbHalves + 1 == absorb ? 1 : 0], x, y, 9, 9);
				}
			} else if (halves < current) {
				g.sprite(HEARTS[type][hc][halves + 1 == current ? 1 : 0], x, y, 9, 9);
			}
		}
	}

	private static void armor(Gfx g, int centre, int h, int armor, float maxHealth, float absorption) {
		if (armor <= 0) {
			return;
		}
		int rows = Mth.ceil((maxHealth + Mth.ceil(absorption)) / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		int y = h - 39 - (rows - 1) * rowHeight - 10;
		for (int i = 0; i < 10; i++) {
			int x = centre - 91 + i * 8;
			Identifier sprite = i * 2 + 1 < armor ? ARMOR_FULL : i * 2 + 1 == armor ? ARMOR_HALF : ARMOR_EMPTY;
			g.sprite(sprite, x, y, 9, 9);
		}
	}

	private static void food(Gfx g, int centre, int h, int food) {
		int right = centre + 91;
		int y = h - 39;
		for (int i = 0; i < 10; i++) {
			int x = right - i * 8 - 9;
			g.sprite(FOOD_EMPTY, x, y, 9, 9);
			if (i * 2 + 1 < food) {
				g.sprite(FOOD_FULL, x, y, 9, 9);
			} else if (i * 2 + 1 == food) {
				g.sprite(FOOD_HALF, x, y, 9, 9);
			}
		}
	}

	private static void experience(Gfx g, int w, int h, int level, float progress) {
		int left = (w - 182) / 2;
		int top = h - 24 - 5;
		g.sprite(XP_BACKGROUND, left, top, 182, 5);
		int filled = (int)(progress * 183.0F);
		if (filled > 0) {
			g.spritePart(XP_PROGRESS, 182, 5, left, top, Math.min(182, filled), 5);
		}
		if (level > 0) {
			Component text = Component.translatable("gui.experience.level", level);
			int x = (w - Gfx.font().width(text)) / 2;
			int y = h - 24 - 9 - 2;
			g.text(text, x + 1, y, 0xFF000000, false);
			g.text(text, x - 1, y, 0xFF000000, false);
			g.text(text, x, y + 1, 0xFF000000, false);
			g.text(text, x, y - 1, 0xFF000000, false);
			g.text(text, x, y, 0xFF80FF20, false);
		}
	}

	/** A crosshair like the game's, for previews of a screen. */
	public static void crosshair(Gfx g, int w, int h) {
		g.sprite(CROSSHAIR, (w - 15) / 2, (h - 15) / 2, 15, 15);
	}
}
