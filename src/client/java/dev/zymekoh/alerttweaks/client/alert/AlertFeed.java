package dev.zymekoh.alerttweaks.client.alert;

import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The cards on screen. The same alert about the same thing (one armour slot, one enemy) refreshes
 * its card instead of stacking a new one, so a burst of events never floods the screen.
 */
public final class AlertFeed {
	/** The in-game HUD. */
	public static final AlertFeed HUD = new AlertFeed(true, 8);
	/** The settings screen's preview; plays sounds but never flashes the real screen. */
	public static final AlertFeed PREVIEW = new AlertFeed(false, 24);

	/** Alerts can have places of their own now, so more cards may be up at once. */
	/** Upper bound of any feed (sizes the drawing scratch). */
	public static final int MAX_CARDS = 24;
	private static final long ALARM_GAP_MS = 140L;

	public static final class Card {
		public final Alert alert;
		public final String subject;
		public Component title;
		public Component detail;
		public Identifier icon;
		public long bornMs;
		/** When the last refresh landed; the card punches again from here. */
		public long refreshedMs;
		public int count = 1;
		/** A player's face instead of {@link #icon} (player alerts). */
		public java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> face;
		/** Where the card is drawn now; eases towards its slot in the stack. */
		public float shownY = Float.NaN;

		Card(Alert alert, String subject) {
			this.alert = alert;
			this.subject = subject;
		}
	}

	private final boolean real;
	/** Cards without sound (the preview's idle demo). */
	public boolean muted;
	/** Frame clock for the stack easing, one per feed so the HUD and the preview do not share it. */
	long lastDrawNanos;
	private final List<Card> cards = new ArrayList<>();
	private long flashMs = -1L;
	private int flashColor;
	private Alert pendingRepeat;
	private long pendingRepeatAt;

	private final int capacity;

	private AlertFeed(boolean real, int capacity) {
		this.capacity = capacity;
		this.real = real;
	}

	public void fire(Alert alert, String subject, Component title, Component detail, Identifier icon) {
		this.fire(alert, subject, title, detail, icon, false);
	}

	/** A player alert: the card shows the player's face. */
	public void fireFace(Alert alert, String subject, Component title, Component detail,
		java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> face) {
		this.fire(alert, subject, title, detail, alert.icon, false);
		Card card = this.find(alert, subject);
		if (card != null) {
			card.face = face;
		}
	}

	/** {@code silentRefresh}: update the text of a live card without a new sound (a ticking counter). */
	public void fire(Alert alert, String subject, Component title, Component detail, Identifier icon, boolean silentRefresh) {
		AlertTweaksConfig config = AlertTweaksConfig.get();
		long now = Util.getMillis();
		Card card = this.find(alert, subject);
		boolean fresh = card == null;
		if (fresh) {
			card = new Card(alert, subject);
			card.bornMs = now;
			card.refreshedMs = now;
			this.cards.add(card);
			while (this.cards.size() > this.capacity) {
				this.cards.remove(0);
			}
		} else if (!silentRefresh) {
			card.count++;
			card.refreshedMs = now;
			card.bornMs = Math.max(card.bornMs, now - 250L);
		} else {
			// A live counter (server lag) keeps its card up while it keeps updating.
			card.bornMs = Math.max(card.bornMs, now - 400L);
		}
		card.title = title;
		card.detail = detail;
		card.icon = icon == null ? alert.icon : icon;
		if (silentRefresh && !fresh) {
			return;
		}
		AlertSound sound = config.sound(alert);
		if (!this.muted) {
			sound.play(config.alertVolume);
			if (sound.repeats()) {
				this.pendingRepeat = alert;
				this.pendingRepeatAt = now + ALARM_GAP_MS;
			}
		}
		if (alert.tone == Alert.Tone.DANGER && config.edgeFlash) {
			this.flashMs = now;
			this.flashColor = 0xE0284E;
		}
	}

	public void dismiss(Alert alert, String subject) {
		Card card = this.find(alert, subject);
		if (card != null) {
			long now = Util.getMillis();
			long life = lifeMs();
			// Jump to the start of the exit animation instead of vanishing.
			card.bornMs = Math.min(card.bornMs, now - life + 450L);
		}
	}

	private Card find(Alert alert, String subject) {
		for (Card card : this.cards) {
			if (card.alert == alert && Objects.equals(card.subject, subject)) {
				return card;
			}
		}
		return null;
	}

	public static long lifeMs() {
		return Math.round(AlertTweaksConfig.get().toastSeconds * 1000.0F);
	}

	/** Drops expired cards and plays the second note of an alarm. Call once per client tick or frame. */
	public List<Card> cards() {
		long now = Util.getMillis();
		long life = lifeMs();
		this.cards.removeIf(card -> now - card.bornMs > life);
		if (this.pendingRepeat != null && now >= this.pendingRepeatAt) {
			AlertTweaksConfig.get().sound(this.pendingRepeat).play(AlertTweaksConfig.get().alertVolume * 0.85F);
			this.pendingRepeat = null;
		}
		return this.cards;
	}

	/** 0..1 strength of the crimson edge flash. */
	public float flash() {
		if (this.flashMs < 0L) {
			return 0.0F;
		}
		long age = Util.getMillis() - this.flashMs;
		if (age > 520L) {
			this.flashMs = -1L;
			return 0.0F;
		}
		float t = age / 520.0F;
		return (1.0F - t) * (1.0F - t) * (age < 60L ? age / 60.0F : 1.0F);
	}

	public int flashColor() {
		return this.flashColor;
	}

	public boolean isReal() {
		return this.real;
	}

	public void clear() {
		this.cards.clear();
		this.flashMs = -1L;
		this.pendingRepeat = null;
	}
}
