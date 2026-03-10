import java.util.Arrays;

public class MoveGenerator {

    /**
     * Packed move:
     * bits 0–5:  from
     * bits 6–11: to
     * bits 12–14: promo (0 none, 1 q, 2 r, 3 b, 4 n)
     */

    public static int[] GenerateLegal(Board b, int sq) {
        int[] pseudo = generatePseudoLegal(b, sq);
        if (pseudo.length == 0) return pseudo;

        boolean moverWhite = b.whiteTurn;
        boolean oppWhite = !moverWhite;

        int[] out = new int[pseudo.length];
        int n = 0;

        for (int pm : pseudo) {
            int from = unpackFrom(pm);
            int to   = unpackTo(pm);

            if (isKingTwoSquareMoveOnBoard(b, from, to)) {
                int pass = (to > from) ? (from + 1) : (from - 1);
                if (isSquareAttacked(b, from, oppWhite)) continue;
                if (isSquareAttacked(b, pass, oppWhite)) continue;
                if (isSquareAttacked(b, to,   oppWhite)) continue;
            }

            try {
                Move m = new Move(b, packedToUci(pm));
                Board b2 = b.Move(m);

                int kingSq = kingSquare(b2, moverWhite);
                if (kingSq < 0) continue; // should never happen
                if (isSquareAttacked(b2, kingSq, oppWhite)) continue;

                out[n++] = pm;
            } catch (IllegalArgumentException ex) {
                // If this ever triggers, pseudo gen created an inconsistent move -- to stop Perft crashes
            }
        }

        return Arrays.copyOf(out, n);
    }

    public static int[] GenerateAllLegal(Board b) {
        int[] buf = new int[256];
        int n = 0;

        for (int sq = 0; sq < 64; sq++) {
            int[] ms = GenerateLegal(b, sq);
            if (ms.length == 0) continue;

            if (n + ms.length > buf.length) {
                buf = Arrays.copyOf(buf, Math.max(buf.length * 2, n + ms.length));
            }
            System.arraycopy(ms, 0, buf, n, ms.length);
            n += ms.length;
        }

        return Arrays.copyOf(buf, n);
    }

    public static String packedToUci(int packed) {
        int from = unpackFrom(packed);
        int to = unpackTo(packed);
        int promo = unpackPromo(packed);

        StringBuilder sb = new StringBuilder(5);
        sb.append((char)('a' + (from & 7)));
        sb.append((char)('1' + (from >>> 3)));
        sb.append((char)('a' + (to & 7)));
        sb.append((char)('1' + (to >>> 3)));

        if (promo != 0) sb.append(promoChar(promo));
        return sb.toString();
    }

    private static int[] generatePseudoLegal(Board b, int sq) {
        long ownOcc = b.whiteTurn ? b.white : b.black;
        long oppOcc = b.whiteTurn ? b.black : b.white;
        long occ    = ownOcc | oppOcc;

        long sqMask = 1L << sq;
        if ((ownOcc & sqMask) == 0L) return new int[0];

        char pt = pieceTypeAt(b, sq);
        if (pt == '\0') return new int[0];

        int[] buf = new int[48];
        int n = 0;

        switch (pt) {
            case 'p' -> n = genPawnMoves(b, sq, oppOcc, occ, buf, n);

            case 'n' -> {
                long targets = Attacks.KNIGHT[sq] & ~ownOcc;
                while (targets != 0) {
                    int to = Long.numberOfTrailingZeros(targets);
                    targets &= targets - 1;
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(sq, to, 0);
                }
            }

            case 'b' -> {
                for (int i = 0; i < 4; i++) {
                    n = walkRay(sq, Attacks.BISHOP_DF[i], Attacks.BISHOP_DR[i], ownOcc, oppOcc, buf, n);
                }
            }

            case 'r' -> {
                for (int i = 0; i < 4; i++) {
                    n = walkRay(sq, Attacks.ROOK_DF[i], Attacks.ROOK_DR[i], ownOcc, oppOcc, buf, n);
                }
            }

            case 'q' -> {
                for (int i = 0; i < 4; i++) {
                    n = walkRay(sq, Attacks.BISHOP_DF[i], Attacks.BISHOP_DR[i], ownOcc, oppOcc, buf, n);
                }
                for (int i = 0; i < 4; i++) {
                    n = walkRay(sq, Attacks.ROOK_DF[i], Attacks.ROOK_DR[i], ownOcc, oppOcc, buf, n);
                }
            }

            case 'k' -> {
                long targets = Attacks.KING[sq] & ~ownOcc;
                while (targets != 0) {
                    int to = Long.numberOfTrailingZeros(targets);
                    targets &= targets - 1;
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(sq, to, 0);
                }
                n = genCastlingPseudo(b, sq, ownOcc, occ, buf, n);
            }
        }

        return Arrays.copyOf(buf, n);
    }

    private static int genPawnMoves(Board b, int from, long oppOcc, long occ, int[] buf, int n) {
        boolean white = b.whiteTurn;
        int file = from & 7;
        int rank = from >>> 3;

        int epSq = (b.enPassantMask == 0L) ? -1 : Long.numberOfTrailingZeros(b.enPassantMask);

        if (white) {
            // push 1
            int to1 = from + 8;
            if (to1 < 64 && (occ & (1L << to1)) == 0L) {
                if (rank == 6) {
                    n = emitPromotions(from, to1, buf, n);
                } else {
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(from, to1, 0);

                    // push 2 from rank 2
                    if (rank == 1) {
                        int to2 = from + 16;
                        if ((occ & (1L << to2)) == 0L) {
                            buf = ensureCapacity(buf, n + 1);
                            buf[n++] = pack(from, to2, 0);
                        }
                    }
                }
            }

            // captures / EP
            if (file > 0) {
                int cap = from + 7;
                if (cap < 64) {
                    long m = 1L << cap;
                    if ((oppOcc & m) != 0L) {
                        if (rank == 6) n = emitPromotions(from, cap, buf, n);
                        else { buf = ensureCapacity(buf, n + 1); buf[n++] = pack(from, cap, 0); }
                    } else if (epSq == cap) {
                        buf = ensureCapacity(buf, n + 1);
                        buf[n++] = pack(from, cap, 0);
                    }
                }
            }
            if (file < 7) {
                int cap = from + 9;
                if (cap < 64) {
                    long m = 1L << cap;
                    if ((oppOcc & m) != 0L) {
                        if (rank == 6) n = emitPromotions(from, cap, buf, n);
                        else { buf = ensureCapacity(buf, n + 1); buf[n++] = pack(from, cap, 0); }
                    } else if (epSq == cap) {
                        buf = ensureCapacity(buf, n + 1);
                        buf[n++] = pack(from, cap, 0);
                    }
                }
            }
        } else {
            // push 1
            int to1 = from - 8;
            if (to1 >= 0 && (occ & (1L << to1)) == 0L) {
                if (rank == 1) {
                    n = emitPromotions(from, to1, buf, n);
                } else {
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(from, to1, 0);

                    // push 2 from rank 7
                    if (rank == 6) {
                        int to2 = from - 16;
                        if ((occ & (1L << to2)) == 0L) {
                            buf = ensureCapacity(buf, n + 1);
                            buf[n++] = pack(from, to2, 0);
                        }
                    }
                }
            }

            // captures / EP
            if (file > 0) {
                int cap = from - 9;
                if (cap >= 0) {
                    long m = 1L << cap;
                    if ((oppOcc & m) != 0L) {
                        if (rank == 1) n = emitPromotions(from, cap, buf, n);
                        else { buf = ensureCapacity(buf, n + 1); buf[n++] = pack(from, cap, 0); }
                    } else if (epSq == cap) {
                        buf = ensureCapacity(buf, n + 1);
                        buf[n++] = pack(from, cap, 0);
                    }
                }
            }
            if (file < 7) {
                int cap = from - 7;
                if (cap >= 0) {
                    long m = 1L << cap;
                    if ((oppOcc & m) != 0L) {
                        if (rank == 1) n = emitPromotions(from, cap, buf, n);
                        else { buf = ensureCapacity(buf, n + 1); buf[n++] = pack(from, cap, 0); }
                    } else if (epSq == cap) {
                        buf = ensureCapacity(buf, n + 1);
                        buf[n++] = pack(from, cap, 0);
                    }
                }
            }
        }

        return n;
    }

    private static int emitPromotions(int from, int to, int[] buf, int n) {
        buf = ensureCapacity(buf, n + 4);
        buf[n++] = pack(from, to, 1); // q
        buf[n++] = pack(from, to, 2); // r
        buf[n++] = pack(from, to, 3); // b
        buf[n++] = pack(from, to, 4); // n
        return n;
    }

    private static int genCastlingPseudo(Board b, int kingSq, long ownOcc, long occ, int[] buf, int n) {
        if (b.whiteTurn) {
            if (kingSq != 4) return n; // e1
            if (b.whiteKingside) {
                long empty = (1L << 5) | (1L << 6);
                long rook  = 1L << 7;
                if ((occ & empty) == 0L && (b.rooks & ownOcc & rook) != 0L) {
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(4, 6, 0);
                }
            }
            if (b.whiteQueenside) {
                long empty = (1L << 3) | (1L << 2) | (1L << 1);
                long rook  = 1L;
                if ((occ & empty) == 0L && (b.rooks & ownOcc & rook) != 0L) {
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(4, 2, 0);
                }
            }
        } else {
            if (kingSq != 60) return n; // e8
            if (b.blackKingside) {
                long empty = (1L << 61) | (1L << 62);
                long rook  = 1L << 63;
                if ((occ & empty) == 0L && (b.rooks & ownOcc & rook) != 0L) {
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(60, 62, 0);
                }
            }
            if (b.blackQueenside) {
                long empty = (1L << 59) | (1L << 58) | (1L << 57);
                long rook  = 1L << 56;
                if ((occ & empty) == 0L && (b.rooks & ownOcc & rook) != 0L) {
                    buf = ensureCapacity(buf, n + 1);
                    buf[n++] = pack(60, 58, 0);
                }
            }
        }
        return n;
    }

    static int walkRay(int fromSq, int df, int dr, long ownOcc, long oppOcc, int[] buf, int size) {
        int f = fromSq & 7;
        int r = fromSq >>> 3;

        for (f += df, r += dr; f >= 0 && f < 8 && r >= 0 && r < 8; f += df, r += dr) {
            int toSq = (r << 3) | f;
            long toMask = 1L << toSq;

            if ((ownOcc & toMask) != 0L) break;

            buf = ensureCapacity(buf, size + 1);
            buf[size++] = pack(fromSq, toSq, 0);

            if ((oppOcc & toMask) != 0L) break;
        }
        return size;
    }

    private static boolean isSquareAttacked(Board b, int sq, boolean byWhite) {
        long byOcc = byWhite ? b.white : b.black;
        long occ = b.white | b.black;

        // knights
        if ((b.knights & byOcc & Attacks.KNIGHT[sq]) != 0L) return true;

        // kings
        if ((b.kings & byOcc & Attacks.KING[sq]) != 0L) return true;

        // pawns (source-square method)
        int file = sq & 7;
        if (byWhite) {
            if (file < 7) { int src = sq - 7; if (src >= 0 && (b.pawns & byOcc & (1L << src)) != 0L) return true; }
            if (file > 0) { int src = sq - 9; if (src >= 0 && (b.pawns & byOcc & (1L << src)) != 0L) return true; }
        } else {
            if (file > 0) { int src = sq + 7; if (src < 64 && (b.pawns & byOcc & (1L << src)) != 0L) return true; }
            if (file < 7) { int src = sq + 9; if (src < 64 && (b.pawns & byOcc & (1L << src)) != 0L) return true; }
        }

        // sliding: rook-like
        for (int i = 0; i < 4; i++) {
            if (rayAttackedBy(b, sq, Attacks.ROOK_DF[i], Attacks.ROOK_DR[i], occ, byOcc, true)) return true;
        }
        // sliding: bishop-like
        for (int i = 0; i < 4; i++) {
            if (rayAttackedBy(b, sq, Attacks.BISHOP_DF[i], Attacks.BISHOP_DR[i], occ, byOcc, false)) return true;
        }

        return false;
    }

    private static boolean rayAttackedBy(Board b, int fromSq, int df, int dr, long occ, long byOcc, boolean rookLike) {
        int f = fromSq & 7;
        int r = fromSq >>> 3;

        for (f += df, r += dr; f >= 0 && f < 8 && r >= 0 && r < 8; f += df, r += dr) {
            int sq = (r << 3) | f;
            long m = 1L << sq;

            if ((occ & m) == 0L) continue; // empty, keep going
            if ((byOcc & m) == 0L) return false; // blocked by non-attacker

            if (rookLike) return ((b.rooks | b.queens) & m) != 0L;
            else          return ((b.bishops | b.queens) & m) != 0L;
        }
        return false;
    }

    private static int kingSquare(Board b, boolean white) {
        long k = b.kings & (white ? b.white : b.black);
        return (k == 0L) ? -1 : Long.numberOfTrailingZeros(k);
    }

    private static boolean isKingTwoSquareMoveOnBoard(Board b, int from, int to) {
        if (pieceTypeAt(b, from) != 'k') return false;
        return Math.abs((to & 7) - (from & 7)) == 2;
    }

    private static int pack(int from, int to, int promoCode) {
        return (from & 63) | ((to & 63) << 6) | ((promoCode & 7) << 12);
    }
    private static int unpackFrom(int packed) { return packed & 63; }
    private static int unpackTo(int packed) { return (packed >>> 6) & 63; }
    private static int unpackPromo(int packed) { return (packed >>> 12) & 7; }

    private static char promoChar(int code) {
        return switch (code) {
            case 1 -> 'q';
            case 2 -> 'r';
            case 3 -> 'b';
            case 4 -> 'n';
            default -> '\0';
        };
    }

    private static char pieceTypeAt(Board b, int sq) {
        long m = 1L << sq;
        if ((b.pawns & m) != 0L)   return 'p';
        if ((b.knights & m) != 0L) return 'n';
        if ((b.bishops & m) != 0L) return 'b';
        if ((b.rooks & m) != 0L)   return 'r';
        if ((b.queens & m) != 0L)  return 'q';
        if ((b.kings & m) != 0L)   return 'k';
        return '\0';
    }

    private static int[] ensureCapacity(int[] a, int needed) {
        if (needed <= a.length) return a;
        return Arrays.copyOf(a, Math.max(needed, a.length * 2));
    }

    public static String PackedToUci(int packed) {
        int from = packed & 63;
        int to = (packed >>> 6) & 63;
        int promo = (packed >>> 12) & 7;

        StringBuilder sb = new StringBuilder(5);
        sb.append((char)('a' + (from & 7)));
        sb.append((char)('1' + (from >>> 3)));
        sb.append((char)('a' + (to & 7)));
        sb.append((char)('1' + (to >>> 3)));

        if (promo != 0) {
            char p = switch (promo) {
                case 1 -> 'q';
                case 2 -> 'r';
                case 3 -> 'b';
                case 4 -> 'n';
                default -> '\0';
            };
            if (p != '\0') sb.append(p);
        }
        return sb.toString();
    }
}