package com.bgsoftware.superiorskyblock.module;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.commands.SuperiorCommand;
import com.bgsoftware.superiorskyblock.api.handlers.ModulesManager;
import com.bgsoftware.superiorskyblock.api.modules.ModuleLoadTime;
import com.bgsoftware.superiorskyblock.api.modules.PluginModule;
import com.bgsoftware.superiorskyblock.core.Manager;
import com.bgsoftware.superiorskyblock.core.debug.PluginDebugger;
import com.bgsoftware.superiorskyblock.core.errors.ManagerLoadException;
import com.bgsoftware.superiorskyblock.core.io.JarFiles;
import com.bgsoftware.superiorskyblock.module.container.ModulesContainer;
import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class ModulesManagerImpl extends Manager implements ModulesManager {

    private final ModulesContainer modulesContainer;
    private final File modulesFolder;
    private final File dataFolder;

    public ModulesManagerImpl(SuperiorSkyblockPlugin plugin, ModulesContainer modulesContainer) {
        super(plugin);
        this.modulesContainer = modulesContainer;
        this.modulesFolder = new File(plugin.getDataFolder(), "modules");
        this.dataFolder = new File(plugin.getDataFolder(), "datastore/modules");
    }

    @Override
    public void loadData() {
        if (!modulesFolder.exists())
            //noinspection ResultOfMethodCallIgnored
            modulesFolder.mkdirs();

        registerModule(BuiltinModules.GENERATORS);
        registerModule(BuiltinModules.MISSIONS);
        registerModule(BuiltinModules.BANK);
        registerModule(BuiltinModules.UPGRADES);
        registerExternalModules();
    }

    @Override
    public void registerModule(PluginModule pluginModule) {
        Preconditions.checkNotNull(pluginModule, "pluginModule parameter cannot be null.");
        this.modulesContainer.registerModule(pluginModule, modulesFolder, dataFolder, plugin);
    }

    @Override
    public PluginModule registerModule(File moduleFile) throws IOException, ReflectiveOperationException {
        if (!moduleFile.getName().endsWith(".jar"))
            throw new IllegalArgumentException("The given file is not a valid jar file.");

        String moduleName = moduleFile.getName().replace(".jar", "");

        ModuleClassLoader moduleClassLoader = new ModuleClassLoader(moduleFile);

        //noinspection deprecation
        Class<?> moduleClass = JarFiles.getClass(moduleFile.toURL(), PluginModule.class, moduleClassLoader);

        if (moduleClass == null)
            throw new IllegalArgumentException("The file " + moduleName + " is not a valid module.");

        PluginModule pluginModule = createInstance(moduleClass);
        pluginModule.initModuleLoader(moduleFile, moduleClassLoader);

        registerModule(pluginModule);

        return pluginModule;
    }

    @Override
    public void unregisterModule(PluginModule pluginModule) {
        Preconditions.checkNotNull(pluginModule, "pluginModule parameter cannot be null.");
        this.modulesContainer.unregisterModule(pluginModule, plugin);
    }

    @Override
    @Nullable
    public PluginModule getModule(String name) {
        Preconditions.checkNotNull(name, "name parameter cannot be null.");
        return this.modulesContainer.getModule(name);
    }

    @Override
    public Collection<PluginModule> getModules() {
        return this.modulesContainer.getModules();
    }

    @Override
    public void enableModule(PluginModule pluginModule) {
        Preconditions.checkNotNull(pluginModule, "pluginModule parameter cannot be null.");

        long startTime = System.currentTimeMillis();

        SuperiorSkyblockPlugin.log("&aEnabling the module " + pluginModule.getName() + "...");

        try {
            pluginModule.onEnable(plugin);
        } catch (Exception ex) {
            SuperiorSkyblockPlugin.log("&cAn error occurred while enabling the module " + pluginModule.getName() + ":");
            SuperiorSkyblockPlugin.log("&cContact " + pluginModule.getAuthor() + " regarding this, this has nothing to do with the plugin.");

            ex.printStackTrace();
            PluginDebugger.debug(ex);

            try {
                // Calling onDisable so the plugin can unregister its data if needed
                pluginModule.onDisable(plugin);
            } catch (Throwable error) {
                SuperiorSkyblockPlugin.log("&cAn error occurred while disabling the module " + pluginModule.getName() + ":");
                SuperiorSkyblockPlugin.log("&cContact " + pluginModule.getAuthor() + " regarding this, this has nothing to do with the plugin.");
                error.printStackTrace();
            }

            return;
        }

        Listener[] listeners = pluginModule.getModuleListeners(plugin);
        SuperiorCommand[] commands = pluginModule.getSuperiorCommands(plugin);
        SuperiorCommand[] adminCommands = pluginModule.getSuperiorAdminCommands(plugin);

        if (listeners != null || commands != null || adminCommands != null)
            this.modulesContainer.addModuleData(pluginModule, new ModuleData(listeners, commands, adminCommands));

        if (listeners != null)
            Arrays.stream(listeners).forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, plugin));

        if (commands != null)
            Arrays.stream(commands).forEach(plugin.getCommands()::registerCommand);

        if (adminCommands != null)
            Arrays.stream(adminCommands).forEach(plugin.getCommands()::registerAdminCommand);

        SuperiorSkyblockPlugin.log("&eFinished enabling the module " + pluginModule.getName() +
                " (Took " + (System.currentTimeMillis() - startTime) + "ms)");
    }

    @Override
    public void enableModules(ModuleLoadTime moduleLoadTime) {
        Preconditions.checkNotNull(moduleLoadTime, "moduleLoadTime parameter cannot be null.");
        filterModules(moduleLoadTime).forEach(this::enableModule);
    }

    public void reloadModules(SuperiorSkyblockPlugin plugin) {
        getModules().forEach(pluginModule -> {
            try {
                pluginModule.onReload(plugin);
            } catch (Throwable error) {
                SuperiorSkyblockPlugin.log("&cAn error occurred while reloading the module " + pluginModule.getName() + ":");
                SuperiorSkyblockPlugin.log("&cContact " + pluginModule.getAuthor() + " regarding this, this has nothing to do with the plugin.");
                error.printStackTrace();
            }
        });
    }

    public void loadModulesData(SuperiorSkyblockPlugin plugin) {
        getModules().forEach(pluginModule -> {
            try {
                pluginModule.loadData(plugin);
            } catch (Throwable error) {
                SuperiorSkyblockPlugin.log("&cAn error occurred while loading data for the module " + pluginModule.getName() + ":");
                SuperiorSkyblockPlugin.log("&cContact " + pluginModule.getAuthor() + " regarding this, this has nothing to do with the plugin.");
                error.printStackTrace();
            }
        });
    }

    private void registerExternalModules() {
        File[] folderFiles = modulesFolder.listFiles();

        if (folderFiles != null) {
            for (File file : folderFiles) {
                if (!file.isDirectory() && file.getName().endsWith(".jar")) {
                    try {
                        registerModule(file);
                    } catch (Exception ex) {
                        SuperiorSkyblockPlugin.log("Couldn't register module " + file.getName() + ": ");
                        ManagerLoadException handlerError = new ManagerLoadException(ex, "Couldn't register module " + file.getName() + ".",
                                ManagerLoadException.ErrorLevel.CONTINUE);
                        handlerError.printStackTrace();
                        PluginDebugger.debug(handlerError);
                    }
                }
            }
        }
    }

    private Stream<PluginModule> filterModules(ModuleLoadTime moduleLoadTime) {
        return this.modulesContainer.getModules().stream()
                .filter(pluginModule -> pluginModule.getLoadTime() == moduleLoadTime);
    }

    private PluginModule createInstance(Class<?> clazz) throws ReflectiveOperationException {
        Preconditions.checkArgument(PluginModule.class.isAssignableFrom(clazz), "Class " + clazz + " is not a PluginModule.");

        for (Constructor<?> constructor : clazz.getConstructors()) {
            if (constructor.getParameterCount() == 0) {
                if (!constructor.isAccessible())
                    constructor.setAccessible(true);

                return (PluginModule) constructor.newInstance();
            }
        }

        throw new IllegalArgumentException("Class " + clazz + " has no valid constructors.");
    }

}
