package com.hazardchess.pieces;

import com.hazardchess.game.Board;

public class Queen extends Piece {
    public Queen(boolean isWhite) {
        super("Queen", isWhite);
    }

    @Override
    public boolean canMove(int sr, int sc, int er, int ec, Board board) {
        int dr = er - sr;
        int dc = ec - sc;

        if (dr == 0 || dc == 0) {
            // đi như Rook
            int r = sr + Integer.signum(dr);
            int c = sc + Integer.signum(dc);
            while (r != er || c != ec) {
                if (board.getCell(r, c).getPiece() != null) return false;
                r += Integer.signum(dr);
                c += Integer.signum(dc);
            }
        } else if (Math.abs(dr) == Math.abs(dc)) {
            // đi như Bishop
            int r = sr + Integer.signum(dr);
            int c = sc + Integer.signum(dc);
            while (r != er || c != ec) {
                if (board.getCell(r, c).getPiece() != null) return false;
                r += Integer.signum(dr);
                c += Integer.signum(dc);
            }
        } else {
            return false;
        }

        return board.getCell(er, ec).getPiece() == null ||
               board.getCell(er, ec).getPiece().isWhite() != isWhite();
    }

    @Override
    public String toString() {
        return isWhite() ? "Q" : "q";
    }
}