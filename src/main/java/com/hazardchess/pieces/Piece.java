package com.hazardchess.pieces;

import com.hazardchess.game.Board;

public abstract class Piece {
    private final boolean isWhite;
    private final String name;

    public Piece(String name, boolean isWhite) {
        this.name = name;
        this.isWhite = isWhite;
    }

    public boolean isWhite() {
        return isWhite;
    }

    public String getName() {
        return name;
    }

    public abstract boolean canMove(int sr, int sc, int er, int ec, Board board);

    // ✅ Thêm vào đây
    public void onMove() {
        // mặc định: không làm gì
    }

    @Override
    public String toString() {
        return name;
    }
}