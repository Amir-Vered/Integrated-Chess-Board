public final class Attacks {
    public static final long[] KNIGHT = new long[64];
    public static final long[] KING   = new long[64];

    public static final int[] ROOK_DF = {  1, -1,  0,  0 };
    public static final int[] ROOK_DR = {  0,  0,  1, -1 };

    public static final int[] BISHOP_DF = {  1,  1, -1, -1 };
    public static final int[] BISHOP_DR = {  1, -1,  1, -1 };

    static {
        initKnight();
        initKing();
    }

    private static void initKnight() {
        int[] df = { 1, 2, 2, 1, -1, -2, -2, -1 };
        int[] dr = { 2, 1, -1, -2, -2, -1, 1, 2 };

        for (int sq = 0; sq < 64; sq++) {
            int f = sq & 7;
            int r = sq >>> 3;

            long mask = 0L;
            for (int i = 0; i < 8; i++) {
                int f2 = f + df[i];
                int r2 = r + dr[i];
                if (f2 >= 0 && f2 < 8 && r2 >= 0 && r2 < 8) {
                    int to = (r2 * 8) + f2;
                    mask |= 1L << to;
                }
            }
            KNIGHT[sq] = mask;
        }
    }

    private static void initKing() {
        int[] df = { -1, 0, 1, -1, 1, -1, 0, 1 };
        int[] dr = { -1,-1,-1,  0, 0,  1, 1, 1 };

        for (int sq = 0; sq < 64; sq++) {
            int f = sq & 7;
            int r = sq >>> 3;

            long mask = 0L;
            for (int i = 0; i < 8; i++) {
                int f2 = f + df[i];
                int r2 = r + dr[i];
                if (f2 >= 0 && f2 < 8 && r2 >= 0 && r2 < 8) {
                    int to = (r2 * 8) + f2;
                    mask |= 1L << to;
                }
            }
            KING[sq] = mask;
        }
    }

    private Attacks() {} // prevent instantiation
}