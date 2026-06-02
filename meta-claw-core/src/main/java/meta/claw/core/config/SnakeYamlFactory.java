package meta.claw.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

public class SnakeYamlFactory {

    public static Yaml createCamelCaseYaml() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> true);

        Constructor constructor = new Constructor(loaderOptions);
        constructor.setPropertyUtils(new CamelCasePropertyUtils());

        return new Yaml(constructor);
    }

    static class CamelCasePropertyUtils extends PropertyUtils {

        @Override
        public Property getProperty(Class<?> type, String name) {
            String camelCase = toCamelCase(name);
            try {
                return super.getProperty(type, camelCase);
            } catch (Exception e) {
                return super.getProperty(type, name);
            }
        }

        private String toCamelCase(String snakeCase) {
            StringBuilder sb = new StringBuilder();
            boolean nextUpper = false;
            for (char c : snakeCase.toCharArray()) {
                if (c == '_') {
                    nextUpper = true;
                } else if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
