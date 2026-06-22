package github.ssourabh58.spawnerfinder;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class VersionHelper {

    private static Object cachedCustomCategory = null;

    public static KeyMapping createKeyMapping(String translationKey, int defaultKey) {
        try {
            // Try to find the new 1.21.2+ Category class
            Class<?> categoryClass = null;
            try {
                categoryClass = Class.forName("net.minecraft.client.KeyMapping$Category");
            } catch (ClassNotFoundException e) {
                // Try Yarn mappings just in case
                try {
                    categoryClass = Class.forName("net.minecraft.client.option.KeyBinding$Category");
                } catch (ClassNotFoundException e2) {
                    // Not found, we are on 1.21.1 or below
                }
            }

            if (categoryClass != null) {
                // 1.21.2+ Logic: Register and use custom KeyMapping.Category
                if (cachedCustomCategory == null) {
                    try {
                        for (java.lang.reflect.Method m : categoryClass.getDeclaredMethods()) {
                            if (m.getName().equals("register") && m.getParameterCount() == 1) {
                                cachedCustomCategory = m.invoke(null, net.minecraft.resources.Identifier.fromNamespaceAndPath("spawnerfinder", "category"));
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Failed to register custom keybind category: " + ex);
                    }
                }

                Object customCategory = cachedCustomCategory;
                if (customCategory == null) {
                    java.lang.reflect.Field gameplayField = categoryClass.getDeclaredField("GAMEPLAY");
                    customCategory = gameplayField.get(null);
                }

                // Find constructor: (String, Type, int, Category)
                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                        String.class, InputConstants.Type.class, int.class, categoryClass);
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, defaultKey,
                        customCategory);
            } else {
                // 1.21.1 Logic: Use String category
                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                        String.class, InputConstants.Type.class, int.class, String.class);
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, defaultKey, "key.category.spawnerfinder.category");
            }
        } catch (Exception e) {
            // Ultimate fallback
            try {
                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                        String.class, InputConstants.Type.class, int.class, String.class);
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, defaultKey,
                        "key.category.spawnerfinder.category");
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public static void setOverlayMessage(net.minecraft.client.Minecraft client, net.minecraft.network.chat.Component message, boolean animate) {
        if (client.gui == null) return;
        try {
            // 26.2+ check: try to find 'hud' field in Gui class
            try {
                java.lang.reflect.Field hudField = client.gui.getClass().getDeclaredField("hud");
                Object hudObj = hudField.get(client.gui);
                if (hudObj != null) {
                    java.lang.reflect.Method method = hudObj.getClass().getMethod("setOverlayMessage", net.minecraft.network.chat.Component.class, boolean.class);
                    method.invoke(hudObj, message, animate);
                    return;
                }
            } catch (NoSuchFieldException | NoSuchMethodException e) {
                // Ignore, we are on 26.1 or below where it is directly on gui
            }

            // 26.1 fallback: invoke setOverlayMessage directly on client.gui
            java.lang.reflect.Method method = client.gui.getClass().getMethod("setOverlayMessage", net.minecraft.network.chat.Component.class, boolean.class);
            method.invoke(client.gui, message, animate);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
