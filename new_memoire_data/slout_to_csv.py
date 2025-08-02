import os

files = [iter for iter in os.listdir() if iter.startswith('slout')]

file_lines = [open(iter,'r').readlines() for iter in files]

filtered = [[iter.strip() for iter in file if 'JAVA' not in iter] for file in file_lines]
filtered = [[iter.strip() for iter in file if not iter.startswith('stateMap')] for file in filtered]
filtered = [[iter.strip() for iter in file if not iter.startswith('[INFO]')] for file in filtered]
filtered = [[iter.strip() for iter in file if not iter.startswith('c Warning')] for file in filtered]
filtered = [[iter.strip() for iter in file if not iter.startswith('#sols')] for file in filtered]
filtered = [[iter.strip() for iter in file if not iter.startswith('completed')] for file in filtered]
filtered = [[iter.strip() for iter in file if not iter.startswith('slurmstepd')] for file in filtered]

filtered = [[iter for iter in file if iter != '642'] for file in filtered]
filtered = [[iter for iter in file if iter != 'Restarts'] for file in filtered]
filtered = [[iter for iter in file if iter != 'No restarts'] for file in filtered]
filtered = [[iter for iter in file if iter != 'domWdegRandom'] for file in filtered]
filtered = [[iter for iter in file if iter != 'maxMarginal'] for file in filtered]
filtered = [[iter for iter in file if iter != 'maxMarginalLDS'] for file in filtered]
filtered = [[iter for iter in file if iter != 'maxMarginalStrength'] for file in filtered]
filtered = [[iter for iter in file if iter != 'maxMarginalStrengthLDS'] for file in filtered]
filtered = [[iter for iter in file if iter != ''] for file in filtered]

filtered = [iter for iter in filtered if len(iter) != 1]

for file in filtered:
    molecule, weight, logp = file[1].split(',')
    molecule.strip("'")
    file.append(weight)
    file.append(logp)
    file[1] = molecule.strip('\"')
    file[2] = file[2].split(': ')[-1]
    file[3] = file[3].split(': ')[-1]
    file[4] = file[4].split(': ')[-1]

output = '\n'.join(','.join(iter) for iter in filtered)
print(output)
