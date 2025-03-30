
import sys

if len(sys.argv) == 1:
    filename = "./server/data/results_cpbp_no_back.txt"
else:
    filename = sys.argv[1]

with open(filename, 'r') as f:
    lines = f.readlines()[2:]

unique = set(iter.split(',')[0] for iter in lines)

print(f"{len(unique)} molecules were unique out of {len(lines)}")