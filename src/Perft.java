public final class Perft {

    ///  [Perft Testing Wiki](https://www.chessprogramming.org/Perft_Results)
    private static final String startFen = Board.startFEN;
    private static final String kiwiPete = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String eps = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String castling = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
    private static final String bugCatcher = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";
    private static final String heavyMid = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10";

    public static long perft(Board b, int depth) {
        if (depth == 0) return 1L;

        long nodes = 0L;
        int[] moves = MoveGenerator.GenerateAllLegal(b);

        for (int pm : moves) {
            Move m = new Move(b, MoveGenerator.PackedToUci(pm));
            Board b2 = b.Move(m);
            nodes += perft(b2, depth - 1);
        }
        return nodes;
    }

    public static void main(String[] args) {
        // manual params
        Board b = new Board(startFen);
        int depth = 6;

        int maxDepth = (args.length > 0) ? Integer.parseInt(args[0]) : depth;

        for (int d = 1; d <= maxDepth; d++) {
            long t0 = System.currentTimeMillis();
            long nodes = perft(b, d);
            long t1 = System.currentTimeMillis();
            System.out.println("depth " + d + ": " + nodes + " nodes (" + (t1 - t0) + " ms)");
        }
    }

    public static void perftDivide(Board b, int depth) {
        int[] moves = MoveGenerator.GenerateAllLegal(b);
        long total = 0;

        for (int pm : moves) {
            String uci = MoveGenerator.packedToUci(pm);
            Move m = new Move(b, uci);
            Board b2 = b.Move(m);

            long nodes = Perft.perft(b2, depth - 1);
            total += nodes;
            System.out.println(uci + ": " + nodes);
        }

        System.out.println("Total: " + total);
    }

    private Perft() {}
}