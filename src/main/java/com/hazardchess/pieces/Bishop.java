package com.hazardchess.pieces;

import com.hazardchess.game.Board;

public class Bishop extends Piece {
    public Bishop(boolean isWhite) {
        super("Bishop", isWhite);
    }

    @Override
    public boolean canMove(int sr, int sc, int er, int ec, Board board) {
        int dr = er - sr;
        int dc = ec - sc;

        if (Math.abs(dr) != Math.abs(dc)) return false;

        int r = sr + Integer.signum(dr);
        int c = sc + Integer.signum(dc);
        while (r != er || c != ec) {
            if (board.getCell(r, c).getPiece() != null) return false;
            r += Integer.signum(dr);
            c += Integer.signum(dc);
        }

        return board.getCell(er, ec).getPiece() == null ||
               board.getCell(er, ec).getPiece().isWhite() != isWhite();
    }

    @Override
    public String toString() {
        return isWhite() ? "B" : "b";
    }
}