package com.vvrgs.irontempest.client.effek;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Puente 100% por reflexión hacia AAA Particles (mod opcional "aaa_particles").
 * Si el mod no está, o su API no coincide, o cualquier invocación falla, el
 * puente se desactiva para siempre (log una sola vez, nunca crashea).
 *
 * El mapping evento → effek vive en assets/irontempest/effeks/manifest.json;
 * las entradas con valor "" se ignoran. FxDirector llama con el nombre del
 * FxType en minúsculas, así que además del lookup directo hay una tabla de
 * alias FxType → clave del manifest (el lookup directo tiene prioridad).
 */
public final class EffekBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Alias FxType (minúsculas) → clave de evento del manifest.
    private static final Map<String, String> FXTYPE_ALIASES = Map.ofEntries(
            Map.entry("explosion_small", "rocket.impact"),
            Map.entry("explosion_large", "missile.impact"),
            Map.entry("airburst", "missile.impact"),
            Map.entry("muzzle_flash", "tank.muzzle"),
            Map.entry("tank_landing", "tank.landing"),
            Map.entry("warp_in", "orbital.warp_in"),
            Map.entry("warp_out", "orbital.warp_out"),
            Map.entry("charge_burst", "orbital.charge"),
            Map.entry("beam_sweep", "orbital.beam"),
            Map.entry("overload_pulse", "orbital.overload"),
            Map.entry("armageddon_opening", "armageddon.opening"));

    private static final Map<String, String> EVENT_TO_EFFEK = new HashMap<>();

    private static boolean initialized;
    private static boolean disabled;

    // AAALevel.addParticle(Level, boolean, ParticleEmitterInfo) — estático.
    private static Method addParticleMethod;
    // ParticleEmitterInfo(ResourceLocation) + fluidos position(...) y rotationLocal(...).
    private static Constructor<?> emitterInfoCtor;
    private static Method positionMethod;
    private static Method rotationLocalMethod;

    /** Lanza el effek asociado al evento en (x,y,z). Silencioso si no aplica. */
    public static void play(String eventKey, double x, double y, double z) {
        playInternal(eventKey, x, y, z, null);
    }

    /** Variante con rotación local (grados/radianes según convención de AAA Particles). */
    public static void play(String eventKey, double x, double y, double z,
                            float rotX, float rotY, float rotZ) {
        playInternal(eventKey, x, y, z, new float[] {rotX, rotY, rotZ});
    }

    private static void playInternal(String eventKey, double x, double y, double z,
                                     float[] rotation) {
        ensureInit();
        if (disabled) {
            return;
        }
        String path = EVENT_TO_EFFEK.get(eventKey);
        if (path == null) {
            String alias = FXTYPE_ALIASES.get(eventKey);
            if (alias != null) {
                path = EVENT_TO_EFFEK.get(alias);
            }
        }
        if (path == null || path.isEmpty()) {
            return; // evento sin effek asignado: solo FX nativos
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        try {
            Object info = emitterInfoCtor.newInstance(new ResourceLocation("irontempest", path));
            positionMethod.invoke(info, x, y, z);
            if (rotation != null) {
                rotationLocalMethod.invoke(info, rotation[0], rotation[1], rotation[2]);
            }
            addParticleMethod.invoke(null, level, true, info);
        } catch (Throwable t) {
            disabled = true;
            LOGGER.error("[irontempest] Error invocando AAA Particles: puente effek desactivado", t);
        }
    }

    private static synchronized void ensureInit() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded("aaa_particles")) {
            disabled = true;
            LOGGER.info("[irontempest] AAA Particles no instalado: eventos effek solo con FX nativos");
            return;
        }
        try {
            Class<?> aaaLevel = Class.forName("mod.chloeprime.aaaparticles.api.common.AAALevel");
            Class<?> emitterInfo = Class.forName("mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo");
            addParticleMethod = aaaLevel.getMethod("addParticle", Level.class, boolean.class, emitterInfo);
            emitterInfoCtor = emitterInfo.getConstructor(ResourceLocation.class);
            positionMethod = emitterInfo.getMethod("position", double.class, double.class, double.class);
            rotationLocalMethod = emitterInfo.getMethod("rotationLocal", float.class, float.class, float.class);
        } catch (Throwable t) {
            disabled = true;
            LOGGER.warn("[irontempest] AAA Particles presente pero API incompatible: puente effek desactivado", t);
            return;
        }
        loadManifest();
    }

    private static void loadManifest() {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager()
                    .getResource(new ResourceLocation("irontempest", "effeks/manifest.json"));
            if (resource.isEmpty()) {
                LOGGER.warn("[irontempest] effeks/manifest.json no encontrado: puente effek sin eventos");
                return;
            }
            try (InputStream in = resource.get().open();
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject events = root.getAsJsonObject("events");
                if (events == null) {
                    LOGGER.warn("[irontempest] effeks/manifest.json sin objeto 'events': puente effek sin eventos");
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : events.entrySet()) {
                    String value = entry.getValue().getAsString();
                    if (!value.isEmpty()) {
                        EVENT_TO_EFFEK.put(entry.getKey(), value);
                    }
                }
            }
            LOGGER.info("[irontempest] Puente effek activo: {} eventos con effek asignado",
                    EVENT_TO_EFFEK.size());
        } catch (Throwable t) {
            LOGGER.warn("[irontempest] No se pudo leer effeks/manifest.json: puente effek sin eventos", t);
        }
    }

    private EffekBridge() {}
}
