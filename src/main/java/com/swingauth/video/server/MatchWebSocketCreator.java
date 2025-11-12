package com.swingauth.video.server;

import org.eclipse.jetty.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory;

public class MatchWebSocketCreator extends JettyWebSocketServlet {
    @Override
    public void configure(JettyWebSocketServletFactory factory) {
        factory.setCreator((req, resp) -> new MatchSocket());
    }
}

