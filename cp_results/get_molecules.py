import os

file_names = [iter for iter in os.listdir("./cp_results_lipinski/") if iter.startswith("slout") and iter.endswith('.txt')]

molecules = []
for name in file_names:
    with open(f"./cp_results_lipinski/{name}", 'r') as f:
        lines = f.readlines()
    
    file_mols = set([line.split(' ')[0].strip() for line in lines if 'weight of' in line])
    molecules.extend(file_mols)

print('\n'.join(molecules))