package com.hazardchess.pieces;

import com.hazardchess.game.Board;

public class Rook extends Piece {
    public Rook(boolean isWhite) {
        super("Rook", isWhite);
    }

    @Override
    public boolean canMove(int sr, int sc, int er, int ec, Board board) {
        if (sr != er && sc != ec) return false;

        int dr = Integer.compare(er, sr);
        int dc = Integer.compare(ec, sc);

        int r = sr + dr, c = sc + dc;
        while (r != er || c != ec) {
            if (board.getCell(r, c).getPiece() != null) return false;
            r += dr; c += dc;
        }

        return board.getCell(er, ec).getPiece() == null ||
               board.getCell(er, ec).getPiece().isWhite() != isWhite();
    }

    @Override
    public String toString() {
        return isWhite() ? "R" : "r";
    }
}