


# rnd_based = set(['maxMarginalStrengthBiasedWheelSelectVal','domWdegRandom'])
rnd_based = set(['domWdegRandom'])
# heuristic_based = set(['maxMarginalStrengthLDS', 'maxMarginalStrength', 'maxMarginal', 'maxMarginalLDS'])
heuristic_based = set([])

instances = [
    (1750, 2250, -400, -300),
    (1750, 2250, -200, -100),
    (1750, 2250, 100, 200),

    (2750, 3250, -400, -300),
    (2750, 3250, -200, -100),
    (2750, 3250, 100, 200),
    
    (3750, 4250, -400, -300),
    (3750, 4250, -200, -100),
    (3750, 4250, 100, 200),
]

def get_median(data: list):
    middle = len(data) // 2
    if len(data) % 2 == 1:
        return data[middle]
    return (data[middle] + data[middle - 1]) / 2

def parse_to_terminal(filename):
    with open(f"./new_memoire_data/{filename}", 'r') as f:
        lines = f.readlines()
    
    rnd_data: dict[str,dict[tuple[int,int,int,int],list[tuple[int,int,int]]]] = {}
    hrs_data: dict[str,dict[tuple[int,int,int,int],tuple[int,int,int]]] = {}
    # Creates a key value dict made up of:
    # Key:
    # Value: (choice, fail, time)
    for line in lines[1:]:
        values = line.split(',')
        params = values[0].split(' ')
        method = params[1]
        key = (int(params[-5]),int(params[-4]),int(params[-3]),int(params[-2]))
        value = (int(values[2]),int(values[3]),int(values[4]))
        
        if method in rnd_based:
            if method not in rnd_data:
                rnd_data[method] = {}
            
            if key not in rnd_data[method].keys():
                rnd_data[method][key] = []
            
            rnd_data[method][key].append(value)
        else:
            if method not in hrs_data:
                hrs_data[method] = {}
            
            hrs_data[method][key] = value
    
    
    
    header_size = len('maxMarginalStrengthBiasedWheelSelectVal') + 2
    column_size = header_size // 3 + 1
    header_size = 3 * column_size + 2
    method_line = f"|{''.center(header_size)}|"
    header_line = f"|{'instance'.center(header_size)}|"
    table_body: dict[tuple[int,int,int,int],str] = {}
    for method in rnd_based:
        method_line += f"{method.center(header_size)}|"
        header_line += f"{'choice'.center(column_size)} {'fail'.center(column_size)} {'time'.center(column_size)}|"
        for instance in instances:
            if instance not in table_body.keys():
                instance_name = f"{instance[:2]},{instance[2:]}".center(header_size)
                table_body[instance] = f"|{instance_name}|"
            
            if len(rnd_data[method][instance]) < 6:
                table_body[instance] += f"{'TIMED OUT'.center(header_size)}|"
                continue
            
            choices = []
            fails = []
            times = []
            for c,f,t in rnd_data[method][instance]:
                choices.append(c)
                fails.append(f)
                times.append(t)
            
            choices.sort()
            fails.sort()
            times.sort()
                
            choice_string = f"{get_median(choices)}".center(column_size)
            fail_string = f"{get_median(fails)}".center(column_size)
            time_string = f"{get_median(times)/1000.0:,.3f}".center(column_size)
            table_body[instance] += f"{choice_string} {fail_string} {time_string}|"
            
    for method in hrs_data:
        method_line += f"{method.center(header_size)}|"
        header_line += f"{'choice'.center(column_size)} {'fail'.center(column_size)} {'time'.center(column_size)}|"
        for instance in instances:
            if instance not in hrs_data[method].keys():
                table_body[instance] += f"{'TIMED OUT'.center(header_size)}|"
                continue
            # print(hrs_data[method][instance])

            choice_string = f"{hrs_data[method][instance][0]}".center(column_size)
            fail_string = f"{hrs_data[method][instance][1]}".center(column_size)
            time_string = f"{hrs_data[method][instance][2]/1000.0:,.3f}".center(column_size)
            table_body[instance] += f"{choice_string} {fail_string} {time_string}|"
    
    print(method_line)
    print(header_line)
    print('\n'.join(list(table_body.values())))
    
parse_to_terminal("new_shortTable.csv")
# parse_to_terminal("new_regular.csv")
# parse_to_terminal("correct_regular_1.csv")