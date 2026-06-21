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
}
