package fr.lecabellec.eniworkshop;

import fr.lecabellec.eniworkshop.cli.EniWorkshopCliCommand;
import fr.lecabellec.eniworkshop.quasar.QuasarProperties;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Dual-mode entry point switching between headless Picocli CLI mode and Spring Boot Web server mode.
 * Traces to REQ-APP-001, CS-0010, CS-0030.
 */
@SpringBootApplication
@EnableConfigurationProperties(QuasarProperties.class)
public class EniWorkshopApplication {

    /** Top-level CLI triggers selecting headless Picocli execution instead of Web server startup. */
    private static final Set<String> CLI_TRIGGERS = Set.of(
        "--cli",
        "-h",
        "--help",
        "-V",
        "--version",
        "inspect",
        "validate",
        "catalog",
        "aggregate",
        "export-csv",
        "plantuml",
        "quasar-scan",
        "compare",
        "project"
    );

    /** Auto-configurations disabled in headless file-oriented CLI mode for instant startup without daemons. */
    private static final String CLI_EXCLUDED_AUTOCONFIG =
        "--spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration";

    /**
     * Application constructor required by Spring Boot CGLIB configuration proxy.
     */
    public EniWorkshopApplication() {
        // Default constructor for Spring Boot
    }

    /**
     * Provides the system UTC clock bean for timestamping metadata and telemetry events.
     *
     * @return UTC system clock
     */
    @Bean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }

    /**
     * Launches ENI Workshop either in CLI mode or Web server mode depending on arguments.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        if (isCliInvocation(args)) {
            int exitCode = runCli(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }
        SpringApplication.run(EniWorkshopApplication.class, args);
    }

    /**
     * Runs the Picocli command hierarchy inside a non-web Spring Boot context.
     *
     * @param args raw CLI arguments (optionally prefixed with {@code --cli})
     * @return Picocli exit code
     */
    public static int runCli(final String[] args) {
        String[] cliArgs = stripCliPrefix(args);
        SpringApplication app = new SpringApplication(EniWorkshopApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setLogStartupInfo(false);
        try (ConfigurableApplicationContext ctx = app.run(
            "--spring.main.banner-mode=off",
            "--logging.level.root=WARN",
            CLI_EXCLUDED_AUTOCONFIG
        )) {
            EniWorkshopCliCommand rootCmd = ctx.getBean(EniWorkshopCliCommand.class);
            IFactory factory = ctx.getBean(IFactory.class);
            CommandLine cmd = new CommandLine(rootCmd, factory);
            return cmd.execute(cliArgs);
        }
    }

    static boolean isCliInvocation(final String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        return CLI_TRIGGERS.contains(args[0]);
    }

    static String[] stripCliPrefix(final String[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        if ("--cli".equals(args[0])) {
            List<String> trimmed = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
            return trimmed.toArray(String[]::new);
        }
        return Arrays.copyOf(args, args.length);
    }
}
