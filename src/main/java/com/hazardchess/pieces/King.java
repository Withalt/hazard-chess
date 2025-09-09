package com.hazardchess.pieces;

import com.hazardchess.game.Board;

public class King extends Piece {
    public King(boolean isWhite) {
        super("King", isWhite);
    }

    @Override
    public boolean canMove(int sr, int sc, int er, int ec, Board board) {
        int dr = Math.abs(sr - er);
        int dc = Math.abs(sc - ec);

        if (dr <= 1 && dc <= 1) {
            return board.getCell(er, ec).getPiece() == null ||
                   board.getCell(er, ec).getPiece().isWhite() != isWhite();
        }
        return false;
    }

    @Override
    public String toString() {
        return isWhite() ? "K" : "k";
    }
}