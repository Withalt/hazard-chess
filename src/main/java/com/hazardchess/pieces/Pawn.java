package com.hazardchess.pieces;

import com.hazardchess.game.Board;
import com.hazardchess.game.Cell;

public class Pawn extends Piece {
    private boolean firstMove = true;

    public Pawn(boolean isWhite) {
        super("Pawn", isWhite);
    }

    @Override
    public boolean canMove(int sr, int sc, int er, int ec, Board board) {
        int dir = isWhite() ? -1 : 1;
        int startRow = isWhite() ? 6 : 1;

        // đi thẳng 1 ô
        if (sc == ec && er == sr + dir && board.getCell(er, ec).getPiece() == null) {
            return true;
        }

        // đi thẳng 2 ô nếu là nước đầu
        if (firstMove && sc == ec && sr == startRow && er == sr + 2 * dir) {
            if (board.getCell(sr + dir, sc).getPiece() == null &&
                board.getCell(er, ec).getPiece() == null) {
                return true;
            }
        }

        // ăn chéo
        if (Math.abs(ec - sc) == 1 && er == sr + dir) {
            Cell target = board.getCell(er, ec);
            if (target.getPiece() != null && target.getPiece().isWhite() != isWhite()) {
                return true;
            }
            // en passant
            Cell sideCell = board.getCell(sr, ec);
            if (sideCell.getPiece() instanceof Pawn) {
                Pawn enemyPawn = (Pawn) sideCell.getPiece();
                if (enemyPawn.isWhite() != this.isWhite() && enemyPawn.firstMove == false) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onMove() {
        firstMove = false;
    }

    @Override
    public String toString() {
        return isWhite() ? "P" : "p";
    }
}