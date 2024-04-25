
def eat(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
    lines = [iter.split(' weight')[0] for iter in lines]
    return [
        (
            iter.split('.txt:')[0].split('_')[2],
            iter.split('.txt:')[1].replace('_','')
        ) for iter in lines
    ]

molecules = eat('grep_results_30/molecules.txt')
molecules.extend(eat('grep_results_40/molecules.txt'))
molecules.extend(eat('maxMarginalStrengthLDS/molecules.txt'))
molecules = set(molecules)

with open('molecules.txt','w') as f:
    for method,mol in molecules:
        f.write(f"('{method}','{mol}'),\n")