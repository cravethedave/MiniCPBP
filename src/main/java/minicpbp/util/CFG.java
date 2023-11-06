/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * mini-cpbp, replacing classic propagation by belief propagation 
 * Copyright (c)  2019. by Gilles Pesant
 */

package minicpbp.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import minicpbp.util.io.InputReader;

/**
 * Context-Free Grammar for Grammar constraint
 * (code adapted from that of Claude-Guy Quimper)
 *
 * Grammar is read in the following order: terminalCount, nonTerminalCount, productionCount, ...
 * ... for each production: left, length, right symbols
 */
// TODO: Consider splitting productions into length-1 and length-2

public class CFG {

    private int productionCount;
    private Production[] productions;
    private int terminalCount; // Any integer c s.t. 0 <= c < terminalCount is a terminal
    private int nonTerminalCount; // Start symbol is terminalCount
	public HashMap<Integer, String> tokenDecoder;
    public HashMap<String, Integer> tokenEncoder;

    public CFG(String filePath) throws FileNotFoundException, IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        HashMap<String, Vector<String[]>> grammar = new HashMap<>();
        
        if (!reader.ready()) {
            throw new IOException();
        }
        
        // Sets up the starting symbol
        String line = reader.readLine();
        String[] production = line.split(" -> ");
        String left = production[0];
        String startSymbol = left;
        String[] rightTokens = production[1].split(" ");
        grammar.putIfAbsent(left, new Vector<>());
        grammar.get(left).add(rightTokens);

        // Reads until done
        while(reader.ready()) {
            line = reader.readLine();
            production = line.split(" -> ");
            left = production[0];
            rightTokens = production[1].split(" ");
            grammar.putIfAbsent(left, new Vector<>());
            grammar.get(left).add(rightTokens);
        }
        reader.close();

        // Creates the terminals and nonTerminals sets
        Set<String> nonTerminals = new HashSet<>();
        Set<String> terminals = new HashSet<>();
        productionCount = 0;
        for(String key : grammar.keySet()) {
            nonTerminals.add(key);
            for(String[] tokens : grammar.get(key)) {
                productionCount++;
                for (String token : tokens) {
                    (grammar.keySet().contains(token) ? nonTerminals : terminals).add(token);
                }
            }
        }

        // Creates the tokenEncoder and Decoder
        terminalCount = terminals.size();
        nonTerminalCount = nonTerminals.size();
        tokenEncoder = new HashMap<>();
        tokenDecoder = new HashMap<>();
        // Terminals go first, from 0 to terminals.size()
        for(String token : terminals) {
            tokenEncoder.put(token, tokenEncoder.size());
            tokenDecoder.put(tokenDecoder.size(), token);
        }
        // Puts the start symbol at the start of the nonTerminals
        tokenEncoder.put(startSymbol, tokenEncoder.size());
        tokenDecoder.put(tokenDecoder.size(), startSymbol);
        // Adds the rest of the nonTerminals
        for(String token : nonTerminals) {
            if (!tokenEncoder.containsKey(token)) {
                tokenEncoder.put(token, tokenEncoder.size());
                tokenDecoder.put(tokenDecoder.size(), token);
            }
        }

        // Encodes the productions so they use the numbers
        productions = new Production[productionCount];
        int processedProductions = 0;
        for(String key : grammar.keySet()) {
            for(String[] tokens : grammar.get(key)) {
                Production prod = new Production();
                prod.left = tokenEncoder.get(key);
                prod.length = tokens.length;
                prod.right = new int[prod.length];
                for (int i = 0; i < prod.length; i++) {
                    prod.right[i] = tokenEncoder.get(tokens[i]);
                }
                productions[processedProductions] = prod;
                processedProductions++;
            }
        }

        // Removes nonTerminals from Encoder
        for (String nonTerminal : nonTerminals) {
            tokenEncoder.remove(nonTerminal);
        }
    }

    public CFG(InputReader reader) {
        terminalCount = reader.getInt();
        nonTerminalCount = reader.getInt();
        productionCount = reader.getInt();
        productions = new Production[productionCount];
        for(int i=0; i<productionCount; i++) {
            productions[i] = new Production();
            productions[i].left = reader.getInt();
            productions[i].length = reader.getInt();
            productions[i].right = new int[productions[i].length];
            for (int j=0; j<productions[i].length; j++) {
                productions[i].right[j] = reader.getInt();
            }
        }
    }

    public int terminalCount() {
    return terminalCount;
    }
    public int nonTerminalCount() {
    return nonTerminalCount;
    }
    public int productionCount() {
    return productionCount;
    }
    public Production[] productions() { return productions; }
    
}


