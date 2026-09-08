package dev.zymekoh.alerttweaks.client.threat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers who hit the player recently and keeps the arrow pointing at them.
 *
 * <p>The hit itself comes from the damage event the server already sends to the victim. While the
 * indicator is alive the attacker's position is refreshed every tick from the entity the client is
 * already tracking, so the arrow follows them instead of staying on the spot where the hit landed.
 * If the attacker unloads or dies, the arrow freezes on the last position we saw.
 */
public final class ThreatTracker {
	public static final long LIFETIME_MS = 5000L;

	private static final int MAX_THREATS = 4;

	/** One attacker: their last known horizontal position and when they last connected. */
	public static final class Threat {
		private final int entityId;
		private double x;
		private double z;
		private long startedAtMillis;

		Threat(int entityId, double x, double z, long startedAtMillis) {
			this.entityId = entityId;
			this.x = x;
			this.z = z;
			this.startedAtMillis = startedAtMillis;
		}

		public int entityId() {
			return this.entityId;
		}

		public double x() {
			return this.x;
		}

		public double z() {
			return this.z;
		}

		public long startedAtMillis() {
			return this.startedAtMillis;
		}
	}

	private static final List<Threat> ACTIVE = new ArrayList<>();
	private static int previousHurtTime;

	private ThreatTracker() {
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			clear();
			return;
		}
		// hurtTime is set to hurtDuration when a damage event arrives and counts down every tick,
		// so any jump upwards means a fresh hit landed since the last tick.
		int hurtTime = player.hurtTime;
		if (hurtTime > previousHurtTime) {
			register(player);
		}
		previousHurtTime = hurtTime;

		long now = Util.getMillis();
		ACTIVE.removeIf(threat -> now - threat.startedAtMillis() > LIFETIME_MS);
		follow(minecraft.level);
	}

	/** Pulls each attacker's current position from the entity the client is already tracking. */
	private static void follow(Level level) {
		if (level == null) {
			return;
		}
		for (Threat threat : ACTIVE) {
			Entity attacker = level.getEntity(threat.entityId());
			if (attacker instanceof Player && attacker.isAlive() && !attacker.isRemoved()) {
				threat.x = attacker.getX();
				threat.z = attacker.getZ();
			}
		}
	}

	public static List<Threat> active() {
		return Collections.unmodifiableList(ACTIVE);
	}

	public static void clear() {
		ACTIVE.clear();
		previousHurtTime = 0;
	}

	private static void register(LocalPlayer player) {
		DamageSource source = player.getLastDamageSource();
		if (source == null || !(source.getEntity() instanceof Player attacker) || attacker == player) {
			return;
		}
		// The causing entity is the one worth pointing at: for a projectile the direct entity is the
		// arrow, which is already on top of us and would give a meaningless direction.
		Vec3 origin = attacker.position();
		if (origin == null) {
			origin = source.getSourcePosition();
		}
		if (origin != null) {
			add(attacker.getId(), origin);
		}
	}

	private static void add(int entityId, Vec3 origin) {
		long now = Util.getMillis();
		for (Threat existing : ACTIVE) {
			if (existing.entityId() == entityId) {
				// Same attacker keeping the pressure on: refresh the timer instead of stacking arrows.
				existing.x = origin.x;
				existing.z = origin.z;
				existing.startedAtMillis = now;
				return;
			}
		}
		while (ACTIVE.size() >= MAX_THREATS) {
			// Refreshing entries in place means insertion order is not age order, so drop the stalest.
			Threat oldest = ACTIVE.get(0);
			for (Threat candidate : ACTIVE) {
				if (candidate.startedAtMillis() < oldest.startedAtMillis()) {
					oldest = candidate;
				}
			}
			ACTIVE.remove(oldest);
		}
		ACTIVE.add(new Threat(entityId, origin.x, origin.z, now));
	}

	/** Yaw, in Minecraft's convention, from the player towards a horizontal position. */
	public static float angleTo(Player player, double x, double z) {
		return (float)(Math.toDegrees(Math.atan2(z - player.getZ(), x - player.getX())) - 90.0);
	}
}
