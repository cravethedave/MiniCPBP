from numpy import arange
import matplotlib.pyplot as plt

with open('new_memoire_data/combined_test_official.csv', 'r') as f:
    lines = f.readlines()

split_lines = [iter.rstrip().split(',') for iter in lines[1:]]
sp  = [[float(iter) for iter in line[1].split(' ')] for line in split_lines if line[1] != '--']
bp  = [[float(iter) for iter in line[2].split(' ')] for line in split_lines if line[2] != '--']
nbp = [[float(iter) for iter in line[3].split(' ')] for line in split_lines if line[3] != '--']

print(min(iter[1] for iter in sp),min(iter[1] for iter in bp),min(iter[1] for iter in nbp))
print(max(iter[1] for iter in sp),max(iter[1] for iter in bp),max(iter[1] for iter in nbp))
print(min(iter[2] for iter in sp),min(iter[2] for iter in bp),min(iter[2] for iter in nbp))
print(max(iter[2] for iter in sp),max(iter[2] for iter in bp),max(iter[2] for iter in nbp))

font = {
    'family' : 'normal',
    # 'weight' : 'bold',
    'size'   : 16
}

plt.rc('font', **font)

plt.xlabel("Fails")
plt.xticks(arange(0,1500,step=150))
plt.xscale('log')
plt.ylabel("Time (s)")
plt.yticks(arange(0,1200,step=150))
# plt.yscale('log')
plt.scatter([iter[1] for iter in sp],[iter[2] for iter in sp], marker='x', label='CP')
plt.scatter([iter[1] for iter in bp],[iter[2] for iter in bp], marker='d', label='CPBP')
plt.scatter([iter[1] for iter in nbp],[iter[2] for iter in nbp], marker='^', label=r'CPBP$^-$')
plt.legend()
plt.show()
