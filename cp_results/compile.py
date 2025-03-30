import os

file_names = [iter for iter in os.listdir("cp_results") if iter.endswith('.txt')]

# dict of method name to dict of (c,b) to (time,fail)
results: dict[str,dict[tuple[int,int],tuple[int,int]]] = {}
for name in file_names:
    split_name = name.split('_')
    method = split_name[1]
    cycle_count = int(split_name[3].lstrip('cycle'))
    branch_count = int(split_name[4].lstrip('brnch'))
    
    if method not in results.keys():
        results[method] = {}
    
    with open(f"cp_results/{name}", 'r') as f:
        lines = f.readlines()
    
    solv_lines = [int(iter.split(': ')[-1].rstrip('\n')) for iter in lines if iter.startswith('\t#sols')]
    if (len(solv_lines) == 1 and solv_lines[0] == 0) or (len(solv_lines) == 11 and sum(solv_lines) < 6):
        results[method][(cycle_count,branch_count)] = (-1,-1)
        continue
    time_lines = [int(iter.split(': ')[-1].rstrip('\n')) for iter in lines if iter.startswith('\texecution')]
    fail_lines = [int(iter.split(': ')[-1].rstrip('\n')) for iter in lines if iter.startswith('\t#fail')]
    
    if len(time_lines) > 1:
        time_value = sorted(time_lines)[5]
        fail_value = sorted(fail_lines)[5]
    else:
        time_value = time_lines[0]
        fail_value = fail_lines[0]
    
    results[method][(cycle_count,branch_count)] = (time_value,fail_value)

column_names = list(results.keys())
column_sizes = max(len(iter) for iter in column_names)

if column_sizes % 2 == 0:
    column_sizes += 1

formatted_table = '|' + 'instance'.center(column_sizes) + '|'
for iter in column_names:
    formatted_table += iter.center(column_sizes) + '|'
formatted_table += '\n'

formatted_table += '|' + '_' * column_sizes + '|'
for iter in column_names:
    formatted_table += '_' * column_sizes + '|'
formatted_table += '\n'


formatted_table += '|' + ''.center(column_sizes) + '|'
column_sizes //= 2
for iter in column_names:
    formatted_table += 'time(s)'.center(column_sizes) + '|' + 'fails'.center(column_sizes) + '|'
formatted_table += '\n'

for c in [1,2,3]:
    for b in [2,3,4]:
        formatted_table += '|' + f"c{c}b{b}".center(2 * column_sizes + 1) + '|'
        for iter in column_names:
            t,f = results[iter][(c,b)]
            t,f = str(t), str(f)
            formatted_table += t.center(column_sizes) + '|' + f.center(column_sizes) + '|'
        formatted_table += '\n'

print(formatted_table)
