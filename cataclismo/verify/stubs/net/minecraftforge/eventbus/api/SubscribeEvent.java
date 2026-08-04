package net.minecraftforge.eventbus.api;

@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
public @interface SubscribeEvent {
    EventPriority priority() default EventPriority.NORMAL;
    boolean receiveCanceled() default false;
}
