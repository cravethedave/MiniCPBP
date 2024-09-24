package minicpbp.examples.molecules;

import minicpbp.engine.core.IntVar;
import minicpbp.engine.core.Solver;
import minicpbp.engine.core.Solver.PropaMode;
import minicpbp.search.DFSearch;
import minicpbp.search.LDSearch;
import minicpbp.search.SearchStatistics;
import minicpbp.util.CFG;
import minicpbp.util.Procedure;
import minicpbp.util.exception.InconsistencyException;

import static minicpbp.cp.BranchingScheme.*;
import static minicpbp.cp.Factory.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Scanner;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse.BodyHandlers;

public class GrammarComparator {
    public static void main(String[] args) {
        grammarComparator(
            "data/moleculeCNF_v6.txt",
            "data/moleculeCNF_v7.txt",
            "big_data/ZINC250k.txt",
            0,
            3000
        );
    }

    private static void grammarComparator(
        String oldGrammarPath,
        String newGrammarPath,
        String dataSetPath,
        int startLine,
        int stopLine
    ) {
        int line = 0;
        BufferedReader reader;
        CFG g1;
        CFG g2;
        Solver cp1;
        Solver cp2;

        //#region Initialization
        try {
            reader = new BufferedReader(new FileReader(dataSetPath));
        
            if (!reader.ready()) {
                reader.close();
                throw new IOException();
            }

            g1 = new CFG(oldGrammarPath);
            cp1 = makeSolver(false);

            g2 = new CFG(oldGrammarPath);
            cp2 = makeSolver(false);

        } catch (Exception e) {
            System.out.println("Failed on initialization");
            System.out.println(e);
            return;
        }

        Vector<String> falseNegative = new Vector<>();
        Vector<String> falsePositive = new Vector<>();
        Vector<String> failedLines = new Vector<>();
        //#endregion

        //#region Move to start
        try {
            while(reader.ready() && line < startLine) {
                reader.readLine();
                line++;
            }
        } catch (Exception e) {
            System.out.println("Failed while going to start point");
            System.out.println(e);
        }
        //#endregion

        try {
            line++;
            Vector<String> tokens = new Vector<>();
            while (reader.ready()) {
                String moleculeString = reader.readLine();
                char[] molecule = moleculeString.toCharArray();
                tokens.clear();
                tokens = Tokenizers.tokenizeV7(moleculeString);

                IntVar[] w1 = makeIntVarArray(cp1, tokens.size(), 0, g1.terminalCount()-1);
                IntVar[] w2 = makeIntVarArray(cp2, tokens.size(), 0, g2.terminalCount()-1);

                int i1 = 0;
                boolean skip = false;
                try {
                    while (i1 < tokens.size()) {
                        Integer token = g1.tokenEncoder.get(tokens.get(i1));
                        w1[i1].assign(token);
                        i1++;
                    }
                } catch (Exception e) {
                    failedLines.add(String.format("Grammar 1: %s", moleculeString));
                    skip = true;
                }

                int i2 = 0;
                try {
                    while (i2 < tokens.size()) {
                        Integer token = g2.tokenEncoder.get(tokens.get(i2));
                        w2[i2].assign(token);
                        i2++;
                    }
                } catch (Exception e) {
                    failedLines.add(String.format("Grammar 2: %s", moleculeString));
                    skip = true;
                }

                if (!skip) {
                    Boolean valid1 = true;
                    Boolean valid2 = true;
                    try {
                        cp1.post(grammar(w1, g1));
                    } catch (Exception e) {
                        valid1 = false;
                    }
                    try {
                        cp2.post(grammar(w2, g2));
                    } catch (Exception e) {
                        valid2 = false;
                    }
                    
                    // Add to lists for output
                    if (valid1 && !valid2) {
                        falseNegative.add(moleculeString);
                    } else if (!valid1 && valid2) {
                        falsePositive.add(moleculeString);
                    }
                }

                // System.out.println(String.format("%d %b %b", line, valid1, valid2));
                if (line % 100 == 0) {
                    System.out.println(line);
                }
                if (line % 1000 == 0) {
                    if (falseNegative.size() > 0 || falsePositive.size() > 0 || failedLines.size() > 0) {
                        try {
                            System.out.println("Writing");
                            FileWriter fstream = new FileWriter(String.format("verifications/out_%dk.txt", new Integer(line/1000)));
                            BufferedWriter out = new BufferedWriter(fstream);
                            out.write("********** FALSE NEGATIVES **********\n");
                            for (String m : falseNegative) {
                                out.write(m + "\n");
                            }
                            out.write("********** FALSE POSITIVES **********\n");
                            for (String m : falsePositive) {
                                out.write(m + "\n");
                            }
                            out.write("********** FAILED LINES **********\n");
                            for (String m : failedLines) {
                                out.write(m + "\n");
                            }
                            out.close();
                            falseNegative.clear();
                            falsePositive.clear();
                            failedLines.clear();
                        } catch (Exception e) {
                            System.out.println(String.format("Failed during writing"));
                            System.out.println(e);
                        }
                    } else {
                        System.out.println("No errors");
                    }
                    if (line == stopLine) {
                        reader.close();
                        return;
                    }
                }
                line++;
            }
            
            reader.close();
        } catch (Exception e) {
            System.out.println(String.format("Failed at line %d", line));
            System.out.println(e);
        }
    }
}
