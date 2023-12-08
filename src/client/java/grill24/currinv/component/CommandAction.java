package grill24.currinv.component;


import com.mojang.brigadier.arguments.ArgumentType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandAction {
    String value();

    Class<? extends ArgumentType>[] arguments() default {};
    String[] argumentKeys() default {};
}

