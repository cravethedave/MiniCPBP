import os

file_names = [iter for iter in os.listdir("./cp_results_lipinski/") if iter.startswith("slout") and iter.endswith('.txt')]

# slout_40_domWdegRandom_1sols_600secs_c1_b3_10.txt
# dict of method name to dict of (c,b) to (time,fail)
array_results: dict[str,dict[tuple[bool,int,int],list[tuple[int,int]]]] = {}
for name in file_names:
    with open(f"./cp_results_lipinski/{name}", 'r') as f:
        lines = f.readlines()

    identifier_line = lines[1]
    n, method, _, _, _, _, _, min_weight, _, min_logP, max_logP = identifier_line.split(' ')
    min_weight = int(min_weight)
    min_logP = int(min_logP)
    max_logP = int(max_logP)

    if method not in array_results.keys():
        array_results[method] = {}

    instance = (min_weight,min_logP,max_logP)

    if instance not in array_results[method].keys():
        array_results[method][instance] = []

    # if the instance was not solved, we add a time out
    sol_line = [iter for iter in lines if iter.lstrip().startswith('#sols')]
    if len(sol_line) == 0:
        array_results[method][instance].append((-2,-2))
        continue
    if sol_line[0].split(':')[-1].strip() != '1':
        array_results[method][instance].append((-1,-1))
        continue

    time_line = int([iter for iter in lines if iter.lstrip().startswith('execution')][0].split(':')[-1].strip())
    fail_line = int([iter for iter in lines if iter.lstrip().startswith('#fail')][0].split(':')[-1].strip())
    array_results[method][instance].append((time_line,fail_line))

for method in array_results.keys():
    for instance in array_results[method].keys():
        if len(array_results[method][instance]) < 11 and len(array_results[method][instance]) > 1:
            print(method,instance,array_results[method][instance])

print("Done reading")
results: dict[str,dict[tuple[bool,int,int],tuple[int,int]]] = {}
for method in array_results.keys():
    results[method] = {}
    for instance in array_results[method].keys():
        # All deterministic methods take the only value as the real one
        if len(array_results[method][instance]) == 1:
            results[method][instance] = array_results[method][instance][0]
            continue
        # Timed out random instance
        timed_out_count = array_results[method][instance].count((-1,-1))
        memory_overload_count = array_results[method][instance].count((-2,-2))
        if timed_out_count + memory_overload_count >= 6:
            if memory_overload_count > 0:
                results[method][instance] = (-2,-2)
            else:
                results[method][instance] = (-1,-1)
        # Passed random instance, get median
        else:
            median_time = [a for a,_ in array_results[method][instance] if a >= 0][5]
            median_fail = [b for _,b in array_results[method][instance] if b >= 0][5]
            results[method][instance] = (median_time,median_fail)

print("Done creating results dict")
column_names = list(results.keys())
print(results)
column_sizes = max(max(len(iter) for iter in column_names),26)

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

for min_weight in [4750,4500,4000]:
    for min_logP, max_logP in [(-400,400),(-200,200),(-100,100)]:
        formatted_table += '|' + f"{min_weight}-5000_{min_logP}-{max_logP}".center(2 * column_sizes + 1) + '|'
        for iter in column_names:
            if (min_weight,min_logP,max_logP) not in results[iter].keys():
                formatted_table += '?'.center(column_sizes) + '|' + '?'.center(column_sizes) + '|'
                continue
            t,f = results[iter][(min_weight,min_logP,max_logP)]
            t,f = str(t), str(f)
            formatted_table += t.center(column_sizes) + '|' + f.center(column_sizes) + '|'
        formatted_table += '\n'

print(formatted_table)