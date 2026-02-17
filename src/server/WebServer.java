package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import components.BookingSystem;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import models.*;
import util.Logger;

public class WebServer {
    private static final int PORT = 8080;
    private static BookingSystem bookingSystem;
    private static List<Taxi> taxis = new CopyOnWriteArrayList<>();
    private static ExecutorService taxiExecutor;
    private static ExecutorService passengerExecutor;

    // Simulation state for visualization
    private static final Map<Integer, TaxiState> taxiStates = new ConcurrentHashMap<>();
    private static final Map<String, PassengerState> passengerStates = new ConcurrentHashMap<>();

    public static class TaxiState {
        public int id;
        public double x, y;
        public double targetX, targetY;
        public String status;
        public double earnings;
        public int currentPassengers;
        public String driverName;
        public String driverAvatarUrl;
        public String message;
        public List<PassengerInfo> passengers = new ArrayList<>();
    }

    public static class PassengerState {
        public String id;
        public String gender;
        public String name;
        public String avatarUrl;
        public double x, y;
        public String destination;
        public String status; // WAITING, PICKED_UP, IN_RIDE
    }

    public static class PassengerInfo {
        public String id;
        public String gender;
        public String name;
        public String avatarUrl;
        public String destination;
    }

    public static class SimulationState {
        public List<TaxiState> taxis = new ArrayList<>();
        public List<PassengerState> passengers = new ArrayList<>();
        public int queueSize;
        public int totalMalesServed;
        public int totalFemalesServed;
        public int totalPassengersServed;
    }

    public static void main(String[] args) throws IOException {
        bookingSystem = new BookingSystem();
        Logger.setLogArea(null); // Disable Swing logging

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Static file serving
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/state", new StateHandler());
        server.createContext("/api/addTaxi", new AddTaxiHandler());
        server.createContext("/api/addPassenger", new AddPassengerHandler());
        server.createContext("/api/start", new StartHandler());
        server.createContext("/api/reset", new ResetHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Open http://localhost:" + PORT + " in your browser");

        // Start update thread
        startUpdateThread();
    }

    private static void startUpdateThread() {
        Thread updateThread = new Thread(() -> {
            while (true) {
                try {
                    updateSimulationState();
                    Thread.sleep(50); // Update every 50ms for smoother animation
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }

    private static void updateSimulationState() {
        try {
            // Update taxi positions and states
            for (Taxi taxi : taxis) {
                TaxiState state = taxiStates.computeIfAbsent(taxi.getId(), k -> new TaxiState());

                // Sync State with Taxi Object
                state.id = taxi.getId();
                state.driverName = taxi.getDriverName();
                state.driverAvatarUrl = taxi.getDriverAvatarUrl();
                state.status = taxi.getStatus();
                state.message = taxi.getLastMessage();
                state.earnings = taxi.getEarnings();
                state.currentPassengers = taxi.getCurrentPassengers().size();
                state.x = taxi.getX();
                state.y = taxi.getY();
                state.targetX = taxi.getTargetX();
                state.targetY = taxi.getTargetY();

                // Update passenger list
                state.passengers.clear();
                try {
                    for (Passenger p : taxi.getCurrentPassengers()) {
                        PassengerInfo info = new PassengerInfo();
                        info.id = p.getId();
                        info.gender = p.getGender().toString();
                        info.name = p.getName();
                        info.avatarUrl = p.getAvatarUrl();
                        info.destination = p.getDestination();
                        state.passengers.add(info);
                    }
                } catch (Exception e) {
                }

                // Centralized Movement Logic (Updates the Taxi Object directly)
                double speed = 1.5; // Reduced speed for realistic observation
                double currentX = taxi.getX();
                double currentY = taxi.getY();
                double targetX = taxi.getTargetX();
                double targetY = taxi.getTargetY();

                if (Math.abs(targetX - currentX) > speed || Math.abs(targetY - currentY) > speed) {
                    // Manhattan Movement
                    if (Math.abs(targetX - currentX) > speed) {
                        currentX += Math.signum(targetX - currentX) * speed;
                    } else {
                        currentX = targetX;
                        currentY += Math.signum(targetY - currentY) * speed;
                    }
                    taxi.setX(currentX);
                    taxi.setY(currentY);
                } else {
                    // Arrived
                    taxi.setX(targetX);
                    taxi.setY(targetY);

                    // If IDLE, pick new random patrol point
                    if (taxi.getStatus().equals("AVAILABLE")) {
                        // Only move if at target
                        int randomDestX = 50 + (int) (Math.random() * 12) * 100;
                        int randomDestY = 50 + (int) (Math.random() * 7) * 100;
                        taxi.setTargetX(randomDestX);
                        taxi.setTargetY(randomDestY);
                    }
                }
            }

            // Rebuild passenger states completely to avoid stale data
            // 1. Add Waiting Passengers
            List<Passenger> waiting = bookingSystem.getWaitingPassengers();
            // Create a set of active IDs to cleanup stale ones
            java.util.Set<String> activeIds = new java.util.HashSet<>();

            for (Passenger p : waiting) {
                activeIds.add(p.getId());
                PassengerState state = passengerStates.computeIfAbsent(p.getId(), k -> new PassengerState());
                state.id = p.getId();
                state.gender = p.getGender().toString();
                state.name = p.getName();
                state.avatarUrl = p.getAvatarUrl();
                state.x = p.getX();
                state.y = p.getY();
                state.destination = p.getDestination();
                state.status = "WAITING";
            }

            // 2. Add In-Ride Passengers
            for (Taxi taxi : taxis) {
                try {
                    for (Passenger p : taxi.getCurrentPassengers()) {
                        activeIds.add(p.getId());
                        PassengerState state = passengerStates.computeIfAbsent(p.getId(), k -> new PassengerState());
                        state.id = p.getId(); // Ensure basic fields are set if wasn't in waiting map
                        state.gender = p.getGender().toString();
                        state.name = p.getName();
                        state.avatarUrl = p.getAvatarUrl();
                        state.destination = p.getDestination();
                        state.status = "IN_RIDE";
                    }
                } catch (Exception e) {}
            }

            // 3. Remove passengers who are no longer waiting OR in a taxi (i.e., Dropped
            // off)
            passengerStates.keySet().removeIf(id -> !activeIds.contains(id));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            String filePath = "web" + path;
            File file = new File(filePath);

            if (!file.exists() || !file.isFile()) {
                sendResponse(exchange, 404, "text/plain", "File not found");
                return;
            }

            String contentType = getContentType(filePath);
            byte[] content = Files.readAllBytes(file.toPath());
            sendResponseBytes(exchange, 200, contentType, content);
        }

        private String getContentType(String filePath) {
            if (filePath.endsWith(".html"))
                return "text/html";
            if (filePath.endsWith(".css"))
                return "text/css";
            if (filePath.endsWith(".js"))
                return "application/javascript";
            if (filePath.endsWith(".json"))
                return "application/json";
            if (filePath.endsWith(".png"))
                return "image/png";
            if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg"))
                return "image/jpeg";
            return "text/plain";
        }
    }

    static class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method not allowed");
                return;
            }

            SimulationState state = new SimulationState();
            state.taxis = new ArrayList<>(taxiStates.values());
            state.passengers = new ArrayList<>(passengerStates.values());
            state.queueSize = bookingSystem.getQueueSize();
            state.totalMalesServed = bookingSystem.getTotalMalesServed();
            state.totalFemalesServed = bookingSystem.getTotalFemalesServed();
            state.totalPassengersServed = state.totalMalesServed + state.totalFemalesServed;

            String json = JsonUtil.toJson(state);
            sendResponse(exchange, 200, "application/json", json);
        }
    }

    static class AddTaxiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method not allowed");
                return;
            }

            if (taxiExecutor == null || taxiExecutor.isShutdown()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Simulation not started\"}");
                return;
            }

            int newTaxiId = taxis.size() + 1;
            Taxi newTaxi = new Taxi(newTaxiId, bookingSystem);
            taxis.add(newTaxi);
            taxiExecutor.execute(newTaxi);

            sendResponse(exchange, 200, "application/json", "{\"success\":true,\"taxiId\":" + newTaxiId + "}");
        }
    }

    static class AddPassengerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method not allowed");
                return;
            }

            if (passengerExecutor == null || passengerExecutor.isShutdown()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Simulation not started\"}");
                return;
            }

            String body = readRequestBody(exchange);
            Map<String, Object> request = JsonUtil.fromJson(body);

            Object maleObj = request.getOrDefault("maleCount", 0);
            Object femaleObj = request.getOrDefault("femaleCount", 0);
            int maleCount = maleObj instanceof Number ? ((Number) maleObj).intValue() : 0;
            int femaleCount = femaleObj instanceof Number ? ((Number) femaleObj).intValue() : 0;

            for (int i = 0; i < maleCount; i++) {
                Passenger p = new Passenger(Gender.MALE, bookingSystem);
                passengerExecutor.execute(p);
            }
            for (int i = 0; i < femaleCount; i++) {
                Passenger p = new Passenger(Gender.FEMALE, bookingSystem);
                passengerExecutor.execute(p);
            }

            sendResponse(exchange, 200, "application/json", "{\"success\":true}");
        }
    }

    static class StartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method not allowed");
                return;
            }

            if (taxiExecutor != null && !taxiExecutor.isShutdown()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Simulation already running\"}");
                return;
            }

            taxiExecutor = Executors.newFixedThreadPool(10);
            passengerExecutor = Executors.newCachedThreadPool();

            // Start existing taxis
            for (Taxi taxi : taxis) {
                taxiExecutor.execute(taxi);
            }

            sendResponse(exchange, 200, "application/json", "{\"success\":true}");
        }
    }

    static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equals("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method not allowed");
                return;
            }

            if (taxiExecutor != null && !taxiExecutor.isShutdown()) {
                taxiExecutor.shutdownNow();
            }
            if (passengerExecutor != null && !passengerExecutor.isShutdown()) {
                passengerExecutor.shutdownNow();
            }

            taxis.clear();
            bookingSystem = new BookingSystem();
            taxiStates.clear();
            passengerStates.clear();

            sendResponse(exchange, 200, "application/json", "{\"success\":true}");
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String response)
            throws IOException {
        sendResponseBytes(exchange, statusCode, contentType,
                response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void sendResponseBytes(HttpExchange exchange, int statusCode, String contentType, byte[] response)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}

