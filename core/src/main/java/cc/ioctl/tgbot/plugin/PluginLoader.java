package cc.ioctl.tgbot.plugin;

import cc.ioctl.util.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOError;

public class PluginLoader {

    private static final String TAG = "PluginLoader";

    @Nullable
    public static IPlugin loadPluginLocked(@NotNull PluginManager pluginManager, @NotNull PluginManager.PluginInfo pluginInfo) {
        String name = pluginInfo.getName();
        Class<?> pluginClass;
        try {
            pluginClass = Class.forName(pluginInfo.getMainClass());
            if (!IPlugin.class.isAssignableFrom(pluginClass)) {
                Log.e(TAG, "Plugin " + name + " class " + pluginInfo.getMainClass() + " does not implement IPlugin interface");
                return null;
            }
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Plugin " + name + " class not found: " + pluginInfo.getMainClass(), e);
            return null;
        }
        IPlugin plugin;
        try {
            plugin = (IPlugin) pluginClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            Log.e(TAG, "Plugin " + name + " class " + pluginInfo.getMainClass() + " could not be instantiated", e);
            return null;
        }
        try {
            plugin.onLoad();
        } catch (Exception | LinkageError | IOError e) {
            Log.e(TAG, "Unable to execute onLoad() method of plugin " + name, e);
            return null;
        }
        return plugin;
    }
}
