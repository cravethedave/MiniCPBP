package minicpbp.examples;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.FMM;
import minicpbp.util.Procedure;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.util.function.Supplier;
import java.util.Arrays;

public class FMMTest {
    private static int failures = 0;

    public static void main(String[] args) {
        //#region Problem Parameters
        FMM fmm = new FMM(2,1,2);
        final int R = 4;
        final int A = fmm.getASize();
        final int B = fmm.getBSize();
        final int C = fmm.getResultSize();
        fmm.printTensors();
        //#endregion


        //#region Problem Setup
        Solver cp = makeSolver();
        IntVar[][] U = new IntVar[A][R];
        IntVar[][] V = new IntVar[B][R];
        IntVar[][] W = new IntVar[C][R];

        for (int a = 0; a < A; a++) {
            for (int r = 0; r < R; r++) {
                U[a][r] = makeIntVar(cp,-1,1);
                U[a][r].setName("U_"+a+"_"+r);
            }
        }
        for (int b = 0; b < B; b++) {
            for (int r = 0; r < R; r++) {
                V[b][r] = makeIntVar(cp,-1,1);
                V[b][r].setName("V_"+b+"_"+r);
            }
        }
        for (int c = 0; c < C; c++) {
            for (int r = 0; r < R; r++) {
                W[c][r] = makeIntVar(cp,-1,1);
                W[c][r].setName("W_"+c+"_"+r);
            }
        }
        //#endregion


        //#region Base Model Constraints
        // Creating the table of possible products
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

        // Applying the table constraint for the products
        for (int c = 0; c < C; c++) {
            for (int a = 0; a < A; a++) {
                for (int b = 0; b < B; b++) {
                    IntVar[] prodResults = makeIntVarArray(cp, R, -1, 1);
                    for (int r = 0; r < R; r++) {
                        IntVar[] productVars = new IntVar[]{
                            U[a][r],
                            V[b][r],
                            W[c][r],
                            prodResults[r]
                        };
                        cp.post(table(productVars, productTable));
                    }
                    // Applying the base constraint to solve for a valid solution
                    cp.post(sum(prodResults, fmm.getTensors(c,a,b)));
                }
            }
        }

        // Garbage collection
        System.gc();
        //#endregion


        //#region Permutation Symmetry
        IntVar[] previousAugmentedColumn = new IntVar[fmm.getASize() + fmm.getBSize()];
        IntVar[] augmentedColumn = new IntVar[fmm.getASize() + fmm.getBSize()];
        
        // Initialize the previous augmented column
        for (int a = 0; a < fmm.getASize(); a++) {
            previousAugmentedColumn[a] = U[a][0];
        }
        for (int b = 0; b < fmm.getBSize(); b++) {
            previousAugmentedColumn[b + fmm.getASize()] = V[b][0];
        }

        // Apply a lexicographic-strict constraint to break permutations
        for (int r = 1; r < R; r++) {
            for (int a = 0; a < fmm.getASize(); a++) {
                augmentedColumn[a] = U[a][r];
            }
            for (int b = 0; b < fmm.getBSize(); b++) {
                augmentedColumn[b + fmm.getASize()] = V[b][r];
            }
            // cp.post(lexLess(previousAugmentedColumn, augmentedColumn));
            previousAugmentedColumn = augmentedColumn;
        }
        //#endregion


        //#region Sign Symmetry using sum of absolute - OBSOLETE
        // IntVar zeroVar = makeIntVar(cp, 0, 0);
        // zeroVar.setName("Zero Variable");
        // for (int r = 0; r < R; r++) {
        //     cp.post(lessOrEqual(U[0][r], zeroVar));
        //     for (int a = 1; a < fmm.getASize(); a++) {
        //         IntVar[] absoluteVars = new IntVar[a];
        //         absoluteVars[0] = abs(U[1][r]);
        //         for (int i = 1; i < a; i++) {
        //             absoluteVars[i] = abs(U[1][r]);
        //         }
        //     }
        // }
        //#endregion

        
        //#region Sign Symmetry using table
        /**
         * This constraint uses a table which grows exponentially in one dimension
         * and linearly in the other. It should not be an issue with smaller problems.
         * We limit ourselves to the 3x3x3 problem which should not cause issues.
         * Consider changing the implementation if n>3 and m=4 (or vice-versa).
        */
        // Creating the table of possible values. The first dimension follows a geometric series.
        int[][] signTable = getIteratingValuesArray(A);

        // Apply table constraint to all columns of U and W
        IntVar[] columnU = new IntVar[A];
        for (int r = 0; r < R; r++) {
            for (int a = 0; a < A; a++) {
                columnU[a] = U[a][r];
            }
            // cp.post(table(columnU, signTable));
        }
        
        if (C != A) {
            signTable = getIteratingValuesArray(C);
        }
        IntVar[] columnW = new IntVar[C];
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                columnW[c] = W[c][r];
            }
            // cp.post(table(columnW, signTable));
        }
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
        DFSearch dfs = makeDfs(cp, maxMarginalRandomTieBreak(searchIntVars));

        dfs.onSolution(() -> {
            System.out.println("New Solution");
            printMatrix(U, fmm.getASize(), R);
            printMatrix(V, fmm.getBSize(), R);
            printMatrix(W, fmm.getResultSize(), R);
            System.out.println();
        });
        dfs.onFailure(() -> {
            failures++;
            if (failures % 5000 == 0)
                System.out.println(String.format("failures: %d", failures));
        });
        SearchStatistics stats = new SearchStatistics();
        stats = dfs.solveRestarts(s -> s.numberOfSolutions() == 10, 100, 1.5);
        System.out.println(stats);
        //#endregion
    }

    public static void printMatrix(IntVar[][] M, int rows, int columns) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int val = M[i][j].max();
                if (val >= 0) System.out.print(" ");
                System.out.print(val);
                System.out.print(' ');
            }
            System.out.print('\n');
        }
        System.out.print('\n');
    }

    private static int[][] getIteratingValuesArray(int arraySize) {
        int firstDim = ((int)Math.pow(3,arraySize) - 1) / 2;
        int[][] signTable = new int[firstDim][arraySize];
        int[] values = new int[arraySize];
        Arrays.fill(values, -1);
        signTable[0] = values.clone();
        boolean carry;
        for (int i = 1; i < firstDim; i++) {
            carry = true;
            for (int j = arraySize - 1; j >= 0; j--) {
                if (!carry) break;
                values[j] += 1;
                carry = values[j] == 2;
                if (carry) values[j] = -1;
            }
            signTable[i] = values.clone();
        }
        return signTable;
    }
}
