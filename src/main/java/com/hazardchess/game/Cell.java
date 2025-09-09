package com.hazardchess.game;

import com.hazardchess.pieces.Piece;

public class Cell {
    private int row, col;
    private Piece piece;
    private boolean revealed = false;
    private boolean exploded = false;
    private boolean hazard = false;
    private boolean flagged = false;
    private int adjacentHazardCount = 0;

    // NEW: đánh dấu đã chạy animation nổ chưa (để không phát lại)
    private boolean explosionAnimated = false;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() { return row; }
    public int getCol() { return col; }

    public Piece getPiece() { return piece; }
    public void setPiece(Piece piece) { this.piece = piece; }

    public boolean isRevealed() { return revealed; }
    public void setRevealed(boolean revealed) { this.revealed = revealed; }

    public boolean isExploded() { return exploded; }
    public void setExploded(boolean exploded) { this.exploded = exploded; }

    public boolean hasHazard() { return hazard; }
    public void setHazard(boolean hazard) { this.hazard = hazard; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    public int getAdjacentHazardCount() { return adjacentHazardCount; }
    public void setAdjacentHazardCount(int count) { this.adjacentHazardCount = count; }

    // Helper: số chỉ hiển thị khi ô đã reveal và không phải ô đã exploded
    public boolean canShowNumber() {
        return revealed && !exploded && adjacentHazardCount > 0;
    }

    // NEW: explosionAnimated getter/setter
    public boolean isExplosionAnimated() { return explosionAnimated; }
    public void setExplosionAnimated(boolean v) { this.explosionAnimated = v; }
}