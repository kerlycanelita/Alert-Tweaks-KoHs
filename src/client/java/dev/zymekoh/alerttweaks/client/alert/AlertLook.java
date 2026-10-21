package dev.zymekoh.alerttweaks.client.alert;

import dev.zymekoh.alerttweaks.client.gui.Gfx;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * How an alert shows up. Every look carries the same alert; they trade detail for space:
 * CARD (icon, title, detail), COMPACT (a one-line pill), ICON (the icon alone in a diamond),
 * TEXT (no panel, like a subtitle) and BANNER (a wide strip for what must not be missed).
 */
public enum AlertLook {
	CARD, COMPACT, ICON, TEXT, BANNER;

	public static final float BANNER_TITLE = 1.25F;

	public Component label() {
		return Component.translatable("alert_tweaks.look." + this.name().toLowerCase(Locale.ROOT));
	}

	public AlertLook next() {
		AlertLook[] all = values();
		return all[(this.ordinal() + 1) % all.length];
	}

	public static AlertLook parse(String name) {
		if (name != null) {
			for (AlertLook look : values()) {
				if (look.name().equalsIgnoreCase(name)) {
					return look;
				}
			}
		}
		return CARD;
	}

	/** Unscaled height. */
	public int height() {
		return switch (this) {
			case CARD -> 26;
			case COMPACT -> 16;
			case ICON -> 26;
			case TEXT -> 21;
			case BANNER -> 24;
		};
	}

	/** Unscaled width for these texts. */
	public int width(Component title, Component detail, String badge) {
		var font = Gfx.font();
		int badgeW = badge == null ? 0 : font.width(badge) + 6;
		return switch (this) {
			case CARD -> 34 + Math.max(font.width(title), font.width(detail)) + badgeW + 8;
			case COMPACT -> 20 + font.width(title) + badgeW + 7;
			case ICON -> 26;
			case TEXT -> 14 + Math.max(font.width(title), font.width(detail)) + badgeW + 2;
			case BANNER -> Math.max(150, 32 + Math.round(font.width(title) * BANNER_TITLE) + 10 + font.width(detail) + badgeW + 14);
		};
	}
}
