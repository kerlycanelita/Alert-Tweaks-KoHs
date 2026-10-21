package dev.zymekoh.alerttweaks.client.alert;

import dev.zymekoh.alerttweaks.client.config.AlertTweaksConfig;
import dev.zymekoh.alerttweaks.client.gui.Icons;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Watches the local player every client tick and raises an alert on the edge where something
 * changes (a totem leaves the offhand, durability crosses the line...), never every tick.
 * Reads only state the client already has; sends nothing.
 */
public final class AlertMonitor {
	/** After joining, respawning or changing dimension the inventory and effects arrive in bursts. */
	private static final int WARMUP_TICKS = 40;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Item[] COOLDOWN_ITEMS = {Items.ENDER_PEARL, Items.WIND_CHARGE, Items.CHORUS_FRUIT, Items.SHIELD};
	private static final Item[] SUPPLIES = {Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.ENDER_PEARL, Items.END_CRYSTAL,
		Items.OBSIDIAN, Items.RESPAWN_ANCHOR, Items.GLOWSTONE, Items.EXPERIENCE_BOTTLE, Items.WIND_CHARGE, Items.ARROW};
	/** Warn when a stack you were carrying drops to this many or fewer. */
	private static final int[] SUPPLY_LOW = {2, 1, 2, 8, 8, 2, 4, 8, 4, 8};
	private static final float VOID_DEPTH = 320.0F;

	private static int warmup = WARMUP_TICKS;
	private static Object lastLevel;
	private static Object lastPlayer;

	private static boolean hadOffhandTotem;
	private static int lastTotems = -1;
	private static final boolean[] armorWarned = new boolean[4];
	private static final Item[] armorItem = new Item[4];
	private static boolean heldWarned;
	private static Item heldItem;
	private static boolean shieldCooling;
	private static final long[] cooldownStart = new long[COOLDOWN_ITEMS.length];
	private static final Map<Holder<MobEffect>, Integer> harmful = new HashMap<>();
	private static final Map<Holder<MobEffect>, Integer> scratch = new HashMap<>();
	private static final Set<Holder<MobEffect>> endingWarned = new HashSet<>();
	private static int lastFood = 20;
	private static boolean fallWarned;
	private static final int[] lastSupply = new int[SUPPLIES.length];
	private static boolean wasFull;
	private static final Map<UUID, Integer> pops = new HashMap<>();

	private static long lastServerPulseMs;
	private static long lagSinceMs = -1L;

	/** Watched players on the server's player list (lower-case name -> their skin there). */
	private static final Map<String, net.minecraft.world.entity.player.PlayerSkin> watchedOnline = new HashMap<>();
	private static final Map<String, net.minecraft.world.entity.player.PlayerSkin> watchedNow = new HashMap<>();
	private static final Map<String, String> displayNames = new HashMap<>();
	private static boolean playersPrimed;
	private static int playerTicks;
	private static int lastWatchHash;

	private AlertMonitor() {
	}

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			reset();
			return;
		}
		if (mc.level != lastLevel || player != lastPlayer) {
			// New world, dimension or respawn: re-learn the state silently.
			lastLevel = mc.level;
			lastPlayer = player;
			warmup = WARMUP_TICKS;
			lastServerPulseMs = Util.getMillis();
		}
		boolean live = warmup <= 0;
		if (warmup > 0) {
			warmup--;
		}
		AlertTweaksConfig config = AlertTweaksConfig.get();
		serverLag(mc, config, live);
		players(mc, config, live);
		if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
			return;
		}
		totems(player, config, live);
		armor(player, config, live);
		held(player, config, live);
		shield(player, config, live);
		cooldowns(player, config, live);
		effects(player, config, live);
		hunger(player, config, live);
		fall(mc, player, config, live);
		supplies(player, config, live);
		inventoryFull(player, config, live);
	}

	public static void reset() {
		warmup = WARMUP_TICKS;
		lastLevel = null;
		lastPlayer = null;
		hadOffhandTotem = false;
		lastTotems = -1;
		java.util.Arrays.fill(armorWarned, false);
		java.util.Arrays.fill(armorItem, null);
		heldWarned = false;
		heldItem = null;
		shieldCooling = false;
		java.util.Arrays.fill(cooldownStart, 0L);
		harmful.clear();
		endingWarned.clear();
		lastFood = 20;
		fallWarned = false;
		java.util.Arrays.fill(lastSupply, -1);
		wasFull = false;
		pops.clear();
		lastServerPulseMs = 0L;
		lagSinceMs = -1L;
		watchedOnline.clear();
		playersPrimed = false;
	}

	private static void fire(boolean live, AlertTweaksConfig config, Alert alert, String subject, Component title, Component detail,
		net.minecraft.resources.Identifier icon) {
		if (live && config.enabled(alert)) {
			AlertFeed.HUD.fire(alert, subject, title, detail, icon);
		}
	}

	private static Component msg(String key, Object... args) {
		return Component.translatable("alert_tweaks.msg." + key, args);
	}

	// ------------------------------------------------------------------ totems

	private static int count(Player player, Item item) {
		int total = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		ItemStack offhand = player.getOffhandItem();
		return offhand.is(item) ? total + offhand.getCount() : total;
	}

	private static void totems(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		boolean offhandTotem = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
		int totems = count(player, Items.TOTEM_OF_UNDYING);
		if (hadOffhandTotem && !offhandTotem) {
			int left = totems;
			fire(live, config, Alert.TOTEM_OFFHAND, "", msg("totem_offhand"),
				left > 0 ? msg("totems_left", left) : msg("totems_none"), Icons.TOTEM);
		} else if (offhandTotem) {
			AlertFeed.HUD.dismiss(Alert.TOTEM_OFFHAND, "");
		}

		int low = Math.round(config.threshold(Alert.TOTEMS_LOW));
		boolean offhandCardSaysIt = hadOffhandTotem && !offhandTotem && config.enabled(Alert.TOTEM_OFFHAND);
		if (lastTotems > low && totems <= low && lastTotems >= 0 && !offhandCardSaysIt) {
			fire(live, config, Alert.TOTEMS_LOW, "", msg("totems_low"),
				totems > 0 ? msg("totems_left", totems) : msg("totems_none"), Icons.TOTEM);
		}
		hadOffhandTotem = offhandTotem;
		lastTotems = totems;
	}

	/** Called from the entity-event packet handler: 35 is a totem pop, 3 a death. */
	public static void onEntityEvent(Entity entity, byte event) {
		Minecraft mc = Minecraft.getInstance();
		if (!(entity instanceof Player other) || other == mc.player) {
			return;
		}
		if (event == 3) {
			pops.remove(other.getUUID());
			return;
		}
		if (event != 35) {
			return;
		}
		int popped = pops.merge(other.getUUID(), 1, Integer::sum);
		AlertTweaksConfig config = AlertTweaksConfig.get();
		fire(warmup <= 0, config, Alert.ENEMY_POP, other.getUUID().toString(), msg("enemy_pop", other.getName()),
			msg("enemy_pop_count", popped), Icons.TOTEM);
	}

	// ------------------------------------------------------------------ gear

	private static float durability(ItemStack stack) {
		if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return 1.0F;
		}
		return 1.0F - (float)stack.getDamageValue() / stack.getMaxDamage();
	}

	private static void armor(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		float line = config.threshold(Alert.ARMOR_LOW) / 100.0F;
		for (int i = 0; i < ARMOR.length; i++) {
			ItemStack stack = player.getItemBySlot(ARMOR[i]);
			Item item = stack.isEmpty() ? null : stack.getItem();
			if (item != armorItem[i]) {
				armorItem[i] = item;
				armorWarned[i] = durability(stack) <= line;
				continue;
			}
			float left = durability(stack);
			if (!armorWarned[i] && left <= line) {
				armorWarned[i] = true;
				fire(live, config, Alert.ARMOR_LOW, ARMOR[i].getName(), msg("armor_low", stack.getHoverName()),
					msg("percent_left", Math.round(left * 100.0F)), Icons.of(stack.getItem(), Icons.CHESTPLATE));
			} else if (armorWarned[i] && left > line + 0.05F) {
				armorWarned[i] = false;
			}
		}
	}

	private static void held(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		ItemStack stack = player.getMainHandItem();
		Item item = stack.isEmpty() ? null : stack.getItem();
		float line = config.threshold(Alert.HELD_LOW) / 100.0F;
		float left = durability(stack);
		if (item != heldItem) {
			heldItem = item;
			heldWarned = left <= line;
			return;
		}
		if (!heldWarned && left <= line) {
			heldWarned = true;
			fire(live, config, Alert.HELD_LOW, "", msg("held_low", stack.getHoverName()),
				msg("percent_left", Math.round(left * 100.0F)), Icons.of(stack.getItem(), Icons.SWORD));
		} else if (heldWarned && left > line + 0.05F) {
			heldWarned = false;
		}
	}

	private static ItemStack find(Player player, Item item) {
		if (player.getOffhandItem().is(item)) {
			return player.getOffhandItem();
		}
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void shield(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		ItemStack shield = player.getOffhandItem().is(Items.SHIELD) ? player.getOffhandItem()
			: player.getMainHandItem().is(Items.SHIELD) ? player.getMainHandItem() : ItemStack.EMPTY;
		boolean cooling = !shield.isEmpty() && player.getCooldowns().isOnCooldown(shield);
		if (cooling && !shieldCooling) {
			fire(live, config, Alert.SHIELD_DISABLED, "", msg("shield_disabled"), msg("shield_disabled_detail"), Icons.AXE);
		}
		shieldCooling = cooling;
	}

	private static void cooldowns(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		long now = Util.getMillis();
		long minimum = Math.round(config.threshold(Alert.COOLDOWN_READY) * 1000.0F);
		for (int i = 0; i < COOLDOWN_ITEMS.length; i++) {
			ItemStack stack = find(player, COOLDOWN_ITEMS[i]);
			if (stack.isEmpty()) {
				cooldownStart[i] = 0L;
				continue;
			}
			boolean cooling = player.getCooldowns().isOnCooldown(stack);
			if (cooling && cooldownStart[i] == 0L) {
				cooldownStart[i] = now;
			} else if (!cooling && cooldownStart[i] != 0L) {
				// Only cooldowns worth waiting for: a vanilla 1 s pearl would ping on every throw.
				if (now - cooldownStart[i] >= minimum) {
					fire(live, config, Alert.COOLDOWN_READY, COOLDOWN_ITEMS[i].toString(), msg("cooldown_ready", stack.getHoverName()),
						msg("cooldown_ready_detail", (now - cooldownStart[i]) / 100 / 10.0F), Icons.of(COOLDOWN_ITEMS[i], Icons.PEARL));
				}
				cooldownStart[i] = 0L;
			}
		}
	}

	// ------------------------------------------------------------------ body

	private static Component effectName(MobEffectInstance effect) {
		Component name = effect.getEffect().value().getDisplayName();
		if (effect.getAmplifier() <= 0 || effect.getAmplifier() > 9) {
			return name;
		}
		return name.copy().append(" ").append(Component.translatable("potion.potency." + effect.getAmplifier()));
	}

	private static void effects(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		int endingTicks = Math.round(config.threshold(Alert.EFFECT_ENDING) * 20.0F);
		scratch.clear();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			Holder<MobEffect> type = effect.getEffect();
			MobEffectCategory category = type.value().getCategory();
			if (category == MobEffectCategory.HARMFUL) {
				scratch.put(type, effect.getAmplifier());
				Integer before = harmful.get(type);
				if (before == null || effect.getAmplifier() > before) {
					fire(live, config, Alert.EFFECT_HARMFUL, type.getRegisteredName(), effectName(effect),
						effect.isInfiniteDuration() ? msg("effect_infinite") : msg("seconds", Math.max(1, effect.getDuration() / 20)),
						Icons.of(type));
				}
			} else if (category == MobEffectCategory.BENEFICIAL && !effect.isInfiniteDuration()) {
				int left = effect.getDuration();
				if (left <= endingTicks && left > 0 && !endingWarned.contains(type)) {
					endingWarned.add(type);
					fire(live, config, Alert.EFFECT_ENDING, type.getRegisteredName(), msg("effect_ending", effectName(effect)),
						msg("seconds", Math.max(1, left / 20)), Icons.of(type));
				} else if (left > endingTicks + 20) {
					endingWarned.remove(type);
				}
			}
		}
		harmful.clear();
		harmful.putAll(scratch);
		endingWarned.removeIf(type -> !player.hasEffect(type));
	}

	private static void hunger(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		int food = player.getFoodData().getFoodLevel();
		// At 6 food points or less the player can no longer sprint.
		if (lastFood > 6 && food <= 6) {
			fire(live, config, Alert.NO_SPRINT, "", msg("no_sprint"), msg("no_sprint_detail"), Icons.BEEF);
		} else if (food > 6) {
			AlertFeed.HUD.dismiss(Alert.NO_SPRINT, "");
		}
		lastFood = food;
	}

	private static void fall(Minecraft mc, LocalPlayer player, AlertTweaksConfig config, boolean live) {
		boolean falling = !player.onGround() && player.getDeltaMovement().y < -0.3
			&& !player.isFallFlying() && !player.getAbilities().flying && !player.isInWater() && !player.isInLava()
			&& !player.isPassenger() && !player.onClimbable()
			&& !player.hasEffect(MobEffects.SLOW_FALLING) && !player.hasEffect(MobEffects.LEVITATION);
		if (!falling) {
			fallWarned = false;
			return;
		}
		if (fallWarned) {
			return;
		}
		float damage = predictFallDamage(mc, player);
		float health = player.getHealth() + player.getAbsorptionAmount();
		float line = config.threshold(Alert.DANGEROUS_FALL) / 100.0F;
		if (damage >= health * line) {
			fallWarned = true;
			Component detail = damage >= health ? msg("fall_lethal") : msg("fall_damage", Math.round(damage) / 2.0F);
			fire(live, config, Alert.DANGEROUS_FALL, "", msg("dangerous_fall"), detail, Icons.FEATHER);
		}
	}

	/**
	 * Damage of landing where the player is heading, from the same numbers the game uses: fall
	 * distance so far plus the drop to the block below, safe fall distance, fall multiplier, the
	 * landing block (water, slime, hay, beds) and Feather Falling / Protection / Resistance.
	 */
	public static float predictFallDamage(Minecraft mc, LocalPlayer player) {
		Vec3 from = player.position();
		Vec3 to = from.subtract(0.0, VOID_DEPTH, 0.0);
		BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		float drop;
		float blockFactor = 1.0F;
		if (hit.getType() == HitResult.Type.MISS) {
			return Float.MAX_VALUE; // the void
		}
		BlockPos pos = hit.getBlockPos();
		if (!mc.level.getFluidState(pos).isEmpty()) {
			return 0.0F;
		}
		BlockState landing = mc.level.getBlockState(pos);
		if (landing.is(Blocks.SLIME_BLOCK) || landing.is(Blocks.POWDER_SNOW) || landing.is(Blocks.COBWEB)
			|| landing.is(Blocks.SCAFFOLDING) || landing.is(Blocks.SWEET_BERRY_BUSH)) {
			return 0.0F;
		}
		if (landing.is(Blocks.HAY_BLOCK) || landing.is(Blocks.HONEY_BLOCK)) {
			blockFactor = 0.2F;
		} else if (landing.is(BlockTags.BEDS)) {
			blockFactor = 0.5F;
		}
		drop = (float)(from.y - hit.getLocation().y);
		double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		double multiplier = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		float raw = Mth.ceil((player.fallDistance + drop - safe) * multiplier * blockFactor);
		if (raw <= 0.0F) {
			return 0.0F;
		}
		raw *= 1.0F - enchantReduction(player);
		MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
		if (resistance != null) {
			raw *= Math.max(0.0F, 1.0F - 0.2F * (resistance.getAmplifier() + 1));
		}
		return raw;
	}

	/** Feather Falling (3 per level) and Protection (1 per level) points, capped at 20, 4 % each. */
	private static float enchantReduction(LocalPlayer player) {
		try {
			var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			Holder<Enchantment> featherFalling = enchantments.getOrThrow(Enchantments.FEATHER_FALLING);
			Holder<Enchantment> protection = enchantments.getOrThrow(Enchantments.PROTECTION);
			int points = 0;
			for (EquipmentSlot slot : ARMOR) {
				ItemStack stack = player.getItemBySlot(slot);
				points += EnchantmentHelper.getItemEnchantmentLevel(protection, stack);
				points += 3 * EnchantmentHelper.getItemEnchantmentLevel(featherFalling, stack);
			}
			return Math.min(20, points) * 0.04F;
		} catch (RuntimeException missing) {
			return 0.0F;
		}
	}

	// ------------------------------------------------------------------ world

	private static void supplies(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		for (int i = 0; i < SUPPLIES.length; i++) {
			int count = count(player, SUPPLIES[i]);
			if (lastSupply[i] > SUPPLY_LOW[i] && count <= SUPPLY_LOW[i]) {
				Component name = new ItemStack(SUPPLIES[i]).getHoverName();
				fire(live, config, Alert.SUPPLIES_LOW, SUPPLIES[i].toString(), msg("supplies_low", name),
					count > 0 ? msg("count_left", count) : msg("none_left"), Icons.of(SUPPLIES[i], Icons.GAPPLE));
			}
			lastSupply[i] = count;
		}
	}

	private static void inventoryFull(LocalPlayer player, AlertTweaksConfig config, boolean live) {
		boolean full = player.getInventory().getFreeSlot() == -1;
		if (full && !wasFull) {
			fire(live, config, Alert.INVENTORY_FULL, "", msg("inventory_full"), msg("inventory_full_detail"), Icons.BUNDLE);
		}
		wasFull = full;
	}

	// ------------------------------------------------------------------ players

	/**
	 * Watched players arriving at or leaving the server, read from the player list the server
	 * sends for Tab. Nothing about where anyone is: players the server hides (vanish) are not on
	 * that list, so they never show, and invisibility does not matter.
	 */
	private static void players(Minecraft mc, AlertTweaksConfig config, boolean live) {
		var connection = mc.getConnection();
		if (connection == null || !live || ++playerTicks % 10 != 0) {
			return;
		}
		int watchHash = 1;
		for (AlertTweaksConfig.WatchedPlayer w : config.players) {
			watchHash = 31 * watchHash + (w.enabled ? w.name.toLowerCase(java.util.Locale.ROOT).hashCode() : 7);
		}
		if (watchHash != lastWatchHash) {
			// The list was edited: say who is online now instead of calling them arrivals.
			lastWatchHash = watchHash;
			playersPrimed = false;
		}
		if (config.players.isEmpty()) {
			watchedOnline.clear();
			return;
		}
		String self = mc.getUser().getName();
		watchedNow.clear();
		for (net.minecraft.client.multiplayer.PlayerInfo info : connection.getListedOnlinePlayers()) {
			String name = info.getProfile().name();
			if (name == null || name.equalsIgnoreCase(self)) {
				continue;
			}
			AlertTweaksConfig.WatchedPlayer watched = config.watched(name);
			if (watched == null || !watched.enabled) {
				continue;
			}
			String key = name.toLowerCase(java.util.Locale.ROOT);
			watchedNow.put(key, info.getSkin());
			displayNames.put(key, name);
		}
		if (!playersPrimed) {
			// Joining a server: one card for everyone already there instead of one per name.
			playersPrimed = true;
			if (!watchedNow.isEmpty() && config.enabled(Alert.PLAYER_ONLINE)) {
				String first = watchedNow.keySet().iterator().next();
				var skin = watchedNow.get(first);
				AlertFeed.HUD.fireFace(Alert.PLAYER_ONLINE, "", watchedNow.size() == 1 ? msg("player_online", displayNames.get(first))
					: msg("player_online_many", displayNames.get(first), watchedNow.size() - 1), msg("player_online_detail", watchedNow.size()),
					() -> skin);
			}
		} else {
			for (Map.Entry<String, net.minecraft.world.entity.player.PlayerSkin> entry : watchedNow.entrySet()) {
				if (!watchedOnline.containsKey(entry.getKey()) && config.enabled(Alert.PLAYER_JOIN)) {
					var skin = entry.getValue();
					AlertFeed.HUD.fireFace(Alert.PLAYER_JOIN, entry.getKey(), msg("player_join", displayNames.get(entry.getKey())),
						msg("player_detail"), () -> skin);
				}
			}
			for (Map.Entry<String, net.minecraft.world.entity.player.PlayerSkin> entry : watchedOnline.entrySet()) {
				if (!watchedNow.containsKey(entry.getKey()) && config.enabled(Alert.PLAYER_LEAVE)) {
					var skin = entry.getValue();
					AlertFeed.HUD.fireFace(Alert.PLAYER_LEAVE, entry.getKey(), msg("player_leave", displayNames.get(entry.getKey())),
						msg("player_detail"), () -> skin);
				}
			}
		}
		watchedOnline.clear();
		watchedOnline.putAll(watchedNow);
	}

	/** Whether a watched name is on the current server's list (for the settings page). */
	public static boolean isOnline(String name) {
		return watchedOnline.containsKey(name.toLowerCase(java.util.Locale.ROOT));
	}

	/** The server's time update arrives every second while it is alive. */
	public static void onServerPulse() {
		long now = Util.getMillis();
		if (lagSinceMs >= 0L) {
			float frozen = (now - lastServerPulseMs) / 1000.0F;
			AlertFeed.HUD.fire(Alert.SERVER_LAG, "", msg("server_back"), msg("server_back_detail", Math.round(frozen * 10.0F) / 10.0F),
				Icons.CLOCK, true);
			AlertFeed.HUD.dismiss(Alert.SERVER_LAG, "");
			lagSinceMs = -1L;
		}
		lastServerPulseMs = now;
	}

	private static void serverLag(Minecraft mc, AlertTweaksConfig config, boolean live) {
		if (mc.isLocalServer() || lastServerPulseMs == 0L || !config.enabled(Alert.SERVER_LAG)) {
			return;
		}
		long now = Util.getMillis();
		long gap = now - lastServerPulseMs;
		long line = Math.round(config.threshold(Alert.SERVER_LAG) * 1000.0F);
		if (gap < line) {
			return;
		}
		Component detail = msg("server_lag_detail", gap / 100 / 10.0F);
		if (lagSinceMs < 0L) {
			lagSinceMs = now;
			fire(live, config, Alert.SERVER_LAG, "", msg("server_lag"), detail, Icons.CLOCK);
		} else if (live) {
			AlertFeed.HUD.fire(Alert.SERVER_LAG, "", msg("server_lag"), detail, Icons.CLOCK, true);
		}
	}
}
