package com.example.carcass.client;

import com.example.carcass.Carcass;
import com.example.carcass.CorpseBlock;
import com.example.carcass.KnifeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Carcass.MODID, value = Dist.CLIENT)
public class CutClientHandler {
    private static double zoom = 1.0;

    private static boolean lookingAtCorpseWithKnife(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        if (!(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof CorpseBlock)) return false;
        return KnifeHelper.isKnife(mc.player.getMainHandItem()) || KnifeHelper.isKnife(mc.player.getOffhandItem());
    }

    /** Під час розрізання камера плавно наближається до тіла. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return; // руку не чіпаємо
        Minecraft mc = Minecraft.getInstance();
        boolean cutting = lookingAtCorpseWithKnife(mc) && mc.options.keyUse.isDown();
        double target = cutting ? 0.45 : 1.0;
        zoom += (target - zoom) * 0.12;
        if (!cutting && Math.abs(zoom - 1.0) < 0.003) zoom = 1.0;
        if (zoom != 1.0) {
            event.setFOV(event.getFOV() * zoom);
        }
    }

    /** Підказка під прицілом, як у вашому відео. */
    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!lookingAtCorpseWithKnife(mc) || mc.options.keyUse.isDown()) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        g.drawCenteredString(mc.font, Component.translatable("hint.carcass.hold_to_cut"), w / 2, h / 2 + 12, 0xFFFFFF);
    }
}
