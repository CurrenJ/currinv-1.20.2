package grill24.currinv.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CommandOption {
    /**
     * The key that will be used in the created command. If not specified, the name of the field will be used.
      */
    String value() default "";

    /**
     * If specified, overrides the parent command literal specified in the {@link Command} tag.
     */
    String parentKey() default "";

    /**
     * Specify a custom setter, otherwise reflection will be used to set the field.
     */
    String setter() default "";

    /**
     * Specify a custom getter, otherwise reflection will be used to get the field. This is only used for printing components and toggling the value of booleans at the moment. Could be used for other things in the future.
     */
    String getter() default "";
}

