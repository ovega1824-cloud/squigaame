package com.squidgame.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.squidgame.SquidGameMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/** Eventos de cliente de la final: modelo, zoom, saltar bloqueado y cargar/lanzar/soltar. */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID, value = Dist.CLIENT)
public class ClientFinalEvents {
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (!ClientFinal.hasModel(p.getUUID())) return;
        FinalModel model = FinalModel.get();
        if (model == null) return; // si el modelo no cargó, se queda el normal

        event.setCanceled(true);
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();

        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) - bodyYaw);
        float headPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        float limbAmount = Math.min(1.0F, Mth.lerp(pt, p.animationSpeedOld, p.animationSpeed));
        float limbSwing = p.animationPosition - p.animationSpeed * (1.0F - pt);
        float down = ClientFinal.downAmount(p.getUUID(), pt);
        ResourceLocation skin = p instanceof AbstractClientPlayer acp
                ? acp.getSkinTextureLocation()
                : new ResourceLocation("textures/entity/steve.png");

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
        model.render(ps, event.getMultiBufferSource(), skin, event.getPackedLight(),
                LivingEntityRenderer.getOverlayCoords(p, 0.0F), down, limbSwing, limbAmount, headYaw, headPitch);
        ps.popPose();
        ClientNames.renderNumber(p, ps, event.getMultiBufferSource(), event.getPackedLight());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientFinal.tick(Minecraft.getInstance());
    }

    /** Cámara con zoom cuando estás tirado. */
    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (ClientFinal.isDowned(event.getPlayer().getUUID())) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.7F);
        }
    }

    /** Tirado: no puedes saltar. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() == mc.player && ClientFinal.isDowned(mc.player.getUUID())) {
            Vec3 m = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(m.x, 0.0D, m.z);
        }
    }
}
