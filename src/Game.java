import java.util.*;

public final class Game {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        final String initialFen = (args.length > 0)
                ? String.join(" ", args)
                : Board.startFEN;

        Board b = new Board(initialFen);

        Deque<Board> boardHistory = new ArrayDeque<>();
        ArrayList<String> moveHistory = new ArrayList<>();

        int selectedSq = -1;

        printIntro();

        while (true) {
            System.out.println();
            System.out.println("============================================================");
            System.out.println();

            if (selectedSq >= 0) Board.PrintLegal(b, selectedSq);
            else Board.PrintBoard(b);

            System.out.println();

            boolean sideWhite = b.whiteTurn;

            if (MoveGenerator.IsCheckmate(b)) {
                System.out.println((sideWhite ? "White" : "Black") + " is checkmated. "
                        + (!sideWhite ? "White" : "Black") + " wins.");
                break;
            }
            if (MoveGenerator.IsStalemate(b)) {
                System.out.println("Stalemate. Draw.");
                break;
            }
            if (MoveGenerator.IsInCheck(b, sideWhite)) {
                System.out.println((sideWhite ? "White" : "Black") + " is in check.");
                System.out.println();
            }

            int[] legalPacked = MoveGenerator.GenerateAllLegal(b);
            Map<String, Integer> legalMap = new HashMap<>(legalPacked.length * 2);
            for (int pm : legalPacked) legalMap.put(MoveGenerator.packedToUci(pm), pm);

            if (selectedSq >= 0) {
                System.out.print((sideWhite ? "White" : "Black") + " selected " + sqToName(selectedSq) + " > ");
            } else {
                System.out.print((sideWhite ? "White" : "Black") + " to move > ");
            }

            String inputRaw = sc.nextLine();
            if (inputRaw == null) break;
            String input = inputRaw.trim();

            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                System.out.println();
                System.out.println("Exiting.");
                break;
            }

            if (input.equalsIgnoreCase("help")) {
                printIntro();
                continue;
            }

            if (input.equalsIgnoreCase("fen")) {
                System.out.println();
                System.out.println("FEN: " + Board.BoardToFEN(b));
                continue;
            }

            if (input.equalsIgnoreCase("moves") || input.equals("?")) {
                System.out.println();
                for (int pm : legalPacked) System.out.print(MoveGenerator.packedToUci(pm) + " ");
                System.out.println();
                continue;
            }

            if (input.equalsIgnoreCase("history") || input.equalsIgnoreCase("hist")) {
                System.out.println();
                printMoveHistory(moveHistory);
                continue;
            }

            if (input.equalsIgnoreCase("cancel")) {
                selectedSq = -1;
                continue;
            }

            if (input.equalsIgnoreCase("takeback") || input.equalsIgnoreCase("undo")) {
                System.out.println();
                if (boardHistory.isEmpty()) {
                    System.out.println("Nothing to take back.");
                } else {
                    b = boardHistory.pop();
                    if (!moveHistory.isEmpty()) moveHistory.removeLast();
                    selectedSq = -1;
                    System.out.println("Took back one move.");
                }
                continue;
            }

            if (input.equalsIgnoreCase("reset")) {
                System.out.println();
                b = new Board(initialFen);
                boardHistory.clear();
                moveHistory.clear();
                selectedSq = -1;
                System.out.println("Reset to initial position.");
                continue;
            }

            if (input.equalsIgnoreCase("draw")) {
                System.out.println();
                boolean offerByWhite = b.whiteTurn;
                System.out.println((offerByWhite ? "White" : "Black") + " offers a draw.");
                System.out.println();

                boolean accepted = promptDrawResponse(sc, !offerByWhite);
                System.out.println();
                if (accepted) {
                    System.out.println("Draw agreed. Game ends in a draw.");
                    break;
                } else {
                    System.out.println("Draw declined. " + (offerByWhite ? "White" : "Black") + " must play a move.");
                }
                continue;
            }

            input = normalizeCastleInput(b, input);

            // direct move
            if (looksLikeUciMove(input)) {
                Integer packed = legalMap.get(input);
                if (packed == null) {
                    System.out.println();
                    System.out.println("Illegal move: " + input);
                    System.out.println("Type 'moves' to list legal moves, or select a square like 'e2'.");
                    continue;
                }

                boardHistory.push(b);
                moveHistory.add(input);

                Move m = new Move(b, input);
                b = b.Move(m);

                selectedSq = -1;
                continue;
            }

            // selection mode
            if (looksLikeSquare(input)) {
                int sq = parseSquare(input);

                if (selectedSq < 0) {
                    // Select piece
                    long ownOcc = b.whiteTurn ? b.white : b.black;
                    if ((ownOcc & (1L << sq)) == 0L) {
                        System.out.println();
                        System.out.println("That square doesn't contain one of your pieces: " + input);
                        continue;
                    }

                    int[] fromMoves = MoveGenerator.GenerateLegal(b, sq);
                    if (fromMoves.length == 0) {
                        System.out.println();
                        System.out.println("No legal moves from " + input + ".");
                        continue;
                    }

                    selectedSq = sq;
                } else {
                    if (sq == selectedSq) {
                        System.out.println();
                        System.out.println("Still selected " + sqToName(selectedSq) + ". Type a destination or 'cancel'.");
                        continue;
                    }

                    long ownOcc = b.whiteTurn ? b.white : b.black;
                    if ((ownOcc & (1L << sq)) != 0L) {
                        int[] fromMoves = MoveGenerator.GenerateLegal(b, sq);
                        if (fromMoves.length == 0) {
                            System.out.println();
                            System.out.println("No legal moves from " + sqToName(sq) + ". Still selected " + sqToName(selectedSq) + ".");
                            continue;
                        }
                        selectedSq = sq;
                        continue;
                    }

                    int[] selectedMoves = MoveGenerator.GenerateLegal(b, selectedSq);

                    int matchCount = 0;
                    int[] matches = new int[4];

                    for (int pm : selectedMoves) {
                        int from = pm & 63;
                        int to = (pm >>> 6) & 63;
                        if (from == selectedSq && to == sq) {
                            if (matchCount < matches.length) matches[matchCount] = pm;
                            matchCount++;
                        }
                    }

                    if (matchCount == 0) {
                        System.out.println();
                        System.out.println("Illegal destination " + sqToName(sq) + " from " + sqToName(selectedSq) + ".");
                        System.out.println("Type 'cancel' to deselect.");
                        continue;
                    }

                    int chosenPacked;
                    if (matchCount == 1) {
                        chosenPacked = matches[0];
                    } else {
                        System.out.println();
                        System.out.print("Promotion! Choose (q/r/b/n) > ");
                        String p = sc.nextLine().trim().toLowerCase(Locale.ROOT);
                        char pc = (p.isEmpty() ? '\0' : p.charAt(0));
                        int promoChoice = promoCode(pc);

                        if (promoChoice == 0) {
                            System.out.println("Invalid promotion choice. Cancelled.");
                            continue;
                        }

                        chosenPacked = -1;
                        for (int i = 0; i < Math.min(matchCount, matches.length); i++) {
                            int pm = matches[i];
                            int promo = (pm >>> 12) & 7;
                            if (promo == promoChoice) {
                                chosenPacked = pm;
                                break;
                            }
                        }

                        if (chosenPacked == -1) {
                            System.out.println("Promotion move not found. Cancelled.");
                            continue;
                        }
                    }

                    String uci = MoveGenerator.packedToUci(chosenPacked);
                    if (!legalMap.containsKey(uci)) {
                        System.out.println();
                        System.out.println("That move isn't legal: " + uci);
                        continue;
                    }

                    boardHistory.push(b);
                    moveHistory.add(uci);

                    Move m = new Move(b, uci);
                    b = b.Move(m);

                    selectedSq = -1;
                }
                continue;
            }

            System.out.println();
            System.out.println("Unrecognized input: " + input);
            System.out.println("Use e2e4, e7e8q, O-O, O-O-O, select with e2 then e4, or type 'help'.");
        }

        sc.close();
    }

    private static boolean promptDrawResponse(Scanner sc, boolean responderIsWhite) {
        while (true) {
            System.out.print((responderIsWhite ? "White" : "Black") + " response (accept/decline) > ");
            String resp = sc.nextLine();
            if (resp == null) return false;
            resp = resp.trim().toLowerCase(Locale.ROOT);

            if (resp.equals("accept") || resp.equals("a") || resp.equals("yes") || resp.equals("y")) return true;
            if (resp.equals("decline") || resp.equals("d") || resp.equals("no") || resp.equals("n")) return false;

            System.out.println("Please type 'accept' or 'decline'.");
        }
    }

    private static void printMoveHistory(List<String> moves) {
        if (moves.isEmpty()) {
            System.out.println("No moves yet.");
            return;
        }

        int moveNo = 1;
        for (int i = 0; i < moves.size(); i += 2) {
            String w = moves.get(i);
            String bl = (i + 1 < moves.size()) ? moves.get(i + 1) : "";
            if (bl.isEmpty()) System.out.println(moveNo + ". " + w);
            else System.out.println(moveNo + ". " + w + " " + bl);
            moveNo++;
        }
    }

    private static void printIntro() {
        System.out.println();
        System.out.println("============================================================");
        System.out.println("ICB Core");
        System.out.println("------------------------------------------------------------");
        System.out.println("Input styles:");
        System.out.println("  - Direct move:     e2e4   e7e8q");
        System.out.println("  - Castling:        O-O    O-O-O   (also accepts 0-0 / 0-0-0)");
        System.out.println("  - Selection mode:  type a square (e2) to select, then a destination (e4)");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  moves / ?    list all legal moves");
        System.out.println("  history      print move history (also: hist)");
        System.out.println("  fen          print current FEN");
        System.out.println("  cancel       deselect the current square");
        System.out.println("  draw         offer a draw (opponent prompted immediately)");
        System.out.println("  takeback     undo one ply (also: undo)");
        System.out.println("  reset        reset to initial position");
        System.out.println("  help         show this help");
        System.out.println("  quit         exit");
        System.out.println("============================================================");
        System.out.println();
    }

    private static String normalizeCastleInput(Board b, String s) {
        String t = s.replace("0", "O"); // allow 0-0
        if (t.equalsIgnoreCase("O-O")) return b.whiteTurn ? "e1g1" : "e8g8";
        if (t.equalsIgnoreCase("O-O-O")) return b.whiteTurn ? "e1c1" : "e8c8";
        return s;
    }

    private static boolean looksLikeSquare(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() != 2) return false;
        char f = Character.toLowerCase(s.charAt(0));
        char r = s.charAt(1);
        return f >= 'a' && f <= 'h' && r >= '1' && r <= '8';
    }

    private static int parseSquare(String s) {
        char f = Character.toLowerCase(s.charAt(0));
        char r = s.charAt(1);
        return (f - 'a') + (r - '1') * 8;
    }

    private static String sqToName(int sq) {
        return "" + (char) ('a' + (sq & 7)) + (char) ('1' + (sq >>> 3));
    }

    private static boolean looksLikeUciMove(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() != 4 && s.length() != 5) return false;

        char f1 = Character.toLowerCase(s.charAt(0));
        char r1 = s.charAt(1);
        char f2 = Character.toLowerCase(s.charAt(2));
        char r2 = s.charAt(3);

        if (!(f1 >= 'a' && f1 <= 'h' && r1 >= '1' && r1 <= '8')) return false;
        if (!(f2 >= 'a' && f2 <= 'h' && r2 >= '1' && r2 <= '8')) return false;

        if (s.length() == 5) {
            char p = Character.toLowerCase(s.charAt(4));
            return p == 'q' || p == 'r' || p == 'b' || p == 'n';
        }
        return true;
    }

    private static int promoCode(char c) {
        return switch (c) {
            case 'q' -> 1;
            case 'r' -> 2;
            case 'b' -> 3;
            case 'n' -> 4;
            default -> 0;
        };
    }

    private Game() {}
}