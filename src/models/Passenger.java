package models;

import components.BookingSystem;
import java.util.UUID;
import util.Logger;

/**
 * SCD Concept: Passenger Thread (Runnable)
 */
public class Passenger implements Runnable {
    /**
     * SCD Concept: Immutability - All core identity fields are final.
     */
    private final String id;
    private final Gender gender;
    private final String destination;
    private final BookingSystem bookingSystem;
    private final int x, y;
    private final String name;
    private final String avatarUrl;

    // Monitor object for ITC (Inter-Thread Communication)
    private final Object monitor = new Object();
    private volatile boolean isPickedUp = false;

    public Passenger(Gender g, BookingSystem bookingSystem) {
        this.id = "P-" + UUID.randomUUID().toString().substring(0, 3);
        this.gender = g;
        this.name = getRandomName(g);
        this.avatarUrl = getRandomAvatar(g);
        this.destination = getDestinations()[(int) (Math.random() * getDestinations().length)];
        this.bookingSystem = bookingSystem;
        this.x = 250 + (int) (Math.random() * 6) * 100;
        this.y = 150 + (int) (Math.random() * 4) * 100;
    }

    public Passenger(Gender g, BookingSystem bookingSystem, int x, int y) {
        this.id = "P-" + UUID.randomUUID().toString().substring(0, 3);
        this.gender = g;
        this.name = getRandomName(g);
        this.avatarUrl = getRandomAvatar(g);
        this.destination = getDestinations()[(int) (Math.random() * getDestinations().length)];
        this.bookingSystem = bookingSystem;
        this.x = x;
        this.y = y;
    }

    private String getRandomName(Gender g) {
        String[] maleNames = { "Ali", "Ahmed", "Bilal", "Usman", "Hamza", "Hassan", "Umer", "Zain", "Saad", "Fahad" };
        String[] femaleNames = { "Ayesha", "Fatima", "Zainab", "Maryam", "Sana", "Hina", "Sidra", "Amna", "Mahnoor",
                "Zara" };
        if (g == Gender.MALE)
            return maleNames[(int) (Math.random() * maleNames.length)];
        return femaleNames[(int) (Math.random() * femaleNames.length)];
    }

    private String getRandomAvatar(Gender g) {
        int id = (int) (Math.random() * 99) + 1;
        if (g == Gender.MALE)
            return "https://randomuser.me/api/portraits/men/" + id + ".jpg";
        return "https://randomuser.me/api/portraits/women/" + id + ".jpg";
    }

    private String[] getDestinations() {
        return new String[] { "Downtown", "Airport", "Suburb A", "Shopping Mall" };
    }

    @Override
    public void run() {
        Logger.log(String.format("%s (%s) sent a booking request.", id, gender));
        bookingSystem.addPassengerToQueue(this);
        synchronized (monitor) {
            while (!isPickedUp) {
                try {
                    Logger.log(String.format("%s (%s) **WAITING** for pickup).", id, gender));
                    monitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        Logger.log(String.format("%s (%s) **PICKED UP**! Ride Started.", id, gender));
    }

    /**
     * Called by the winning Taxi to signal that this passenger is claimed.
     * SCD Concept: ITC - Notifying the waiting thread.
     */
    public void signalPickedUp() {
        synchronized (monitor) {
            this.isPickedUp = true;
            monitor.notifyAll();
        }
    }

    public String getId() {
        return id;
    }

    public Gender getGender() {
        return gender;
    }

    public String getName() {
        return name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getDestination() {
        return destination;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}