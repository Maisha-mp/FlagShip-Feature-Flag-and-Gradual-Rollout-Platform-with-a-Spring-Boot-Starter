package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flagship.core.eval.EvaluationContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class FlagGuardWiringTest {

    /** A normal application bean using the annotation. */
    static class Shop {

        @FlagGuard(flag = "new-checkout")
        public String checkout() {
            return "new checkout";
        }

        @FlagGuard(flag = "new-checkout", fallback = "oldCheckout")
        public String checkoutWithFallback() {
            return "new checkout";
        }

        String oldCheckout() {
            return "old checkout";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ShopConfig {

        @Bean
        Shop shop() {
            return new Shop();
        }
    }

    /** An application that tells FlagShip who the current user is. */
    @Configuration(proxyBeanMethods = false)
    static class VipUserConfig {

        @Bean
        FlagContextProvider vipUser() {
            return () -> EvaluationContext.builder("vip").build();
        }
    }

    private static final String DEFAULT_OFF_BUT_VIP_ON = """
            {"project":"checkout-app","environment":"PRODUCTION","generatedAt":"2026-11-23T10:00:00Z",
             "flags":[{"key":"new-checkout","type":"BOOLEAN","variations":{"on":true,"off":false},
               "config":{"killSwitchOn":false,"offVariation":"off","defaultVariation":"off",
                 "userTargets":{"vip":"on"},"rules":[],"rollout":null}}]}
            """;

    @TempDir
    Path dir;

    private ApplicationContextRunner runnerWith(String snapshotJson) throws IOException {
        Files.writeString(dir.resolve("snapshot.json"), snapshotJson);
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AopAutoConfiguration.class, FlagShipAutoConfiguration.class))
                .withUserConfiguration(ShopConfig.class)
                .withPropertyValues(
                        "flagship.project=checkout-app",
                        "flagship.server-url=http://127.0.0.1:1",   // unreachable: flags come from the local file
                        "flagship.push-enabled=false",
                        "flagship.snapshot-file=" + dir.resolve("snapshot.json").toString().replace('\\', '/'));
    }

    @Test
    void flagOnAllowsTheCall() throws IOException {
        runnerWith(TestSnapshots.snapshot(TestSnapshots.flag("new-checkout", "on")))
                .run(context -> assertEquals("new checkout", context.getBean(Shop.class).checkout()));
    }

    @Test
    void flagOffThrowsFlagDisabledException() throws IOException {
        runnerWith(TestSnapshots.snapshot(TestSnapshots.flag("new-checkout", "off")))
                .run(context -> assertThrows(FlagDisabledException.class,
                        () -> context.getBean(Shop.class).checkout()));
    }

    @Test
    void flagOffUsesTheFallback() throws IOException {
        runnerWith(TestSnapshots.snapshot(TestSnapshots.flag("new-checkout", "off")))
                .run(context -> assertEquals("old checkout",
                        context.getBean(Shop.class).checkoutWithFallback()));
    }

    @Test
    void callsAreAnonymousUnlessTheAppProvidesAUser() throws IOException {
        // only the user "vip" is targeted to "on"; the default anonymous user is not
        runnerWith(DEFAULT_OFF_BUT_VIP_ON)
                .run(context -> assertThrows(FlagDisabledException.class,
                        () -> context.getBean(Shop.class).checkout()));
    }

    @Test
    void theAppCanProvideTheCurrentUser() throws IOException {
        runnerWith(DEFAULT_OFF_BUT_VIP_ON)
                .withUserConfiguration(VipUserConfig.class)
                .run(context -> assertEquals("new checkout", context.getBean(Shop.class).checkout()));
    }
}