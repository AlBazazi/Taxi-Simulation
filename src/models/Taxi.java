package models;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import components.BookingSystem;
import util.*;

/**
 * SCD Concept: Taxi Thread (Runnable)
 */
public class Taxi implements Runnable {
    /**
     * SCD Concept: Immutability - All core identity fields are final.
     */
    private final int id;
    private final int capacity = 3;
    private final BookingSystem bookingSystem;
    private final List<Passenger> currentPassengers = new ArrayList<>();
    private static final int MAX_WAIT_CYCLES = 2;
    private final String driverName;
    private final String driverAvatarUrl;

    private double earnings = 0.0;
    private volatile String status = "AVAILABLE";
    private int waitCycles = 0;
    private String lastMessage = "";

    // Movement state
    private double x, y;
    private double targetX, targetY;
    private Passenger targetPassenger;

    public Taxi(int id, BookingSystem bookingSystem) {
        this.id = id;
        this.bookingSystem = bookingSystem;
        this.driverName = getRandomDriverName();
        this.driverAvatarUrl = "https://randomuser.me/api/portraits/men/" + ((int) (Math.random() * 90) + 10) + ".jpg";

        // Random initial position (Restricted Area)
        int gridX = 2 + (int) (Math.random() * 7); // 2 to 8
        int gridY = 1 + (int) (Math.random() * 4); // 1 to 4
        this.x = 50 + gridX * 100;
        this.y = 50 + gridY * 100;
        this.targetX = this.x;
        this.targetY = this.y;
    }

    private String getRandomDriverName() {
        String[] names = { "Muhammad", "Imran", "Rashid", "Naveed", "Tariq", "Javed", "Kamran", "Adnan", "Sohail",
                "Rizwan" };
        return names[(int) (Math.random() * names.length)];
    }

    @Override
    public void run() {
        Logger.log(String.format("Taxi T%d: Started. Searching for Passengers...", id));
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Polling interval (Checking the queue every 2 seconds)
                int pollInterval = 1; // Faster polling for responsiveness
                TimeUnit.SECONDS.sleep(pollInterval);

                if (isReadyToDepart()) {
                    startRide();
                } else {
                    // Try to find a passenger
                    bookingSystem.findAndPickPassengers(this);

                    // If we found one (assigned by bookingSystem calling pickPassenger)
                    if (targetPassenger != null) {
                        waitForArrival();
                        // Arrived!
                        targetPassenger.signalPickedUp();
                        Logger.log(String.format("Taxi T%d: Physically reached %s. Boarded.", id,
                                targetPassenger.getId()));
                        targetPassenger = null;

                        // If not full, become available to patrol/find more
                        if (!isReadyToDepart()) {
                            status = "AVAILABLE";
                        }
                    } else if (status.equals("AVAILABLE") && Math.abs(x - targetX) < 10 && Math.abs(y - targetY) < 10) {
                        // Patrol logic: If idle and not moving, pick a new random spot in restricted
                        // area
                        if (Math.random() < 0.05) { // Occasional move
                            int pX = 2 + (int) (Math.random() * 7);
                            int pY = 1 + (int) (Math.random() * 4);
                            this.targetX = 50 + pX * 100;
                            this.targetY = 50 + pY * 100;
                        }
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void waitForArrival() throws InterruptedException {
        while (Math.abs(x - targetX) > 1 || Math.abs(y - targetY) > 1) {
            Thread.sleep(100);
        }
    }

    // Checks if the taxi is full based on gender rules or general capacity
    // Also considers whether the taxi should wait for more passengers instead of
    // departing
    private boolean isReadyToDepart() {
        int size = currentPassengers.size();
        if (size == 0)
            return false;

        long gents = currentPassengers.stream().filter(p -> p.getGender() == Gender.MALE).count();
        long ladies = currentPassengers.stream().filter(p -> p.getGender() == Gender.FEMALE).count();
        // Sassti Sawari Rule: If 1M + 1F, depart.
        if (gents == 1 && ladies == 1) {
            waitCycles = 0;
            return true;
        }
        if (size == capacity) {
            waitCycles = 0;
            return true;
        }
        if (shouldWaitForMorePassengers()) {
            waitCycles++;
            if (waitCycles >= MAX_WAIT_CYCLES) {
                waitCycles = 0;
                return true;
            }
            return false;
        }
        waitCycles = 0;
        return false;
    }

    private boolean shouldWaitForMorePassengers() {
        int passengerCount = currentPassengers.size();
        if (passengerCount == 1) {
            return Math.random() < 0.5;
        }
        if (passengerCount == 2) {
            return Math.random() < 0.3;
        }
        return false; // Don't wait if full
    }

    public void startRide() throws InterruptedException {
        synchronized (this) {
            if (status.equals("ON_RIDE"))
                return;
            status = "ON_RIDE";
            Logger.log(String.format("Taxi T%d: **RIDE STARTED** (Load: %d).", id, currentPassengers.size()));
        }

        // Simulate travel to destination (Restricted Area)
        int dX = 2 + (int) (Math.random() * 7);
        int dY = 1 + (int) (Math.random() * 4);
        this.targetX = 50 + dX * 100;
        this.targetY = 50 + dY * 100;

        waitForArrival(); // Drive there

        Logger.log(String.format("Taxi T%d: Reached destination. Dropping off in 4s...", id));
        Logger.log(String.format("Taxi T%d: Reached destination. Dropping off in 5s...", id));
        synchronized (this) {
            this.lastMessage = "Arrived at Destination. Dropping off...";
        }
        Thread.sleep(5000); // Wait for 5 seconds

        dropPassengers();
    }

    private synchronized void dropPassengers() {
        int numPassengers = currentPassengers.size();
        double revenue = FareCalculator.calculateRevenue(numPassengers);
        earnings += revenue;

        // Record passenger statistics before clearing
        for (Passenger p : currentPassengers) {
            bookingSystem.recordPassengerDropOff(p);
        }

        currentPassengers.clear();
        status = "AVAILABLE";
        Logger.log(String.format("Taxi T%d: **PASSENGERS DROPPED**. New Earnings: PKR %.2f. Now Empty.", id, earnings));
        status = "AVAILABLE";
        this.lastMessage = "Dropped off passenger(s)";
        Logger.log(String.format("Taxi T%d: **PASSENGERS DROPPED**. New Earnings: PKR %.2f. Now Empty.", id, earnings));

        // Clear message after a short delay so it doesn't persist forever on UI
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (Exception e) {
            }
            this.lastMessage = "";
        }).start();
    }

    /**
     * Implements the Sassti Sawari Gender Rule Check.
     * Rule: Max 3 capacity. If mixed gender, max 2 total.
     */
    public boolean canPickUp(Passenger newPassenger) {
        if (currentPassengers.size() >= capacity)
            return false;
        if (status.equals("ON_RIDE"))
            return false; // Can't pick if already engaged in ride journey

        long gents = currentPassengers.stream().filter(p -> p.getGender() == Gender.MALE).count();
        long ladies = currentPassengers.stream().filter(p -> p.getGender() == Gender.FEMALE).count();

        long newGents = gents + (newPassenger.getGender() == Gender.MALE ? 1 : 0);
        long newLadies = ladies + (newPassenger.getGender() == Gender.FEMALE ? 1 : 0);

        int newTotal = currentPassengers.size() + 1;

        // Rule Check: If mixed gender, total load cannot exceed 2.
        if (newGents > 0 && newLadies > 0 && newTotal > 2) {
            return false;
        }

        return newTotal <= capacity;
    }

    public synchronized void pickPassenger(Passenger passenger) {
        currentPassengers.add(passenger);
        // Do NOT signal yet. Wait for travel.
        this.targetPassenger = passenger;
        this.targetX = passenger.getX();
        this.targetY = passenger.getY();
        this.status = "PICKING_UP";
        this.lastMessage = "On way to pickup " + passenger.getName();
        Logger.log(String.format("Taxi T%d assigned %s. Moving to pickup...", id, passenger.getId()));
    }

    // Getters for GUI updates
    public int getId() {
        return id;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDriverAvatarUrl() {
        return driverAvatarUrl;
    }

    public synchronized String getLastMessage() {
        return lastMessage;
    }

    public String getStatus() {
        return status;
    }

    public double getEarnings() {
        return earnings;
    }

    public synchronized List<Passenger> getCurrentPassengers() {
        return new ArrayList<>(currentPassengers);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public void setTargetX(double x) {
        this.targetX = x;
    }

    public void setTargetY(double y) {
        this.targetY = y;
    }
}