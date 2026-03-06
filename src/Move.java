public final class Move {
    // From/to squares (0-63)
    public int from;
    public int to;

    // Mover info (piece type always lowercase: 'p','n','b','r','q','k')
    public char movedType;
    public boolean movedWhite;

    // Capture info (if any)
    public char capturedType; // '\0' if none
    public int capturedSquare; // -1 if none

    // Promotion (if any)
    public char promotionType;  // '\0' if none

    // Castling rook move (if any)
    public int rookFromSquare; // -1 if none
    public int rookToSquare; // -1 if none

    // Previous state
    public byte prevCastlingRights; // 4 bits: KQkq
    public int prevEnPassantSquare; // -1 if none
    public int prevHalfMoves;
    public int prevFullMoves;

    // Flags
    public boolean isCastling;
    public boolean isDoublePush;

    // Format: <from><to>[<promotion>]
    public Move(Board b, String s) {
        if (b == null) throw new IllegalArgumentException("Board is null");
        if (s == null) throw new IllegalArgumentException("Move string is null");

        s = s.trim();
        if (s.length() != 4 && s.length() != 5)
            throw new IllegalArgumentException("Bad move format (expected 4 or 5 chars): " + s);

        // Parse squares
        this.from = squareToIndex(s.charAt(0), s.charAt(1));
        this.to   = squareToIndex(s.charAt(2), s.charAt(3));

        // Sentinels/defaults
        this.capturedType = '\0';
        this.capturedSquare = -1;
        this.promotionType = '\0';
        this.rookFromSquare = -1;
        this.rookToSquare = -1;
        this.isCastling = false;
        this.isDoublePush = false;

        // Promotion (if present)
        if (s.length() == 5) {
            this.promotionType = normalizePromotion(s.charAt(4)); // 'q','r','b','n'
        }

        this.movedWhite = b.whiteTurn;

        long fromMask = 1L << this.from;
        long toMask   = 1L << this.to;

        long moverOcc = this.movedWhite ? b.white : b.black;
        if ((moverOcc & fromMask) == 0)
            throw new IllegalArgumentException("No mover piece on from-square: " + s);

        this.movedType = pieceTypeAt(b, fromMask);
        if (this.movedType == '\0')
            throw new IllegalStateException("Inconsistent board: occupancy set but no piece type at from-square");

        // Determine EP target square (if any)
        int epTargetSq = (b.enPassantMask == 0L) ? -1 : Long.numberOfTrailingZeros(b.enPassantMask);

        // Detect castling
        if (this.movedType == 'k') {
            int fromFile = this.from & 7;
            int toFile   = this.to & 7;
            int fileDiff = Math.abs(toFile - fromFile);

            if (fileDiff == 2) {
                this.isCastling = true;

                if (this.movedWhite) {
                    if (this.to == 6) {
                        this.rookFromSquare = 7;
                        this.rookToSquare   = 5;
                    } else if (this.to == 2) {
                        this.rookFromSquare = 0;
                        this.rookToSquare   = 3;
                    }
                } else {
                    if (this.to == 62) {
                        this.rookFromSquare = 63;
                        this.rookToSquare   = 61;
                    } else if (this.to == 58) {
                        this.rookFromSquare = 56;
                        this.rookToSquare   = 59;
                    }
                }
            }
        }

        // Detect captures (normal or en passant)
        long oppOcc = this.movedWhite ? b.black : b.white;

        boolean toOccupiedByOpponent = (oppOcc & toMask) != 0;

        // En passant capture: pawn moves to EP target square, and target square is empty of opponent occupancy
        if (this.movedType == 'p' && epTargetSq != -1 && this.to == epTargetSq && !toOccupiedByOpponent) {
            int fromFile = this.from & 7;
            int toFile   = this.to & 7;
            // EP is only possible on a diagonal pawn move
            if (Math.abs(toFile - fromFile) == 1) {
                this.capturedType = 'p';
                this.capturedSquare = this.movedWhite ? (this.to - 8) : (this.to + 8);

                long capMask = 1L << this.capturedSquare;
                if ((oppOcc & capMask) == 0 || (b.pawns & capMask) == 0) {
                    throw new IllegalArgumentException("EP capture inferred but no opponent pawn to capture: " + s);
                }
            }
        } else if (toOccupiedByOpponent) {
            // Normal capture
            this.capturedSquare = this.to;
            this.capturedType = pieceTypeAt(b, toMask);
            if (this.capturedType == '\0')
                throw new IllegalStateException("Inconsistent board: opponent occupancy set but no piece type at to-square");
        } else {
            if (((moverOcc & toMask) != 0)) {
                throw new IllegalArgumentException("Destination occupied by own piece: " + s);
            }
        }

        // Detect double pawn push
        if (this.movedType == 'p') {
            int diff = this.to - this.from;
            if (this.movedWhite && diff == 16) this.isDoublePush = true;
            if (!this.movedWhite && diff == -16) this.isDoublePush = true;
        }

        // Validate promotion usage
        if (this.promotionType != '\0') {
            if (this.movedType != 'p')
                throw new IllegalArgumentException("Promotion specified but moved piece is not a pawn: " + s);

            int toRank = this.to >>> 3;
            if (this.movedWhite && toRank != 7)
                throw new IllegalArgumentException("White promotion must land on rank 8: " + s);
            if (!this.movedWhite && toRank != 0)
                throw new IllegalArgumentException("Black promotion must land on rank 1: " + s);
        }
    }

    private static int squareToIndex(char file, char rank) {
        file = Character.toLowerCase(file);
        if (file < 'a' || file > 'h' || rank < '1' || rank > '8')
            throw new IllegalArgumentException("Bad square: " + file + " " + rank);
        return (file - 'a') + (rank - '1') * 8;
    }

    private static char normalizePromotion(char p) {
        p = Character.toLowerCase(p);
        if (p != 'q' && p != 'r' && p != 'b' && p != 'n')
            throw new IllegalArgumentException("Bad promotion piece (use q,r,b,n): " + p);
        return p;
    }

    private static char pieceTypeAt(Board b, long mask) {
        if ((b.kings & mask) != 0)   return 'k';
        if ((b.queens & mask) != 0)  return 'q';
        if ((b.rooks & mask) != 0)   return 'r';
        if ((b.bishops & mask) != 0) return 'b';
        if ((b.knights & mask) != 0) return 'n';
        if ((b.pawns & mask) != 0)   return 'p';
        return '\0';
    }

    @Override
    public String toString() {
        return Board.SquareToString(this.from) + Board.SquareToString(this.to) + (promotionType == '\0' ? "" : promotionType);
    }
}