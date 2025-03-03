package kanela.agent;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigResolveOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Logger;

record Configuration(Agent agent, List<Configuration.Module> modules) {

  static record Agent(boolean debug, String logLevel) {}

  static record Module(
      String name,
      Optional<String> description,
      boolean enabled,
      int order,
      List<String> instrumentations,
      List<String> withinPackages,
      Optional<List<String>> excludePackages) {}

  static Configuration createFrom(ClassLoader classLoader) {
    var config =
        ConfigFactory.load(
            classLoader,
            ConfigParseOptions.defaults(),
            ConfigResolveOptions.defaults().setAllowUnresolved(true));

    if (!config.hasPath("kanela")) {
      throw new RuntimeException("Couldn't find kanela configuration in the classpath");
    }

    var debug = config.getBoolean("kanela.debug-mode");
    var logLevel = config.getString("kanela.log-level");
    var agent = new Agent(debug, logLevel);

    var logFormat = "{date} [kanela][{level}] {message} {exception}";
    Configurator.defaultConfig().formatPattern(logFormat).activate();

    Logger.debug("Reading configuration from {}", () -> config.root().render());

    var modules = new ArrayList<Module>();
    if (!config.hasPath("kanela.modules")) {
      Logger.warn("No modules found in the kanela.modules. Kanela won't do anything");
    } else {
      var modulesConfig = config.getConfig("kanela.modules");
      modulesConfig
          .root()
          .entrySet()
          .forEach(
              entry -> {
                var moduleConfig = modulesConfig.getConfig(entry.getKey());
                var name = moduleConfig.getString("name");
                var description = optionalString(moduleConfig, "description");
                var enabled = optionalBoolean(moduleConfig, "enabled").orElse(true);
                var order = optionalNumber(moduleConfig, "order").orElse(1);
                var instrumentations = moduleConfig.getStringList("instrumentations");
                var withinPackages = moduleConfig.getStringList("within");
                var excludePackages = optionalStringList(moduleConfig, "exclude");

                modules.add(
                    new Module(
                        name,
                        description,
                        enabled,
                        order,
                        instrumentations,
                        withinPackages,
                        excludePackages));
              });
    }

    return new Configuration(agent, modules);
  }

  private static Optional<String> optionalString(Config config, String path) {
    if (config.hasPath(path)) {
      return Optional.of(config.getString(path));
    } else return Optional.empty();
  }

  private static Optional<List<String>> optionalStringList(Config config, String path) {
    if (config.hasPath(path)) {
      return Optional.of(config.getStringList(path));
    } else return Optional.empty();
  }

  private static Optional<Boolean> optionalBoolean(Config config, String path) {
    if (config.hasPath(path)) {
      return Optional.of(config.getBoolean(path));
    } else return Optional.empty();
  }

  private static Optional<Integer> optionalNumber(Config config, String path) {
    if (config.hasPath(path)) {
      return Optional.of(config.getInt(path));
    } else return Optional.empty();
  }
}
