package sopra.steria.ordering;

import knight.clubbing.core.BBoard;
import knight.clubbing.core.BMove;

public interface MoveOrderer {
    void orderMoves(BMove[] moves, BBoard board, BMove[][] killers, int ply, int ttMove);
}