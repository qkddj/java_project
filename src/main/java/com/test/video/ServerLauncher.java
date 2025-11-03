package com.test.video;

import com.test.video.server.MatchWebSocketCreator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.util.resource.Resource;

public class ServerLauncher {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("PORT",
                System.getenv().getOrDefault("PORT", "8080")));
        Server server = new Server(port);

        // WebSocket + 정적 리소스 매핑이 같은 컨텍스트("/")에서 동작하도록 설정
        ServletContextHandler appCtx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        appCtx.setContextPath("/");
        // 정적 리소스 베이스를 클래스패스의 /public 으로 지정
        appCtx.setBaseResource(Resource.newResource(ServerLauncher.class.getResource("/public")));
        // DefaultServlet 등록해 정적 파일 서빙
        appCtx.addServlet(DefaultServlet.class, "/");
        // 메인 화면을 기본 페이지로 설정
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
