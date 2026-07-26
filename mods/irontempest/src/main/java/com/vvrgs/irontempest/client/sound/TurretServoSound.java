package com.vvrgs.irontempest.client.sound;

import com.vvrgs.irontempest.entity.TankEntity;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Servo eléctrico de la torreta: suena SOLO mientras la torreta gira, con
 * volumen proporcional a la velocidad angular (suavizado para no petardear).
 */
public class TurretServoSound extends AbstractTickableSoundInstance {

    private final TankEntity tank;
    private float smoothed;

    public TurretServoSound(TankEntity tank) {
        super(ModSounds.TURRET_SERVO.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.tank = tank;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.x = tank.getX();
        this.y = tank.getY();
        this.z = tank.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.tank.isRemoved()) {
            stop();
            return;
        }
        this.x = this.tank.getX();
        this.y = this.tank.getY() + 1.2D;
        this.z = this.tank.getZ();
        float angular = Math.abs(Mth.wrapDegrees(this.tank.getTurretYaw() - this.tank.turretYawO));
        float targetVol = Mth.clamp(angular / 2.2F, 0.0F, 1.0F) * 0.85F;
        this.smoothed += (targetVol - this.smoothed) * 0.25F;
        this.volume = this.smoothed < 0.02F ? 0.0F : this.smoothed;
        this.pitch = 0.95F + this.smoothed * 0.15F;
    }
}
