import torch
import random
import time

from math import log, exp
from sample_creator import tokenize
from threading import Thread
from transformers import GPT2TokenizerFast, GPT2LMHeadModel

from rdkit.Chem import AllChem as Chem
from rdkit.Chem.rdchem import Mol as Mol
from rdkit.Chem.Descriptors import ExactMolWt

tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')

def next_token(current_mol) -> dict[str,float]:
    # print("##################### New Request #####################")
    inputs = torch.tensor([tokenizer(current_mol)['input_ids']])
    outputs = model.generate(
        inputs,
        do_sample=True,
        # max_length=40,
        temperature=1.5,
        max_new_tokens=1,
        # early_stopping=True,
        return_dict_in_generate=True,
        output_scores=True,
        pad_token_id=tokenizer.pad_token_id,
        num_return_sequences=512
    )
    transition_scores = torch.exp(model.compute_transition_scores(outputs.sequences, outputs.scores, normalize_logits=True)).flatten().tolist()
    generated_tokens = [tokenizer.decode(token) for token in outputs.sequences[:, -1:].flatten()]
    big_token_prob = {generated_tokens[i]:transition_scores[i] for i in range(len(generated_tokens))}
    real_token_prob = {}

    for k,v in big_token_prob.items():
        token = tokenize(k)[0]
        # if token.isdigit():
        #     if token not in real_token_prob.keys():
        #         for i in range(1,9):
        #             real_token_prob[str(i)] = 0
        #     for i in range(1,9):
        #         real_token_prob[str(i)] += v
        #     continue
        if token.startswith('</s'):
            token = '_'
        if token not in real_token_prob.keys():
            real_token_prob[token] = 0
        real_token_prob[token] += v
    
    summed_prob = sum(real_token_prob.values())
    for k,v in real_token_prob.items():
        real_token_prob[k] = v/summed_prob
    
    return real_token_prob

def run_model_n_times(num_mols, molecule_weights):
    for _ in range(num_mols):
        start = time.time()
        perplexity_results = ""
        log_sum_probs = 0
        current_mol = "<s>"
        real_length = 40
        for i in range(40):
            # Fills the molecule with padding
            if current_mol.endswith('_'):
                real_length = i
                for _ in range(i,40):
                    current_mol += '_'
                break
            
            # Get next token
            probabilities = next_token(current_mol)
            available_tokens = list(probabilities.keys())
            max_prob = max(probabilities.values())
            
            # Biased wheel
            chosen = random.choice(available_tokens)
            while random.random() > probabilities[chosen]/max_prob:
                chosen = random.choice(available_tokens)
            
            current_mol += chosen
            print(current_mol)
            perplexity_results += str(probabilities[chosen]) + ","
            log_sum_probs += log(probabilities[chosen])
            
        run_time = time.time() - start
        
        mol_weight = 0
        # try:
        #     mol = Chem.MolFromSmiles(current_mol.lstrip('<s>').rstrip('_'))
        #     mol_weight = ExactMolWt(mol)
        # except Exception as _:
        #     print("Molecule was invalid")
        #     continue
        perplexity_score = exp(-log_sum_probs / real_length)
        
        # Only adds working molecules
        # if mol_weight < molecule_weights[0] or mol_weight > molecule_weights[1]:
        #     print(mol_weight)
        #     continue
        
        with open("./perplexity.txt", 'a') as f:
            f.write(f"{perplexity_results}\n")
        with open("./run_results.txt", 'a') as f:
            f.write(f"{current_mol},{mol_weight},{perplexity_score},{run_time}\n")

def parallel_test_model(num_runs, num_mol_per_run, molecule_weights):
    with open("./perplexity.txt", 'w') as f:
        f.write(f"molecule count: {num_runs * num_mol_per_run}, target weight: {molecule_weights}\n")
    with open("./run_results.txt", 'w') as f:
        f.write(f"molecule count: {num_runs * num_mol_per_run}, target weight: {molecule_weights}\n")
        f.write("molecule, weight, perplexity, time (s)\n")
    
    threads: list[Thread] = []
    for i in range(num_runs):
        threads.append(Thread(target=run_model_n_times, args=(num_mol_per_run, molecule_weights, )))
        threads[-1].start()
    
    print("threads started")
    
    for i in range(num_runs):
        threads[i].join()

start = time.time()
parallel_test_model(1,10,[2000,2750])
print(f"took {time.time()-start}s")