package com.swingauth.video;

import com.swingauth.video.server.MatchWebSocketCreator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

import java.net.URL;

public class ServerLauncher {
    private Server server;
    private int port;

    public void start() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(0); // 자동 포트 할당
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // WebSocket
        context.addServlet(new ServletHolder(new MatchWebSocketCreator()), "/ws");
        JettyWebSocketServletContainerInitializer.configure(context, null);

        // 정적 파일 서빙
        URL resourceBase = getClass().getClassLoader().getResource("public");
        if (resourceBase != null) {
            String resourcePath = resourceBase.toURI().toString();
            context.setResourceBase(resourcePath);
        } else {
            // 개발 환경에서 직접 경로 사용
            String publicPath = System.getProperty("user.dir") + "/src/main/resources/public";
            context.setResourceBase(publicPath);
        }
        
        // DefaultServlet 추가 (정적 파일 서빙)
        ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
        defaultServlet.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServlet, "/");

        server.setHandler(context);
        server.start();

        port = connector.getLocalPort();
        System.out.println("Video call server started on port: " + port);
    }

    public void stop() throws Exception {
        if (server != null && server.isStarted()) {
            server.stop();
        }
    }

    public int getPort() {
        return port;
    }
}

