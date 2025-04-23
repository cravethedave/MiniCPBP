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
    "C(C(C(C(C(C(C(C(C(C(C(C(CCCS))))))))))))",
    "`C(C(C(C(C(C(C(C(C(C(C(C(CCCO))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCCCl))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCCN))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCCF))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCCC))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNCC))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNCS))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNCO))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNCCl))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNCN))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNCF))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSCF))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSCC))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSCS))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSCO))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSCCl))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSCN))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COCN))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COCF))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COCC))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COCS))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COCO))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COCCl))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CCl))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CBr))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CN))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CF))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CC))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CS))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=CO))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CO))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CCl))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CBr))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CN))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CF))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CC))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#CS))))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCC)O)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCC)S)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCC)Cl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCC)N)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCC)F)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CCC)C)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNC)C)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNC)O)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNC)S)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNC)Cl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNC)N)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CNC)F)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSC)F)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSC)C)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSC)O)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSC)S)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSC)Cl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(CSC)N)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COC)N)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COC)F)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COC)C)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COC)O)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COC)S)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(COC)Cl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)Cl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)Br)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)N)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)F)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)C)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)O)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C=C)S)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)S)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)Cl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)Br)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)N)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)F)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)C)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C#C)O)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)COO)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)COCl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)COS)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CON)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)COF)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)COC)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CSC)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CSO)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CSCl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CSS)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CSN)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CSF)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CCF)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CCC)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CCO)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CCCl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CCS)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CCN)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CNN)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CNF)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CNC)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CNO)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CNCl)))))))))))",
    "C(C(C(C(C(C(C(C(C(C(C(C(C)CNS)))))))))))"
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