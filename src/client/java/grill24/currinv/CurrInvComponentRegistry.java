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
     * Build command from an object component who's class has the {@link Command} annotation.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommandsFromAnnotations(ComponentDto component, CommandRegistryAccess commandRegistryAccess) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null) {
            Class<?> clazz = component.clazz;

            if (ComponentUtility.hasCustomClassAnnotation(clazz, Command.class)) {
                Command annotation = clazz.getAnnotation(Command.class);

                com.mojang.brigadier.Command<FabricClientCommandSource> printInstance = (context) -> {
                    if (component.instance != null)
                        DebugUtility.print(context, component.instance.toString());
                    else
                        DebugUtility.print(context, ComponentUtility.toStringStatic(clazz));
                    return 1;
                };

                LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommandManager.literal(annotation.value()).executes(printInstance);


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

            ArgumentType<?> argumentType = null;
            SuggestionProvider<FabricClientCommandSource> suggestionProvider = null;
            com.mojang.brigadier.Command<FabricClientCommandSource> setFieldValue = null;
            // By default, print the value of the field when no specific option is provided.
            com.mojang.brigadier.Command<FabricClientCommandSource> noOptionProvidedFunc = (context -> {
                try {
                    field.setAccessible(true);
                    DebugUtility.print(context, optionAnnotation.value() + "=" + field.get(component.instance));
                    return 1;
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    return -1;
                }
            });

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

                // If the option is an enum, we want to provide a list of options to the user
                setFieldValue = (context) -> {
                    try {
                        field.setAccessible(true);
                        field.set(component.instance, Enum.valueOf((Class<Enum>) fieldClass, ComponentUtility.convertCamelToSnake(context.getArgument(optionAnnotation.value(), String.class)).toUpperCase()));
                        return 1;
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return -1;
                    }
                };
            } else if (field.getType() == boolean.class) {
                argumentType = BoolArgumentType.bool();

                setFieldValue = (context) -> {
                    try {
                        field.setAccessible(true);
                        field.set(component.instance, context.getArgument(optionAnnotation.value(), boolean.class));
                        return 1;
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return -1;
                    }
                };

                // If the option is a boolean, we want to toggle the value of the field when no specific option is provided.
                noOptionProvidedFunc = (context -> {
                    try {
                        field.setAccessible(true);
                        field.set(component.instance, !field.getBoolean(component.instance));
                        DebugUtility.print(context, optionAnnotation.value() + "=" + field.get(component.instance));
                        return 1;
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                        return -1;
                    }
                });
            }


            LiteralArgumentBuilder<FabricClientCommandSource> subCommand = ClientCommandManager.literal(optionAnnotation.value());
            // Build the subCommand option
            if (noOptionProvidedFunc != null)
                subCommand = subCommand.executes(noOptionProvidedFunc);
            if (argumentType != null && setFieldValue != null) {
                RequiredArgumentBuilder<FabricClientCommandSource, ?> argument = ClientCommandManager.argument(optionAnnotation.value(), argumentType);
                if (suggestionProvider != null)
                    argument = argument.suggests(suggestionProvider);
                argument.executes(setFieldValue);
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

            LiteralArgumentBuilder<FabricClientCommandSource> subCommand = ClientCommandManager.literal(actionAnnotation.value());
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
