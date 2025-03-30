import time
import torch
import random
import subprocess
import numpy as np
import matplotlib as mpl
import matplotlib.pyplot as plt
from server import tokenize
from threading import Thread
from math import floor, log, exp

# subprocess.run([
#     "mvn",
#     "clean",
#     "compile"
# ], stdout=None)

oracle_weights = [0.5, 1.0, 1.5]
molecule_weights = [(4750,5000),(3157,3445)] # 3301 +- 144
MOLECULE_LENGTH = 40

def fill_masked_zinc(num_mol, oracle_weight, molecule_weights, remove_percent):
    with open("./perplexity.txt", 'w') as perplexity:
        perplexity.write("")
    csv = open(f"./results_{oracle_weight}.csv", 'w')
    csv.write("molecule,weight,perplexity\n")
    print(f"Csv {oracle_weight} created")

    with open("big_data/small_ZINC.txt", 'r') as f:
        molecules = f.readlines()

    failed_molecules = 0
    successful_molecules = 0
    while successful_molecules < num_mol:
        original_mol = random.choice(molecules).replace('\n','').strip()
        chosen_mol = tokenize(original_mol)
        remove_tokens = floor(len(chosen_mol) * remove_percent)
        edge_index = (MOLECULE_LENGTH - remove_tokens)//2
        chosen_mol = ','.join(chosen_mol[:edge_index] + ["<mask>"] * (MOLECULE_LENGTH - edge_index * 2) + chosen_mol[-edge_index:])
        
        # if i % 10 == 0:
        #     print(f"Processed {i}")
        f = open(f"./test_{oracle_weight}.txt", "w")
        subprocess.call([
            "mvn",
            "exec:java",
            f"-Dexec.args=40 maxMarginal {molecule_weights[0]} {molecule_weights[1]} {chosen_mol}"
        ], stdout=f)
        f.close()
        
        f = open(f"./test_{oracle_weight}.txt", "r")
        lines: list[str] = [iter for iter in f.readlines() if not iter.startswith("[INFO]")]
        # print(lines)
        f.close()
        
        for line in lines:
            if line.startswith("[RESTART]"):
                break
            if line.startswith("=====UNSAT"):
                failed_molecules += 1
                print(f"Failed {failed_molecules} molecules out of {successful_molecules + failed_molecules} so far\n")
                # print(lines)
                break
            if not line.startswith("Final molecule"):
                continue
            # words = line.split(' ')
            successful_molecules += 1
            print(f"Processed molecule #{successful_molecules + failed_molecules}, {successful_molecules} successful")
            print(line)
            break
            # csv.write(f"{words[2]},{words[7]},{words[-1]}\n")

    # csv.close()
    subprocess.run(["rm", "-f", f"test_{oracle_weight}.txt"])

def run_all(molecule_weights, remove_percent):
    succ_file = open("big_data/working_molecules.txt", 'w')
    
    with open("big_data/small_ZINC.txt", 'r') as f:
        molecules = f.readlines()

    failed_molecules = 0
    successful_molecules = 0
    for mol in molecules:
        original_mol = mol.replace('\n','').strip()
        chosen_mol = tokenize(original_mol)
        remove_tokens = floor(len(chosen_mol) * remove_percent)
        edge_index = (MOLECULE_LENGTH - remove_tokens)//2
        chosen_mol = ','.join(chosen_mol[:edge_index] + ["<mask>"] * (MOLECULE_LENGTH - edge_index * 2) + chosen_mol[-edge_index:])
        
        # if i % 10 == 0:
        #     print(f"Processed {i}")
        f = open("./test_all.txt", "w")
        subprocess.call([
            "mvn",
            "exec:java",
            f"-Dexec.args=40 maxMarginal {molecule_weights[0]} {molecule_weights[1]} {chosen_mol}"
        ], stdout=f)
        f.close()
        
        f = open("./test_all.txt", "r")
        lines: list[str] = [iter for iter in f.readlines() if not iter.startswith("[INFO]")]
        # print(lines)
        f.close()
        
        for line in lines:
            if line.startswith("[RESTART]"):
                break
            if line.startswith("=====UNSAT"):
                failed_molecules += 1
                print(f"Failed {failed_molecules} molecules out of {successful_molecules + failed_molecules} so far\n")
                # print(lines)
                break
            if not line.startswith("Final molecule"):
                continue
            words = line.split(' ')
            succ_file.write(f"{original_mol}, {words[7]}, {words[-1]}")
            successful_molecules += 1
            print(f"Processed molecule #{successful_molecules + failed_molecules}, {successful_molecules} successful")
            print(line)
            break
            # csv.write(f"{words[2]},{words[7]},{words[-1]}\n")
    
    succ_file.close()
    # csv.close()
    subprocess.run(["rm", "-f", "./test_all.txt"])

def generate_entropy_nncpbp(num_mol, molecule_weights):
    for i in range(num_mol):
        print(f"###################### {i}th molecule ######################")
        subprocess.call([
            "mvn",
            "exec:java",
            f"-Dexec.args=40 minEntropyBiasedWheel {molecule_weights[0]} {molecule_weights[1]}"
        ],)

def parallel_entropy(num_runs, num_mol_per_run, molecule_weights):
    with open("./perplexity.txt", 'w') as f:
        f.write(f"molecule count: {num_runs * num_mol_per_run}, target weight: {molecule_weights}\n")
    with open("./results.txt", 'w') as f:
        f.write(f"molecule count: {num_runs * num_mol_per_run}, target weight: {molecule_weights}\n")
        f.write("molecule, weight, perplexity, time (s)\n")
    
    threads: list[Thread] = []
    for i in range(num_runs):
        threads.append(Thread(target=generate_entropy_nncpbp, args=(num_mol_per_run, molecule_weights, )))
        threads[-1].start()
    
    print("threads started")
    
    for i in range(num_runs):
        threads[i].join()

def median_value(values: list[float]) -> float:
    n = len(values)
    half_point = n//2
    sorted_values = sorted(values)
    if n % 2 == 1:
        return sorted_values[half_point]
    else:
        return (sorted_values[half_point-1] + sorted_values[half_point])/2

def read_results_file(filename = "./results.txt"):
    with open(filename, 'r') as f:
        lines = f.readlines()

    start = time.time()
    run_count = int(lines[0].split(', ')[0].split(': ')[1])
    lines = [iter for iter in lines if not iter.startswith("molecule")]
    lines = [iter.rstrip('\n').split(',') for iter in lines]
    duration = sum([float(iter[-1]) for iter in lines])/len(lines)
    success_rate = len(lines)/run_count * 100
    molecules = ['<s>'+iter[0].lstrip('<s>').rstrip('_')+'</s>' for iter in lines]
    print(f"Took {time.time() - start} seconds to read file")
    
    start = time.time()
    from transformers import GPT2TokenizerFast, GPT2LMHeadModel#, DataCollatorWithPadding#, pipeline

    tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=42) # increased length to allow for start and end
    model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')
    print(f"Took {time.time() - start} seconds to import")
    
    start = time.time()
    molecule_perplexity = []
    loss_by_token = []
    for mol in molecules:
        tokenize_input = tokenizer.tokenize(mol)
        tensor_input = torch.tensor([tokenizer.convert_tokens_to_ids(tokenize_input)])
        out = model(tensor_input, labels=tensor_input)
        molecule_perplexity.append(exp(out.loss.item()))
        
        loss_by_token.append([])
        last_contribution = -model(tensor_input[:,:2], labels=tensor_input[:,:2]).loss.item()
        loss_by_token[-1].append(last_contribution)
        for i in range(2, min(len(tensor_input[0]), 41)):
            out = -i * model(tensor_input[:,:i+1], labels=tensor_input[0,:i+1]).loss.item()
            loss_by_token[-1].append(out - last_contribution)
            if exp(out-last_contribution) > 1.001:
                print(f"Greater than one: {exp(out-last_contribution)}")
            last_contribution = out
    print(f"Took {time.time() - start} seconds to calculate loss")
    
    start = time.time()
    with open("server/molecule_perplexities.txt", 'w') as f:
        f.write('\n'.join(','.join(str(iter) for iter in line) for line in loss_by_token))
    print(f"Took {time.time() - start} seconds to write molecule perplexities")
    
    start = time.time()
    biggest_mol = max(len(iter) for iter in loss_by_token)
    position_loss = []
    for i in range(biggest_mol):
        position_loss.append([])
        for iter in loss_by_token:
            if i >= len(iter):
                continue
            position_loss[-1].append(iter[i])
    print(f"Took {time.time() - start} seconds to sum loss")
    
    # position_loss = [sum(iter)/len(iter) for iter in position_loss]
    position_loss = [median_value(iter) for iter in position_loss]
    with open("server/token_perplexity.txt", 'w') as f:
        f.write(','.join(str(iter) for iter in position_loss))
    
    perplexity = sum(molecule_perplexity)/len(molecules)
    print(f"Success rate: {success_rate}\nAvg PPL: {perplexity}\nAvg time: {duration/60}")

def perplexity_grapher(filename: str):
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    lines = [[-exp(float(iter)) for iter in line.split(',')] for line in lines]
    
    biggest_mol = max(len(line) for line in lines)
    for line in lines:
        while len(line) < biggest_mol:
            line.append(np.nan)
    
    masked_array = np.ma.array(lines, mask=np.isnan(lines))
    # cmap = matplotlib.cm.jet
    # cmap.set_bad('white',1.)
    # ax.imshow(masked_array, interpolation='nearest', cmap=cmap)
    
    # data = np.array(lines)
    plt.imshow(masked_array,alpha=1,cmap=mpl.colormaps['YlGn'])
    plt.show()

# From 300 to 325 there are 12% of molecules in ZINC
# generate_entropy(num_mol=1, molecule_weights=[2000,2750], run_nb=3)
# start = time.time()
# parallel_entropy(num_runs=5,num_mol_per_run=10,molecule_weights=[2000,2750])
# print("\n\n\n\n\This took " + str(time.time()-start) + " seconds")

start = time.time()
read_results_file("./server/data/results_nncpbp_15.txt")
print("\n\n\n\n\This took " + str(time.time()-start) + " seconds")
# perplexity_grapher("server/token_perplexity.txt")
# run_all(molecule_weights=[0,6000], remove_percent=0)
