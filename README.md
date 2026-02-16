# Taxi Sharing Simulation - Web Application

A web-based taxi sharing simulation application with a Java backend and modern web frontend.

## Features

- **Dynamic Phone Interfaces**: 
  - Driver phones displayed on the right side (one per taxi)
  - Passenger phones displayed on the left side (one per passenger)
  - Automatically updates as you add taxis and passengers

- **Live Map Visualization**: 
  - Real-time map in the center showing taxi positions and passenger locations
  - Visual representation of the taxi sharing system

[TODO: Make own map with selected pickup and dropoff points]

- **Java Backend**: 
  - All business logic remains in Java
  - RESTful API for communication
  - Thread-safe booking system

## How to Run

1. **Compile the Java code**:
   ```bash
   javac -d bin -sourcepath src src/server/WebServer.java src/server/JsonUtil.java src/components/*.java src/models/*.java src/util/*.java
   ```

2. **Run the web server**:
   ```bash
   java -cp bin server.WebServer
   ```

3. **Open your browser** and navigate to:
   ```
   http://localhost:8080
   ```

## Usage

1. **Start Simulation**: Click "Start" to begin
2. **Add Taxis**: Click "Add Taxi" to add driver phones (appears on the right)
3. **Add Passengers**: Click "Add Passenger" to add passenger phones (appears on the left)
   - Enter the number of male and female passengers
4. **View Map**: The center panel shows a live map with taxi and passenger positions
5. **Monitor Stats**: The footer shows real-time statistics

## Project Structure

```
final/
├── src/
│   ├── server/          # Web server and API endpoints
│   ├── components/       # Booking system logic
│   ├── models/          # Taxi, Passenger, Gender models
│   └── util/            # Utilities (Logger, FareCalculator)
├── web/                 # Frontend files
│   ├── index.html      # Main HTML page
│   ├── styles.css      # Styling
│   └── app.js          # Frontend JavaScript
```

## API Endpoints

- `GET /api/state` - Get current simulation state
- `POST /api/start` - Start the simulation
- `POST /api/reset` - Reset the simulation
- `POST /api/addTaxi` - Add a new taxi
- `POST /api/addPassenger` - Add passengers (requires JSON body with `maleCount` and `femaleCount`)

## Notes

- The web server runs on port 8080 by default
- All static files (HTML, CSS, JS) are served from the `web/` directory
- The simulation updates every 200ms for smooth real-time visualization

