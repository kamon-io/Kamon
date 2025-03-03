package kanela.agent.api.instrumentation.mixin;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Designates a method in a Mixin class that should be called exactly once during the construction
 * of each target type.
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface Initializer {}
