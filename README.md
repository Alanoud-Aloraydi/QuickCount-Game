# Quick Count — Multiplayer Math Game

A real-time multiplayer puzzle game built with **Java sockets** and a **Swing** GUI.
Players connect to a shared server, get matched into a game room, and race to solve
number puzzles across three levels. The first player to finish each level earns points,
and live scores are broadcast to everyone in the room.

> **University:** King Saud University — Information Technology
> **Course:** Computer Networks (socket programming project)

## How it works
- **`GameServer`** — a multi-threaded TCP server (port `9090`). It tracks connected
  players, matches them into rooms (2–4 players), runs the start countdown, and
  broadcasts player lists, scores, and level results.
- **`GameClient`** — the Swing desktop client. Players enter a username and the
  server's address, join the waiting room, then play the puzzle board.

Because this is a socket application (a desktop client plus a server process), it does
**not** run as a website — there is no browser URL to host. To play, you run the server
on one machine and each player runs the client, pointing it at the server's IP.

## Requirements
- Java 8 or newer (`java -version` to check)

## Run it

**1. Start the server** (on the host machine):
```bash
cd src
javac GameServer.java GameClient.java
java GameServer
```
The server prints `Game Server is running...` and listens on port `9090`.

**2. Start a client** (on each player's machine):
```bash
java GameClient
```
In the login window, enter a username and the server address:
- Same computer as the server: `localhost`
- Another computer on the network: the server's local IP (e.g. `192.168.1.20`)

Open at least **two** clients to start a match. The game begins once enough players
join the waiting room.

> Assets (`math.jpg`, `sounds/`) load from the classpath, so run the client from a
> location where those files sit alongside the compiled classes (the `src` folder, or
> the NetBeans `build/classes` output).

## Project structure
```
src/
  GameServer.java    server: matchmaking, rooms, scoring, broadcasts
  GameClient.java    client: login, waiting room, puzzle board, live scores
  math.jpg           background image
  sounds/            correct / wrong / intro sound effects
```

## Notes
This project was built for the Computer Networks course in the Information Technology
program at King Saud University, to demonstrate TCP socket communication,
multi-threading, and a client–server architecture.
