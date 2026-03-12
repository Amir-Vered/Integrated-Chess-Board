public class Board {
    /*** Color Bitboards ***/
    public long white;
    public long black;

    /*** Piece Bitboards ***/
    public long pawns;
    public long knights;
    public long bishops;
    public long rooks;
    public long queens;
    public long kings;

    /*** Side to Move ***/
    public boolean whiteTurn;

    /*** Castling Ability ***/
    public boolean whiteKingside;
    public boolean whiteQueenside;
    public boolean blackKingside;
    public boolean blackQueenside;

    /*** En Passant Target Square ***/
    public long enPassantMask;

    /*** Halfmove Clock ***/
    public int halfMoves;

    /*** Fullmove Counter ***/
    public int fullMoves;

    public static String startFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public Board(String fen) {
        String[] splitFen = fen.split(" ");

        // Piece placement
        int rank = 7;
        int file = 0;
        for (char c : splitFen[0].toCharArray()) {
            if (c == '/') {
                rank--;
                file = 0;
                continue;
            }
            if (Character.isDigit(c)) {
                file += (c - '0');
                continue;
            }

            int sq = rank * 8 + file;
            long mask = 1L << sq;

            if (Character.isUpperCase(c)) this.white |= mask;
            else this.black |= mask;

            switch (Character.toLowerCase(c)) {
                case 'p': this.pawns   |= mask; break;
                case 'n': this.knights |= mask; break;
                case 'b': this.bishops |= mask; break;
                case 'r': this.rooks   |= mask; break;
                case 'q': this.queens  |= mask; break;
                case 'k': this.kings   |= mask; break;
            }
            file++;
        }

        // Side to move
        if ("w".equals(splitFen[1])) {
            this.whiteTurn = true;
        }

        // Castling Ability
        for (char c : splitFen[2].toCharArray()) {
            switch (c) {
                case '-': break;
                case 'K': whiteKingside = true; break;
                case 'Q': whiteQueenside = true; break;
                case 'k': blackKingside = true; break;
                case 'q': blackQueenside = true; break;
            }
        }

        // En Passant Target Square
        this.enPassantMask = "-".equals(splitFen[3]) ? 0L : SquareToMask(splitFen[3]);

        // Halfmove Clock
        this.halfMoves = Integer.parseInt(splitFen[4]);

        // Fullmove Counter
        this.fullMoves = Integer.parseInt(splitFen[5]);
    }

    // Copy Constructor
    public Board(Board b) {
        this.white = b.white;
        this.black = b.black;

        this.pawns = b.pawns;
        this.knights = b.knights;
        this.bishops = b.bishops;
        this.rooks = b.rooks;
        this.queens = b.queens;
        this.kings = b.kings;

        this.whiteTurn = b.whiteTurn;

        this.whiteKingside = b.whiteKingside;
        this.whiteQueenside = b.whiteQueenside;
        this.blackKingside = b.blackKingside;
        this.blackQueenside = b.blackQueenside;

        this.enPassantMask = b.enPassantMask;

        this.halfMoves = b.halfMoves;
        this.fullMoves = b.fullMoves;
    }

    public Board Move(Move m) {
        final byte WK = 8, WQ = 4, BK = 2, BQ = 1; // KQkq
        m.prevCastlingRights = (byte)(
                (this.whiteKingside  ? WK : 0) |
                (this.whiteQueenside ? WQ : 0) |
                (this.blackKingside  ? BK : 0) |
                (this.blackQueenside ? BQ : 0)
        );
        m.prevEnPassantSquare = (this.enPassantMask == 0L) ? -1 : Long.numberOfTrailingZeros(this.enPassantMask);
        m.prevHalfMoves = this.halfMoves;
        m.prevFullMoves = this.fullMoves;

        Board b = new Board(this); // copy

        long fromMask = 1L << m.from;
        long toMask   = 1L << m.to;

        // Clear EP by default; may be set again
        b.enPassantMask = 0L;

        // Capture
        if (m.capturedType != '\0') {
            long capMask = 1L << m.capturedSquare;

            if (m.movedWhite) b.black &= ~capMask;
            else              b.white &= ~capMask;

            removePieceType(b, m.capturedType, capMask);
        }

        // Remove Mover
        if (m.movedWhite) b.white &= ~fromMask;
        else              b.black &= ~fromMask;
        removePieceType(b, m.movedType, fromMask);

        // Place Piece
        char placedType = (m.promotionType != '\0') ? m.promotionType : m.movedType;

        if (m.movedWhite) b.white |= toMask;
        else              b.black |= toMask;
        addPieceType(b, placedType, toMask);

        // Castling
        if (m.isCastling) {
            long rookFromMask = 1L << m.rookFromSquare;
            long rookToMask   = 1L << m.rookToSquare;

            if (m.movedWhite) {
                b.white &= ~rookFromMask;
                b.white |=  rookToMask;
            } else {
                b.black &= ~rookFromMask;
                b.black |=  rookToMask;
            }

            b.rooks &= ~rookFromMask;
            b.rooks |=  rookToMask;
        }

        // Update Castling Rights
        if (m.movedType == 'k') {
            if (m.movedWhite) { b.whiteKingside = false; b.whiteQueenside = false; }
            else              { b.blackKingside = false; b.blackQueenside = false; }
        }
        if (m.movedType == 'r') {
            if (m.movedWhite) {
                if (m.from == 0) b.whiteQueenside = false; // a1
                else if (m.from == 7) b.whiteKingside = false; // h1
            } else {
                if (m.from == 56) b.blackQueenside = false; // a8
                else if (m.from == 63) b.blackKingside = false; // h8
            }
        }
        if (m.capturedType == 'r') {
            if (m.movedWhite) { // captured black rook
                if (m.capturedSquare == 56) b.blackQueenside = false;
                else if (m.capturedSquare == 63) b.blackKingside = false;
            } else { // captured white rook
                if (m.capturedSquare == 0) b.whiteQueenside = false;
                else if (m.capturedSquare == 7) b.whiteKingside = false;
            }
        }

        // EP
        if (m.isDoublePush && m.movedType == 'p') {
            int epSq = m.movedWhite ? (m.from + 8) : (m.from - 8);
            b.enPassantMask = 1L << epSq;
        }

        // Clocks
        if (m.movedType == 'p' || m.capturedType != '\0') b.halfMoves = 0;
        else b.halfMoves = this.halfMoves + 1;

        b.fullMoves = this.fullMoves + (m.movedWhite ? 0 : 1);

        b.whiteTurn = !m.movedWhite;

        return b;
    }

    public Board UndoMove(Move m) {
        Board b = new Board(this); // copy

        final byte WK = 8, WQ = 4, BK = 2, BQ = 1; // KQkq
        b.whiteKingside  = (m.prevCastlingRights & WK) != 0;
        b.whiteQueenside = (m.prevCastlingRights & WQ) != 0;
        b.blackKingside  = (m.prevCastlingRights & BK) != 0;
        b.blackQueenside = (m.prevCastlingRights & BQ) != 0;

        b.enPassantMask = (m.prevEnPassantSquare == -1) ? 0L : (1L << m.prevEnPassantSquare);
        b.halfMoves = m.prevHalfMoves;
        b.fullMoves = m.prevFullMoves;
        b.whiteTurn = m.movedWhite;

        long fromMask = 1L << m.from;
        long toMask   = 1L << m.to;

        char placedType = (m.promotionType != '\0') ? m.promotionType : m.movedType;

        // Remove the moved piece from `to`
        if (m.movedWhite) b.white &= ~toMask;
        else              b.black &= ~toMask;
        removePieceType(b, placedType, toMask);

        // Put the original moved piece back on `from`
        char originalType = (m.promotionType != '\0') ? 'p' : m.movedType;

        if (m.movedWhite) b.white |= fromMask;
        else              b.black |= fromMask;
        addPieceType(b, originalType, fromMask);

        // If castling, move rook back
        if (m.isCastling) {
            long rookFromMask = 1L << m.rookFromSquare;
            long rookToMask   = 1L << m.rookToSquare;

            if (m.movedWhite) {
                b.white &= ~rookToMask;
                b.white |=  rookFromMask;
            } else {
                b.black &= ~rookToMask;
                b.black |=  rookFromMask;
            }

            b.rooks &= ~rookToMask;
            b.rooks |=  rookFromMask;
        }

        // If there was a capture, restore captured piece
        if (m.capturedType != '\0') {
            long capMask = 1L << m.capturedSquare;

            // captured color is opponent
            if (m.movedWhite) b.black |= capMask;
            else              b.white |= capMask;

            addPieceType(b, m.capturedType, capMask);
        }

        return b;
    }

    private static void addPieceType(Board b, char type, long mask) {
        switch (type) {
            case 'p': b.pawns   |= mask; break;
            case 'n': b.knights |= mask; break;
            case 'b': b.bishops |= mask; break;
            case 'r': b.rooks   |= mask; break;
            case 'q': b.queens  |= mask; break;
            case 'k': b.kings   |= mask; break;
            default: throw new IllegalArgumentException("Bad piece type: " + type);
        }
    }

    private static void removePieceType(Board b, char type, long mask) {
        switch (type) {
            case 'p': b.pawns   &= ~mask; break;
            case 'n': b.knights &= ~mask; break;
            case 'b': b.bishops &= ~mask; break;
            case 'r': b.rooks   &= ~mask; break;
            case 'q': b.queens  &= ~mask; break;
            case 'k': b.kings   &= ~mask; break;
            default: throw new IllegalArgumentException("Bad piece type: " + type);
        }
    }

    public static String BoardToFEN(Board b) {
        // Piece placement
        StringBuilder placement = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int sq = rank * 8 + file;
                long mask = 1L << sq;

                char p = PieceCharAt(b, mask);
                if (p == ' ') {
                    empty++;
                } else {
                    if (empty != 0) {
                        placement.append(empty);
                        empty = 0;
                    }
                    placement.append(p);
                }
            }
            if (empty != 0) placement.append(empty);
            if (rank != 0) placement.append('/');
        }

        // Side to move
        String stm = b.whiteTurn ? "w" : "b";

        // Castling rights
        StringBuilder castling = new StringBuilder();
        if (b.whiteKingside)  castling.append('K');
        if (b.whiteQueenside) castling.append('Q');
        if (b.blackKingside)  castling.append('k');
        if (b.blackQueenside) castling.append('q');
        String castlingStr = castling.isEmpty() ? "-" : castling.toString();

        // En passant
        String ep = "-";
        if (b.enPassantMask != 0L && Long.bitCount(b.enPassantMask) == 1) {
            ep = MaskToSquare(b.enPassantMask);
        }

        // Clocks
        return placement + " " + stm + " " + castlingStr + " " + ep + " " + b.halfMoves + " " + b.fullMoves;
    }

    public static String MaskToSquare(long mask) {
        int sq = Long.numberOfTrailingZeros(mask);
        return SquareToString(sq);
    }

    public static String SquareToString(int sq) {
        int file = sq & 7;
        int rank = sq >>> 3;
        return "" + (char)('a' + file) + (char)('1' + rank);
    }

    public static long SquareToMask(String s) {
        if (s == null || s.length() != 2) throw new IllegalArgumentException("Bad square: " + s);
        char f = Character.toLowerCase(s.charAt(0));
        char r = s.charAt(1);
        if (f < 'a' || f > 'h' || r < '1' || r > '8') throw new IllegalArgumentException("Bad square: " + s);
        int i = (f - 'a') + (r - '1') * 8;
        return 1L << i;
    }

    public static void PrintBoard(Board b) {
        System.out.println("\n   +---+---+---+---+---+---+---+---+");
        for (int rank = 7; rank >= 0; rank--) {
            System.out.print(" " + (rank + 1) + " |");
            for (int file = 0; file < 8; file++) {
                int sq = rank * 8 + file;
                long mask = 1L << sq;

                char piece = PieceCharAt(b, mask);
                System.out.print(" " + piece + " |");
            }
            System.out.println("\n   +---+---+---+---+---+---+---+---+");
        }
        System.out.println("     a   b   c   d   e   f   g   h\n");
    }

    public static void PrintLegal(Board b, int fromSq) {
        int[] moves = MoveGenerator.GenerateLegal(b, fromSq);

        long targetsMask = 0L;
        for (int pm : moves) {
            int to = (pm >>> 6) & 63;
            targetsMask |= 1L << to;
        }

        long occ = b.white | b.black;

        System.out.println("\nSelected: " + sqToName(fromSq) + "   Legal moves: " + moves.length);
        System.out.println("Legend: [ ] selected square, '.' quiet move, 'x' capture\n");

        System.out.println("   +---+---+---+---+---+---+---+---+");
        for (int rank = 7; rank >= 0; rank--) {
            System.out.print(" " + (rank + 1) + " |");
            for (int file = 0; file < 8; file++) {
                int sq = rank * 8 + file;
                long mask = 1L << sq;

                if (sq == fromSq) {
                    char pc = PieceCharAt(b, mask);
                    if (pc == ' ') pc = '*';
                    System.out.print("[" + pc + "]|");
                    continue;
                }

                if ((targetsMask & mask) != 0L) {
                    // destination square
                    if ((occ & mask) != 0L) System.out.print(" x |");  // capture
                    else                    System.out.print(" . |");  // quiet
                    continue;
                }

                char piece = PieceCharAt(b, mask);
                System.out.print(" " + piece + " |");
            }
            System.out.println("\n   +---+---+---+---+---+---+---+---+");
        }
        System.out.println("     a   b   c   d   e   f   g   h\n");
    }

    private static String sqToName(int sq) {
        int file = sq & 7;
        int rank = sq >>> 3;
        return "" + (char)('a' + file) + (char)('1' + rank);
    }

    public static char PieceCharAt(Board b, long mask) {
        if (((b.white | b.black) & mask) == 0) return ' ';

        boolean isWhite = (b.white & mask) != 0;

        char p;
        if ((b.kings & mask) != 0) p = 'k';
        else if ((b.queens & mask) != 0) p = 'q';
        else if ((b.rooks & mask) != 0) p = 'r';
        else if ((b.bishops & mask) != 0) p = 'b';
        else if ((b.knights & mask) != 0) p = 'n';
        else if ((b.pawns & mask) != 0) p = 'p';
        else p = '?';

        return isWhite ? Character.toUpperCase(p) : p;
    }

    public static void PrintBitboard(long bitboard) {
        System.out.println("\n   +---+---+---+---+---+---+---+---+");
        for (int rank = 7; rank >= 0; rank--) {
            System.out.print(" " + (rank + 1) + " |");
            for (int file = 0; file < 8; file++) {
                int sq = rank * 8 + file;
                long mask = 1L << sq;
                char v = ((bitboard & mask) != 0) ? '1' : ' ';
                System.out.print(" " + v + " |");
            }
            System.out.println("\n   +---+---+---+---+---+---+---+---+");
        }
        System.out.println("     a   b   c   d   e   f   g   h\n");
    }
}
