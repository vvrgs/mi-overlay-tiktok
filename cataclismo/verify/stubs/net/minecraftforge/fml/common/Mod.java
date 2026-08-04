package net.minecraftforge.fml.common;

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
public @interface Mod {
    String value();

    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)
    @interface EventBusSubscriber {
        enum Bus { FORGE, MOD }
        String modid() default "";
        Bus bus() default Bus.FORGE;
        net.minecraftforge.api.distmarker.Dist[] value() default {};
    }
}
