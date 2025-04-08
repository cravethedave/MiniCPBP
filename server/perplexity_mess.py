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
    "C(CC(C(TC(C(C)(C1CTC1)))F)(C(T)(X)))C___",
    "C(C(C(C(C1CC(CTC)C1(X))(CN=CTT))))C_____",
    "C(CC(CC#CCTCCOCCTCCCCCCCCOCCC1CTCTC1))C_",
    "C(CC(C(TC(C(C)(C1CTC1)))))(C(T)(X))SC___",
    "C1CC(CC)C(CCCC(CTCCSCOCCTCTCCTCCCCTC))C1",
    "C(CC(CC#CC#CCTCTCTCCCOCTC1CTCC1))C______",
    "C1CC(C)C(CC(CCTCCOC)CTC(C2CCCTC2))C1____",
    "C(C(C(C(C#CC(C1CTC1(T))(CCT)C)X)))C_____",
    "C(CC(C(=C(C(CTTC1CTC1)))F)=C(T)(T))=C___",
    "C(CC(C(=C(C(C)(C1CTC1)))X)(C(F)(R)))C___",
    "C(CC(CC=CCTCCTCCOCCTCCOCCOCOC1CTCTC1))C_",
    "C(CC(CC1CC=CC(CTCTCTCCTCC2CCC2)C1))C____",
    "C(CC(C(=C(C(CS(C1CTC1))(T)T))C)(T))TC___",
    "C(C(C(C(CCCC(C1CTC1(T))(COT)X)R)))C_____",
    "C1CC(CTC(CC#CC#CCTCOCTCOC2CTCTC2))C1____",
    "C1CC(CC)C(COCC(CTCCSC#CCTCOCCTCCTCTC))C1",
    "C(CC(CC1CCTCC(C=CTCOCCTCC#CCTT)C1))C____",
    "C(CC(C(TC(C(C)(C1CTC1)))X)(C(C)(Cl)))C__",
    "C(CC(CC1CC=CC(CTCTCTCCTCC2CCC2)C1))C____",
    "CTCCOCCTCCOCCOCCTCC(TC)(COCC1(CCSCF))C1_",
    "C(C(C(C(C1CC(C)CTC1(X))(C)C)(TR)))C_____",
    "C1CC(C)C(CC(CCTCCOC)CTC(C2CCCTC2))C1____",
    "C(C(C(C(C(C1CTCC1)=T)TC2CCTTC2)))C______",
    "C1CC(CC)C(CCCC(CTCCSCOCCTCTCCTCCCCTC))C1",
    "C(CC(CCTCC#CCTCCOCC#CCOCCTCCC1CTCTC1))CF",
    "C1CC(C)C(CCOCCOCCTCTCTC(C2CTCTC2))C1____",
    "C1CC(CC)C(C(CCTCOCCTCOCCTCOCCTCCTCOC))C1",
    "C(CC(CCTCCOCCTCCTCCCCCCCCOCOCTC1CTC1))C_",
    "C(C(C(C(C#CC(C1CTC1(T))(CTC)T)T)))C_____",
    "C1CC(C=C(CC=CCTCCOCTCTCOC2CTCTC2))C1____",
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