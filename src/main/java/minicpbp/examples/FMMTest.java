package minicpbp.examples;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLongArray;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.engine.core.Solver.PropaMode;
import minicpbp.search.DFSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.FMM;
import minicpbp.util.FMMThread;

public class FMMTest {
    public static volatile boolean foundResult = false;

    /** Contains in order: nbFinishedThreads, totalTime, totalFailures, totalNodes, minTime, maxTime */
    public static AtomicLongArray threadStats = new AtomicLongArray(new long[]{0,0,0,0,Integer.MAX_VALUE, Integer.MIN_VALUE});
    static int failures = 0;

    public static void main(String[] args) {
        // K1 UB = nm+mp
        // K2 [M,R]
        FMM fmm = new FMM(1,2,3,6,2,2); // R = 6, k1 = 2, k2 = 2
        // FMM fmm = new FMM(1,2,4,8,3,4); // R = 8, k1 = 3, k2 = 4
        // FMM fmm = new FMM(1,2,5,10,5,4); // R = 10, k1 = 5, k2 = 4 (3 works but slow)
        fmm.printValues();
        fmm.printTensors();

        // parallel_runner(fmm, 10, 1000);
        // sequential_runner(fmm, 15, 1000);
        solve(fmm, 1000);
    }

    /**
     * Runs multiple threads solving the same problem and averages the values
     * @param fmm The info on the problem
     * @param count Number of times to run the problem
     * @param limit Restart limit depth
     */
    private static void parallel_runner(FMM fmm, int count, int limit) {
        FMMThread[] threads = new FMMThread[count];

        for (int i = 0; i < count; i++) {
            threads[i] = new FMMThread(fmm, limit);
            threads[i].start();
        }
    }

    /**
     * Runs multiple the same problem multiple times and averages the values
     * Useful if the runs take very little time, at which point the atomic variable makes them wait
     * @param fmm The info on the problem
     * @param count Number of times to run the problem
     * @param limit Restart limit depth
     */
    private static void sequential_runner(FMM fmm, int count, int limit) {
        for (int i = 0; i < count; i++) {
            solve(fmm, limit);
        }
    }

    public static void solve(FMM fmm, int limit) {
        //#region Problem Parameters
        final int R = fmm.getR();
        final int A = fmm.getASize();
        final int B = fmm.getBSize();
        final int C = fmm.getResultSize();
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

        bind1x2x3Solution(U, V, W);

        //#region Absolute Matrices useful later
        IntVar[][] absU = new IntVar[A][R];
        IntVar[][] absV = new IntVar[B][R];
        IntVar[][] absW = new IntVar[C][R];

        for (int a = 0; a < A; a++) {
            for (int r = 0; r < R; r++) {
                absU[a][r] = abs(U[a][r]);
                absU[a][r].setName("absU_"+a+"_"+r);
            }
        }
        for (int b = 0; b < B; b++) {
            for (int r = 0; r < R; r++) {
                absV[b][r] = abs(V[b][r]);
                absV[b][r].setName("absV_"+b+"_"+r);
            }
        }
        for (int c = 0; c < C; c++) {
            for (int r = 0; r < R; r++) {
                absW[c][r] = abs(W[c][r]);
                absW[c][r].setName("absW_"+c+"_"+r);
            }
        }
        //#endregion


        baseModelConstraints(cp,U,V,W,fmm);


        // permutationSymmetryConstraints(cp,U,V,W); // Eliminates valid solutions


        signSymmetryConstraints(cp,U,V,W);


        validInequalityConstraints(cp,absU,absV,absW, W,fmm);


        // sparseMatrixConstraints(cp,absU,absV,absW,fmm); // Eliminates valid solutions


        // bind1x2x3Solution(U,V,W);
        // cp.propagateSolver();
        // findAndPrintTensors(U,V,W);
        // printMatrix(U, A, R);
        // printMatrix(V, B, R);
        // printMatrix(W, C, R);


        //#region Flatten Variables
        IntVar[] searchIntVars = new IntVar[
            A * R +
            B * R +
            C * R
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
        cp.setMode(PropaMode.SBP);
        // DFSearch dfs = makeDfs(cp, maxMarginalRandomTieBreak(searchIntVars));
        // DFSearch dfs = makeDfs(cp, minEntropyBiasedWheelSelectVal(searchIntVars));
        // DFSearch dfs = makeDfs(cp, minEntropyRandomTieBreak(searchIntVars));
        DFSearch dfs = makeDfs(cp, domWdeg(searchIntVars));
        // DFSearch dfs = makeDfs(cp, randomVarRandomVal(searchIntVars));
        dfs.onSolution(() -> {
            String message = "New Solution\n";
            message += matrixStringBuilder(U, A, R);
            message += matrixStringBuilder(V, B, R);
            message += matrixStringBuilder(W, C, R);
            System.out.println(message);
        });
        // dfs.onFailure(() -> {
        //     failures++;
        //     if (failures % 5000 == 0)
        //         System.out.println(String.format("%d", failures));
        // });
        SearchStatistics stats = new SearchStatistics();
        stats = dfs.solveRestarts(s -> {
            return s.numberOfSolutions() == 1 || s.timeElapsed() >= 7200000;
        }, limit, 1.5);

        // long nbFinishedThreads = threadStats.addAndGet(0, 1);
        // long avgTime = threadStats.addAndGet(1, stats.timeElapsed()) / nbFinishedThreads;
        // long avgFailures = threadStats.addAndGet(2, stats.numberOfFailures()) / nbFinishedThreads;
        // long avgNodes = threadStats.addAndGet(3, stats.numberOfNodes()) / nbFinishedThreads;
        // if (stats.timeElapsed() < threadStats.get(4)) threadStats.set(4, stats.timeElapsed());
        // if (stats.timeElapsed() > threadStats.get(5)) threadStats.set(5, stats.timeElapsed());
        // System.out.println(String.format("Average time after %d threads: %d", nbFinishedThreads, avgTime));
        // System.out.println(String.format("Average failures after %d threads: %d", nbFinishedThreads, avgFailures));
        // System.out.println(String.format("Average nodes after %d threads: %d", nbFinishedThreads, avgNodes));
        // System.out.println(String.format("Min time after %d threads: %d", nbFinishedThreads, threadStats.get(4)));
        // System.out.println(String.format("Max time after %d threads: %d", nbFinishedThreads, threadStats.get(5)));
        //#endregion
    }

    private static void baseModelConstraints(Solver cp, IntVar[][] U, IntVar[][] V, IntVar[][] W, FMM fmm) {
        int A = U.length;
        int B = V.length;
        int C = W.length;
        int R = U[0].length;

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
    }

    private static void permutationSymmetryConstraints(Solver cp, IntVar[][] U, IntVar[][] V, IntVar[][] W) {
        int A = U.length;
        int B = V.length;
        int C = W.length;
        int R = U[0].length;

        IntVar[] previousAugmentedColumn = new IntVar[A + B];
        IntVar[] augmentedColumn = new IntVar[A + B];
        
        // Initialize the previous augmented column
        for (int a = 0; a < A; a++) {
            previousAugmentedColumn[a] = U[a][0];
        }
        for (int b = 0; b < B; b++) {
            previousAugmentedColumn[A + b] = V[b][0];
        }

        // Apply a lexicographic-strict constraint to break permutations
        for (int r = 1; r < R; r++) {
            for (int a = 0; a < A; a++) {
                augmentedColumn[a] = U[a][r];
            }
            for (int b = 0; b < B; b++) {
                augmentedColumn[A + b] = V[b][r];
            }

            cp.post(lexLess(previousAugmentedColumn, augmentedColumn));

            // BoolVar[] isLessVar = new BoolVar[A + B];
            // for (int i = 0; i < A + B; i++) {
            //     isLessVar[i] = isLess(previousAugmentedColumn[i], augmentedColumn[i]);
            // }
            // cp.post(or(isLessVar));
            previousAugmentedColumn = augmentedColumn;
        }
    }

    private static void signSymmetryConstraints(Solver cp, IntVar[][] U, IntVar[][] V, IntVar[][] W) {
        int A = U.length;
        int B = V.length;
        int C = W.length;
        int R = U[0].length;


        //#region Sign Symmetry using sum of absolute - OBSOLETE
        // IntVar zeroVar = makeIntVar(cp, 0, 0);
        // zeroVar.setName("Zero Variable");
        // for (int r = 0; r < R; r++) {
        //     cp.post(lessOrEqual(U[0][r], zeroVar));
        //     for (int a = 1; a < A; a++) {
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
            cp.post(table(columnU, signTable));
        }
        
        if (C != A) {
            signTable = getIteratingValuesArray(C);
        }
        IntVar[] columnW = new IntVar[C];
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                columnW[c] = W[c][r];
            }
            cp.post(table(columnW, signTable));
        }
        //#endregion
    }

    private static void validInequalityConstraints(
        Solver cp, 
        IntVar[][] absU, 
        IntVar[][] absV, 
        IntVar[][] absW,
        IntVar[][] W,
        FMM fmm
    ) {
        int A = absU.length;
        int B = absV.length;
        int C = absW.length;
        int R = absU[0].length;

        IntVar oneValueVar = makeIntVar(cp, 1, 1);
        // Absolute sum of each column of W must be at least 1
        IntVar[] absColW = new IntVar[C];
        for (int r = 0; r < R; r++) {
            for (int c = 0; c < C; c++) {
                absColW[c] = absW[c][r];
            }
            cp.post(lessOrEqual(oneValueVar, sum(absColW))); // Causing issues
        }

        // Absolute sum of each row of W must be at least m
        for (int c = 0; c < C; c++) {
            cp.post(lessOrEqual(makeIntVar(cp, fmm.getM(), fmm.getM()), sum(absW[c])));
        }

        // c terms must differ in at least two m terms
        // Slows down solutions a lot
        // IntVar[] absDifference = new IntVar[R];
        // for (int c = 0; c < C; c++) {
        //     for (int i = c + 1; i < C; i++) {
        //         for (int r = 0; r < R; r++) {
        //             absDifference[r] = abs(sum(W[c][r], mul(W[i][r], -1)));
        //         }
        //         cp.post(lessOrEqual(makeIntVar(cp, 2, 2), sum(absDifference)));
        //     }
        // }

        // Each row of U must have at least one non-zero term
        for (int a = 0; a < A; a++) {
            cp.post(lessOrEqual(oneValueVar, sum(absU[a])));
        }

        // Each row of V must have at least one non-zero term
        for (int b = 0; b < B; b++) {
            cp.post(lessOrEqual(oneValueVar, sum(absV[b])));
        }

        // Last one
    }

    private static void sparseMatrixConstraints(
        Solver cp, 
        IntVar[][] absU, 
        IntVar[][] absV, 
        IntVar[][] absW,
        FMM fmm
    ) {
        int A = absU.length;
        int B = absV.length;
        int C = absW.length;
        int R = absU[0].length;
        int K1 = fmm.getK1();
        int K2 = fmm.getK2();

        IntVar[] absAugmentedColumn = new IntVar[A + B];
        for (int r = 0; r < R; r++) {
            for (int a = 0; a < A; a++) {
                absAugmentedColumn[a] = absU[a][r];
            }
            for (int b = 0; b < B; b++) {
                absAugmentedColumn[A+b] = absV[b][r];
            }
            cp.post(lessOrEqual(sum(absAugmentedColumn), makeIntVar(cp, K1, K1)));
        }

        for (int c = 0; c < C; c++) {
            cp.post(lessOrEqual(sum(absW[c]), makeIntVar(cp, K2, K2)));
        }
    }

    private static void bind1x2x3Solution(IntVar[][] U, IntVar[][] V, IntVar[][] W) {
        int A = U.length;
        int B = V.length;
        int C = W.length;
        int R = U[0].length;
        
        // UV augmented matrix
        U[0][0].assign(-1);
        V[2][0].assign(-1);

        U[0][1].assign(-1);
        V[1][1].assign(1);
        V[4][1].assign(1);

        U[0][2].assign(-1);
        V[0][2].assign(1);
        V[2][2].assign(1);
        V[3][2].assign(1);

        U[0][3].assign(-1);
        U[1][3].assign(1);
        V[3][3].assign(-1);

        U[0][4].assign(-1);
        U[1][4].assign(1);
        V[4][4].assign(-1);

        U[1][5].assign(-1);
        V[5][5].assign(1);

        // W matrix
        W[0][0].assign(-1);
        W[2][0].assign(1);

        W[1][1].assign(-1);

        W[0][2].assign(-1);

        W[0][3].assign(-1);

        W[1][4].assign(-1);

        W[2][5].assign(-1);

        //Set all others to 0
        for (int a = 0; a < A; a++) {
            for (int r = 0; r < R; r++) {
                if (!U[a][r].isBound()) {
                    U[a][r].assign(0);
                }
            }
        }
        for (int b = 0; b < B; b++) {
            for (int r = 0; r < R; r++) {
                if (!V[b][r].isBound()) {
                    V[b][r].assign(0);
                }
            }
        }
        for (int c = 0; c < C; c++) {
            for (int r = 0; r < R; r++) {
                if (!W[c][r].isBound()) {
                    W[c][r].assign(0);
                }
            }
        }
    }

    private static void bind2x2x2Solution(IntVar[][] U, IntVar[][] V, IntVar[][] W) {
        int A = U.length;
        int B = V.length;
        int C = W.length;
        int R = U[0].length;
        
        // UV augmented matrix
        U[0][0].assign(-1);
        U[1][0].assign(-1);
        V[3][0].assign(-1);

        U[0][1].assign(-1);
        U[3][1].assign(-1);
        V[0][1].assign(1);
        V[3][1].assign(1);

        U[0][2].assign(-1);
        V[1][2].assign(1);
        V[3][2].assign(-1);

        U[0][3].assign(-1);
        U[2][3].assign(1);
        V[0][3].assign(-1);
        V[1][3].assign(-1);

        U[1][4].assign(-1);
        U[3][4].assign(1);
        V[2][4].assign(1);
        V[3][4].assign(1);

        U[2][5].assign(-1);
        U[3][5].assign(-1);
        V[0][5].assign(1);

        U[3][6].assign(-1);
        V[0][6].assign(-1);
        V[2][6].assign(1);

        // W matrix
        W[0][0].assign(-1);
        W[1][0].assign(1);

        W[0][1].assign(-1);
        W[3][1].assign(-1);

        W[1][2].assign(-1);
        W[3][2].assign(-1);

        W[3][3].assign(-1);

        W[0][4].assign(-1);

        W[2][5].assign(-1);
        W[3][5].assign(1);

        W[0][6].assign(-1);
        W[2][6].assign(-1);

        //Set all others to 0
        for (int a = 0; a < A; a++) {
            for (int r = 0; r < R; r++) {
                if (!U[a][r].isBound()) {
                    U[a][r].assign(0);
                }
            }
        }
        for (int b = 0; b < B; b++) {
            for (int r = 0; r < R; r++) {
                if (!V[b][r].isBound()) {
                    V[b][r].assign(0);
                }
            }
        }
        for (int c = 0; c < A; c++) {
            for (int r = 0; r < R; r++) {
                if (!W[c][r].isBound()) {
                    W[c][r].assign(0);
                }
            }
        }
    }

    private static void findAndPrintTensors(IntVar[][] U, IntVar[][] V, IntVar[][] W) {
        int A = U.length;
        int B = V.length;
        int C = W.length;
        int R = U[0].length;
        int sum;

        for (int c = 0; c < C; c++) {
            for (int a = 0; a < A; a++) {
                for (int b = 0; b < B; b++) {
                    sum = 0;
                    for (int r = 0; r < R; r++) {
                        sum += U[a][r].max() * V[b][r].max() * W[c][r].max();
                    }
                    if (sum >= 0) System.out.print(" ");
                    System.out.print(sum + " ");
                }
                System.out.print("\n");
            }
            System.out.print("\n");
        }
    }

    private static void printMatrix(IntVar[][] M, int rows, int columns) {
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

    private static String matrixStringBuilder(IntVar[][] M, int rows, int columns) {
        String message = "";
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                int val = M[i][j].max();
                if (val >= 0) message += ' ';
                message += val;
                message += ' ';
            }
            message += '\n';
        }
        return message + '\n';
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