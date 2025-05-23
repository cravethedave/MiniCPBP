def tokenize(mol_string: str) -> 'list[str]':
    if len(mol_string) == 0:
        return []
    elif len(mol_string) == 1:
        return [mol_string]
    skip = 0
    n = len(mol_string)
    mol_array = []
    for i in range(n):
        if skip > 0:
            skip -= 1
            continue
        token = mol_string[i]
        if token == '%':
            skip = 2
            mol_array.append(mol_string[i:i+3])
        elif token == '<':
            if mol_string[i+1] == '/':
                skip = 3
                mol_array.append(mol_string[i:i+4])
            else:
                skip = 2
                mol_array.append(mol_string[i:i+3])
        elif i != n - 1 and token == 'C' and mol_string[i+1] == 'l':
            skip = 1
            mol_array.append('Cl')
        elif i != n - 1 and token == 'B' and mol_string[i+1] == 'r':
            skip = 1
            mol_array.append('Br')
        elif i != n - 1 and token == 'H' and mol_string[i+1] == '3':
            skip = 1
            mol_array.append('H3')
        else:
            mol_array.append(token)
            
    return mol_array

def fix(mol):
    skip = False
    seen_indices = {}
    conversion_table = {}
    final_mol = ""
    for i, c in enumerate(mol):
        if skip:
            if c == ']':
                skip = False
            final_mol += c
            continue
        if c == '[':
            final_mol += c
            skip = True
            continue
        if c.isdigit():
            if c not in seen_indices.keys():
                seen_indices[c] = 1
                final_mol += c
                continue
            if c in conversion_table.keys():
                if seen_indices[conversion_table[c]] == 2:
                    conversion_table[c] = str(len(seen_indices.keys()) + 1)
                    seen_indices[conversion_table[c]] = 1
                    final_mol += str(conversion_table[c])
                else:
                    seen_indices[conversion_table[c]] += 1
                    final_mol += str(conversion_table[c])
                continue
            if c not in conversion_table.values() and seen_indices[c] < 2:
                seen_indices[c] += 1
                final_mol += c
            else:
                conversion_table[c] = str(len(seen_indices.keys()) + 1)
                seen_indices[conversion_table[c]] = 1
                final_mol += str(conversion_table[c])
        else:
            final_mol += c
    return final_mol

def create():
    with open("big_data/ZINC250k.txt", 'r') as f:
        lines = f.readlines()

    # lines = [tokenize(iter)[:-1] for iter in lines]
    # lines = [iter for iter in lines if len(iter) <= 40 and len(iter) >= 35]
    # lines = [iter + ["_"] * (40 - len(iter)) + ['\n'] for iter in lines]
    # lines = [''.join(iter) for iter in lines]

    lines = [
        fix(''.join(tokens + ["_"] * (40 - len(tokens)) + ['\n']))
        for tokens in
        (tokenize(iter)[:-1] for iter in lines)
        if len(tokens) <= 40 and len(tokens) >= 35
    ]

    with open("big_data/small_ZINC.txt", 'w') as f:
        f.writelines(lines)

create()

# print(fix("c1ccc2c(c1)CC[C@H]([C@H]1CCCc3cccnc31)N2"))
