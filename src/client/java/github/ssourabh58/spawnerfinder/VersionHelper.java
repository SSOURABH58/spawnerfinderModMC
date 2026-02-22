package github.ssourabh58.spawnerfinder;

import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class VersionHelper {

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
                // 1.21.2+ Logic: Use KeyMapping.Category.GAMEPLAY
                Field gameplayField = categoryClass.getDeclaredField("GAMEPLAY");
                Object gameplayCategory = gameplayField.get(null);

                // Find constructor: (String, Type, int, Category)
                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                        String.class, InputConstants.Type.class, int.class, categoryClass);
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, defaultKey,
                        gameplayCategory);
            } else {
                // 1.21.1 Logic: Use KeyMapping.CATEGORY_GAMEPLAY (String)
                Field categoryField = KeyMapping.class.getDeclaredField("CATEGORY_GAMEPLAY");
                String categoryString = (String) categoryField.get(null);

                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                        String.class, InputConstants.Type.class, int.class, String.class);
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, defaultKey, categoryString);
            }
        } catch (Exception e) {
            // Ultimate fallback: Use reflection to try the 1.21.1 String signature manually
            // if the Category check failed.
            try {
                Constructor<KeyMapping> constructor = KeyMapping.class.getConstructor(
                        String.class, InputConstants.Type.class, int.class, String.class);
                return constructor.newInstance(translationKey, InputConstants.Type.KEYSYM, defaultKey,
                        "key.categories.gameplay");
            } catch (Exception e2) {
                // Return null or throw a runtime exception if it's truly broken
                return null;
            }
        }
    }
}
