package dev.zymekoh.alerttweaks.client.mixin;

import dev.zymekoh.alerttweaks.client.alert.AlertMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two packets the server already sends to every client. Both handlers first bounce to the main
 * thread, so TAIL only runs there.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void alertTweaks$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Entity entity = packet.getEntity(mc.level);
		if (entity != null) {
			AlertMonitor.onEntityEvent(entity, packet.getEventId());
		}
	}

	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void alertTweaks$serverPulse(ClientboundSetTimePacket packet, CallbackInfo ci) {
		AlertMonitor.onServerPulse();
	}
}
