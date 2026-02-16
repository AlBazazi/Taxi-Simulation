package components;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import models.*;
import java.util.List;
import util.Logger;

/**
 * SCD Concept: Shared Resource & Synchronization Core
 * Simulates a thread-safe Queue and handles the Race Condition.
 */
public class BookingSystem {
    // SCD Concept: Generics - Type Safety is enforced.
    private final Queue<Passenger> passengerQueue = new LinkedList<>();
    private int totalMalesServed = 0;
    private int totalFemalesServed = 0;

    public synchronized void addPassengerToQueue(Passenger passenger) {
        passengerQueue.offer(passenger);
    }
    public synchronized void recordPassengerDropOff(Passenger passenger) {
        if (passenger.getGender() == Gender.MALE) {
            totalMalesServed++;
        } else {
            totalFemalesServed++;
        }
    }

    /**
     * SCD Concept: Synchronization Point & Race Condition Avoidance (RCA)
     */
    public synchronized void findAndPickPassengers(Taxi taxi) {
        if (passengerQueue.isEmpty()) {
            return;
        }
        Passenger bestPassenger = null;
        double minDistance = Double.MAX_VALUE;
        Iterator<Passenger> iterator = passengerQueue.iterator();
        while (iterator.hasNext()) {
            Passenger p = iterator.next();
            if (taxi.canPickUp(p)) {
                double dist = Math.sqrt(Math.pow(taxi.getX() - p.getX(), 2) 
                    + Math.pow(taxi.getY() - p.getY(), 2));
                if (dist < minDistance) {
                    minDistance = dist;
                    bestPassenger = p;
                }
            }
        }
        
        if (bestPassenger != null) {
            passengerQueue.remove(bestPassenger);
            Logger.log(String.format("Taxi T%d: Lock acquired, claimed %s (RCA Success). Distance: %.0f", 
                taxi.getId(), bestPassenger.getId(), minDistance));
            taxi.pickPassenger(bestPassenger);
        }
    }
    public int getQueueSize() {
        return passengerQueue.size();
    }
    
    public int getTotalMalesServed() {
        return totalMalesServed;
    }
    
    public int getTotalFemalesServed() {
        return totalFemalesServed;
    }
    public synchronized List<Passenger> getWaitingPassengers() {
        return new java.util.ArrayList<>(passengerQueue);
    }
}