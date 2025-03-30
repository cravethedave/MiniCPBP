filename = "results_1.5.csv"

with open(filename, 'r') as f:
    lines = f.readlines()

with open(filename, 'w') as f:
    for line in lines:
        if line == '\n':
            continue
        f.write(line)
