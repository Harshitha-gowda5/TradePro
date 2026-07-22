import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class AdvancedMatchingEngine {
    
    public static class Order {
        String orderId, ticker, side;
        int qty;
        double price;
        long timestamp;

        public Order(String orderId, String ticker, String side, int qty, double price) {
            this.orderId = orderId;
            this.ticker = ticker;
            this.side = side;
            this.qty = qty;
            this.price = price;
            this.timestamp = System.nanoTime(); 
        }
    }

    public static class OrderBook {
        private final PriorityQueue<Order> bids = new PriorityQueue<>((a, b) -> {
            if (b.price != a.price) return Double.compare(b.price, a.price);
            return Long.compare(a.timestamp, b.timestamp);
        });
        
        private final PriorityQueue<Order> asks = new PriorityQueue<>((a, b) -> {
            if (a.price != b.price) return Double.compare(a.price, b.price);
            return Long.compare(a.timestamp, b.timestamp);
        });

        public synchronized void submitOrder(Order order) {
            long startTime = System.nanoTime();
            
            if (order.side.equalsIgnoreCase("BUY")) {
                while (!asks.isEmpty() && order.qty > 0 && asks.peek().price <= order.price) {
                    Order activeAsk = asks.peek();
                    int matchQty = Math.min(order.qty, activeAsk.qty);
                    long latencyNs = System.nanoTime() - startTime;
                    
                    System.out.printf("[MATCH MATCHED] %s | %d shares @ ₹%.2f | Latency: %d ns%n", 
                                      order.ticker, matchQty, activeAsk.price, latencyNs);
                    
                    order.qty -= matchQty;
                    activeAsk.qty -= matchQty;
                    if (activeAsk.qty == 0) asks.poll();
                }
                if (order.qty > 0) bids.add(order);
            } else {
                while (!bids.isEmpty() && order.qty > 0 && bids.peek().price >= order.price) {
                    Order activeBid = bids.peek();
                    int matchQty = Math.min(order.qty, activeBid.qty);
                    long latencyNs = System.nanoTime() - startTime;
                    
                    System.out.printf("[MATCH MATCHED] %s | %d shares @ ₹%.2f | Latency: %d ns%n", 
                                      order.ticker, matchQty, activeBid.price, latencyNs);
                    
                    order.qty -= matchQty;
                    activeBid.qty -= matchQty;
                    if (activeBid.qty == 0) bids.poll();
                }
                if (order.qty > 0) asks.add(order);
            }
        }
    }

    private static final ConcurrentHashMap<String, OrderBook> clusterBooks = new ConcurrentHashMap<>();

    public static void routeOrder(Order order) {
        clusterBooks.computeIfAbsent(order.ticker, k -> new OrderBook()).submitOrder(order);
    }

    public static void main(String[] args) {
        System.out.println("--- Booting Indian Markets High-Throughput Java Matching Engine ---");
        System.out.println("[INFO] Listening for incoming trade dispatches on Local Network Port 9999...");
        
        ExecutorService pool = Executors.newFixedThreadPool(4);
        
        // This keeps the server socket locked and open permanently!
        try (ServerSocket serverSocket = new ServerSocket(9999)) {
            while (true) {
                // The engine pauses right here and waits for Python to send an order
                Socket clientSocket = serverSocket.accept(); 
                
                pool.submit(() -> {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                        String rawLine = in.readLine();
                        if (rawLine != null) {
                            String[] segments = rawLine.split(",");
                            if (segments.length == 4) {
                                String ticker = segments[0].trim();
                                String side = segments[1].trim();
                                int qty = Integer.parseInt(segments[2].trim());
                                double price = Double.parseDouble(segments[3].trim());
                                
                                String generatedId = "ORDER-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
                                Order networkOrder = new Order(generatedId, ticker, side, qty, price);
                                
                                routeOrder(networkOrder); 
                            }
                        }
                        clientSocket.close();
                    } catch (Exception e) {
                        System.err.println("[NET ERROR] Read failed: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[CRITICAL] Could not open port 9999: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }
}