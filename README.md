# Integrated Chess Board — ICB Core

A lightweight, bitboard-based **chess rules + move-generation core** for the **Integrated Chess Board (ICB)** project: a physical chessboard using **Hall effect sensors + LEDs** so you can play **on the board** against other people, bots, or online platforms.

> **Status (WIP)**  
> 🚧 Next: interface this core with the physical board + add bot/online play layers.


<p align="center">
  <img src="https://i.imgur.com/W9yTJt2.png" alt="Opening page screenshot" width=800>
  <br/>
  <em>Opening page (current CLI UI)</em>
</p>

---

## Table of Contents
- [What this repo is](#what-this-repo-is)
- [Current capabilities](#current-capabilities)
- [What’s still in progress](#whats-still-in-progress)
- [How the engine works (high-level)](#how-the-engine-works-high-level)
- [Code tour](#code-tour)
- [Board + bitboard model](#board--bitboard-model)
- [Run it locally (CLI)](#run-it-locally-cli)
- [Perft validation](#perft-validation)
- [Using ICB core in other projects](#using-icb-core-in-other-projects)
- [Planned physical-board integration](#planned-physical-board-integration)
- [Roadmap](#roadmap)
- [Contributing](#contributing)

---

## What this repo is

This repository is the **central chess engine core** for the Integrated Chess Board project. It focuses on:

- Efficient chess position representation (bitboards)
- Parsing/exporting **FEN**
- Generating **legal moves** (including special rules)
- Detecting **check / checkmate / stalemate**
- Providing a simple CLI to play a full game locally

The intent is to keep the core **dependency-free** (plain Java) so it can be embedded later into a board controller app, a hardware bridge layer (serial/BLE), or future integrations.

---

## Current capabilities

### Rules & legality (implemented)
- Full 8×8 chess position representation using a minimal set of state variables:
  - piece placement, side to move, castling rights, en passant square, halfmove/fullmove clocks
- Legal move generation for all pieces
- **Castling**, **en passant**, **promotion**
- Check detection + **checkmate / stalemate** detection
- Move application and reversal:
  - `Board.Move(Move)` produces the next board state
  - `Board.UndoMove(Move)` reconstructs the previous state using stored metadata

### Terminal game (local two-player)
The CLI (`Game`) supports:
- Direct coordinate moves: `e2e4`, promotions like `e7e8q`
- Castling input: `O-O` / `O-O-O` (also accepts `0-0` / `0-0-0`)
- “Selection mode”: type `e2` then `e4` (prints legal destinations)
- Useful commands:
  - `moves` / `?` list all legal moves
  - `fen` print current FEN
  - `history` / `hist` show move history
  - `takeback` / `undo` undo one ply
  - `reset` restart from the initial position
  - `draw` offer a draw (opponent prompted immediately)
  - `help`, `quit`

---

## What’s still in progress

This repo is the **core engine**, not the full ICB system. These layers are planned or not-yet-implemented here:

- Hardware integration layer (Hall sensor reads → square deltas → move inference, calibration, debouncing)
- LED feedback layer (highlight legal moves, errors, confirmations, check states)
- Bot play (e.g., Stockfish via UCI, or a custom evaluator/search)
- Online play (e.g., chess.com syncing)
- Additional draw rules automation (threefold repetition, 50-move rule, insufficient material)
- PGN import/export and saving games
- Clocks / time controls
- A richer UI (desktop/web/mobile) or stable API wrapper

---

## How the engine works 

Move generation works like this:

1. **Generate pseudo-legal moves** for a given square (piece movement rules + special moves)
2. For each candidate move:
   - Apply castling “pass-through square” attack checks
   - Apply the move to get a new board state
   - Reject it if the mover’s king is left in check
3. The remaining moves are **legal**

---

## Code tour

- `Board.java`  
  Board state using bitboards; FEN import/export; apply/undo moves; printing helpers.

- `Move.java`  
  Parses coordinate move strings (e.g., `e2e4`, `e7e8q`) and annotates:
  - moved piece type + color
  - captures (including en passant)
  - castling rook movement
  - promotion type
  - previous state needed for undo (castling rights, EP square, clocks)

- `MoveGenerator.java`  
  Pseudo-legal move generation + legal filtering (king safety), including:
  - sliding rays (rook/bishop/queen)
  - king/knight attack masks
  - pawn moves, captures, en passant, promotion
  - castling + attacked-square validation

- `Attacks.java`  
  Precomputed attack masks for knights/kings, plus direction vectors for slider pieces.

- `Game.java`  
  CLI game loop and UX: parsing, selection mode, history, takeback, draw offering.

- `Perft.java`  
  Perft harness to validate movegen correctness against known node counts.

- `Spec.txt`  
  Development TODO + key references

---

## Board + bitboard model

### Square indexing
Squares are indexed `0..63`:

- `a1 = 0`, `b1 = 1`, …, `h1 = 7`
- `a2 = 8`, …
- `h8 = 63`

### Bitboards
The engine uses:
- **color occupancy**: `white`, `black`
- **piece-type boards**: `pawns`, `knights`, `bishops`, `rooks`, `queens`, `kings`
- plus game state:
  - side to move
  - castling rights
  - en passant target (mask)
  - halfmove/fullmove clocks

### Packed move format
Moves are also represented in a packed `int`:
- bits `0–5`: from square
- bits `6–11`: to square
- bits `12–14`: promotion code (`0` none, `1 q`, `2 r`, `3 b`, `4 n`)

---

## Run it locally (CLI)

### Requirements
- Java 17+ recommended
- No external dependencies

### Compile

**macOS / Linux**
```bash
javac src/*.java
```

**Windows (PowerShell)**
```powershell
javac src\*.java
```

### Play
```bash
java -cp src Game
```

### Start from a custom FEN
```bash
java -cp src Game "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
```

---

## Perft validation

Perft is included to ensure the move generation is accurate for any and all positions.

Run:
```bash
java -cp src Perft 6
```

Expected node counts from the initial position:
- depth 1: `20`
- depth 2: `400`
- depth 3: `8902`
- depth 4: `197281`
- depth 5: `4865609`
- depth 6: `119060324`

<p>
  <img src="https://i.imgur.com/W0FVLsp.png" alt="Perft screenshot" width=400">
  <br/>
</p>

---

## Using ICB core in other projects

A simple one-move example:

```java
Board b = new Board(Board.startFEN);

// apply a move
Move m = new Move(b, "e2e4");
Board b2 = b.Move(m);

// list all legal moves in UCI coordinate format
int[] legal = MoveGenerator.GenerateAllLegal(b2);
for (int pm : legal) {
    System.out.println(MoveGenerator.packedToUci(pm));
}

// export to FEN
System.out.println(Board.BoardToFEN(b2));
```

---

## Planned physical-board integration

This core is designed to be called by a “board controller” layer.

### Expected flow
1. **Sense**: Hall sensors detect piece presence changes
2. **Interpret**: convert sensor deltas into a `(from, to)` candidate (and promotion choice if needed)
3. **Validate**: confirm the move exists in the engine’s legal move list
4. **Commit**: apply the move to update board state
5. **Feedback**: LEDs show legal moves, confirmations, or errors

---

## Roadmap

- [ ] Hardware I/O bridge (serial/BLE) to ingest board events
- [ ] Robust move inference from sensor changes (debounce + ambiguity resolution)
- [ ] LED guidance mode (legal moves, hints, capture highlighting)
- [ ] Bot integration (Stockfish/UCI or custom search)
- [ ] Online syncing (chess.com or other platform)
- [ ] Complete draw rules (threefold / 50-move / insufficient material)
- [ ] PGN import/export + saving games
- [ ] Clean public API wrapper around the core classes
- [ ] CI (compile + perft regression)

---

## Contributing

Issues and PRs are welcome -- especially around:
- making the core easier to embed (clean API boundaries)
- improving move generation/performance
- adding missing draw rules + PGN
- building the hardware-facing integration layer
