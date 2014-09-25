package org.graphwalker.service;

/*
 * #%L
 * GraphWalker As A Service
 * %%
 * Copyright (C) 2011 - 2014 GraphWalker
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.graphwalker.core.event.EventType;
import org.graphwalker.core.event.Observer;
import org.graphwalker.core.machine.Context;
import org.graphwalker.core.machine.Machine;
import org.graphwalker.core.machine.SimpleMachine;
import org.graphwalker.core.model.Element;
import org.graphwalker.core.utils.LoggerUtil;
import org.graphwalker.io.factory.json.JsonContextFactory;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * A WebSocketServer implementation.
 */
public class GraphWalkerServer extends WebSocketServer implements Observer {

    private static final Logger logger = LoggerFactory.getLogger(GraphWalkerServer.class);
    private static Options options;

    private Set<WebSocket> conns;
    private Map<WebSocket, Machine> machines;
    private Map<WebSocket, List<Context>>  contexts;

    public GraphWalkerServer(int port) throws UnknownHostException {
        super(new InetSocketAddress(port));
        conns = new HashSet<>();
        machines = new HashMap<>();
        contexts = new HashMap<>();
    }

    public GraphWalkerServer(InetSocketAddress address) {
        super(address);
        conns = new HashSet<>();
        machines = new HashMap<>();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conns.add(conn);
        machines.put(conn, null);
        contexts.put(conn, new ArrayList<Context>());
        logger.info(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " is now connected");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        conns.remove(conn);
        machines.remove(conn);
        logger.info(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " has disconnected");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.info(conn.getRemoteSocketAddress().getAddress().getHostAddress() + " sent msg: " + message);
        JSONObject response = new JSONObject();
        JSONObject root = null;
        try {
            root  = new JSONObject(message);
        } catch (JSONException e) {
            response.put("message", "Unknown command: " + e.getMessage());
            response.put("success", false);
            conn.send(response.toString());
            return;
        }

        String type = root.getString("type").toUpperCase();
        if (type.equals("LOADMODEL")) {
            response.put("type", "loadModel");
            try {
                Context context = new JsonContextFactory().create(root.getJSONObject("model").toString());
                List<Context> executionContexts = contexts.get(conn);
                executionContexts.add(context);
                response.put("success", true);
            } catch (JSONException e) {
                response.put("success", false);
                response.put("message", "Could not parse the model: " + e.getMessage());
            }

        } else if (type.equals("START")) {
            List<Context> executionContexts = contexts.get(conn);
            Machine machine = new SimpleMachine(executionContexts);
            machine.addObserver(this);
            machines.put(conn, machine);
            response.put("type", "start");
            response.put("success", true);

        } else if (type.equals("GETNEXT")) {
            Machine machine = machines.get(conn);
            response.put("type", "getNext");
            if (machine!=null) {
                machine.getNextStep();
                response.put("success", true);
            } else {
                response.put("success", false);
                response.put("message", "The GraphWalker state machine is not initiated. Is a model loaded, and started?");
            }

        } else if (type.equals("HASNEXT")) {
            Machine machine = machines.get(conn);
            response.put("type", "hasNext");
            if (machine == null) {
                response.put("success", false);
                response.put("message", "The GraphWalker state machine is not initiated. Is a model loaded, and started?");
            } else if (machine.hasNextStep()) {
                response.put("success", true);
                response.put("hasNext", true);
            } else {
                response.put("success", true);
                response.put("hasNext", false);
            }

        } else if (type.equals("RESTART")) {
            machines.put(conn,null);
            contexts.put(conn, null);
            contexts.put(conn, new ArrayList<Context>());
            response.put("type", "restart");
            response.put("success", true);

        } else if (type.equals("GETDATA")) {
            response.put("type", "getData");
            Machine machine = machines.get(conn);
            if (machine!=null) {
                JSONArray jsonKeys = new JSONArray();
                for (Map.Entry<String, String> key : machine.getCurrentContext().getKeys().entrySet()) {
                    JSONObject jsonKey = new JSONObject();
                    jsonKey.put(key.getKey(), key.getValue());
                    jsonKeys.put(jsonKey);
                }
                response.put("data", jsonKeys);
                response.put("success", true);
            } else {
                response.put("success", false);
                response.put("message", "The GraphWalker state machine is not initiated. Is a model loaded, and started?");
            }

        } else {
            response.put("message", "Unknown command");
            response.put("success", false);
        }
        conn.send(response.toString());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        if (conn != null) {
            // some errors like port binding failed may not be assignable to a specific websocket
        }
    }

    @Override
    public void update(Machine machine, Element element, EventType type) {
        logger.info("Received an update from a GraphWalker machine");
        Iterator it = machines.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            if (machine == pairs.getValue()) {
                logger.info("Event: " + type);
                WebSocket conn = (WebSocket) pairs.getKey();
                if (type == EventType.AFTER_ELEMENT) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("type", "visitedElement");
                    jsonObject.put("id", element.getId());
                    jsonObject.put("visitedCount", machine.getProfiler().getVisitCount(element));
                    conn.send(jsonObject.toString());
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        options = new Options();
        JCommander jc = new JCommander(options);
        jc.setProgramName("java -jar GraphWalkerServer.jar");
        jc.parse(args);
        setLogLevel(options);

        GraphWalkerServer GraphWalkerServer = new GraphWalkerServer(options.port);
        try {
            GraphWalkerServer.run(args);
        } catch (Exception e) {
            logger.error("Something went wrong.", e);
        }
    }

    private void run(String[] args) {
        if (options.version) {
            System.out.println(printVersionInformation());
            return;
        }

        if (options.debug.equalsIgnoreCase("TRACE")) {
            WebSocketImpl.DEBUG = true;
        }

        start();

        logger.info("GraphWalkerServer started on port: " + getPort());

        // Shutdown event
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println();
                System.out.println("GraphWalkerServer shutting down");
                System.out.println();
                logger.info("GraphWalkerServer shutting down");
            }
        }));

        while (true) {
            try {
                Thread.currentThread().sleep(10);
            } catch (InterruptedException i) {
                break;
            }
        }
    }

    private static void setLogLevel(Options options) {
        // OFF, ERROR, WARN, INFO, DEBUG, TRACE, ALL
        if (options.debug.equalsIgnoreCase("OFF")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.OFF);
        } else if (options.debug.equalsIgnoreCase("ERROR")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.ERROR);
        } else if (options.debug.equalsIgnoreCase("WARN")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.WARN);
        } else if (options.debug.equalsIgnoreCase("INFO")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.INFO);
        } else if (options.debug.equalsIgnoreCase("DEBUG")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.DEBUG);
        } else if (options.debug.equalsIgnoreCase("TRACE")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.TRACE);
        } else if (options.debug.equalsIgnoreCase("ALL")) {
            LoggerUtil.setLogLevel(LoggerUtil.Level.ALL);
        } else {
            throw new ParameterException("Incorrect argument to --debug");
        }
    }

    private String printVersionInformation() {
        String version = "org.graphwalker version: " + getVersionString() + System.getProperty("line.separator");
        version += System.getProperty("line.separator");

        version += "org.graphwalker is open source software licensed under MIT license" + System.getProperty("line.separator");
        version += "The software (and it's source) can be downloaded from http://graphwalker.org" + System.getProperty("line.separator");
        version += "For a complete list of this package software dependencies, see TO BE DEFINED" + System.getProperty("line.separator");

        return version;
    }

    private String getVersionString() {
        Properties properties = new Properties();
        InputStream inputStream = getClass().getResourceAsStream("/version.properties");
        if (null != inputStream) {
            try {
                properties.load(inputStream);
            } catch (IOException e) {
                logger.error("An error occurred when trying to get the version string", e);
                return "unknown";
            }
        }
        return properties.getProperty("graphwalker.version");
    }
}