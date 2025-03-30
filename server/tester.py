import random
import time
import torch
from math import log, exp, sqrt
from sample_creator import tokenize
from transformers import GPT2TokenizerFast, GPT2LMHeadModel#, pipeline

from rdkit.Chem.rdchem import Mol as Mol
from rdkit.Chem import AllChem as Chem
from rdkit.Chem.Descriptors import MolWt, ExactMolWt

TOKENS = {
    'F',
    'Cl',
    'Br',
    'I',
    'O',
    'N',
    'S',
    'C',
    '[',
    ']',
    '-',
    '+',
    '@',
    'H',
    'H3',
    '1',
    '2',
    '3',
    '4',
    '5',
    '6',
    '7',
    '8',
    '=',
    '/',
    '\\',
    '(',
    ')',
    '#',
    'o',
    'n',
    's',
    'c',
    '</s>'
}

tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')

def server_test():
    import requests
    import random

    URL = 'http://localhost:5000/token'

    molecules = []
    for i in range(10):
        if i % 1000 == 0:
            print(i)
        molecule = "<s>"
        while not molecule.endswith('</s>'):
            response = requests.post(URL, data=molecule)
            # print(response.text)
            values = [iter.split(':') for iter in response.text[1:-2].replace('\"','').split(',')]
            # print(values)
            values = {iter[0]:float(iter[1]) for iter in values}
            values = sorted(values.items(), key=lambda x: x[1], reverse=True)[:5]
            # molecule += random.choice(values)[0]
            random.shuffle(values)
            roulette_value = random.random()
            for k,v in values:
                roulette_value -= v
                if roulette_value < 0:
                    molecule += k
                    break
            # molecule += values[0][0]
            print(molecule)
        molecules.append(molecule + '\n')

    with open("generated_molecules.txt", 'w') as f:
        f.writelines(molecules)

def model_test():
    import torch
    from transformers import GPT2TokenizerFast, GPT2LMHeadModel#, pipeline

    tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
    model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')
    
    current_mol = "<s>"
    while not current_mol.endswith("</s"):
        print(current_mol)
        inputs = torch.tensor([tokenizer(current_mol)['input_ids']])
        # print(inputs)
        outputs = model.generate(
            inputs,
            do_sample=True,
            max_length=40,
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
            if token not in real_token_prob.keys():
                real_token_prob[token] = 0
            real_token_prob[token] += v
        
        summed_prob = sum(real_token_prob.values())
        for k,v in real_token_prob.items():
            real_token_prob[k] = v/summed_prob
        
        possible_tokens = list(real_token_prob.keys())
        random.shuffle(possible_tokens)
        
        chosen_prob = random.random()
        for k in possible_tokens:
            chosen_prob -= real_token_prob[k]
            if chosen_prob < 0:
                current_mol += k
                break
    
    return current_mol

def model_test_2():
    import torch
    from transformers import GPT2TokenizerFast, GPT2LMHeadModel#, pipeline

    tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=30)
    model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')
    
    current_mol = "<s>C[C@H]1CC"
    # inputs = torch.tensor([tokenizer(current_mol)['input_ids']])
    inputs = torch.tensor([[tokenizer.bos_token_id]])
    gen = model.generate(
        inputs,
        do_sample=True, 
        max_length=30,
        temperature=0.5,
        early_stopping=True,
        pad_token_id=tokenizer.pad_token_id,
        num_return_sequences=32
    )
    smiles = tokenizer.batch_decode(gen, skip_special_tokens=True)
    print("#" * 40)
    for iter in smiles:
        print(iter)

def model_1000_run():
    import torch
    from transformers import GPT2TokenizerFast, GPT2LMHeadModel

    tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=35)
    model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')
    
    # inputs = torch.tensor([tokenizer(current_mol)['input_ids']])
    inputs = torch.tensor([[tokenizer.bos_token_id]])
    gen = model.generate(
        inputs,
        do_sample=True,
        max_length=35,
        temperature=1.5,
        early_stopping=True,
        pad_token_id=tokenizer.pad_token_id,
        num_return_sequences=1024
    )
    smiles = tokenizer.batch_decode(gen, skip_special_tokens=True)
    
    # for iter in smiles:
    #     print(iter)
    
    smiles_text = '\n'.join(smiles)
    with open("solo_nn.txt", 'w') as f:
        f.write(smiles_text)

def evaluate_molecules():
    with open("solo_nn.txt", 'r') as f:
        lines = f.readlines()
    
    molecule_weights = []
    failed = 0
    for i,mol in enumerate(lines):
        if i % 10 == 0:
            print(f"Failed {failed}/{i}")
        try:
            print(mol.rstrip('\n'))
            molecule = Chem.MolFromSmiles(mol.rstrip('\n'))
            value = ExactMolWt(molecule)
            molecule_weights.append(f"{value}\n")
        except Exception as e:
            failed += 1
    
    with open("solo_nn_weights.txt", 'w') as f:
        f.writelines(molecule_weights)

def evaluate_weights():
    with open("solo_nn_weights.txt", 'r') as f:
        lines = f.readlines()
    lines = [float(iter.rstrip('\n')) for iter in lines]

    weights = range(175, 526, 25)
    weight_distribution = {}
    for w in weights:
        weight_distribution[w] = 0
    
    for mol in lines:
        for w in weights:
            if mol < w:
                weight_distribution[w] += 1
                break
    
    weight_percentages = {k:(v/len(lines))*100 for k,v in weight_distribution.items()}
    for k,v in weight_percentages.items():
        print(f"{k}: {v}")

def get_next_token_odds(current_mol):
    # Get probabilities of 512 most likely chains
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
    # Exp to cancel log
    transition_scores = torch.exp(model.compute_transition_scores(outputs.sequences, outputs.scores, normalize_logits=True)).flatten().tolist()
    # Decode tokens
    generated_tokens = [tokenizer.decode(token) for token in outputs.sequences[:, -1:].flatten()]
    # Link decoded tokens to their scores
    big_token_prob = {generated_tokens[i]:transition_scores[i] for i in range(len(generated_tokens))}
    real_token_prob = {}

    # Transform into our tokens
    for k,v in big_token_prob.items():
        token = tokenize(k)[0]
        if token.startswith('</s'):
            token = '_'
        if token not in real_token_prob.keys():
            real_token_prob[token] = 0
        real_token_prob[token] += v
    
    # Normalizes
    summed_prob = sum(real_token_prob.values())
    for k,v in real_token_prob.items():
        real_token_prob[k] = v/summed_prob
    
    # Smooths the distribution
    min_value = min(real_token_prob.values())
    for token in TOKENS:
        if token not in real_token_prob.keys():
            real_token_prob[token] = min_value/10
    
    # Normalizes again
    summed_prob = sum(real_token_prob.values())
    for k,v in real_token_prob.items():
        real_token_prob[k] = v/summed_prob
    
    return real_token_prob

def one_shot_stats():
    NUM_SEQUENCES = 100
    
    inputs = torch.tensor([[tokenizer.bos_token_id]])
    
    start = time.time()
    gen = model.generate(
        inputs,
        do_sample=True, 
        max_length=30,
        temperature=0.5,
        early_stopping=True,
        pad_token_id=tokenizer.pad_token_id,
        num_return_sequences=NUM_SEQUENCES
    )
    run_time = time.time() - start
    print(f"Total time taken {run_time}, time per molecule: {run_time/NUM_SEQUENCES}")
    smiles: list[str] = tokenizer.batch_decode(gen, skip_special_tokens=True)
    
    smiles = [iter.lstrip('<s>').rstrip('\n').rstrip('</s>') for iter in smiles]
    
    # PPL
    initial_ppl = sum([better_perplexity(f"<s>{iter}</s>") for iter in smiles])/len(smiles)
    print(f"Perplexity of all generated molecules is: {initial_ppl}")
    
    # Verify 
    molecule_weights = {}
    valid_mols = []
    for mol in smiles:
        molecule = Chem.MolFromSmiles(mol.rstrip('\n'))
        if molecule is None:
            continue
        molecule_weights[mol] = ExactMolWt(molecule)
        valid_mols.append(mol)
    print(f"Parsed molecules, {len(valid_mols)} are valid")
    print(f"Time taken to generate was: {run_time}")
    
    valid_ppl = sum([better_perplexity(f"<s>{iter}</s>") for iter in valid_mols])/len(valid_mols)
    print(f"Perplexity of valid molecules is: {valid_ppl}")
    
    with open("./results.txt", 'w') as f:
        for mol in valid_mols:
            f.write(f"{mol},{molecule_weights[mol]},{better_perplexity('<s>' + mol + '</s>')},{run_time/NUM_SEQUENCES}\n")
    
    final_mols = [k for k,v in molecule_weights.items() if v >= 200 and v <= 275]
    weighted_ppl = sum([better_perplexity(f"<s>{iter}</s>") for iter in final_mols])/len(final_mols)
    print(f"Perplexity of weighted molecules is: {weighted_ppl}")

    print(f"Final results\n\tTime: {run_time}\n\tSuccess rate: {len(final_mols)/NUM_SEQUENCES}\n\tInitial PPL: {initial_ppl}\n\tValid PPL: {valid_ppl}\n\tWeighted PPL: {weighted_ppl}")

def new_method_get_token(molecule):
    # Encode molecule
    inputs = tokenizer.encode(molecule, return_tensors="pt")

    # Get probabilities for next tokens
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    
    gpt_probabilities = torch.nn.functional.softmax(predictions[0,-1,:], dim=-1).tolist()
    gpt_tokens: dict[str,float] = {tokenizer.decode([idx]).strip():gpt_probabilities[idx] for idx in range(len(gpt_probabilities))}
    
    probs = {iter:0 for iter in TOKENS}
    for g_t in gpt_tokens.keys():
        split_ngram = tokenize(g_t)
        if len(split_ngram) == 0: # Skip empty strings
            continue
        t = split_ngram[0] # Get the first token from the ngram
        if t not in TOKENS: # Skip if the token is not in our grammar
            continue
        probs[t] += gpt_tokens[g_t]
    summed_values = sum(probs.values())
    probs = {k:v/summed_values for k,v in probs.items()}
    probs['_'] = probs.pop('</s>') # replace end token by our padding
    return probs

def get_new_stats():
    NUM_RUNS = 100
    MOL_LENGTH = 40
    TOP_K = -1
    
    time_values = []
    ppl_values = []
    molecules = []
    for _ in range(NUM_RUNS):
        start = time.time()
        tokens  = ["<s>"]
        molecule = "<s>"
        
        while len(tokens) < MOL_LENGTH + 1:
            probs = new_method_get_token(molecule)
        
            best_options = [k for k,v in sorted(probs.items(), reverse=True, key=lambda x: x[1])]
            best_options = best_options[:TOP_K]
            probs = {k:v for k,v in probs.items() if k in best_options}
            
            new_total = sum(probs.values())
            for k,v in probs.items():
                probs[k] = v/new_total
        
            max_value = max(probs.values())
            options = list(probs.keys())
            chosen_token = random.choice(options)
            while random.random() > probs[chosen_token]/max_value:
                chosen_token = random.choice(options)

            if chosen_token == "_":
                break
            
            tokens.append(chosen_token)
            molecule = ''.join(tokens)
        
        molecule = ''.join(tokens[:]).lstrip('<s>').rstrip('</s>')
        molecules.append(molecule)
        mol_time = time.time() - start
        time_values.append(mol_time)
        ppl_values.append(better_perplexity(f"<s>{molecule}</s>"))
        print(f"time: {mol_time}\nreal perplexity: {ppl_values[-1]}\n{molecule}\n")
    
    avg_time = sum(time_values)/NUM_RUNS
    avg_perplexity = sum(ppl_values)/NUM_RUNS
    if NUM_RUNS % 2 == 0:
        ppl_values = sorted(ppl_values)
        half = NUM_RUNS//2
        median_perplexity = (ppl_values[half] + ppl_values[half + 1])/2
    else:
        median_perplexity = sorted(ppl_values)[NUM_RUNS//2]
    
    std_dev = sqrt(sum((iter - avg_perplexity) ** 2 for iter in ppl_values)/len(ppl_values))

    print("##############################################")
    print(f"top-k: {TOP_K}\ntime: {avg_time}\navg ppl: {avg_perplexity}\nmedian ppl: {median_perplexity}\nstd_dev ppl: {std_dev}")
    molecules = [f"{iter}\n" for iter in molecules]

    with open("results_raw.txt", 'w') as f:
        for i in range(len(molecules)):
            f.write(f"{molecules[i]},{0},{ppl_values[i]},{time_values[i]}\n")

def get_validity():
    with open("results_raw.txt", 'r') as f:
        molecules = f.readlines()
    molecules = [iter.rstrip('\n') for iter in molecules]
    
    molecule_weights = []
    successful_molecules = []
    failed = 0
    for line in molecules:
        split_line = line.split(',')
        mol = split_line[0]
        
        try:
            print(mol)
            molecule = Chem.MolFromSmiles(mol)
            value = ExactMolWt(molecule)
            molecule_weights.append(float(value))
            successful_molecules.append(f"{mol},{float(value)},{split_line[2]},{split_line[3]}\n")
        except Exception as e:
            failed += 1
    
    successful_molecules = [line for i,line in enumerate(successful_molecules) if molecule_weights[i] >= 200 and molecule_weights[i] <= 275]
    
    print("success rate ", len(successful_molecules)/len(molecules))
    
    with open("results.txt", 'w') as f:
        for line in successful_molecules:
            f.write(line)

# perplexity: 1.4747548016674537
# C[C@H]1C[C@H]1C(O)C1(C)C/C(N)C(C)C
def better_perplexity(molecule):
    tokenize_input = tokenizer.tokenize(molecule)
    tensor_input = torch.tensor([tokenizer.convert_tokens_to_ids(tokenize_input)])
    out = model(tensor_input, labels=tensor_input)
    return exp(out.loss.item())

# get_new_stats()
# get_validity()
one_shot_stats()
# model_1000_run()
# evaluate_molecules()
# evaluate_weights()