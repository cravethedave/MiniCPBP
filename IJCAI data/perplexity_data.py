import numpy as np 
import matplotlib.pyplot as plt
import matplotlib as mpl
from math import log

def reversed_perplexity(lines):
    data: list[list[float]] = []
    failed = 0
    for i, line in enumerate(lines):
        try:
            test = []
            for val in line.split(',')[:-1]:
                oracle_odds, nlp_odds = val.split('/')
                test.append(-float(nlp_odds))
                # test.append(-log(float(nlp_odds))) # Shows more clearly the procrastination
            test.reverse()
            for i in range(len(test), 40):
                test.append(-1.1)
            test.reverse()
        except ValueError:
            print(f"Failed line {i}")
            failed += 1
            continue
        data.append(test)

    print(f"failed {failed} out of {failed + len(data)}")

    return data

def perplexity(lines):
    data: list[list[float]] = []
    failed = 0
    for i, line in enumerate(lines):
        try:
            test = []
            for val in line.split(',')[:-1]:
                oracle_odds, nlp_odds = val.split('/')
                test.append(-float(nlp_odds))
                # test.append(-log(float(nlp_odds))) # Shows more clearly the procrastination
            for i in range(len(test), 40):
                test.append(-1)
        except ValueError:
            print(f"Failed line {i}")
            failed += 1
            continue
        data.append(test)

    print(f"failed {failed} out of {failed + len(data)}")

    return data

def reversed_log_perplexity(lines):
    data: list[list[float]] = []
    failed = 0
    for i, line in enumerate(lines):
        try:
            test = []
            for val in line.split(',')[:-1]:
                oracle_odds, nlp_odds = val.split('/')
                test.append(-log(float(nlp_odds))) # Shows more clearly the procrastination
            test.reverse()
            for i in range(len(test), 40):
                test.append(0)
            test.reverse()
        except ValueError:
            print(f"Failed line {i}")
            failed += 1
            continue
        data.append(test)

    print(f"failed {failed} out of {failed + len(data)}")

    return data

def log_perplexity(lines):
    data: list[list[float]] = []
    failed = 0
    for i, line in enumerate(lines):
        try:
            test = []
            for val in line.split(',')[:-1]:
                oracle_odds, nlp_odds = val.split('/')
                test.append(-log(float(nlp_odds))) # Shows more clearly the procrastination
            for i in range(len(test), 40):
                test.append(0)
        except ValueError:
            print(f"Failed line {i}")
            failed += 1
            continue
        data.append(test)

    print(f"failed {failed} out of {failed + len(data)}")

    return data

def averaged_perplexity(lines: list[str]):
    data: list[list[float]] = []
    failed = 0
    values = []
    
    #0.9382996382669323/0.8592135470296749,0.5401815105636673/0.23822124952278195,0.939987826556829/0.23255813953488363,0.985785082118369/0.47132478853321447,0.9895471880619695/0.6926734172755511,0.9357014282051418/0.9703512517308921,0.9979144730494885/0.9735570731452345,0.9938491789905038/0.5323065193464903,0.9996166680723363/0.998522051361284,0.9999572009228769/0.9998765322585078,0.7697043977045029/0.23255813953488363,0.9944414315115221/0.21831074650212506,0.9383805368471163/0.8826514507931793,0.8692973660648161/0.9716227735775252,0.9980742713479118/0.9892060304160442,0.9970773587399857/0.036856054944469994,0.9131747850736904/0.8079271745285806,0.8101345803667935/0.9361643660700814,0.994250082506258/0.9829823594171992,0.9629044856030148/0.018627166026804094,0.9396320497082209/0.8423769298879833,0.8541032856421044/0.9371878285242714,0.9925876705367014/0.9798713094533901,0.005999319970104489/0.0018086843495014424,0.9999674009630775/0.6649007567274878,0.6466742590968514/0.053168166489623477,0.9573188042667022/0.8446065266890037,0.024958563037153315/0.004746274568163456,0.8728218182773905/0.5756262986307628,0.8976580495737952/0.11091197866072264,0.8854773745557692/0.6627434047267284,0.2941468162383225/0.139277258347745,0.7591121055579184/0.5007572176414928,0.9848101398723434/0.9448618448050035,0.9986174180500582/0.9960768417464336,0.9951802499730483/0.9903092947659252,0.905633423367039/0.7821356444939502,0.3473137419211354/0.03865565739698558,0.7057279073780074/0.6466883907126902,1.0/0.9172256042115138,

    lines = [iter.split(',')[:-1] for iter in lines]
    lines = [[iter.split('/') for iter in line] for line in lines]
    
    for i in range(40):
        values.append([])
        for j, line in enumerate(lines):
            if len(line) <= i:
                continue
            try:
                values[-1].append(-float(line[i][1]))
            except ValueError:
                print(f"Failed line {j}, position {i}, value {line[i][1]}")
                failed += 1

    print(f"failed {failed} out of {failed + len(data)}")
    
    values = [sum(iter)/len(iter) for iter in values]

    return [values]

files = {
    "cpbp" : "IJCAI data/cpbp/perplexity.txt",
    "nn" : "IJCAI data/nn/perplexity.txt",
    "nncp" : "IJCAI data/nncp/perplexity.txt",
    "nncpbp_0.5" : "IJCAI data/nncpbp/0.5_perplexity.txt",
    "nncpbp_1.0" : "IJCAI data/nncpbp/1.0_perplexity.txt",
    "nncpbp_1.5" : "IJCAI data/nncpbp/1.5_perplexity.txt"
}

figure, axis = plt.subplots(3,2)

i = j = 0
for title, filename in files.items():
    if i == 3:
        i = 0
        j += 1
    print(i,j)
    
    with open(filename, 'r') as f:
        lines = f.readlines()[1:]
    data = averaged_perplexity(lines)
    
    axis[i,j].imshow(data, alpha=1, cmap=mpl.colormaps['YlGn'], vmin=-1.0, vmax=0.0, aspect='2.0')
    axis[i,j].set_title(title)
    axis[i,j].axes.get_yaxis().set_visible(False)
    
    i += 1

plt.subplots_adjust(wspace=0.1, hspace=-0.7)
plt.show()