package com.hazardchess.pieces;

import com.hazardchess.game.Board;

public class Knight extends Piece {
    public Knight(boolean isWhite) {
        super("Knight", isWhite);
    }

    @Override
    public boolean canMove(int sr, int sc, int er, int ec, Board board) {
        int dr = Math.abs(sr - er);
        int dc = Math.abs(sc - ec);

        if ((dr == 2 && dc == 1) || (dr == 1 && dc == 2)) {
            return board.getCell(er, ec).getPiece() == null ||
                   board.getCell(er, ec).getPiece().isWhite() != isWhite();
        }
        return false;
    }

    @Override
    public String toString() {
        return isWhite() ? "N" : "n";
    }
}