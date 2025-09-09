package com.hazardchess.game;

import com.hazardchess.pieces.*;
import java.util.*;

/**
 * Board (merged)
 * - giữ Random chia sẻ (rand)
 * - bổ sung undo/clearHistory/isGameOver/getWhiteWinner
 * - giữ checkQuickReveal(Cell, Piece, boolean), chooseBestAIMove(), findQuickRevealCandidate()
 */
public class Board {
    private int width = 8;
    private int height;
    private Cell[][] cells;
    private boolean whiteTurn = true;

    private final Random rand = new Random();

    private boolean gameOver = false;
    private Boolean whiteWinner = null;

    // history / undo
    private final Deque<BoardSnapshot> history = new ArrayDeque<>();
    private final int MAX_HISTORY = 200;

    public Board(int height, int hazardLevel) {
        this.height = height;
        cells = new Cell[height][width];
        initCells();
        placeHazards();
        setupPieces();
        updateHazardCounts();
    }

    private void initCells() {
        for (int r = 0; r < height; r++)
            for (int c = 0; c < width; c++)
                cells[r][c] = new Cell(r, c);
    }

    private void setupPieces() {
        // White bottom
        cells[height-1][0].setPiece(new Rook(true));
        cells[height-1][1].setPiece(new Knight(true));
        cells[height-1][2].setPiece(new Bishop(true));
        cells[height-1][3].setPiece(new Queen(true));
        cells[height-1][4].setPiece(new King(true));
        cells[height-1][5].setPiece(new Bishop(true));
        cells[height-1][6].setPiece(new Knight(true));
        cells[height-1][7].setPiece(new Rook(true));
        for (int i = 0; i < width; i++) cells[height - 2][i].setPiece(new Pawn(true));

        // Black top
        cells[0][0].setPiece(new Rook(false));
        cells[0][1].setPiece(new Knight(false));
        cells[0][2].setPiece(new Bishop(false));
        cells[0][3].setPiece(new Queen(false));
        cells[0][4].setPiece(new King(false));
        cells[0][5].setPiece(new Bishop(false));
        cells[0][6].setPiece(new Knight(false));
        cells[0][7].setPiece(new Rook(false));
        for (int i = 0; i < width; i++) cells[1][i].setPiece(new Pawn(false));
    }

    private void placeHazards() {
        int totalCells = width * height;
        int bombs = Math.max(5, totalCells / 8);
        int placed = 0;
        // avoid infinite loop if few empty cells: attempt limited tries (but still place bombs until condition)
        int attempts = 0;
        while (placed < bombs && attempts < bombs * 20) {
            int r = rand.nextInt(height);
            int c = rand.nextInt(width);
            Cell cell = cells[r][c];
            if (!cell.hasHazard() && cell.getPiece() == null) {
                cell.setHazard(true);
                placed++;
            }
            attempts++;
        }
    }

    private void updateHazardCounts() {
        for (int r=0; r<height; r++)
            for (int c=0; c<width; c++)
                cells[r][c].setAdjacentHazardCount(countAdjacentHazards(r, c));
    }

    public Cell getCell(int r, int c) {
        if (r < 0 || r >= height || c < 0 || c >= width) return null;
        return cells[r][c];
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < height && c >= 0 && c < width;
    }

    public boolean isGameOver() { return gameOver; }
    public Boolean getWhiteWinner() { return whiteWinner; }

    // ---------------- History / Undo ----------------
    private static class CellState {
        Piece piece;
        boolean revealed;
        boolean exploded;
        boolean hazard;
        boolean flagged;
        int adjacent;
    }
    private static class BoardSnapshot {
        CellState[][] cells;
        boolean whiteTurn;
        boolean gameOver;
        Boolean whiteWinner;
    }

    private void saveSnapshot() {
        BoardSnapshot s = new BoardSnapshot();
        s.cells = new CellState[height][width];
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                Cell src = cells[r][c];
                CellState cs = new CellState();
                cs.piece = src.getPiece();
                cs.revealed = src.isRevealed();
                cs.exploded = src.isExploded();
                cs.hazard = src.hasHazard();
                cs.flagged = src.isFlagged();
                cs.adjacent = src.getAdjacentHazardCount();
                s.cells[r][c] = cs;
            }
        }
        s.whiteTurn = whiteTurn;
        s.gameOver = gameOver;
        s.whiteWinner = whiteWinner;

        history.push(s);
        if (history.size() > MAX_HISTORY) {
            while (history.size() > MAX_HISTORY) history.removeLast();
        }
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        BoardSnapshot s = history.pop();
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                CellState cs = s.cells[r][c];
                Cell dst = cells[r][c];
                dst.setPiece(cs.piece);
                dst.setRevealed(cs.revealed);
                dst.setExploded(cs.exploded);
                dst.setHazard(cs.hazard);
                dst.setFlagged(cs.flagged);
                dst.setAdjacentHazardCount(cs.adjacent);
                // reset animation flag if existent (Cell provides setter)
                try { dst.setExplosionAnimated(false); } catch (Throwable ignored) {}
            }
        }
        this.whiteTurn = s.whiteTurn;
        this.gameOver = s.gameOver;
        this.whiteWinner = s.whiteWinner;
        return true;
    }

    public void clearHistory() {
        history.clear();
    }

    // ---------------- Minesweeper logic ----------------
    public void revealCell(int row, int col) {
        if (!inBounds(row, col)) return;
        Cell cell = cells[row][col];
        if (cell == null || cell.isRevealed() || cell.isFlagged()) return;

        cell.setRevealed(true);

        if (cell.hasHazard()) {
            cell.setExploded(true);
            if (cell.getPiece() != null) {
                if ("King".equals(cell.getPiece().getName())) {
                    gameOver = true;
                    whiteWinner = !cell.getPiece().isWhite();
                }
                cell.setPiece(null);
            }
            return;
        }

        int count = countAdjacentHazards(row, col);
        cell.setAdjacentHazardCount(count);

        if (count == 0) {
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                    if (dr != 0 || dc != 0)
                        revealCell(row + dr, col + dc);
        }
    }

    public int countAdjacentHazards(int row, int col) {
        int cnt = 0;
        for (int dr = -1; dr <= 1; dr++)
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr, nc = col + dc;
                if (inBounds(nr, nc) && cells[nr][nc].hasHazard()) cnt++;
            }
        return cnt;
    }

    /**
     * Quick reveal:
     * - triggerPiece: nếu mở sai và triggerPiece != null -> loại piece đó
     * - consumeTurnIfValid: nếu true và quickReveal mở được thì đổi lượt
     * - return true nếu có hành động (mở ô hoặc nổ)
     */
    public boolean checkQuickReveal(Cell numberCell, Piece triggerPiece, boolean consumeTurnIfValid) {
        if (numberCell == null || !numberCell.isRevealed()) return false;
        // save state for undo
        saveSnapshot();

        int r0 = numberCell.getRow(), c0 = numberCell.getCol();
        int required = numberCell.getAdjacentHazardCount();
        if (required <= 0) {
            history.pop(); // nothing changed
            return false;
        }

        int flagged = 0;
        List<Cell> neighbors = new ArrayList<>();
        for (int dr = -1; dr <= 1; dr++) for (int dc = -1; dc <= 1; dc++) {
            if (dr == 0 && dc == 0) continue;
            int nr = r0 + dr, nc = c0 + dc;
            if (!inBounds(nr, nc)) continue;
            Cell adj = cells[nr][nc];
            neighbors.add(adj);
            if (adj.isFlagged()) flagged++;
        }

        if (flagged != required) {
            // no-op -> discard snapshot
            history.pop();
            return false;
        }

        boolean anyOpened = false;
        boolean explosionOccurred = false;

        for (Cell adj : neighbors) {
            if (!adj.isFlagged() && !adj.isRevealed()) {
                anyOpened = true;
                adj.setRevealed(true);
                if (adj.hasHazard()) {
                    adj.setExploded(true);
                    if (adj.getPiece() != null) {
                        if ("King".equals(adj.getPiece().getName())) {
                            gameOver = true;
                            whiteWinner = !adj.getPiece().isWhite();
                        }
                        adj.setPiece(null);
                    }
                    explosionOccurred = true;
                } else {
                    int cnt = countAdjacentHazards(adj.getRow(), adj.getCol());
                    adj.setAdjacentHazardCount(cnt);
                    if (cnt == 0) revealCell(adj.getRow(), adj.getCol());
                }
            }
        }

        if (explosionOccurred && triggerPiece != null) {
            for (int r = 0; r < height; r++) for (int c = 0; c < width; c++) {
                Cell cc = cells[r][c];
                if (cc.getPiece() == triggerPiece) {
                    if ("King".equals(triggerPiece.getName())) {
                        gameOver = true;
                        whiteWinner = !triggerPiece.isWhite();
                    }
                    cc.setPiece(null);
                }
            }
        }

        if (!anyOpened) {
            // nothing actually opened -> pop snapshot
            history.pop();
            return false;
        }

        if (consumeTurnIfValid) whiteTurn = !whiteTurn;
        return true;
    }

    // ---------------- Chess move logic ----------------
    public boolean movePiece(int sr, int sc, int dr, int dc) {
        if (gameOver) return false;
        if (!inBounds(sr, sc) || !inBounds(dr, dc)) return false;
        Cell from = cells[sr][sc];
        Cell to   = cells[dr][dc];
        if (from.getPiece() == null) return false;

        Piece piece = from.getPiece();
        if (!piece.canMove(sr, sc, dr, dc, this)) return false;

        // save snapshot for undo
        saveSnapshot();

        Piece captured = to.getPiece();
        to.setPiece(piece);
        from.setPiece(null);

        if (captured != null && "King".equals(captured.getName())) {
            gameOver = true;
            whiteWinner = piece.isWhite();
        }

        // stepping into hazard
        if (to.hasHazard() && !to.isExploded()) {
            to.setRevealed(true);
            to.setExploded(true);
            if ("King".equals(piece.getName())) {
                gameOver = true;
                whiteWinner = !piece.isWhite();
            }
            to.setPiece(null);
        } else {
            revealCell(dr, dc);
            if (to.getAdjacentHazardCount() > 0) {
                checkQuickReveal(to, piece, false);
            }
        }

        whiteTurn = !whiteTurn;
        return true;
    }

    // ---------------- AI helpers ----------------
    public int[] findQuickRevealCandidate() {
        for (int r=0;r<height;r++) for (int c=0;c<width;c++) {
            Cell num = cells[r][c];
            if (!num.isRevealed()) continue;
            int number = num.getAdjacentHazardCount();
            if (number <= 0) continue;
            int flagged = 0;
            boolean hasHidden = false;
            for (int dr=-1; dr<=1; dr++) for (int dc=-1; dc<=1; dc++) {
                if (dr==0 && dc==0) continue;
                int nr=r+dr, nc=c+dc;
                if (!inBounds(nr,nc)) continue;
                Cell adj = cells[nr][nc];
                if (adj.isFlagged()) flagged++;
                if (!adj.isRevealed() && !adj.isFlagged()) hasHidden = true;
            }
            if (flagged == number && hasHidden) return new int[]{r,c};
        }
        return null;
    }

    public int[] chooseBestAIMove() {
        List<MoveCandidate> candidates = new ArrayList<>();

        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                Cell from = cells[r][c];
                if (from.getPiece() == null || from.getPiece().isWhite()) continue;
                Piece p = from.getPiece();

                for (int tr = 0; tr < height; tr++) {
                    for (int tc = 0; tc < width; tc++) {
                        if (!inBounds(tr, tc)) continue;
                        if (!p.canMove(r, c, tr, tc, this)) continue;

                        Cell to = cells[tr][tc];
                        double score = 0.0;

                        if (to.getPiece() != null && to.getPiece().isWhite()) {
                            score += 200 + pieceValue(to.getPiece()) * 40;
                        }

                        if (to.isRevealed()) {
                            if (to.isExploded()) score -= 500;
                            else {
                                int adj = to.getAdjacentHazardCount();
                                if (adj == 0) score += 30;
                                else score += Math.max(0, 8 - adj);
                            }
                        } else {
                            double risk = estimateRiskForUnrevealed(to);
                            score -= risk * 80;
                            score += 2;
                        }

                        int centerDist = Math.abs(tr - height/2) + Math.abs(tc - width/2);
                        score += (14 - centerDist) * 0.5;

                        if (p.getName().equals("Pawn")) {
                            score += (height - 1 - tr) * 0.3;
                        }

                        score += rand.nextDouble() * 0.5;
                        candidates.add(new MoveCandidate(r,c,tr,tc,score));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort((a,b) -> Double.compare(b.score, a.score));
        MoveCandidate best = candidates.get(0);
        return new int[]{best.fromR,best.fromC,best.toR,best.toC};
    }

    private double estimateRiskForUnrevealed(Cell cell) {
        int r = cell.getRow(), c = cell.getCol();
        int knownNeighbors = 0;
        int sumNumbers = 0;
        int flagged = 0;
        for (int dr=-1; dr<=1; dr++) for (int dc=-1; dc<=1; dc++) {
            if (dr==0 && dc==0) continue;
            int nr=r+dr, nc=c+dc;
            if (!inBounds(nr,nc)) continue;
            Cell adj = cells[nr][nc];
            if (adj.isRevealed()) { knownNeighbors++; sumNumbers += adj.getAdjacentHazardCount(); }
            if (adj.isFlagged()) flagged++;
        }
        if (knownNeighbors == 0) return 0.12;
        double avg = (double) sumNumbers / (double) knownNeighbors;
        double risk = Math.min(0.85, (avg + flagged * 0.9) / 6.0);
        return risk;
    }

    private static class MoveCandidate { int fromR, fromC, toR, toC; double score; MoveCandidate(int fr,int fc,int tr,int tc,double s){ fromR=fr; fromC=fc; toR=tr; toC=tc; score=s; }}

    private int pieceValue(Piece p) {
        if (p == null) return 0;
        return switch (p.getName()) {
            case "King" -> 1000;
            case "Queen" -> 9;
            case "Rook" -> 5;
            case "Bishop" -> 3;
            case "Knight" -> 3;
            case "Pawn" -> 1;
            default -> 1;
        };
    }

    public void toggleFlag(int row, int col) {
        if (!inBounds(row, col)) return;
        Cell cell = cells[row][col];
        if (cell.isRevealed() && cell.getPiece() != null && cell.getPiece().isWhite() != whiteTurn) return;

        if (!cell.isRevealed() || cell.isExploded() || cell.getPiece() != null) {
            // save snapshot
            saveSnapshot();
            cell.setFlagged(!cell.isFlagged());
        }
    }

    public boolean isWhiteTurn() { return whiteTurn; }
    public int getWidth(){ return width; }
    public int getHeight(){ return height; }
}