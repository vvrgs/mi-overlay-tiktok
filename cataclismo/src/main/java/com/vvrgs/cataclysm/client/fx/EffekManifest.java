package com.vvrgs.cataclysm.client.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.fx.FxEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Manifest hot-swap de effeks: assets/cataclysm/effeks/manifest.json
 * mapea evento de FX (clave en minusculas) -> ruta del .efkefc SIN extension
 * ("" = solo FX nativos). El streamer suelta sus .efkefc en la carpeta,
 * edita el manifest, recarga recursos (F3+T) y NO se toca codigo.
 */
public final class EffekManifest implements ResourceManagerReloadListener {

    public static final EffekManifest INSTANCE = new EffekManifest();
    private static final ResourceLocation MANIFEST =
            new ResourceLocation(Cataclysm.MODID, "effeks/manifest.json");

    private final Map<String, ResourceLocation> mapping = new HashMap<>();

    private EffekManifest() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        mapping.clear();
        Optional<Resource> resource = manager.getResource(MANIFEST);
        if (resource.isEmpty()) {
            Cataclysm.LOGGER.info("[cataclysm] sin effeks/manifest.json: FX nativos solamente");
            return;
        }
        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                String value = entry.getValue().getAsString().trim();
                if (value.isEmpty()) {
                    continue; // "" = este evento va solo con FX nativos
                }
                ResourceLocation effek = value.contains(":")
                        ? new ResourceLocation(value)
                        : new ResourceLocation(Cataclysm.MODID, value);
                mapping.put(key, effek);
            }
            Cataclysm.LOGGER.info("[cataclysm] manifest de effeks cargado: {} mapeos", mapping.size());
        } catch (Exception e) {
            Cataclysm.LOGGER.warn("[cataclysm] effeks/manifest.json invalido: {}", e.toString());
        }
    }

    /** Effek mapeado para un evento, o null (FX nativos solamente). */
    @Nullable
    public ResourceLocation effekFor(FxEvent event) {
        return mapping.get(event.manifestKey());
    }
}
