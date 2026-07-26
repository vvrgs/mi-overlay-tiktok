# Hallazgos — verificación de compilación contra stubs (1.20.1 mojmap + Forge 47)

Resultado final: `bash verify/compile.sh` → **exit 0, 0 errores de javac** (59 fuentes del mod + 166 stubs).

## Cambios en el código fuente del mod

### 1. FxDirector.java — helper `cone(Vec3, double)` inexistente (inconsistencia interna)
- **Archivo/líneas:** `src/main/java/com/vvrgs/irontempest/client/fx/FxDirector.java` — usos en las líneas 173, 177 y 185 (`muzzleFlash`); helper añadido en la sección de helpers, justo después de `randomDir()` (~línea 405).
- **Qué estaba mal:** `muzzleFlash(...)` llamaba tres veces a un método privado `cone(dir, spread)` que no existía ni en la clase ni en ningún otro archivo del mod. No es un problema de API de Minecraft sino una inconsistencia interna del mod (helper referenciado pero nunca escrito). Eran los únicos 3 errores de compilación reales del código fuente.
- **Fix (mínimo):** se añadió el helper privado con la semántica que implican los call-sites (dirección unitaria dentro de un cono alrededor de `dir`, con apertura relativa `spread`; el llamador aplica la magnitud con `.scale(...)`), usando solo API real de `Vec3` ya empleada en el mismo archivo:
  ```java
  /** Dirección unitaria dentro de un cono alrededor de {@code dir} (spread = apertura relativa). */
  private static Vec3 cone(Vec3 dir, double spread) {
      return dir.add(randomDir().scale(spread)).normalize();
  }
  ```

No hizo falta ningún otro cambio de fuente: el resto del mod usa el API real de 1.20.1 mojmap / Forge 47 con firmas correctas.

## Notas de modelado de stubs (puntos delicados verificados, sin cambios de fuente)

- `ClientboundFxPacket` es record con `encode(FriendlyByteBuf)` / ctor `(FriendlyByteBuf)` manuales; casan con `SimpleChannel.MessageBuilder.encoder(BiConsumer<MSG,FriendlyByteBuf>)`, `.decoder(Function<FriendlyByteBuf,MSG>)` y `.consumerMainThread(BiConsumer<MSG,Supplier<NetworkEvent.Context>>)` reales de Forge 47.
- `ClientSetup.onRegisterShaders` declara `throws java.io.IOException` — válido: es un método `@SubscribeEvent` (reflexión, no interfaz funcional). El stub de `ShaderInstance` modela los DOS ctors reales de 1.20.1: `(ResourceProvider, String, VertexFormat)` y `(ResourceProvider, ResourceLocation, VertexFormat)` (este último existe desde 1.19.3 para core-shaders con namespace); `RegisterShadersEvent.getResourceProvider()` es el accessor real en Forge 47.
- `PostFxManager`: `EffectInstance(ResourceManager, String)` (lanza `IOException`), `setSampler(String, IntSupplier)`, `safeGetUniform` → `com.mojang.blaze3d.shaders.AbstractUniform`; `TextureTarget(int,int,boolean,boolean)` con campos públicos `width/height/frameBufferId` heredados de `RenderTarget`; `GlStateManager._glBindFramebuffer(int,int)` y `_glBlitFrameBuffer(10×int)`; constantes de `GL30`; `Minecraft.ON_OSX`; `RenderSystem.resetTextureMatrix()` — todo según el API real.
- `ModRenderTypes extends RenderStateShard`: modelado fiel — `RenderType.create(...)` **public static**; los shards `LIGHTNING_TRANSPARENCY` / `NO_CULL` / `COLOR_WRITE` como `protected static final` y las clases anidadas `ShaderStateShard` / `TextureStateShard` (extends `EmptyTextureStateShard`) como **`protected static` en `RenderStateShard`** (su ubicación real): compilar exige de verdad el patrón "extender RenderStateShard". `CompositeStateBuilder` con `setShaderState/setTextureState/setTransparencyState/setWriteMaskState/setCullState/createCompositeState(boolean)`.
- `EffekBridge` usa reflexión pura sobre AAA Particles: no se creó ningún stub de `mod.chloeprime.aaaparticles.*` (solo gson/slf4j/LogUtils y `ResourceManager.getResource` → `Optional<Resource>`, `Resource.open()`).
- Jerarquías fieles: `Entity → LivingEntity → Player → ServerPlayer` (con `defineSynchedData/readAdditionalSaveData/addAdditionalSaveData` `protected` y overridables, implementados a partir de `LivingEntity` como en vanilla); `Particle → SingleQuadParticle → TextureSheetParticle` (campos `protected`: `xd/yd/zd`, `x/y/z`, `xo/yo/zo`, `gravity`, `friction`, `hasPhysics`, `lifetime`, `age`, `rCol/gCol/bCol/alpha`, `roll/oRoll`, `onGround`, `quadSize`; `getU0..getV1` protected; `getLightColor(float)` protected).
- `@FunctionalInterface` donde el mod usa lambdas/method-refs: `ParticleProvider`, `EntityRendererProvider`, `EntityType.EntityFactory`, `ParticleEngine.SpriteParticleRegistration`, `com.mojang.brigadier.Command` (con `throws CommandSyntaxException`, necesario para los executes de `WarCommands` que llaman a `EntityArgument.getPlayer`), `com.mojang.math.Axis`.
- Firmas 1.20 verificadas de memoria contra código vanilla/Forge concreto: `CommandSourceStack.sendSuccess(Supplier<Component>, boolean)` (cambió en 1.20), `Entity.level()` y `Entity.onGround()` como métodos (1.20+), `ServerPlayer.serverLevel()`, `BlockPos.containing(Position)`, `SoundEvent.createVariableRangeEvent`, `TickEvent.ServerTickEvent.getServer()` (añadido en Forge 45+), `ViewportEvent.getPartialTick()` → `double` vs `RenderLevelStageEvent.getPartialTick()` → `float`, `SoundInstance.createUnseededRandom()`, `EntityBoundSoundInstance(SoundEvent, SoundSource, float, float, Entity, long)`.
- [DUDA] `Mth.RAD_TO_DEG`: estoy razonablemente seguro de que existe en 1.20.1 junto a `DEG_TO_RAD` (ambos `public static final float` en `Mth` desde 1.19.x, usados por código vanilla de render); si la revisión final de mappings lo desmintiera, el equivalente sería `* (180F / (float) Math.PI)`. No se tocó el fuente.
- [DUDA] `SimpleParticleType(boolean)`: modelado como ctor **public** — es el patrón estándar de registro en mods 1.19/1.20 (`() -> new SimpleParticleType(true)`) y compila en entornos reales, lo que confirma la visibilidad pública.
