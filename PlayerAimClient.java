package com.example.playeraim;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Player Aim: apunta la cámara al jugador más cercano dentro de un radio de 10 bloques.
 * Solo actúa sobre jugadores (nunca sobre mobs). Se activa y desactiva con una tecla (G por defecto).
 */
public class PlayerAimClient implements ClientModInitializer {

	public static final String MOD_ID = "playeraim";

	/** Radio máximo en bloques. */
	private static final double RANGE = 10.0;
	private static final double RANGE_SQ = RANGE * RANGE;

	/** 1.0 = apunta al instante. Valores menores (ej. 0.3) suavizan el giro. */
	private static final float SMOOTHING = 1.0F;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "main")
	);

	private static KeyMapping toggleKey;
	private static boolean enabled = false;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.playeraim.toggle",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_G,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(PlayerAimClient::onTick);
	}

	private static void onTick(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			if (mc.player != null) {
				mc.player.displayClientMessage(
						Component.translatable(enabled ? "message.playeraim.on" : "message.playeraim.off"),
						true
				);
			}
		}

		if (!enabled) {
			return;
		}

		LocalPlayer self = mc.player;
		if (self == null || mc.level == null || mc.screen != null) {
			return;
		}

		AbstractClientPlayer target = findNearestPlayer(mc, self);
		if (target == null) {
			return;
		}

		aimAt(self, target.getBoundingBox().getCenter());
	}

	private static AbstractClientPlayer findNearestPlayer(Minecraft mc, LocalPlayer self) {
		AbstractClientPlayer nearest = null;
		double best = RANGE_SQ;

		for (AbstractClientPlayer other : mc.level.players()) {
			if (other == self || !other.isAlive() || other.isSpectator()) {
				continue;
			}
			double dist = self.distanceToSqr(other);
			if (dist <= best) {
				best = dist;
				nearest = other;
			}
		}
		return nearest;
	}

	private static void aimAt(LocalPlayer self, Vec3 point) {
		Vec3 eyes = self.getEyePosition();
		double dx = point.x - eyes.x;
		double dy = point.y - eyes.y;
		double dz = point.z - eyes.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);

		float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));

		float currentYaw = self.getYRot();
		float currentPitch = self.getXRot();

		// wrapDegrees evita giros de 360° al cruzar el límite -180/180.
		float newYaw = currentYaw + Mth.wrapDegrees(targetYaw - currentYaw) * SMOOTHING;
		float newPitch = Mth.clamp(currentPitch + (targetPitch - currentPitch) * SMOOTHING, -90.0F, 90.0F);

		self.setYRot(newYaw);
		self.setXRot(newPitch);
	}
}
