from transformers import GPT2TokenizerFast, GPT2LMHeadModel

from rdkit.Chem import AllChem as Chem
# from rdkit.Chem.rdchem import Mol as Mol
# from rdkit.Chem.Descriptors import ExactMolWt

tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')

filename = "results_size40_v7.txt"

with open(filename, 'r') as f:
    lines = f.readlines()

if lines[0].startswith('molecule'):
    lines = lines[2:]




# molecule = Chem.MolFromSmiles(mol.rstrip('\n'))
