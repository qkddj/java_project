package com.test.video;

import com.test.video.server.MatchWebSocketCreator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

public class ServerLauncher {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("PORT",
                System.getenv().getOrDefault("PORT", "8080")));
        Server server = new Server(port);

        ServletContextHandler appCtx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        appCtx.setContextPath("/");
        appCtx.setBaseResource(Resource.newResource(ServerLauncher.class.getResource("/public")));
        appCtx.addServlet(DefaultServlet.class, "/");
        appCtx.setWelcomeFiles(new String[] { "main.html" });
        JettyWebSocketServletContainerInitializer.configure(appCtx, (servletContext, container) -> {
            container.addMapping("/ws", new MatchWebSocketCreator());
        });

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { appCtx });
        server.setHandler(handlers);

        server.start();
        int actualPort = port;
        if (port == 0 && server.getConnectors().length > 0) {
            actualPort = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        }
        System.out.println("Random Call server started on http://localhost:" + actualPort);
        server.join();
    }
}
