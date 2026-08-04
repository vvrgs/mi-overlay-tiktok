package com.vvrgs.cataclysm.fx;

import com.vvrgs.cataclysm.network.CataclysmNetwork;
import com.vvrgs.cataclysm.network.ClearFxPacket;
import com.vvrgs.cataclysm.network.FxEventPacket;
import com.vvrgs.cataclysm.network.SkyFxPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Director de FX del lado servidor: emite eventos puntuales (radio 256),
 * estados de cielo globales y sonido. Todo el detalle visual vive en el
 * cliente (recetas cronologicas) — el server solo describe QUE paso y DONDE.
 */
public final class FxDirector {

    private static final double FX_RANGE = 256.0D;

    public static void fire(ServerLevel level, FxEvent type, Vec3 pos, float intensity, int data) {
        FxEventPacket packet = new FxEventPacket(type, pos.x, pos.y, pos.z,
                intensity, level.random.nextInt(), data);
        CataclysmNetwork.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        pos.x, pos.y, pos.z, FX_RANGE, level.dimension())),
                packet);
    }

    public static void fire(ServerLevel level, FxEvent type, Vec3 pos, float intensity) {
        fire(level, type, pos, intensity, 0);
    }

    /** Estado de cielo para TODOS los jugadores (impactor, tintes, agujero negro). */
    public static void sky(MinecraftServer server, SkyFx type, int durationTicks, float intensity) {
        CataclysmNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new SkyFxPacket(type, durationTicks, intensity));
    }

    public static void skyClear(MinecraftServer server, SkyFx type) {
        sky(server, type, 0, 0.0F);
    }

    /** stopall: corta recetas, shake, overlays y cielo en todos los clientes. */
    public static void clearAll(MinecraftServer server) {
        CataclysmNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new ClearFxPacket());
    }

    /** Sonido posicional. Radio audible = 16 x volumen: avisos lejanos con 3.0+. */
    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.WEATHER, volume, pitch);
    }

    /** Sonido global sin radio: sirenas y avisos tier U, a cada jugador. */
    public static void globalSound(MinecraftServer server, SoundEvent sound, float volume, float pitch) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        }
    }

    private FxDirector() {
    }
}
