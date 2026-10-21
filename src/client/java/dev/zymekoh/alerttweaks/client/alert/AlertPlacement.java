package dev.zymekoh.alerttweaks.client.alert;

/**
 * Where a stack of alert cards sits, from a point stored as fractions of the screen (so it survives
 * any resolution or GUI scale). Pure arithmetic: the HUD, the settings preview and the position
 * editor all lay cards out through here, so what the editor shows is what the game draws.
 *
 * <p>The point is the centre of the first card. Horizontally it is the card's left edge in the
 * left third of the screen, its right edge in the right third and its centre in between, so cards
 * of any width grow away from the nearest side. Vertically the stack grows away from the crosshair
 * when it sits near it, and away from the nearest edge otherwise.
 */
public final class AlertPlacement {
	public static final float LEFT_ZONE = 0.33F;
	public static final float RIGHT_ZONE = 0.67F;

	private AlertPlacement() {
	}

	/** -1: the point is the card's left edge, 1: its right edge, 0: its centre. */
	public static int align(float x) {
		return x < LEFT_ZONE ? -1 : x > RIGHT_ZONE ? 1 : 0;
	}

	/** +1: the stack grows downwards, -1: upwards. */
	public static int direction(float y) {
		if (y >= 0.35F && y <= 0.65F) {
			return y >= 0.5F ? 1 : -1;
		}
		return y < 0.5F ? 1 : -1;
	}

	/** Left edge of a card of width {@code w} whose stack point is at {@code anchorPx}. */
	public static float cardLeft(float anchorPx, float w, int align) {
		return align < 0 ? anchorPx : align > 0 ? anchorPx - w : anchorPx - w / 2.0F;
	}

	/** The stored x for a card whose left edge is at {@code left} (area pixels from the area's left). */
	public static float storeX(float left, float w, float areaW) {
		float centre = (left + w / 2.0F) / areaW;
		int align = align(centre);
		float px = align < 0 ? left : align > 0 ? left + w : left + w / 2.0F;
		return clamp01(px / areaW);
	}

	/** The stored y for a card whose centre is at {@code centreY} (area pixels from the area's top). */
	public static float storeY(float centreY, float areaH) {
		return clamp01(centreY / areaH);
	}

	/**
	 * How many cards fit from the stack point to the edge it grows towards, keeping clear of the
	 * hotbar (when the stack is above it) by {@code reserveBottom}.
	 */
	public static int room(float anchorY, int direction, float step, float cardH, float areaTop, float areaBottom, float reserveBottom,
		boolean overHotbar) {
		float free;
		if (direction > 0) {
			float floor = areaBottom - (overHotbar ? reserveBottom : 2.0F);
			free = floor - (anchorY + cardH / 2.0F);
		} else {
			free = (anchorY - cardH / 2.0F) - (areaTop + 2.0F);
		}
		return Math.max(1, 1 + (int)Math.floor(free / step));
	}

	private static float clamp01(float v) {
		return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
	}
}
