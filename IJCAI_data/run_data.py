def get_weight_perplexity(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
    wanted_molecule_count = int(lines[0].split(', ')[0].split(': ')[1])
    data = [iter.lstrip('<s>').rstrip('\n') for iter in lines if not iter.startswith("molecule")]
    data = [iter.split(',') for iter in data]
    perplexity = sum(float(iter[2]) for iter in data)/len(data)
    avg_time = sum(float(iter[3]) for iter in data)/len(data)
    
    print(f"Success rate: {(len(data) / wanted_molecule_count) * 100}%")
    print(f"Perplexity: {perplexity}")
    print(f"Time: {avg_time}s or {avg_time/60}m")

get_weight_perplexity("IJCAI data/cpbp/results.txt")