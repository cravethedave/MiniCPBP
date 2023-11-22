package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.FMM;
import minicpbp.util.Procedure;

import static minicpbp.cp.BranchingScheme.firstFail;
import static minicpbp.cp.BranchingScheme.maxMarginal;
import static minicpbp.cp.BranchingScheme.and;
import static minicpbp.cp.Factory.*;

import java.util.function.Supplier;

public class FMMTest {
    public static void main(String[] args) {
        //#region Problem Parameters
        int R = 7;
        FMM fmm = new FMM(2,2,2);
        fmm.printTensors();
        //#endregion


        //#region Problem Setup
        Solver cp = makeSolver();
        IntVar[][] U = new IntVar[fmm.getASize()][R];
        IntVar[][] V = new IntVar[fmm.getBSize()][R];
        IntVar[][] W = new IntVar[fmm.getResultSize()][R];

        for (int a = 0; a < U.length; a++) {
            for (int r = 0; r < R; r++) {
                U[a][r] = makeIntVar(cp,-1,1);
            }
        }
        for (int b = 0; b < V.length; b++) {
            for (int r = 0; r < R; r++) {
                V[b][r] = makeIntVar(cp,-1,1);
            }
        }
        for (int c = 0; c < W.length; c++) {
            for (int r = 0; r < R; r++) {
                W[c][r] = makeIntVar(cp,-1,1);
            }
        }
        //#endregion


        //#region Base Model Constraints
        int[][] productTable = new int[27][4];
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    int index = 9*(i+1) + 3*(j+1) + (k+1);
                    productTable[index][0] = i;
                    productTable[index][1] = j;
                    productTable[index][2] = k;
                    productTable[index][3] = i*j*k;
                }
            }
        }

        for (int c = 0; c < fmm.getResultSize(); c++) {
            for (int a = 0; a < fmm.getASize(); a++) {
                for (int b = 0; b < fmm.getBSize(); b++) {
                    IntVar[] prodResults = makeIntVarArray(cp, R, -1, 1);
                    for (int r = 0; r < R; r++) {
                        // table constraint on 4 vars and table containing all options
                        IntVar[] productVars = new IntVar[]{
                            U[a][r],
                            V[b][r],
                            W[c][r],
                            prodResults[r]
                        };
                        cp.post(table(productVars, productTable));
                    }
                    cp.post(sum(prodResults, fmm.getTensors(c,a,b)));
                }
            }
        }
        System.gc(); // Garbage collection
        //#endregion


        //#region Flatten Variables
        IntVar[] searchIntVars = new IntVar[
            fmm.getASize() * R +
            fmm.getBSize() * R +
            fmm.getResultSize() * R
        ];
        int index = 0;
        for (int i = 0; i < U.length; i++) {
            for (int r = 0; r < R; r++) {
                searchIntVars[index] = U[i][r];
                index++;
            }
        }
        for (int i = 0; i < V.length; i++) {
            for (int r = 0; r < R; r++) {
                searchIntVars[index] = V[i][r];
                index++;
            }
        }
        for (int i = 0; i < W.length; i++) {
            for (int r = 0; r < R; r++) {
                searchIntVars[index] = W[i][r];
                index++;
            }
        }
        //#endregion

        
        //#region Search
        // cp.setTraceBPFlag(true);
        DFSearch dfs = makeDfs(cp, maxMarginal(searchIntVars));

        // dfs.onSolution(() -> {
        //     System.out.println("New Solution");
        //     if (!V[5].isBound()) System.out.println("This is wrong");
        //     printMatrix(U, fmm.getASize(), R);
        //     printMatrix(V, fmm.getBSize(), R);
        //     printMatrix(W, fmm.getResultSize(), R);
        //     System.out.println();
        // });
        SearchStatistics stats = dfs.solve();
        System.out.println(stats);
        //#endregion
    }

    public static void printMatrix(IntVar[] M, int rows, int columns) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int val = M[i * columns + j].max();
                if (val >= 0) System.out.print(" ");
                System.out.print(val);
                System.out.print(' ');
            }
            System.out.print('\n');
        }
        System.out.print('\n');
    }
}
