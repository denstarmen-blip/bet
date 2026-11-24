package com.example.carcass;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Поки гравець розрізає труп, його ставлять у центр тіла й повертають камеру вниз.
 * Коли розрізання закінчилось (або гравець відпустив кнопку), повертають на старе місце.
 */
public final class CutSession {
    private static final Map<UUID, CutSession> SESSIONS = new HashMap<>();

    private final BlockPos corpse;
    private final double cx, cy, cz;   // центр тіла (куди ставимо гравця)
    private final double ox, oy, oz;   // де гравець стояв раніше
    private final float oldPitch;
    private long lastHit;

    private CutSession(BlockPos corpse, double cx, double cy, double cz,
                       double ox, double oy, double oz, float oldPitch, long lastHit) {
        this.corpse = corpse;
        this.cx = cx; this.cy = cy; this.cz = cz;
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.oldPitch = oldPitch;
        this.lastHit = lastHit;
    }

    /** Викликається на кожен "удар" ножем: починає сесію або продовжує поточну. */
    public static void begin(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        CutSession existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            if (existing.corpse.equals(pos)) existing.lastHit = now;
            return;
        }

        double tx = pos.getX() + 0.5;
        double ty = pos.getY() + 0.376; // стаємо на верх блока-трупа
        double tz = pos.getZ() + 0.5;

        // Якщо в центрі тіла нема місця (стеля тощо), просто ріжемо без переміщення
        AABB box = player.getDimensions(player.getPose()).makeBoundingBox(tx, ty, tz);
        if (!level.noCollision(player, box)) return;

        SESSIONS.put(player.getUUID(), new CutSession(pos, tx, ty, tz,
                player.getX(), player.getY(), player.getZ(), player.getXRot(), now));

        // дивимось строго вниз на тіло
        player.connection.teleport(tx, ty, tz, player.getYRot(), 90.0f);
    }

    /** Розрізання завершено: повертаємо гравця на місце. */
    public static void finish(ServerPlayer player) {
        end(player, true);
    }

    private static void end(ServerPlayer player, boolean teleportBack) {
        CutSession s = SESSIONS.remove(player.getUUID());
        if (s == null || !teleportBack) return;
        player.connection.teleport(s.ox, s.oy, s.oz, player.getYRot(), s.oldPitch);
        player.fallDistance = 0;
    }

    public static class Events {
        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer player)) return;

            CutSession s = SESSIONS.get(player.getUUID());
            if (s == null) return;

            ServerLevel level = player.serverLevel();

            // труп зник (згнив, зламали): повертаємо гравця
            if (!(level.getBlockState(s.corpse).getBlock() instanceof CorpseBlock)) {
                end(player, true);
                return;
            }

            // гравець сам відійшов: не смикаємо його назад
            double dx = player.getX() - s.cx;
            double dz = player.getZ() - s.cz;
            double dy = player.getY() - s.cy;
            if (dx * dx + dz * dz > 2.25 || Math.abs(dy) > 3.0) {
                SESSIONS.remove(player.getUUID());
                return;
            }

            // відпустив ПКМ більш ніж на ~0.6 с: повертаємо
            if (level.getGameTime() - s.lastHit > 12) {
                end(player, true);
            }
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            SESSIONS.remove(event.getEntity().getUUID());
        }
    }
}
