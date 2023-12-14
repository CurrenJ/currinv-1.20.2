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
import oshi.util.tuples.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ModComponentRegistry {
    private record ComponentDto(Object instance, Class<?> clazz) {
    }

    private List<ComponentDto> components;

    private record ClientTickMethodDto(Object instance, Method method, int tickRate) {
    }

    private List<ClientTickMethodDto> clientTickMethods;

    private record ScreenMethodDto(Object instance, Method method) {
    }

    private List<ScreenMethodDto> screenTickMethods;
    private List<ScreenMethodDto> screenInitMethods;

    private CommandTreeNode commandTreeRoot;
    private ComponentDto commandRootComponent;

    private int tickCounter;

    public static class CommandTreeNode
    {
        public String literal;
        public LiteralArgumentBuilder<FabricClientCommandSource> command;
        public HashMap<String, CommandTreeNode> children;

        public CommandTreeNode(String literal, LiteralArgumentBuilder<FabricClientCommandSource>command)
        {
            this.literal = literal;
            this.command = command;
            this.children = new HashMap<>();
        }

        public Optional<CommandTreeNode> getChildNode(String literal) {
            return children.containsKey(literal) ? Optional.of(children.get(literal)) : Optional.empty();
        }
    }

    public ModComponentRegistry(String commandRootString) {
        commandTreeRoot = new CommandTreeNode(commandRootString, ClientCommandManager.literal(commandRootString));
        initialize();
    }

    public ModComponentRegistry(Class<?> clazz) {
        commandRootComponent = new ComponentDto(null, clazz);
        initialize();
    }

    public ModComponentRegistry(Object instance) {
        commandRootComponent = new ComponentDto(instance, instance.getClass());
        initialize();
    }

    private void initialize() {
        components = new ArrayList<>();
        clientTickMethods = new ArrayList<>();
        screenTickMethods = new ArrayList<>();
        screenInitMethods = new ArrayList<>();
    }

    public void registerComponent(Object component) {
        components.add(new ComponentDto(component, component.getClass()));
    }

    public void registerComponent(Class<?> clazz) {
        components.add(new ComponentDto(null, clazz));
    }

    public void registerComponents() {
        registerComponentCommands();
        registerTickEvents();
        registerScreenTickEvents();
    }

    private void registerComponentCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            if(commandTreeRoot == null && commandRootComponent != null) {
                LiteralArgumentBuilder<FabricClientCommandSource> rootCommand = buildCommandsFromAnnotations(commandRootComponent, registryAccess, commandTreeRoot);
                commandTreeRoot = new CommandTreeNode(ComponentUtility.getCommandKey(commandRootComponent.clazz), rootCommand);
            }

            for (ComponentDto component : this.components) {
                if (ComponentUtility.hasCustomClassAnnotation(component.clazz, Command.class)) {
                    LiteralArgumentBuilder<FabricClientCommandSource> command = buildCommandsFromAnnotations(component, registryAccess, commandTreeRoot);

                    commandTreeRoot.command.then(command);

                    String commandKey = ComponentUtility.getCommandKey(component.clazz);;
                    commandTreeRoot.children.put(commandKey, new CommandTreeNode(commandKey, command));
                }
            }

            dispatcher.register(commandTreeRoot.command);
        });
    }

    /**
     * Register tick events for all methods with {@link ClientTick} annotations.
     */
    private void registerTickEvents() {
        for (ComponentDto component : this.components) {
            for (Method method : ComponentUtility.getClientTickMethods(component.clazz)) {
                this.clientTickMethods.add(new ClientTickMethodDto(component.instance, method, method.getAnnotation(ClientTick.class).value()));
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register((client) -> {
            tickCounter++;
            for (ClientTickMethodDto clientTickMethodDto : clientTickMethods) {
                doClientTick(clientTickMethodDto);
            }
            if (tickCounter == Integer.MAX_VALUE)
                tickCounter = 0;
        });
    }

    /**
     * Register screen tick events for all methods with {@link ScreenTick} or {@link ScreenInit} annotations..
     */
    private void registerScreenTickEvents() {
        for (ComponentDto component : this.components) {
            for (Method method : ComponentUtility.getScreenTickMethods(component.clazz)) {
                this.screenTickMethods.add(new ScreenMethodDto(component.instance, method));
            }

            for (Method method : ComponentUtility.getScreenInitMethods(component.clazz)) {
                this.screenInitMethods.add(new ScreenMethodDto(component.instance, method));
            }
        }

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?>) {
                registerScreenMethods(client, screen, screenInitMethods);

                // Repeatedly called while screen is being rendered (each tick).
                ScreenEvents.afterTick(screen).register((tickScreen) -> {
                    registerScreenMethods(client, screen, screenTickMethods);
                });
            }
        });
    }

    private void registerScreenMethods(MinecraftClient client, Screen screen, List<ScreenMethodDto> screenInitMethods) {
        for (ScreenMethodDto screenInitMethodDto : screenInitMethods) {
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
    }

    /**
     * Build command from an object component whose class has the {@link Command} annotation.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommandsFromAnnotations(ComponentDto component, CommandRegistryAccess commandRegistryAccess, CommandTreeNode commandRoot) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null) {
            Class<?> clazz = component.clazz;

            if (ComponentUtility.hasCustomClassAnnotation(clazz, Command.class)) {
                String commandKey = ComponentUtility.getCommandKey(component.clazz);

                com.mojang.brigadier.Command<FabricClientCommandSource> printInstance = (context) -> {
                    if (component.instance != null)
                        DebugUtility.print(context, component.instance.toString());
                    else
                        DebugUtility.print(context, ComponentUtility.toStringStatic(clazz));
                    return 1;
                };


                LiteralArgumentBuilder<FabricClientCommandSource> command = ComponentUtility.getCommandOrElse(commandRoot, commandKey, (key) -> ClientCommandManager.literal(commandKey)).executes(printInstance);

                for (Pair<CommandOption, LiteralArgumentBuilder<FabricClientCommandSource>> subCommandData : buildCommandsFromFields(component)) {
                    attachSubCommandToParentCommand(commandRoot, subCommandData.getA().parentKey(), command, subCommandData.getB());
                }

                for (Pair<CommandAction, LiteralArgumentBuilder<FabricClientCommandSource>> subCommandData : buildCommandsFromMethods(component, commandRegistryAccess)) {
                    attachSubCommandToParentCommand(commandRoot, subCommandData.getA().parentKey(), command, subCommandData.getB());
                }

                return command;
            }
        }
        return null;
    }

    private static void attachSubCommandToParentCommand(CommandTreeNode commandRoot, String parentOverrideKey, LiteralArgumentBuilder<FabricClientCommandSource> defaultParentCommand, LiteralArgumentBuilder<FabricClientCommandSource> subCommand) {
        if(parentOverrideKey.isEmpty())
            defaultParentCommand.then(subCommand);
        else {
            LiteralArgumentBuilder<FabricClientCommandSource> parentCommand = ComponentUtility.getCommandOrElse(commandRoot, parentOverrideKey, (key) -> ClientCommandManager.literal(parentOverrideKey));
            parentCommand.then(subCommand);
        }
    }

    /**
     * Build commands from fields with the {@link CommandOption} in an object's class.
     */
    private static List<Pair<CommandOption, LiteralArgumentBuilder<FabricClientCommandSource>>> buildCommandsFromFields(ComponentDto component) {
        Class<?> clazz = component.clazz;

        List<Pair<CommandOption, LiteralArgumentBuilder<FabricClientCommandSource>>> commands = new ArrayList<>();
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

            commands.add(new Pair<>(optionAnnotation, subCommand));
        }
        return commands;
    }

    /**
     * Build commands from methods with the {@link CommandAction} in an object's class.
     */
    private static List<Pair<CommandAction, LiteralArgumentBuilder<FabricClientCommandSource>>> buildCommandsFromMethods(ComponentDto component, CommandRegistryAccess commandRegistryAccess) {
        Class<?> clazz = component.clazz;
        MinecraftClient client = MinecraftClient.getInstance();
        assert client != null;

        List<Pair<CommandAction, LiteralArgumentBuilder<FabricClientCommandSource>>> commands = new ArrayList<>();
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

            commands.add(new Pair<>(actionAnnotation, subCommand));
        }
        return commands;
    }

    private void doClientTick(ClientTickMethodDto clientTickMethodDto) {
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
