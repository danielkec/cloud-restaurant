package com.acme.cloud.restaurant;

import io.helidon.logging.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;

public class Main {
    public static void main(String[] args) {
        
        // load logging configuration
        LogConfig.configureRuntime();

        // initialize global config from default configuration
        Config config = Config.create();
        Config.global(config);

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(r -> r.register("/restaurant", new RestaurantService()))
                .build()
                .start();

        System.out.println("WEB server is up! http://localhost:" + server.port() + "/restaurant");

    }
}