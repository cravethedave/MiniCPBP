import torch
from sample_creator import tokenize
from transformers import GPT2TokenizerFast, GPT2LMHeadModel, DataCollatorWithPadding#, pipeline

TOKENS = {
    'F',
    'Cl',
    'Br',
    'I',
    'O',
    'N',
    'S',
    'C',
    '[',
    ']',
    '-',
    '+',
    '@',
    'H',
    'H3',
    '1',
    '2',
    '3',
    '4',
    '5',
    '6',
    '7',
    '8',
    '=',
    '/',
    '\\',
    '(',
    ')',
    '#',
    'n',
    'c',
    'o',
    's'
}

tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')

molecules = [
"CC(C)c1nnc(N2CCC(NC(=O)C3CC3)CC2)n1C[C",
"O=C(NCc1ccc(OCCN2CCCC2)cc1)N1CCC[C@H]1c1ccsc1",
"C[C@@H]1C[C@@H](NCc2nc(C3CC3)nn2C)CN1C",
"C[C@H]1[C@@H](NC(=O)C(F)(F)F)CCCN1C(=O",
"C[C@@H]1CN(C(=O)C2=COCCC2)C[C@H]1CNC(=O",
"CC(C)[C@@H](CNC(=O)c1cc(C(F)(F)F)n[nH]1",
]

for m,mol in enumerate(molecules):
    tokens = tokenize(mol.lstrip('<s>').rstrip('\n'))
    current = "<s>"
    perplexity_result = ""
    failed = False
    print(f"molecule {m}")
    for i, t in enumerate(tokens):
        inputs = torch.tensor([tokenizer(current)['input_ids']])
        outputs = model.generate(
            inputs,
            do_sample=True,
            # max_length=40,
            temperature=1.5,
            max_new_tokens=1,
            # early_stopping=True,
            return_dict_in_generate=True,
            output_scores=True,
            pad_token_id=tokenizer.pad_token_id,
            num_return_sequences=512
        )
        transition_scores = torch.exp(model.compute_transition_scores(outputs.sequences, outputs.scores, normalize_logits=True)).flatten().tolist()
        generated_tokens = [tokenizer.decode(token) for token in outputs.sequences[:, -1:].flatten()]
        big_token_prob = {generated_tokens[i]:transition_scores[i] for i in range(len(generated_tokens))}
        real_token_prob = {}

        for k,v in big_token_prob.items():
            token = tokenize(k)[0]
            if token.startswith('</s'):
                token = '_'
            if token not in real_token_prob.keys():
                real_token_prob[token] = 0
            real_token_prob[token] += v
        
        summed_prob = sum(real_token_prob.values())
        for k,v in real_token_prob.items():
            real_token_prob[k] = v/summed_prob
        
        smallest_value = min(real_token_prob.values())
        for token in TOKENS:
            if token not in real_token_prob.keys():
                real_token_prob[token] = smallest_value/10
        
        summed_prob = sum(real_token_prob.values())
        for k,v in real_token_prob.items():
            real_token_prob[k] = v/summed_prob
            
        if tokens[i] in real_token_prob.keys():
            perplexity_result += f"{real_token_prob[tokens[i]]},"
            current += tokens[i]
        else:
            print("########################## ERROR ##########################")
            failed = True
            break
    perplexity_result += '\n'
    
    with open("perplexity.txt", 'a') as f:
        f.write(perplexity_result)