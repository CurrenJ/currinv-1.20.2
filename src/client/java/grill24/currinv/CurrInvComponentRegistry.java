package grill24.currinv;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import grill24.currinv.component.*;
import grill24.currinv.component.accessor.GetFieldValue;
import grill24.currinv.component.accessor.GetNewFieldValue;
import grill24.currinv.component.accessor.SetNewFieldValue;
import grill24.currinv.debug.CurrInvDebugRenderer;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgumentType;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CurrInvComponentRegistry {
    private record ComponentDto(Object instance, Class<?> clazz) {
    }

    private static final List<ComponentDto> COMPONENTS;

    private record ClientTickMethodDto(Object instance, Method method, int tickRate) {
    }

    private static final List<ClientTickMethodDto> CLIENT_TICK_METHODS;

    private record ScreenMethodDto(Object instance, Method method) {
    }

    private static final List<ScreenMethodDto> SCREEN_TICK_METHODS;
    private static final List<ScreenMethodDto> SCREEN_INIT_METHODS;

    private static LiteralArgumentBuilder<FabricClientCommandSource> commandRoot;

    private static int tickCounter = 0;

    static {
        COMPONENTS = new ArrayList<>();
        CLIENT_TICK_METHODS = new ArrayList<>();
        SCREEN_TICK_METHODS = new ArrayList<>();
        SCREEN_INIT_METHODS = new ArrayList<>();

        registerComponent(CurrInvClient.config);
        registerComponent(CurrInvClient.navigator);
        registerComponent(CurrInvClient.sorter);
        registerComponent(CurrInvClient.fullSuiteSorter);
        registerComponent(DebugParticles.class);
        registerComponent(CurrInvClient.currInvDebugRenderer);
    }

    private static void registerComponent(Object component) {
        COMPONENTS.add(new ComponentDto(component, component.getClass()));
    }

    private static void registerComponent(Class<?> clazz) {
        COMPONENTS.add(new ComponentDto(null, clazz));
    }

    public static void registerComponents() {
        registerComponentCommands();
        registerTickEvents();
        registerScreenTickEvents();
    }

    public static void registerComponentCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            commandRoot = buildCommandsFromAnnotations(new ComponentDto(null, CurrInvClient.class), registryAccess);
            for (ComponentDto component : CurrInvComponentRegistry.COMPONENTS) {
                if (ComponentUtility.hasCustomClassAnnotation(component.clazz, Command.class))
                    commandRoot = commandRoot.then(buildCommandsFromAnnotations(component, registryAccess));
            }
            dispatcher.register(commandRoot);
        });
    }

    /**
     * Register tick events for all methods with {@link ClientTick} annotations.
     */
    public static void registerTickEvents() {
        for (ComponentDto component : CurrInvComponentRegistry.COMPONENTS) {
            for (Method method : ComponentUtility.getClientTickMethods(component.clazz)) {
                CLIENT_TICK_METHODS.add(new ClientTickMethodDto(component.instance, method, method.getAnnotation(ClientTick.class).value()));
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            tickCounter++;
            for (ClientTickMethodDto clientTickMethodDto : CLIENT_TICK_METHODS) {
                doClientTick(clientTickMethodDto);
            }
            if (tickCounter == Integer.MAX_VALUE)
                tickCounter = 0;
        });
    }

    /**
     * Register screen tick events for all methods with {@link ScreenTick} or {@link ScreenInit} annotations..
     */
    public static void registerScreenTickEvents() {
        for (ComponentDto component : CurrInvComponentRegistry.COMPONENTS) {
            for (Method method : ComponentUtility.getScreenTickMethods(component.clazz)) {
                SCREEN_TICK_METHODS.add(new ScreenMethodDto(component.instance, method));
            }

            for (Method method : ComponentUtility.getScreenInitMethods(component.clazz)) {
                SCREEN_INIT_METHODS.add(new ScreenMethodDto(component.instance, method));
            }
        }

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?>) {
                for (ScreenMethodDto screenInitMethodDto : SCREEN_INIT_METHODS) {
                    try {
                        screenInitMethodDto.method.setAccessible(true);
                        Parameter[] parameters = screenInitMethodDto.method.getParameters();
                        if (parameters.length == 2 && parameters[0].getType() == MinecraftClient.class && parameters[1].getType() == Screen.class)
                            screenInitMethodDto.method.invoke(screenInitMethodDto.instance, client, screen);
                        else if (parameters.length == 1 && parameters[0].getType() == Screen.class)
                            screenInitMethodDto.method.invoke(screenInitMethodDto.instance, screen);
                        else if (parameters.length == 1 && parameters[0].getType() == MinecraftClient.class)
                            screenInitMethodDto.method.invoke(screenInitMethodDto.instance, client);
                        else if (parameters.length == 0)
                            screenInitMethodDto.method.invoke(screenInitMethodDto.instance);
                        else
                            throw new Exception("Invalid parameters for subCommand action method: " + screenInitMethodDto.method.getName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // Repeatedly called while screen is being rendered (each tick).
                ScreenEvents.afterTick(screen).register((tickScreen) -> {
                    for (ScreenMethodDto screenMethodDto : SCREEN_TICK_METHODS) {
                        try {
                            screenMethodDto.method.setAccessible(true);
                            Parameter[] parameters = screenMethodDto.method.getParameters();
                            if (parameters.length == 2 && parameters[0].getType() == MinecraftClient.class && parameters[1].getType() == Screen.class)
                                screenMethodDto.method.invoke(screenMethodDto.instance, client, screen);
                            else if (parameters.length == 1 && parameters[0].getType() == Screen.class)
                                screenMethodDto.method.invoke(screenMethodDto.instance, screen);
                            else if (parameters.length == 1 && parameters[0].getType() == MinecraftClient.class)
                                screenMethodDto.method.invoke(screenMethodDto.instance, client);
                            else if (parameters.length == 0)
                                screenMethodDto.method.invoke(screenMethodDto.instance);
                            else
                                throw new Exception("Invalid parameters for subCommand action method: " + screenMethodDto.method.getName());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
    }

    /**
     * Build command from an object component whose class has the {@link Command} annotation.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommandsFromAnnotations(ComponentDto component, CommandRegistryAccess commandRegistryAccess) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null) {
            Class<?> clazz = component.clazz;

            if (ComponentUtility.hasCustomClassAnnotation(clazz, Command.class)) {
                Command annotation = clazz.getAnnotation(Command.class);
                String commandKey = annotation.value().isEmpty() ? ComponentUtility.convertDeclarationToCamel(clazz.getSimpleName()) : annotation.value();

                com.mojang.brigadier.Command<FabricClientCommandSource> printInstance = (context) -> {
                    if (component.instance != null)
                        DebugUtility.print(context, component.instance.toString());
                    else
                        DebugUtility.print(context, ComponentUtility.toStringStatic(clazz));
                    return 1;
                };

                LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommandManager.literal(commandKey).executes(printInstance);


                for (LiteralArgumentBuilder<FabricClientCommandSource> subCommand : buildCommandsFromFields(component)) {
                    command = command.then(subCommand);
                }

                for (LiteralArgumentBuilder<FabricClientCommandSource> subCommand : buildCommandsFromMethods(component, commandRegistryAccess)) {
                    command = command.then(subCommand);
                }

                return command;
            }
        }
        return null;
    }

    /**
     * Build commands from fields with the {@link CommandOption} in an object's class.
     */
    private static List<LiteralArgumentBuilder<FabricClientCommandSource>> buildCommandsFromFields(ComponentDto component) {
        Class<?> clazz = component.clazz;

        List<LiteralArgumentBuilder<FabricClientCommandSource>> commands = new ArrayList<>();
        for (Field field : ComponentUtility.getCommandOptionFields(clazz)) {
            Class<?> fieldClass = field.getType();
            CommandOption optionAnnotation = field.getAnnotation(CommandOption.class);
            String optionKey = optionAnnotation.value().isEmpty() ? ComponentUtility.convertDeclarationToCamel(field.getName()) : optionAnnotation.value();

            // Setter method inside our component class, as specified by the annotation.
            final Method setterMethod;
            if (!optionAnnotation.setter().isEmpty()) {
                try {
                    setterMethod = clazz.getMethod(optionAnnotation.setter(), fieldClass);
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            } else setterMethod = null;

            // Getter method inside our component class, as specified by the annotation.
            final Method getterMethod;
            if (!optionAnnotation.getter().isEmpty()) {
                try {
                    getterMethod = clazz.getMethod(optionAnnotation.getter());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            } else getterMethod = null;

            // Get the value of the field from the component instance via reflection. If a getter method is specified, use that instead.
            final GetFieldValue getFieldValue = context -> {
                try {
                    if (getterMethod != null)
                        return getterMethod.invoke(component.instance);
                    else {
                        field.setAccessible(true);
                        return field.get(component.instance);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    return null;
                }
            };

            // By default, print the value of the field when no specific option is provided.
            com.mojang.brigadier.Command<FabricClientCommandSource> noOptionProvidedFunc = (context -> {
                DebugUtility.print(context, optionKey + "=" + getFieldValue.run(component.instance));
                return 1;
            });

            ArgumentType<?> argumentType = null;
            SuggestionProvider<FabricClientCommandSource> suggestionProvider = null;
            final GetNewFieldValue<FabricClientCommandSource> getNewFieldValue;
            final SetNewFieldValue<FabricClientCommandSource> setNewFieldValue;
            if (fieldClass.isEnum()) {
                argumentType = StringArgumentType.string();
                suggestionProvider = new SuggestionProvider<FabricClientCommandSource>() {
                    @Override
                    public CompletableFuture<Suggestions> getSuggestions(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
                        for (Object enumConstant : fieldClass.getEnumConstants()) {
                            builder.suggest(ComponentUtility.convertSnakeToCamel(enumConstant.toString()));
                        }
                        return builder.buildFuture();
                    }
                };

                getNewFieldValue = context -> Enum.valueOf((Class<Enum>) fieldClass, ComponentUtility.convertCamelToSnake(context.getArgument(optionKey, String.class)).toUpperCase());
            } else if (field.getType() == boolean.class) {
                argumentType = BoolArgumentType.bool();

                getNewFieldValue = context -> context.getArgument(optionKey, boolean.class);
            } else {
                getNewFieldValue = null;
            }

            setNewFieldValue = (context, value) -> {
                try {
                    if (setterMethod != null) {
                        setterMethod.invoke(component.instance, value);
                    } else {
                        field.setAccessible(true);
                        field.set(component.instance, value);
                    }
                    return 1;
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                    return -1;
                }
            };

            if (field.getType() == boolean.class) {
                // If the option is a boolean, we want to toggle the value of the field when no specific option is provided.
                noOptionProvidedFunc = (context -> {
                    setNewFieldValue.run(context, !(boolean) getFieldValue.run(component.instance));
                    DebugUtility.print(context, optionKey + "=" + getFieldValue.run(component.instance));
                    return 1;
                });
            }


            LiteralArgumentBuilder<FabricClientCommandSource> subCommand = ClientCommandManager.literal(optionKey);
            subCommand = subCommand.executes(noOptionProvidedFunc);

            if (argumentType != null && getNewFieldValue != null && setNewFieldValue != null) {
                RequiredArgumentBuilder<FabricClientCommandSource, ?> argument = ClientCommandManager.argument(optionKey, argumentType);
                if (suggestionProvider != null)
                    argument = argument.suggests(suggestionProvider);

                argument.executes((context) -> {
                    setNewFieldValue.run(context, getNewFieldValue.run(context));
                    return 1;
                });
                subCommand.then(argument);
            }

            commands.add(subCommand);
        }
        return commands;
    }

    /**
     * Build commands from methods with the {@link CommandAction} in an object's class.
     */
    private static List<LiteralArgumentBuilder<FabricClientCommandSource>> buildCommandsFromMethods(ComponentDto component, CommandRegistryAccess commandRegistryAccess) {
        Class<?> clazz = component.clazz;
        MinecraftClient client = MinecraftClient.getInstance();
        assert client != null;

        List<LiteralArgumentBuilder<FabricClientCommandSource>> commands = new ArrayList<>();
        for (Method method : ComponentUtility.getCommandActionMethods(clazz)) {
            CommandAction actionAnnotation = method.getAnnotation(CommandAction.class);
            String actionKey = actionAnnotation.value().isEmpty() ? ComponentUtility.convertDeclarationToCamel(method.getName()) : actionAnnotation.value();

            com.mojang.brigadier.Command<FabricClientCommandSource> action = (context) -> {
                try {
                    Parameter[] parameters = method.getParameters();
                    method.setAccessible(true);
                    // Yeesh.
                    if (parameters.length == 2 && parameters[0].getType() == MinecraftClient.class && parameters[1].getType() == CommandContext.class)
                        method.invoke(component.instance, client, context);
                    else if (parameters.length == 1 && parameters[0].getType() == CommandContext.class)
                        method.invoke(component.instance, context);
                    else if (parameters.length == 1 && parameters[0].getType() == MinecraftClient.class)
                        method.invoke(component.instance, client);
                    else if (parameters.length == 0)
                        method.invoke(component.instance);
                    else
                        throw new Exception("Invalid parameters for subCommand action method: " + method.getName());
                    return 1;
                } catch (Exception e) {
                    e.printStackTrace();
                    return -1;
                }
            };

            RequiredArgumentBuilder<FabricClientCommandSource, ?> commandArgument = null;
            if (actionAnnotation.arguments().length == actionAnnotation.argumentKeys().length) {
                for (int i = 0; i < actionAnnotation.arguments().length; i++) {
                    Class<? extends ArgumentType> argumentType = actionAnnotation.arguments()[i];
                    String argumentKey = actionAnnotation.argumentKeys()[i];

                    if (argumentType == ItemStackArgumentType.class)
                        commandArgument = ClientCommandManager.argument(argumentKey, ItemStackArgumentType.itemStack(commandRegistryAccess));
                    else if (argumentType == StringArgumentType.class)
                        commandArgument = ClientCommandManager.argument(argumentKey, StringArgumentType.string());
                    else
                        throw new RuntimeException("Unsupported argument type: " + argumentType.getName());
                }
            } else {
                throw new RuntimeException("Number of argument types does not match number of argument keys for subCommand action method: " + method.getName());
            }

            LiteralArgumentBuilder<FabricClientCommandSource> subCommand = ClientCommandManager.literal(actionKey);
            if (commandArgument != null)
                subCommand = subCommand.then(commandArgument.executes(action));
            else
                subCommand = subCommand.executes(action);

            commands.add(subCommand);
        }
        return commands;
    }

    private static void doClientTick(ClientTickMethodDto clientTickMethodDto) {
        if (tickCounter % clientTickMethodDto.tickRate == 0) {
            try {
                clientTickMethodDto.method.setAccessible(true);
                Parameter[] parameters = clientTickMethodDto.method.getParameters();
                if (parameters.length == 0)
                    clientTickMethodDto.method.invoke(clientTickMethodDto.instance);
                else if (parameters.length == 1 && parameters[0].getType() == MinecraftClient.class)
                    clientTickMethodDto.method.invoke(clientTickMethodDto.instance, MinecraftClient.getInstance());
                else
                    throw new Exception("Invalid parameters for client tick method: " + clientTickMethodDto.method.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
