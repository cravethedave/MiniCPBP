


rnd_based = set(['domWdegRandom', 'maxMarginalStrengthBiasedWheelSelectVal'])
heuristic_based = set(['maxMarginalStrengthLDS', 'maxMarginalStrength', 'maxMarginal', 'maxMarginalLDS'])
    
def get_median(data: list):
    middle = len(data) // 2
    if len(data) % 2 == 1:
        return data[middle]
    return (data[middle] + data[middle - 1]) / 2

def generic_parse_to_terminal(filename):
    with open(f"./new_memoire_data/{filename}", 'r') as f:
        lines = f.readlines()
    
    all_instances = set()
    data: dict[str,dict[str,list[tuple[int,int,int]]]] = {}
    for line in lines[1:]:
        values = line.split(',')
        params = values[0].split(' ')
        method = params[1]
        instance = ' '.join(params[7:-1])
        
        # compile all instances
        all_instances.add(instance)
        
        if method not in data.keys():
            data[method] = {}
        if instance not in data[method]:
            data[method][instance] = []
        data[method][instance].append((int(values[2]),int(values[3]),int(values[4])))
    
    header_size = max(len(iter) for iter in data.keys()) + 2
    column_size = header_size // 3 + 1
    header_size = 3 * column_size + 2
    method_line = f"|{''.center(header_size)}|"
    header_line = f"|{'wtMn wtMx logMn logMx c b'.center(header_size)}|"
    table_body: dict[str,str] = {}
    for method in data.keys():
        method_line += f"{method.center(header_size)}|"
        header_line += f"{'choice'.center(column_size)} {'fail'.center(column_size)} {'time'.center(column_size)}|"
        for instance in all_instances:
            if instance not in table_body.keys():
                instance_name = f"{instance}".center(header_size)
                table_body[instance] = f"|{instance_name}|"
            
            # No returns, timed out
            if instance not in data[method].keys():
                table_body[instance] += f"{'--'.center(header_size)}|"
                continue
            
            if method in heuristic_based:
                choice_string = f"{data[method][instance][0][0]}".center(column_size)
                fail_string = f"{data[method][instance][0][1]}".center(column_size)
                time_string = f"{data[method][instance][0][2]/1000.0:,.3f}".center(column_size)
                table_body[instance] += f"{choice_string} {fail_string} {time_string}|"
                continue
            
            # Random method that timed out
            if len(data[method][instance]) < 6:
                table_body[instance] += f"{'--'.center(header_size)}|"
                continue
            
            # Random method that did not time out
            choices = []
            fails = []
            times = []
            for c,f,t in data[method][instance]:
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
    
    print(method_line)
    print(header_line)
    for k,v in sorted(table_body.items(),key=lambda x: x[0]):
        print(v)

generic_parse_to_terminal("new_cp_data.csv")
# parse_to_terminal("new_regular.csv")
# parse_to_terminal("correct_regular_1.csv")