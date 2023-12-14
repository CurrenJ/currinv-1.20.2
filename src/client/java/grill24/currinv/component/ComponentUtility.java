package grill24.currinv.component;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import grill24.currinv.ModComponentRegistry;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ComponentUtility {

    public static boolean hasCustomClassAnnotation(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        return clazz.isAnnotationPresent(annotationClass);
    }

    public static String convertSnakeToCamel(String snakeCase) {
        StringBuilder camelCase = new StringBuilder();
        boolean capitalizeNext = false;

        for (char character : snakeCase.toLowerCase().toCharArray()) {
            if (character == '_') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    camelCase.append(Character.toUpperCase(character));
                    capitalizeNext = false;
                } else {
                    camelCase.append(character);
                }
            }
        }

        return camelCase.toString();
    }

    public static String convertCamelToSnake(String camelCase) {
        StringBuilder snakeCase = new StringBuilder();

        for (char character : camelCase.toCharArray()) {
            if (Character.isUpperCase(character)) {
                snakeCase.append('_');
                snakeCase.append(Character.toLowerCase(character));
            } else {
                snakeCase.append(character);
            }
        }

        return snakeCase.toString();
    }

    public static String convertDeclarationToCamel(String declaration) {
        return Character.toLowerCase(declaration.charAt(0)) + declaration.substring(1);
    }

    public static Method[] getScreenTickMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(ScreenTick.class) && hasCorrectScreenParameterSignature(method))
                .toArray(Method[]::new);
    }

    public static Method[] getScreenInitMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(ScreenInit.class) && hasCorrectScreenParameterSignature(method))
                .toArray(Method[]::new);
    }

    public static Method getStaticToStringMethod(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(StaticToString.class))
                .findFirst()
                .orElse(null);
    }

    public static String toStringStatic(Class<?> clazz) {
        Method method = getStaticToStringMethod(clazz);
        if (method != null) {
            try {
                return method.invoke(null).toString();
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    private static boolean hasCorrectScreenParameterSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean correctParameters = parameterTypes.length == 0
                || (parameterTypes.length == 1 && parameterTypes[0] == MinecraftClient.class)
                || (parameterTypes.length == 1 && parameterTypes[0] == Screen.class)
                || (parameterTypes.length == 2 && parameterTypes[0] == MinecraftClient.class && parameterTypes[1] == Screen.class);
        if (!correctParameters)
            System.out.println("WARNING: Incorrect parameters for screen tick method: " + method.getName());
        return correctParameters;
    }

    public static Field[] getCommandOptionFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        return Arrays.stream(fields)
                .filter(field -> field.isAnnotationPresent(CommandOption.class))
                .toArray(Field[]::new);
    }

    public static Method[] getCommandActionMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(CommandAction.class))
                .toArray(Method[]::new);
    }

    public static Method[] getClientTickMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        return Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(ClientTick.class) && hasCorrectClientTickParameterSignature(method))
                .toArray(Method[]::new);
    }

    private static boolean hasCorrectClientTickParameterSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        boolean correctParameters = parameterTypes.length == 0
                || (parameterTypes.length == 1 && parameterTypes[0] == MinecraftClient.class);
        if (!correctParameters)
            System.out.println("WARNING: Incorrect parameters for client tick method: " + method.getName());
        return correctParameters;
    }

    public static String getCommandKey(Class<?> clazz) {
        if(hasCustomClassAnnotation(clazz, Command.class)) {
            Command annotation = clazz.getAnnotation(Command.class);
            return annotation.value().isEmpty() ? ComponentUtility.convertDeclarationToCamel(clazz.getSimpleName()) : annotation.value();
        }
        return "";
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> getCommandOrElse(ModComponentRegistry.CommandTreeNode commandTreeRoot, String commandKey, LiteralArgumentBuilderSupplier value) {
        LiteralArgumentBuilder<FabricClientCommandSource> command;
        if(commandTreeRoot != null && commandTreeRoot.getChildNode(commandKey).isPresent())
            return commandTreeRoot.getChildNode(commandKey).get().command;
        else {
            LiteralArgumentBuilder<FabricClientCommandSource> newCommand = value.run(commandKey);
            if(commandTreeRoot != null) {
                ModComponentRegistry.CommandTreeNode node = new ModComponentRegistry.CommandTreeNode(commandKey, newCommand);
                commandTreeRoot.children.put(commandKey, node);
            }
            return newCommand;
        }
    }
}
