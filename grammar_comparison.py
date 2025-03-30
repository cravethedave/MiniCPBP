
from rdkit import Chem
from rdkit import RDLogger

RDLogger.DisableLog('rdApp.info')

# Read files
with open("cp_data/no_lipinski/results_size10_v12.txt", 'r') as f:
    old_lines = f.readlines()

with open("results_size10_v12.txt", 'r') as f:
    new_lines = f.readlines()

# Extract molecules
print("--- Unique generated chains ---")
old_mols = set(iter.split(',')[0] for iter in old_lines)
new_mols = set(iter.split(',')[0] for iter in new_lines)
print(f"Old grammar: {len(old_mols)}")
print(f"New grammar: {len(new_mols)}")
print('')

# Translate custom tokens to SMILES tokens
print("--- Unique translated chains ---")
old_mols_translated = set(iter.replace('T','N').replace('X','O').replace('R','S') for iter in old_mols)
new_mols_translated = set(iter.replace('T','N').replace('X','O').replace('R','S') for iter in new_mols)
print(f"Old grammar: {len(old_mols_translated)}")
print(f"New grammar: {len(new_mols_translated)}")
print('')

# Common molecules
print("--- Shared/Unaccounted chains ---")
# common = old_mols.intersection(new_mols)
# print(f"There are {len(common)} common molecules")
common_translated = old_mols_translated.intersection(new_mols_translated)
print(f"Common to both: {len(common_translated)}")

# Find different molecules
# old_unique = old_mols.difference(common)
# new_unique = new_mols.difference(common)
# print(f"There are {len(old_unique)} unique old molecules and {len(new_unique)} unique new ones")

old_unique_translated = old_mols_translated - new_mols_translated
new_unique_translated = new_mols_translated - old_mols_translated
print(f"Only in old: {len(old_unique_translated)}")
print(f"Only in new: {len(new_unique_translated)}")
print('')

# Canonical common and differences
print("--- Shared/Unaccounted SMILES molecules ---")
common_canon = set(Chem.CanonSmiles(iter.rstrip('_')) for iter in common_translated)
old_canon = set(Chem.CanonSmiles(iter.rstrip('_')) for iter in old_unique_translated) - common_canon
new_canon = set(Chem.CanonSmiles(iter.rstrip('_')) for iter in new_unique_translated) - common_canon
common_canon = common_canon.union(old_canon.intersection(new_canon))
old_canon_unique = old_canon - common_canon
new_canon_unique = new_canon - common_canon
print(f"Total in old: {len(old_canon_unique) + len(common_canon)}")
print(f"Total in new: {len(new_canon_unique) + len(common_canon)}")
print('')
print(f"Common to both: {len(common_canon)}")
print(f"Only in old: {len(old_canon_unique)}")
print(f"Only in new: {len(new_canon_unique)}")
