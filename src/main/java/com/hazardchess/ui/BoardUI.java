package com.hazardchess.ui;

import com.hazardchess.game.Board;
import com.hazardchess.game.Cell;
import com.hazardchess.pieces.Piece;
import com.hazardchess.pieces.Queen;
import com.hazardchess.pieces.Rook;
import com.hazardchess.pieces.Bishop;
import com.hazardchess.pieces.Knight;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Polished BoardUI with:
 * - centered fixed-size board
 * - nicer piece visuals (round backgrounds, proper colors)
 * - Reveal toggle visually aligned
 * - Game over overlay when king is captured
 * - Pawn promotion UI (player chooses piece) — now with icon buttons
 *
 * Note: Undo uses Board.undo(); Board maintains history internally.
 */
public class BoardUI extends Application {

    private final int CELL_SIZE = 64;
    private final int BOARD_PADDING = 18;
    private final double BOARD_RADIUS = 12.0;

    private Board board;
    private Cell selectedCell = null;
    private List<Cell> validMoves = new ArrayList<>();

    private StackPane boardContainer;
    private GridPane grid;
    private Pane overlayPane; // for animation layer
    private StackPane[][] nodeCache;

    private boolean useAltRevealColors = false;

    private TextArea logArea;
    private Button undoButton;

    // numeric log counter
    private int logCounter = 0;

    // promotion and game-over overlay nodes
    private Pane modalOverlay;
    
    private String toChessCoord(int row, int col) {
        char file = (char) ('A' + col);
        int rank = board.getHeight() - row; // hàng 0 là 8, hàng 7 là 1
        return "" + file + rank;
    }

    @Override
    public void start(Stage stage) {
        board = new Board(8, 2);

        grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setPadding(Insets.EMPTY);

        boardContainer = new StackPane();
        boardContainer.setPadding(new Insets(BOARD_PADDING));
        boardContainer.setAlignment(Pos.CENTER);

        // board background panel (size based on board)
        double boardW = board.getWidth() * CELL_SIZE + BOARD_PADDING * 2;
        double boardH = board.getHeight() * CELL_SIZE + BOARD_PADDING * 2;
        Rectangle bg = new Rectangle(boardW, boardH);
        bg.setArcWidth(BOARD_RADIUS * 2);
        bg.setArcHeight(BOARD_RADIUS * 2);
        bg.setFill(Color.web("#f7f9fc"));
        bg.setStroke(Color.web("#e6ebf3"));
        bg.setStrokeWidth(1.0);
        bg.setEffect(new DropShadow(10, Color.rgb(20, 30, 40, 0.08)));

        overlayPane = new Pane();
        overlayPane.setPickOnBounds(false);
        overlayPane.setMouseTransparent(true);
        overlayPane.setPrefSize(board.getWidth() * CELL_SIZE, board.getHeight() * CELL_SIZE);

        boardContainer.getChildren().addAll(bg, grid, overlayPane);
        StackPane.setAlignment(grid, Pos.CENTER);
        StackPane.setAlignment(overlayPane, Pos.CENTER);

        initGridCache();
        refreshAllCells();

        // fix boardContainer size so it cannot be stretched by surrounding layout
        boardContainer.setPrefSize(boardW, boardH);
        boardContainer.setMaxSize(boardW, boardH);
        boardContainer.setMinSize(boardW, boardH);

        // TOP: controls row
        HBox topBar = new HBox();
        topBar.setPadding(new Insets(10, 16, 10, 16));
        topBar.setSpacing(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        // Buttons
        undoButton = new Button("Undo");
        stylePrimarySmall(undoButton);
        undoButton.setDisable(true);
        undoButton.setOnAction(evt -> {
            boolean ok = board.undo();
            if (ok) {
                refreshAllCells();
                simpleLog("Undo");
            } else simpleLog("Nothing to undo");
            undoButton.setDisable(!ok); // if can't undo further, disable
        });

        Button resetBtn = new Button("Reset");
        styleSecondarySmall(resetBtn);
        resetBtn.setOnAction(evt -> {
            board = new Board(board.getHeight(), 2);
            selectedCell = null;
            validMoves.clear();
            initGridCache();
            refreshAllCells();
            animateExplosions();
            simpleLog("Reset");
            undoButton.setDisable(true);
        });

        Button newGameBtn = new Button("New Game");
        stylePrimarySmall(newGameBtn);
        newGameBtn.setOnAction(evt -> {
            board = new Board(board.getHeight(), 2);
            selectedCell = null;
            validMoves.clear();
            initGridCache();
            refreshAllCells();
            simpleLog("New Game");
            undoButton.setDisable(true);
        });

        ToggleButton themeToggle = new ToggleButton("Reveal");
        styleToggleSmall(themeToggle);
        themeToggle.setSelected(useAltRevealColors);
        updateToggleStyle(themeToggle);
        themeToggle.setOnAction(evt -> {
            useAltRevealColors = themeToggle.isSelected();
            refreshAllCells();
            simpleLog("Reveal: " + (useAltRevealColors ? "ON" : "OFF"));
            updateToggleStyle(themeToggle);
        });

        HBox controlsBox = new HBox(8, undoButton, resetBtn, newGameBtn);
        controlsBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(controlsBox, spacer, themeToggle);

        // RIGHT: fixed log panel
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefWidth(320);
        logArea.setMinWidth(260);
        logArea.setMaxWidth(360);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12; -fx-focus-color: transparent;");
        VBox logBox = new VBox(8);
        Label logLabel = new Label("Event Log");
        logLabel.setFont(Font.font(13));
        logLabel.setStyle("-fx-font-weight:600;");
        logBox.getChildren().addAll(logLabel, logArea);
        logBox.setPadding(new Insets(12));
        logBox.setPrefWidth(320);
        logBox.setMaxWidth(360);
        logBox.setStyle("-fx-background-color: rgba(250,250,252,0.95); -fx-background-radius:8; -fx-border-color: rgba(0,0,0,0.04); -fx-border-radius:8;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // CENTER: container that centers the fixed-size board
        HBox centerBox = new HBox();
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(12));
        centerBox.getChildren().add(boardContainer);

        // root layout with overall padding (thụt lề)
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(centerBox);
        root.setRight(logBox);
        root.setPadding(new Insets(18));

        BorderPane.setMargin(topBar, new Insets(6, 12, 0, 12));
        BorderPane.setMargin(centerBox, new Insets(12, 6, 12, 12));
        BorderPane.setMargin(logBox, new Insets(12, 12, 12, 6));

        // compute scene size carefully so nothing overflows
        double sceneW = boardW + logBox.getPrefWidth() + 120; // some gutters
        double sceneH = Math.max(boardH + 140, 640);

        Scene scene = new Scene(root, sceneW, sceneH);
        scene.setCursor(Cursor.DEFAULT);

        // initial log
        simpleLog("Game initialized");
        undoButton.setDisable(true);

        // window title
        stage.setTitle("Hazard Chess");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        Platform.runLater(() -> {
            refreshAllCells();
            animateExplosions();
        });
    }

    // style helpers
    private void stylePrimarySmall(Button b) {
        b.setPrefHeight(30);
        b.setStyle("-fx-background-color: linear-gradient(#4f46e5,#3b82f6); -fx-text-fill: white; -fx-background-radius:10; -fx-padding:6 12; -fx-font-weight:600;");
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: linear-gradient(#6366f1,#60a5fa); -fx-text-fill: white; -fx-background-radius:10; -fx-padding:6 12; -fx-font-weight:600;"));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: linear-gradient(#4f46e5,#3b82f6); -fx-text-fill: white; -fx-background-radius:10; -fx-padding:6 12; -fx-font-weight:600;"));
    }

    private void styleSecondarySmall(Button b) {
        b.setPrefHeight(30);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: #374151; -fx-border-color: rgba(55,65,81,0.12); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600;");
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: rgba(15,23,42,0.04); -fx-text-fill: #111827; -fx-border-color: rgba(55,65,81,0.12); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600;"));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: transparent; -fx-text-fill: #374151; -fx-border-color: rgba(55,65,81,0.12); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600;"));
    }

    private void styleToggleSmall(ToggleButton t) {
        // make toggle visually consistent with other buttons (same height/padding)
        t.setPrefHeight(30);
        t.setStyle("-fx-background-color: transparent; -fx-border-color: rgba(55,65,81,0.08); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600; -fx-text-fill: #374151;");
        t.setOnMouseEntered(e -> t.setStyle("-fx-background-color: rgba(15,23,42,0.03); -fx-border-color: rgba(55,65,81,0.08); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600; -fx-text-fill: #111827;"));
        t.setOnMouseExited(e -> t.setStyle("-fx-background-color: transparent; -fx-border-color: rgba(55,65,81,0.08); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600; -fx-text-fill: #374151;"));
    }

    private void updateToggleStyle(ToggleButton t) {
        String unselected = "-fx-background-color: transparent; -fx-text-fill: #374151; -fx-border-color: rgba(55,65,81,0.08); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600;";
        String unselectedHover = "-fx-background-color: rgba(15,23,42,0.03); -fx-text-fill: #111827; -fx-border-color: rgba(55,65,81,0.08); -fx-border-radius:10; -fx-padding:6 12; -fx-font-weight:600;";
        String selected = "-fx-background-color: linear-gradient(#4f46e5,#3b82f6); -fx-text-fill: white; -fx-background-radius:10; -fx-padding:6 12; -fx-font-weight:600;";
        String selectedHover = "-fx-background-color: linear-gradient(#6366f1,#60a5fa); -fx-text-fill: white; -fx-background-radius:10; -fx-padding:6 12; -fx-font-weight:600;";

        t.setFocusTraversable(false);
        t.setStyle(t.isSelected() ? selected : unselected);

        t.setOnMouseEntered(e -> t.setStyle(t.isSelected() ? selectedHover : unselectedHover));
        t.setOnMouseExited(e -> t.setStyle(t.isSelected() ? selected : unselected));

        t.selectedProperty().addListener((obs, oldV, newV) -> t.setStyle(newV ? selected : unselected));
    }

    private void initGridCache() {
        int h = board.getHeight();
        int w = board.getWidth();
        nodeCache = new StackPane[h][w];
        grid.getChildren().clear();
        grid.getRowConstraints().clear();
        grid.getColumnConstraints().clear();

        for (int r = 0; r < h; r++) {
            RowConstraints rc = new RowConstraints(CELL_SIZE);
            grid.getRowConstraints().add(rc);
            for (int c = 0; c < w; c++) {
                if (r == 0) grid.getColumnConstraints().add(new ColumnConstraints(CELL_SIZE));
                StackPane pane = new StackPane();
                pane.setMinSize(CELL_SIZE, CELL_SIZE);
                pane.setPrefSize(CELL_SIZE, CELL_SIZE);
                pane.setMaxSize(CELL_SIZE, CELL_SIZE);
                Rectangle clip = new Rectangle(CELL_SIZE, CELL_SIZE);
                clip.setArcWidth(8);
                clip.setArcHeight(8);
                pane.setClip(clip);
                nodeCache[r][c] = pane;
                grid.add(pane, c, r);
            }
        }

        for (int r = 0; r < h; r++)
            for (int c = 0; c < w; c++)
                refreshCell(r, c);
    }

    private void refreshCell(int r, int c) {
        StackPane pane = nodeCache[r][c];
        pane.getChildren().clear();
        Cell cell = board.getCell(r, c);

        Rectangle rect = new Rectangle(CELL_SIZE - 2, CELL_SIZE - 2);
        rect.setArcWidth(8);
        rect.setArcHeight(8);
        rect.setStrokeType(StrokeType.INSIDE);
        Color light = Color.web("#eef2ff");
        Color dark = Color.web("#ffffff");

        if (cell.isRevealed() && useAltRevealColors) {
            light = Color.web("#e6ffef");
            dark = Color.web("#e6ffef");
        }

        if (cell.isRevealed()) {
            if (cell.isExploded()) rect.setFill(Color.rgb(255, 87, 34, 0.10));
            else rect.setFill((r + c) % 2 == 0 ? light : dark);
        } else {
            rect.setFill((r + c) % 2 == 0 ? light : dark);
        }
        rect.setStroke(Color.web("#e6ebf3"));
        rect.setStrokeWidth(0.6);

        if (cell == selectedCell) {
            rect.setStroke(Color.web("#2563eb"));
            rect.setStrokeWidth(3.0);
        } else if (validMoves.contains(cell)) {
            rect.setStroke(Color.web("#f59e0b"));
            rect.setStrokeWidth(2.0);
        }

        pane.getChildren().add(rect);

        if (validMoves.contains(cell) && !cell.isRevealed()) {
            Circle ring = new Circle(CELL_SIZE * 0.36);
            ring.setFill(Color.color(1.0, 0.94, 0.6, 0.16));
            ring.setStroke(Color.web("#f59e0b"));
            ring.setStrokeWidth(3.0);
            pane.getChildren().add(ring);
        }

        if (cell.isExploded()) {
            Circle explosion = new Circle(CELL_SIZE * 0.22);
            explosion.setFill(Color.rgb(255, 99, 71, 0.9));
            explosion.setEffect(new DropShadow(6, Color.rgb(255, 80, 40, 0.5)));
            pane.getChildren().add(explosion);
        }

        if (cell.isFlagged()) {
            Text flag = new Text("⚑");
            if (cell.isExploded() || cell.getPiece() != null) {
                flag.setFont(Font.font(12));
                flag.setFill(Color.web("#8b0000"));
                StackPane.setAlignment(flag, Pos.TOP_RIGHT);
                flag.setTranslateX(-6);
                flag.setTranslateY(6);
                pane.getChildren().add(flag);
            } else if (!cell.isRevealed()) {
                flag.setFont(Font.font(20));
                flag.setFill(Color.web("#b32121"));
                pane.getChildren().add(flag);
            }
        }

        // Piece: smaller circular background + stroke + glyph color tuned to white/black side
        if (cell.getPiece() != null) {
            double rRad = CELL_SIZE * 0.24;
            Circle pieceBg = new Circle(rRad);
            Text pieceText = new Text(getPieceSymbol(cell.getPiece()));
            pieceText.setFont(Font.font(18));
            pieceText.setStrokeWidth(0.0);
            pieceText.setTranslateY(-1);

            if (cell.getPiece().isWhite()) {
                pieceBg.setFill(Color.web("#fffaf0")); // cream
                pieceBg.setStroke(Color.web("#d1d5db"));
                pieceBg.setStrokeWidth(1.0);
                pieceText.setFill(Color.web("#1f2937")); // dark glyph
            } else {
                pieceBg.setFill(Color.web("#111827")); // near black
                pieceBg.setStroke(Color.web("#0b1220"));
                pieceBg.setStrokeWidth(1.0);
                pieceText.setFill(Color.web("#fff8e1")); // light glyph
            }
            pieceBg.setEffect(new DropShadow(6, Color.rgb(10, 20, 30, 0.18)));
            pane.getChildren().addAll(pieceBg, pieceText);
        }

        if (cell.canShowNumber()) {
            int count = cell.getAdjacentHazardCount();
            Text num = new Text(String.valueOf(count));
            num.setFont(Font.font(14));
            switch (count) {
                case 1 -> num.setFill(Color.web("#2563eb"));
                case 2 -> num.setFill(Color.web("#16a34a"));
                case 3 -> num.setFill(Color.web("#ef4444"));
                case 4 -> num.setFill(Color.web("#1e40af"));
                case 5 -> num.setFill(Color.web("#7f1d1d"));
                case 6 -> num.setFill(Color.web("#0f766e"));
                case 7 -> num.setFill(Color.web("#111827"));
                case 8 -> num.setFill(Color.web("#6b7280"));
                default -> num.setFill(Color.BLACK);
            }
            StackPane.setAlignment(num, Pos.TOP_RIGHT);
            num.setTranslateX(-6);
            num.setTranslateY(6);
            pane.getChildren().add(num);
        }

        final int rr = r, cc = c;
        pane.setOnMouseEntered(e -> { pane.setOpacity(0.95); pane.setCursor(Cursor.HAND); });
        pane.setOnMouseExited(e -> { pane.setOpacity(1.0); pane.setCursor(Cursor.DEFAULT); });
        
        pane.setOnMouseClicked(e -> {
            if (board.isGameOver()) return;
            if (e.getButton() == MouseButton.SECONDARY) {
                board.toggleFlag(rr, cc);
                refreshCell(rr, cc);
                animateExplosions();
                simpleLog("Flag toggled at " + toChessCoord(rr, cc));
                undoButton.setDisable(false);
                return;
            }

            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 2) {
                    Cell dbl = board.getCell(rr, cc);
                    if (dbl != null && dbl.isRevealed() && dbl.getAdjacentHazardCount() > 0) {
                        Piece trigger = null;
                        if (selectedCell != null && selectedCell.getPiece() != null && selectedCell.getPiece().isWhite() == board.isWhiteTurn()) {
                            trigger = selectedCell.getPiece();
                        }
                        boolean executed = board.checkQuickReveal(dbl, trigger, true); // double-click consumes turn when valid
                        if (executed) {
                            selectedCell = null;
                            validMoves.clear();
                            refreshAllCells();
                            animateExplosions();
                            simpleLog("Quick reveal at " + toChessCoord(rr, cc));
                            undoButton.setDisable(false);
                            if (!board.isWhiteTurn()) runAIMoveWithAnimation();
                        }
                        return;
                    }
                }
                handleClickWithAnimation(rr, cc);
            }
        });

        if (r == board.getHeight() - 1) {
            char file = (char) ('A' + c);
            Text label = new Text(String.valueOf(file));
            label.setFont(Font.font(10));
            label.setFill(Color.web("#6b7280"));
            StackPane.setAlignment(label, Pos.BOTTOM_LEFT);
            label.setTranslateX(4);
            label.setTranslateY(-4);
            pane.getChildren().add(label);
        }

        if (c == 0) {
            int rank = board.getHeight() - r;
            Text label = new Text(String.valueOf(rank));
            label.setFont(Font.font(10));
            label.setFill(Color.web("#6b7280"));
            StackPane.setAlignment(label, Pos.TOP_LEFT);
            label.setTranslateX(4);
            label.setTranslateY(4);
            pane.getChildren().add(label);
        }
    }

    private String getPieceSymbol(Piece p) {
        boolean w = p.isWhite();
        return switch (p.getName()) {
            case "King" -> w ? "♔" : "♚";
            case "Queen" -> w ? "♕" : "♛";
            case "Rook" -> w ? "♖" : "♜";
            case "Bishop" -> w ? "♗" : "♝";
            case "Knight" -> w ? "♘" : "♞";
            case "Pawn" -> w ? "♙" : "♟";
            default -> "?";
        };
    }

    private void handleClickWithAnimation(int row, int col) {
    Cell clicked = board.getCell(row, col);
    if (selectedCell == null) {
        if (clicked.getPiece() != null && clicked.getPiece().isWhite()) {
            selectedCell = clicked;
            validMoves.clear();
            Piece p = clicked.getPiece();
            for (int r = 0; r < board.getHeight(); r++)
                for (int c = 0; c < board.getWidth(); c++)
                    if (p.canMove(selectedCell.getRow(), selectedCell.getCol(), r, c, board))
                        validMoves.add(board.getCell(r, c));
        }
        refreshAllCells();
        return;
    }

    if (clicked == selectedCell) {
        selectedCell = null;
        validMoves.clear();
        refreshAllCells();
        return;
    }

    Piece p = selectedCell.getPiece();
    if (p == null || !p.canMove(selectedCell.getRow(), selectedCell.getCol(), row, col, board)) {
        selectedCell = null;
        validMoves.clear();
        refreshAllCells();
        return;
    }

    int sr = selectedCell.getRow(), sc = selectedCell.getCol();
    String pieceName = p.getName();
    boolean pieceIsWhite = p.isWhite();
    String moveLogText = (pieceIsWhite ? "White " : "Black ") + pieceName + " " +
            toChessCoord(sr, sc) + " → " + toChessCoord(row, col);

    StackPane sourceNode = nodeCache[sr][sc];
    StackPane destNode = nodeCache[row][col];

    if (sourceNode == null || destNode == null) {
        board.movePiece(sr, sc, row, col);
        selectedCell = null;
        validMoves.clear();
        refreshAllCells();
        animateExplosions();
        simpleLog(moveLogText);
        undoButton.setDisable(false);
        if (!board.isWhiteTurn()) runAIMoveWithAnimation();
        return;
    }

    Platform.runLater(() -> {
        javafx.geometry.Bounds sb = sourceNode.localToScene(sourceNode.getBoundsInLocal());
        javafx.geometry.Bounds db = destNode.localToScene(destNode.getBoundsInLocal());
        Point2D startScene = new Point2D(sb.getMinX() + sb.getWidth() / 2, sb.getMinY() + sb.getHeight() / 2);
        Point2D endScene = new Point2D(db.getMinX() + db.getWidth() / 2, db.getMinY() + db.getHeight() / 2);
        Point2D startLocal = overlayPane.sceneToLocal(startScene);
        Point2D endLocal = overlayPane.sceneToLocal(endScene);

        double rRad = CELL_SIZE * 0.28;
        Circle animPiece = new Circle(rRad);
        animPiece.setCenterX(rRad);
        animPiece.setCenterY(rRad);

        animPiece.setFill(pieceIsWhite ? Color.web("#fffaf0") : Color.web("#111827"));
        animPiece.setEffect(new DropShadow(6, Color.rgb(10,20,30,0.18)));
        animPiece.setManaged(false);

        animPiece.setLayoutX(startLocal.getX() - rRad);
        animPiece.setLayoutY(startLocal.getY() - rRad);
        overlayPane.getChildren().add(animPiece);

        TranslateTransition tt = new TranslateTransition(Duration.millis(260), animPiece);
        double dx = endLocal.getX() - startLocal.getX();
        double dy = endLocal.getY() - startLocal.getY();
        tt.setFromX(0); tt.setToX(dx);
        tt.setFromY(0); tt.setToY(dy);
        tt.setInterpolator(Interpolator.EASE_BOTH);

        tt.setOnFinished(ev -> {
            overlayPane.getChildren().remove(animPiece);
            board.movePiece(sr, sc, row, col);

            // check promotion
            Cell dest = board.getCell(row, col);
            if (dest != null && dest.getPiece() != null && "Pawn".equals(dest.getPiece().getName())) {
                boolean isWhite = dest.getPiece().isWhite();
                if ((isWhite && row == 0) || (!isWhite && row == board.getHeight()-1)) {
                    promptPromotion(dest);
                }
            }

            selectedCell = null;
            validMoves.clear();
            refreshAllCells();
            animateExplosions();
            simpleLog(moveLogText);
            undoButton.setDisable(false);

            if (!board.isWhiteTurn() && !board.isGameOver()) runAIMoveWithAnimation();
        });

        tt.play();
    });
}

private void runAIMoveWithAnimation() {
    if (board.isGameOver()) return;

    Task<int[]> task = new Task<>() {
        @Override
        protected int[] call() { return board.chooseBestAIMove(); }
    };

    task.setOnSucceeded(ev -> {
        int[] mv = task.getValue();
        if (mv == null) {
            int[] quick = board.findQuickRevealCandidate();
            if (quick != null) {
                Cell numcell = board.getCell(quick[0], quick[1]);
                Piece trigger = (numcell.getPiece() != null && !numcell.getPiece().isWhite()) ? numcell.getPiece() : null;
                boolean executed = board.checkQuickReveal(numcell, trigger, true);
                if (executed) {
                    refreshAllCells();
                    animateExplosions();
                    simpleLog("Black quick reveal at " + toChessCoord(quick[0], quick[1]));
                    undoButton.setDisable(false);
                }
            }
            return;
        }
        int fr = mv[0], fc = mv[1], tr = mv[2], tc = mv[3];
        StackPane sourceNode = nodeCache[fr][fc];
        StackPane destNode = nodeCache[tr][tc];

        Cell fromCell = board.getCell(fr, fc);
        String pieceName = (fromCell != null && fromCell.getPiece() != null) ? fromCell.getPiece().getName() : "Piece";
        boolean pieceIsWhite = (fromCell != null && fromCell.getPiece() != null && fromCell.getPiece().isWhite());
        String moveLogText = (pieceIsWhite ? "White " : "Black ") + pieceName + " " +
                toChessCoord(fr, fc) + " → " + toChessCoord(tr, tc);

        if (sourceNode == null || destNode == null) {
            board.movePiece(fr, fc, tr, tc);
            // auto-promote AI pawn
            Cell dest = board.getCell(tr, tc);
            if (dest != null && dest.getPiece() != null && "Pawn".equals(dest.getPiece().getName())) {
                boolean white = dest.getPiece().isWhite();
                if ((white && tr == 0) || (!white && tr == board.getHeight()-1)) {
                    dest.setPiece(new Queen(white));
                    simpleLog("Black pawn promoted to Queen at " + toChessCoord(tr, tc));
                }
            }
            refreshAllCells();
            animateExplosions();
            simpleLog(moveLogText);
            undoButton.setDisable(false);
            return;
        }

        Platform.runLater(() -> {
            javafx.geometry.Bounds sb = sourceNode.localToScene(sourceNode.getBoundsInLocal());
            javafx.geometry.Bounds db = destNode.localToScene(destNode.getBoundsInLocal());
            Point2D startScene = new Point2D(sb.getMinX() + sb.getWidth() / 2, sb.getMinY() + sb.getHeight() / 2);
            Point2D endScene = new Point2D(db.getMinX() + db.getWidth() / 2, db.getMinY() + db.getHeight() / 2);
            Point2D startLocal = overlayPane.sceneToLocal(startScene);
            Point2D endLocal = overlayPane.sceneToLocal(endScene);

            double rp = CELL_SIZE * 0.28;
            Circle animPiece = new Circle(rp);
            animPiece.setCenterX(rp);
            animPiece.setCenterY(rp);

            animPiece.setFill(pieceIsWhite ? Color.web("#fffaf0") : Color.web("#111827"));
            animPiece.setEffect(new DropShadow(6, Color.rgb(10,20,30,0.18)));
            animPiece.setManaged(false);
            animPiece.setLayoutX(startLocal.getX() - rp);
            animPiece.setLayoutY(startLocal.getY() - rp);
            overlayPane.getChildren().add(animPiece);

            TranslateTransition tt = new TranslateTransition(Duration.millis(300), animPiece);
            double dx = endLocal.getX() - startLocal.getX();
            double dy = endLocal.getY() - startLocal.getY();
            tt.setFromX(0); tt.setToX(dx);
            tt.setFromY(0); tt.setToY(dy);
            tt.setInterpolator(Interpolator.EASE_BOTH);
            tt.setOnFinished(e -> {
                overlayPane.getChildren().remove(animPiece);
                board.movePiece(fr, fc, tr, tc);
                Cell dest = board.getCell(tr, tc);
                if (dest != null && dest.getPiece() != null && "Pawn".equals(dest.getPiece().getName())) {
                    boolean white = dest.getPiece().isWhite();
                    if ((white && tr == 0) || (!white && tr == board.getHeight()-1)) {
                        dest.setPiece(new Queen(white));
                        simpleLog("Black pawn promoted to Queen at " + toChessCoord(tr, tc));
                    }
                }
                refreshAllCells();
                animateExplosions();
                simpleLog(moveLogText);
                undoButton.setDisable(false);
            });
            tt.play();
        });
    });

    new Thread(task, "AI-Worker").start();
}

    private void refreshAllCells() {
        for (int r = 0; r < board.getHeight(); r++)
            for (int c = 0; c < board.getWidth(); c++)
                refreshCell(r, c);

        // if game ended, show overlay
        if (board.isGameOver()) showGameOverOverlay(board.getWhiteWinner());
    }

    private void animateExplosions() {
        for (int r = 0; r < board.getHeight(); r++) {
            for (int c = 0; c < board.getWidth(); c++) {
                Cell cc = board.getCell(r, c);
                try {
                    if (cc.isExploded() && !cc.isExplosionAnimated()) {
                        playHazardEffect(r, c);
                        cc.setExplosionAnimated(true);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private void playHazardEffect(int row, int col) {
        StackPane node = nodeCache[row][col];
        if (node == null) return;
        Platform.runLater(() -> {
            javafx.geometry.Bounds b = node.localToScene(node.getBoundsInLocal());
            Point2D centerScene = new Point2D(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
            Point2D centerLocal = overlayPane.sceneToLocal(centerScene);

            double radius = CELL_SIZE * 0.45;
            Circle explosion = new Circle(radius);
            explosion.setCenterX(radius);
            explosion.setCenterY(radius);

            explosion.setFill(Color.rgb(255, 99, 71, 0.85));
            explosion.setEffect(new DropShadow(8, Color.rgb(255, 80, 40, 0.6)));
            explosion.setManaged(false);
            explosion.setLayoutX(centerLocal.getX() - explosion.getRadius());
            explosion.setLayoutY(centerLocal.getY() - explosion.getRadius());
            overlayPane.getChildren().add(explosion);

            ScaleTransition scale = new ScaleTransition(Duration.millis(350), explosion);
            scale.setFromX(0.4); scale.setFromY(0.4); scale.setToX(1.2); scale.setToY(1.2);
            FadeTransition fade = new FadeTransition(Duration.millis(350), explosion);
            fade.setFromValue(1.0); fade.setToValue(0.0);
            ParallelTransition pt = new ParallelTransition(scale, fade);
            pt.setOnFinished(e -> overlayPane.getChildren().remove(explosion));
            pt.play();
        });
    }

    // promotion dialog (player) — icons instead of text
    private void promptPromotion(Cell dest) {
        if (modalOverlay != null) return; // only one modal
        modalOverlay = new Pane();
        modalOverlay.setPickOnBounds(true);
        modalOverlay.setPrefSize(board.getWidth() * CELL_SIZE + BOARD_PADDING * 2, board.getHeight() * CELL_SIZE + BOARD_PADDING * 2);

        Rectangle backdrop = new Rectangle(modalOverlay.getPrefWidth(), modalOverlay.getPrefHeight());
        backdrop.setFill(Color.rgb(10, 10, 10, 0.45));

        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12));
        box.setStyle("-fx-background-color: white; -fx-background-radius:8; -fx-border-radius:8; -fx-border-color: rgba(0,0,0,0.06);");

        Text t = new Text("Choose promotion");
        t.setFont(Font.font(16));

        HBox choices = new HBox(10);
        choices.setAlignment(Pos.CENTER);

        boolean isWhite = dest.getPiece() != null && dest.getPiece().isWhite();

        // unicode glyphs
        String queenSym = isWhite ? "♕" : "♛";
        String rookSym  = isWhite ? "♖" : "♜";
        String bishopSym= isWhite ? "♗" : "♝";
        String knightSym= isWhite ? "♘" : "♞";

        Button q = makePromoteIconButton(queenSym, "Queen", isWhite);
        Button r = makePromoteIconButton(rookSym, "Rook", isWhite);
        Button b = makePromoteIconButton(bishopSym, "Bishop", isWhite);
        Button n = makePromoteIconButton(knightSym, "Knight", isWhite);

        q.setOnAction(e -> { dest.setPiece(new Queen(isWhite)); closeModal(); refreshAllCells(); simpleLog((isWhite?"White":"Black")+" promoted to Queen at " + toChessCoord(dest.getRow(), dest.getCol())); });
        r.setOnAction(e -> { dest.setPiece(new Rook(isWhite)); closeModal(); refreshAllCells(); simpleLog((isWhite?"White":"Black")+" promoted to Rook at " + toChessCoord(dest.getRow(), dest.getCol())); });
        b.setOnAction(e -> { dest.setPiece(new Bishop(isWhite)); closeModal(); refreshAllCells(); simpleLog((isWhite?"White":"Black")+" promoted to Bishop at " + toChessCoord(dest.getRow(), dest.getCol())); });
        n.setOnAction(e -> { dest.setPiece(new Knight(isWhite)); closeModal(); refreshAllCells(); simpleLog((isWhite?"White":"Black")+" promoted to Knight at " + toChessCoord(dest.getRow(), dest.getCol())); });

        choices.getChildren().addAll(q, r, b, n);
        box.getChildren().addAll(t, choices);

        // center box
        StackPane.setAlignment(box, Pos.CENTER);
        box.setLayoutX((modalOverlay.getPrefWidth()-280)/2);
        box.setLayoutY((modalOverlay.getPrefHeight()-120)/2);
        box.setPrefWidth(280);

        modalOverlay.getChildren().addAll(backdrop, box);
        boardContainer.getChildren().add(modalOverlay);
    }

    // create a round icon button for promotion
    private Button makePromoteIconButton(String symbol, String pieceName, boolean isWhite) {
        Button btn = new Button();
        btn.setPrefSize(64, 64);
        btn.setStyle("-fx-background-color: white; -fx-border-color: rgba(0,0,0,0.06); -fx-background-radius:12; -fx-border-radius:12;");
        Text glyph = new Text(symbol);
        glyph.setFont(Font.font(26));
        glyph.setFill(isWhite ? Color.web("#111827") : Color.web("#111827"));
        btn.setGraphic(glyph);
        btn.setTooltip(new Tooltip(pieceName));
        btn.setOnMouseEntered(e -> btn.setScaleX(1.06));
        btn.setOnMouseExited(e -> btn.setScaleX(1.0));
        return btn;
    }

    private void closeModal() {
        if (modalOverlay != null) {
            boardContainer.getChildren().remove(modalOverlay);
            modalOverlay = null;
        }
    }

    private void showGameOverOverlay(Boolean whiteWinner) {
        if (modalOverlay != null) return; // reuse modal area
        modalOverlay = new Pane();
        modalOverlay.setPickOnBounds(true);
        modalOverlay.setPrefSize(board.getWidth() * CELL_SIZE + BOARD_PADDING * 2, board.getHeight() * CELL_SIZE + BOARD_PADDING * 2);

        Rectangle backdrop = new Rectangle(modalOverlay.getPrefWidth(), modalOverlay.getPrefHeight());
        backdrop.setFill(Color.rgb(10, 10, 10, 0.45));

        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(18));
        box.setStyle("-fx-background-color: white; -fx-background-radius:12; -fx-border-radius:12; -fx-border-color: rgba(0,0,0,0.06);");

        Text t = new Text((whiteWinner != null && whiteWinner) ? "White wins!" : "Black wins!");
        t.setFont(Font.font(20));

        Button ng = new Button("New Game");
        stylePrimarySmall(ng);
        ng.setOnAction(e -> {
            board = new Board(board.getHeight(), 2);
            selectedCell = null;
            validMoves.clear();
            initGridCache();
            refreshAllCells();
            closeModal();
            simpleLog("New Game");
            undoButton.setDisable(true);
        });

        box.getChildren().addAll(t, ng);
        box.setLayoutX((modalOverlay.getPrefWidth()-240)/2);
        box.setLayoutY((modalOverlay.getPrefHeight()-120)/2);
        box.setPrefWidth(240);

        modalOverlay.getChildren().addAll(backdrop, box);
        boardContainer.getChildren().add(modalOverlay);
    }

    // numeric simple log lines (1., 2., ...)
    private void simpleLog(String msg) {
        Platform.runLater(() -> {
            if (logArea == null) return;
            logCounter++;
            String prefix = logCounter + ". ";
            logArea.appendText(prefix + msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public static void main(String[] args) { launch(); }
}